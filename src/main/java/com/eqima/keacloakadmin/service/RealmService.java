package com.eqima.keacloakadmin.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmsResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.eqima.keacloakadmin.models.dto.RealmRepresentationDto;
import com.eqima.keacloakadmin.models.entity.KeycloakClient;
import com.eqima.keacloakadmin.models.entity.KeycloakUser;
import com.eqima.keacloakadmin.models.request.CreateClientRequest;
import com.eqima.keacloakadmin.models.request.CreateRealmRequest;
import com.eqima.keacloakadmin.models.request.RealmRequest;
import com.eqima.keacloakadmin.util.SecurityUtils;
@Service
public class RealmService {
    private final Keycloak keycloak;

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    private final ClientService clientService;

    private final UserService userService;

    private final AuditService auditService;

    @Autowired
    private EncryptionService encryptionService;

    public RealmService(
        ClientService clientService, 
        UserService userService,  
        @Qualifier("keycloakAdmin") Keycloak keycloakAdmin,
        AuditService auditService
    ) {
        this.keycloak = keycloakAdmin;
        this.clientService = clientService;
        this.userService = userService;
        this.auditService = auditService;
    }

    private RealmsResource realms() {
        return this.keycloak.realms();
    }

    public String createRealm(CreateRealmRequest realmRequest) throws Exception {

// 1) Prepare a new realm representation
        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm(realmRequest.realm());
        realm.setEnabled(true);

// 2) Create the realm
        this.realms().create(realm);

// 3) Confirm creation
        boolean exists = this.realms().findAll().stream()
                        .anyMatch(r -> r.getRealm().equals(realmRequest.realm()));
        if (!exists) {
            throw new RuntimeException("Failed to create realm");
        }

// 4) Grant Client and User management roles on the new realm to the master realm        
        grantClientManagementRoles("master", realmRequest.realm());

// 5) Create a client in the new realm
        CreateClientRequest clientRequest = new CreateClientRequest(
            realmRequest.realm(),
            realmRequest.realm() + "-cli",
            "http://localhost:4200",
            false
        );
        String uuid = clientService.createClient(clientRequest);

// 5.1) Grant User-manager role to the new client of the realm
        clientService.addUserManagementRoles(clientRequest, uuid);

// 5.2) Save the client details and encrypted credentials in MySQL Database
        KeycloakClient newRealmClient = clientService.mysqlSaveClient(clientRequest, uuid);

// 6) Connect to the newly create Realm as the newly created Client
        Keycloak userKeycloak = KeycloakBuilder.builder()
            .serverUrl(authServerUrl)
            .realm(realmRequest.realm())
            .clientId(realmRequest.realm()+"-cli")
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .clientSecret(encryptionService.decrypt(newRealmClient.getClientSecret()))
            .build();

// 7) Create a user in the new realm
        userService.createUser(
            realmRequest.userRequest(), userKeycloak, realmRequest.realm()
        );
// 7.1) Save the manager user in MySQL database joined to the new client
        KeycloakUser userMysql = new KeycloakUser();
        userMysql.setUsername(realmRequest.userRequest().username());
        userMysql.setFirstname(realmRequest.userRequest().firstname());
        userMysql.setLastname(realmRequest.userRequest().lastname());
        userMysql.setEmail(realmRequest.userRequest().email());
        // userMysql.setKeycloakClient(newRealmClient);

        userService.mysqlSaveKeycloakUser(userMysql);

        auditService.log("CREATE_REALM",  null, Map.of("realmName", realmRequest.realm()));

        return realmRequest.realm();
    }

    public List<RealmRepresentationDto> fetchRealm() {
        return keycloak.realms().findAll().stream()
        .map(this::toDto)
        .collect(Collectors.toList());
    }

    public RealmRepresentationDto toDto(RealmRepresentation realm) {
        return new RealmRepresentationDto(
            realm.getId(),
            realm.getRealm(),
            realm.isEnabled(),
            realm.getDisplayName()
        );
    }

    public String grantClientManagementRoles(String masterRealm,String realmName){
        ClientRepresentation clientRepresentation = keycloak.realm(masterRealm).clients().findByClientId("admin-cli").get(0);
        
        UserRepresentation svcUser = keycloak.realm(masterRealm)
            .clients().get(clientRepresentation.getId())
            .getServiceAccountUser();

        ClientRepresentation realmMgmtClient = keycloak.realm(masterRealm).clients().findByClientId(realmName+"-realm").get(0);

        List<RoleRepresentation> rolesToAssign =
        keycloak.realm(masterRealm)
                .clients()
                .get(realmMgmtClient.getId())
                .roles()
                .list()
                .stream()
                .filter(r -> List.of(
                        "manage-clients",
                        "manage-users"
                ).contains(r.getName()))
                .toList();
        
        keycloak.realm(masterRealm)
            .users()
            .get(svcUser.getId())
            .roles().clientLevel(realmMgmtClient.getId())
            .add(rolesToAssign);
        
        return realmName;
    }

    // public RealmRepresentationDto updateRealm(RealmRequest realmRequest) {
    //     keycloak.
    // }

}

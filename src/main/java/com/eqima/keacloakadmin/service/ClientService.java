package com.eqima.keacloakadmin.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.eqima.keacloakadmin.dto.ClientDetailsDto;
import com.eqima.keacloakadmin.dto.ClientRepresentationDto;
import com.eqima.keacloakadmin.dto.ClientRequest;
import com.eqima.keacloakadmin.entity.KeycloakClient;
import com.eqima.keacloakadmin.exception.ResourceNotFoundException;
import com.eqima.keacloakadmin.repository.KeycloakClientRepository;
import com.eqima.keacloakadmin.request.CreateClientRequest;
import com.eqima.keacloakadmin.util.SecurityUtils;

import jakarta.persistence.EntityNotFoundException;
import jakarta.ws.rs.core.Response;

@Service
public class ClientService {

    private static final Set<String> DEFAULT_CLIENTS = Set.of(
        "account",
        "account-console",
        "admin-cli",
        "broker",
        "realm-management",
        "security-admin-console"
    );

    private final Keycloak keycloakClient;

    private final Keycloak keycloakAdmin;

    private final KeycloakClientRepository keycloakClientRepository;

    private final AuditService auditService;

    @Autowired
    private EncryptionService encryptionService;

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;
    @Value("${keycloak.realm}")
    private String realmName;

    public ClientService(@Qualifier("keycloakClient") Keycloak keycloakClient, @Qualifier("keycloakAdmin") Keycloak keycloakAdmin, KeycloakClientRepository keycloakClientRepository, AuditService auditService) {
        this.keycloakClient = keycloakClient;
        this.keycloakAdmin = keycloakAdmin;
        this.keycloakClientRepository = keycloakClientRepository;
        this.auditService = auditService;
    }

//Create the client in a specific realm in keycloak
    public String createClient(CreateClientRequest clientRequest) throws Exception{

        String realm = clientRequest.realmName();
        
        ClientRepresentation client = new ClientRepresentation();

        client.setClientId(clientRequest.clientId());
        client.setEnabled(true);
        client.setPublicClient(clientRequest.publicClient());
        client.setProtocol("openid-connect");
        if(clientRequest.publicClient()) {
            client.setWebOrigins(List.of(clientRequest.uri()));
            client.setRedirectUris(List.of(clientRequest.uri()+"/*"));
        } else {
            client.setClientAuthenticatorType("client-secret");
            client.setServiceAccountsEnabled(true);
        }

        Response response = keycloakClient.realm(realm).clients().create(client);

        if (!(response.getStatusInfo().getFamily()==Response.Status.Family.SUCCESSFUL)) {
            throw new RuntimeException("Failed to create Keycloak client: " + response.getStatusInfo().getReasonPhrase());
        }

        auditService.log("CREATE_CLIENT",  null, Map.of("clientId", clientRequest.clientId(), "realm", realm));

        String clientUuid = CreatedResponseUtil.getCreatedId(response);

        return clientUuid;
    }

//Get list of clients/Applications in a realm
    public List<ClientRepresentationDto> fetchClients() {
        RealmResource realmResource = keycloakClient.realm(realmName);
        ClientsResource clientsResource = realmResource.clients();

        // fetch all clients
        return clientsResource.findAll()
        .stream()
        .filter(client -> !DEFAULT_CLIENTS.contains(client.getClientId()))
        .map(this::toDto)
        .collect(Collectors.toList());
    }

//Add user management roles to a realm client
    public String addUserManagementRoles(CreateClientRequest createClientRequest, String clientUuid){
        String realm = createClientRequest.realmName();
        String clientId = createClientRequest.clientId();
        ClientRepresentation clientRepresentation = keycloakClient.realm(realm).clients().findByClientId(clientId).get(0);
        
        UserRepresentation svcUser = keycloakClient.realm(realm)
            .clients().get(clientRepresentation.getId())
            .getServiceAccountUser();

        ClientRepresentation realmMgmtClient = keycloakClient.realm(realm) .clients().findByClientId("realm-management").get(0);

        List<RoleRepresentation> rolesToAssign =
        keycloakClient.realm(realm)
                .clients()
                .get(realmMgmtClient.getId())
                .roles()
                .list()
                .stream()
                .filter(r -> List.of(
                        "manage-users",
                        "view-users",
                        "query-users"
                ).contains(r.getName()))
                .toList();
        
        keycloakClient.realm(realm)
            .users()
            .get(svcUser.getId())
            .roles().clientLevel(realmMgmtClient.getId())
            .add(rolesToAssign);

        auditService.log("ASSIGN_ROLES",  null, Map.of("clientId", clientId, "realm", realm, "rolesAssigned", rolesToAssign.stream().map(RoleRepresentation::getName).toList()));
        
        return clientUuid;
    }
    public String addUserManagementRoles(CreateClientRequest createClientRequest, String clientUuid,Keycloak keycloak){
        String realm = createClientRequest.realmName();
        String clientId = createClientRequest.clientId();
        ClientRepresentation clientRepresentation = keycloak.realm(realm).clients().findByClientId(clientId).get(0);
        
        UserRepresentation svcUser = keycloak.realm(realm)
            .clients().get(clientRepresentation.getId())
            .getServiceAccountUser();

        ClientRepresentation realmMgmtClient = keycloak.realm(realm) .clients().findByClientId("realm-management").get(0);

        List<RoleRepresentation> rolesToAssign =
        keycloak.realm(realm)
                .clients()
                .get(realmMgmtClient.getId())
                .roles()
                .list()
                .stream()
                .filter(r -> List.of(
                        "manage-users",
                        "view-users",
                        "query-users"
                ).contains(r.getName()))
                .toList();
        
        keycloak.realm(realm)
            .users()
            .get(svcUser.getId())
            .roles().clientLevel(realmMgmtClient.getId())
            .add(rolesToAssign);

        auditService.log("ASSIGN_ROLES",  null, Map.of("clientId", clientId, "realm", realm, "rolesAssigned", rolesToAssign.stream().map(RoleRepresentation::getName).toList()));
        
        return clientUuid;
    }

//Save keycloak client in mysql
    public KeycloakClient mysqlSaveClient(CreateClientRequest createClientRequest,String clientUuid) throws Exception {
        String realm = createClientRequest.realmName();
        String clientId = createClientRequest.clientId();

        String secret = keycloakClient.realm(realm)
                                .clients()
                                .get(clientUuid)
                                .getSecret()
                                .getValue();

        String encryptedSecret = encryptionService.encrypt(secret);
        
        String issuerUri = authServerUrl+"/realms/"+realm;

        KeycloakClient newClient = new KeycloakClient();
        newClient.setRealmName(realm);
        newClient.setClientId(clientId);
        newClient.setClientSecret(encryptedSecret);
        newClient.setIssuerUri(issuerUri);

        auditService.log("SAVE_CLIENT",  null, Map.of("clientId", clientId, "realm", realm, "issuerUri", issuerUri));

        return keycloakClientRepository.save(newClient);
    }

//Retrieve client in mysql by issuer_uri
    public KeycloakClient findClientByAccessTokenIssuer() throws Exception {   
//List informations from access token in Authorization Bearer Token
//iss (realm path)
//azp (client-id)
        String issuer = ((Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getIssuer().toString();

        KeycloakClient client = keycloakClientRepository.findByIssuerUri(issuer).orElseThrow(() -> new EntityNotFoundException("Client not found"));

        client.setClientSecret(encryptionService.decrypt(client.getClientSecret()));

        return client;
    }

    // ClientService
// ClientService
    public List<ClientRepresentationDto> findClientsManagedByCurrentUser() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess == null || resourceAccess.isEmpty()) {
            return List.of();
        }
        Set<String> managedClientIds = resourceAccess.keySet();

        return fetchClients().stream() // fetchClients() already drops DEFAULT_CLIENTS (account, broker, etc.)
            .filter(c -> managedClientIds.contains(c.getClientId()))
            .collect(Collectors.toList());
    }

    public List<ClientRepresentationDto> fetchClientsForRealm(String realmName) {
        RealmResource realmResource = keycloakAdmin.realm(realmName);
        ClientsResource clientsResource = realmResource.clients();

        return clientsResource.findAll().stream()
        .map(this::toDto)
        .collect(Collectors.toList());
    }

    public ClientRepresentationDto toDto(ClientRepresentation client) {
        return new ClientRepresentationDto(
            client.getId(),
            client.getClientId(),
            client.getName(),
            client.isEnabled(),
            client.isPublicClient(),
            client.isBearerOnly()
        );
    }
    public KeycloakClient findClientByRealmName(String realmName){
        if(keycloakClientRepository.findByRealmName(realmName).isEmpty()){
            throw new ResourceNotFoundException("Client inexistant.");
        }
        return keycloakClientRepository.findByRealmName(realmName).get();
    }

    public ClientDetailsDto getDetails(String clientId) {
        ClientRepresentation r = keycloakClient.realm(realmName).clients().findByClientId(clientId)
        .stream().findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("Client inexistant."));

        return new ClientDetailsDto(r.getId(), r.getClientId(), r.getName(),
            r.isEnabled(), r.isPublicClient(), r.isBearerOnly(),
            r.getProtocol(), r.getRedirectUris(), r.getWebOrigins());
    }

    public void updateClient(ClientRequest clientRequest, String clientId) {

        ClientRepresentation clientRepresentation = keycloakClient.realm(clientRequest.realmName()).clients().findByClientId(clientId).get(0);

        clientRepresentation.setName(clientRequest.name());
        clientRepresentation.setRedirectUris(List.of(clientRequest.uri()+"/*"));
        clientRepresentation.setWebOrigins(List.of(clientRequest.uri()));
        clientRepresentation.setEnabled(clientRequest.enabled());

        keycloakClient.realm(clientRequest.realmName()).clients().get(clientRepresentation.getId()).update(clientRepresentation);

        auditService.log("UPDATE_CLIENT",  null, Map.of("clientId", clientId, "realm", clientRequest.realmName(), "name", clientRequest.name(), "enabled", clientRequest.enabled(), "redirectUris", List.of(clientRequest.uri()+"/*"), "webOrigins", List.of(clientRequest.uri())));
    }

    public void updateClientStatus(boolean status, String clientId) {

        ClientRepresentation clientRepresentation = keycloakClient.realm(realmName).clients().findByClientId(clientId).get(0);

        clientRepresentation.setEnabled(status);

        keycloakClient.realm(realmName).clients().get(clientRepresentation.getId()).update(clientRepresentation);

        auditService.log("UPDATE_CLIENT_STATUS",  null, Map.of("clientId", clientId, "realm", realmName, "enabled", status));
    }

    public void updateRedirectUris(List<String> redirectUris, String clientId) {

        ClientRepresentation clientRepresentation = keycloakClient.realm(realmName).clients().findByClientId(clientId).get(0);

        clientRepresentation.setRedirectUris(redirectUris.stream().map(uri -> uri + "/*").collect(Collectors.toList()));
        clientRepresentation.setWebOrigins(redirectUris);

        keycloakClient.realm(realmName).clients().get(clientRepresentation.getId()).update(clientRepresentation);
        keycloakClientRepository.findByRealmName(realmName).ifPresent(client -> {
            client.setIssuerUri(redirectUris.get(0));
            keycloakClientRepository.save(client);
        });

        auditService.log("UPDATE_REDIRECT_URIS",  null, Map.of("clientId", clientId, "realm", realmName, "redirectUris", redirectUris.stream().map(uri -> uri + "/*").collect(Collectors.toList()), "webOrigins", redirectUris));
    }

    public void deleteClient(String clientId) {
        ClientRepresentation clientRepresentation = keycloakClient.realm(realmName).clients().findByClientId(clientId).get(0);
        keycloakClient.realm(realmName).clients().get(clientRepresentation.getId()).remove();

        auditService.log("DELETE_CLIENT",  null, Map.of("clientId", clientId, "realm", realmName));
    }

    public KeycloakClient rotateSecret(String clientId) throws Exception {
        ClientRepresentation clientRepresentation = keycloakClient.realm(realmName).clients().findByClientId(clientId).get(0);
        String newSecret = keycloakClient.realm(realmName).clients().get(clientRepresentation.getId()).generateNewSecret().getValue();
        String encryptedSecret = encryptionService.encrypt(newSecret);

        auditService.log("ROTATE_CLIENT_SECRET",  null, Map.of("clientId", clientId, "realm", realmName));

        KeycloakClient existingClient = keycloakClientRepository.findByRealmName(realmName)
                .stream()
                .filter(client -> client.getClientId().equals(clientId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Client not found in database"));
        existingClient.setClientSecret(encryptedSecret);
        return keycloakClientRepository.save(existingClient);
    }

    public String getClientSecret(String clientId) throws Exception {
        KeycloakClient existingClient = keycloakClientRepository.findByRealmName(realmName)
                .stream()
                .filter(client -> client.getClientId().equals(clientId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Client not found in database"));
        return encryptionService.decrypt(existingClient.getClientSecret());
    }
}

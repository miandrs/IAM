package com.eqima.keacloakadmin.config;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.eqima.keacloakadmin.models.entity.KeycloakClient;
import com.eqima.keacloakadmin.models.entity.KeycloakUser;
import com.eqima.keacloakadmin.models.entity.Organization;
import com.eqima.keacloakadmin.repository.KeycloakClientRepository;
import com.eqima.keacloakadmin.repository.KeycloakUserRepository;
import com.eqima.keacloakadmin.repository.OrganizationRepository;
import com.eqima.keacloakadmin.service.EncryptionService;
import com.eqima.keacloakadmin.service.GroupService;

/**
 * À chaque démarrage, copie dans MySQL les informations Keycloak (client "<realm>-cli" et utilisateurs
 * de chaque realm). Idempotent : upsert, jamais de doublon. Keycloak reste la source de vérité.
 * Désactivable avec keycloak.sync-on-startup=false.
 */
@Component
@ConditionalOnProperty(name = "keycloak.sync-on-startup", havingValue = "true", matchIfMissing = true)
public class KeycloakMysqlSyncRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KeycloakMysqlSyncRunner.class);
    private static final int PAGE_SIZE = 100;

    private final Keycloak keycloakAdmin;
    private final KeycloakClientRepository clientRepository;
    private final KeycloakUserRepository userRepository;
    private final EncryptionService encryptionService;
    private final GroupService groupService;
    private final OrganizationRepository organizationRepository;

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    public KeycloakMysqlSyncRunner(@Qualifier("keycloakAdmin") Keycloak keycloakAdmin,
                                   KeycloakClientRepository clientRepository,
                                   KeycloakUserRepository userRepository,
                                   EncryptionService encryptionService,
                                   GroupService groupService,
                                   OrganizationRepository organizationRepository) {
        this.keycloakAdmin = keycloakAdmin;
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.encryptionService = encryptionService;
        this.groupService = groupService;
        this.organizationRepository = organizationRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<RealmRepresentation> realms = keycloakAdmin.realms().findAll();
            for (RealmRepresentation realm : realms) {
                if ("master".equals(realm.getRealm())) continue;
                syncRealm(realm.getRealm());
            }
            syncOrganizations();
        } catch (Exception e) {
            // Ne jamais empêcher l'application de démarrer si Keycloak est injoignable
            log.error("Synchronisation Keycloak -> MySQL impossible", e);
        }
    }

    private void syncRealm(String realm) {
        try {
            syncClient(realm);
            syncUsers(realm);
        } catch (Exception e) {
            log.error("Échec de la synchronisation du realm {}", realm, e);
        }
    }

    // Le code existant (findByRealmName) suppose UN seul client par realm : on ne synchronise que "<realm>-cli"
    private void syncClient(String realm) throws Exception {
        String clientId = realm + "-cli";
        List<ClientRepresentation> found = keycloakAdmin.realm(realm).clients().findByClientId(clientId);
        if (found.isEmpty() || Boolean.TRUE.equals(found.get(0).isPublicClient())) return;

        String secret = keycloakAdmin.realm(realm).clients().get(found.get(0).getId()).getSecret().getValue();
        String issuerUri = authServerUrl + "/realms/" + realm;

        KeycloakClient client = clientRepository.findByRealmNameAndClientId(realm, clientId)
                .orElseGet(KeycloakClient::new);

        boolean unchanged = client.getId() != null
                && Objects.equals(client.getIssuerUri(), issuerUri)
                && Objects.equals(encryptionService.decrypt(client.getClientSecret()), secret);
        if (unchanged) return;

        client.setRealmName(realm);
        client.setClientId(clientId);
        client.setIssuerUri(issuerUri);
        client.setClientSecret(encryptionService.encrypt(secret));
        clientRepository.save(client);
        log.info("Client {} synchronisé dans MySQL", clientId);
    }

    // Les groupes ne vivent que dans le realm applicatif (keycloak.realm), comme GroupService / OrganizationService.
    // On ne CRÉE que les lignes manquantes : "active" appartient à MySQL et n'est jamais écrasé.
    // Les organisations dont le groupe a disparu ne sont pas supprimées (OrganizationService les signale déjà).
    private void syncOrganizations() {
        int created = 0;
        for (GroupRepresentation group : groupService.listAllRaw()) {
            if (organizationRepository.findByKeycloakGroupId(group.getId()).isPresent()) continue;
            Organization org = new Organization();
            org.setKeycloakGroupId(group.getId());
            org.setActive(true);
            organizationRepository.save(org);
            created++;
        }
        log.info("Organisations : {} créée(s) à partir des groupes Keycloak", created);
    }

    private void syncUsers(String realm) {
        int first = 0;
        int count = 0;
        List<UserRepresentation> page;
        do {
            page = keycloakAdmin.realm(realm).users().list(first, PAGE_SIZE);
            for (UserRepresentation u : page) {
                UUID id = UUID.fromString(u.getId());
                KeycloakUser user = userRepository.findById(id).orElseGet(KeycloakUser::new);

                if (user.getId() != null
                        && Objects.equals(user.getUsername(), u.getUsername())
                        && Objects.equals(user.getFirstname(), u.getFirstName())
                        && Objects.equals(user.getLastname(), u.getLastName())
                        && Objects.equals(user.getEmail(), u.getEmail())) {
                    continue;
                }
                user.setId(id);
                user.setUsername(u.getUsername());
                user.setFirstname(u.getFirstName());
                user.setLastname(u.getLastName());
                user.setEmail(u.getEmail());
                userRepository.save(user);
                count++;
            }
            first += PAGE_SIZE;
        } while (page.size() == PAGE_SIZE);
        log.info("Realm {} : {} utilisateur(s) créé(s) ou mis à jour dans MySQL", realm, count);
    }
}
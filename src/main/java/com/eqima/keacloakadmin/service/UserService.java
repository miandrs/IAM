package com.eqima.keacloakadmin.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.GroupsResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.eqima.keacloakadmin.models.dto.UserDTO;
import com.eqima.keacloakadmin.models.entity.KeycloakUser;
import com.eqima.keacloakadmin.models.entity.RequiredActions;
import com.eqima.keacloakadmin.repository.KeycloakUserRepository;
import com.eqima.keacloakadmin.models.request.CreateOrganizationRequest;
import com.eqima.keacloakadmin.models.request.CreateUserRequest;
import com.eqima.keacloakadmin.util.SecurityUtils;

import jakarta.ws.rs.core.Response;

@Service
public class UserService {
    
    private final Keycloak keycloak;

    private final KeycloakUserRepository keycloakUserRepository;

    private final GroupService groupService;

    @Value("${keycloak.realm}")
    private String realm;

    private final AuditService auditService;

    private final OrganizationService organizationService;

    public UserService(@Qualifier("keycloakClient") Keycloak keycloakClient, KeycloakUserRepository keycloakUserRepository, GroupService groupService, AuditService auditService, OrganizationService organizationService) {
        this.keycloak = keycloakClient;
        this.keycloakUserRepository = keycloakUserRepository;
        this.groupService = groupService;
        this.auditService = auditService;
        this.organizationService = organizationService;
    }

    private UsersResource users(){
        return this.keycloak.realm(this.realm).users();
    }
    private UsersResource users(Keycloak keycloak, String realm){
        return keycloak.realm(realm).users();
    }

//Get user groups from the JWT token
    public List<String> getUserGroups() {
        return ((Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getClaimAsStringList("groups");
    }
//Get user roles from the JWT token
    public List<String> getUserRoles() {
        Map<String, Object> realmAccess = ((Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getClaim("realm_access");
        if (realmAccess == null || !realmAccess.containsKey("roles")) {
            return List.of(); // no roles
        }
        // cast roles to List<String>
        List<String> roles = (List<String>) realmAccess.get("roles");
        return roles;
    }

    public List<UserDTO> getAllUsers() {
        List<String> roles = this.getUserRoles();
        if (roles.contains("SUPERADMIN")) {
            return this.getUsersByRole("ADMIN");
        } else {
            return this.getGroupMembers();
        }
        /* return keycloak.realm(realm)
            .users()
            .list()
            .stream()
            .map(user -> {
                List<String> groups = keycloak.realm(realm)
                                .users()
                                .get(user.getId())
                                .groups()
                                .stream()
                                .map(g -> g.getName())
                                .toList();

                return new UserDTO(
                    user.getId(),
                    user.getUsername(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getEmail(),
                    user.getCreatedTimestamp() != null ? Instant.ofEpochMilli(user.getCreatedTimestamp()) : null,
                    Boolean.TRUE.equals(user.isEnabled()),
                    groups
                );
            })
            .toList(); */
    }
    public List<UserDTO> getGroupMembers() {
        List<String> userGroups = getUserGroups();
        String groupName = userGroups.stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "L'utilisateur authentifié n'appartient à aucun groupe — impossible de lister les membres de son tenant."));

        GroupRepresentation group = groupService.findByName(groupName)
                .orElseThrow(() -> new RuntimeException("Group not found: " + groupName));
        return keycloak.realm(realm)
            .groups()
            .group(group.getId())
            .members()
            .stream()
            .map(user -> {
                List<String> groups = keycloak.realm(realm)
                                .users()
                                .get(user.getId())
                                .groups()
                                .stream()
                                .map(g -> g.getName())
                                .toList();

                return new UserDTO(
                    user.getId(),
                    user.getUsername(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getEmail(),
                    user.getCreatedTimestamp() != null ? Instant.ofEpochMilli(user.getCreatedTimestamp()) : null,
                    Boolean.TRUE.equals(user.isEnabled()),
                    groups
                );
            })
            .toList();
    }
    public List<UserDTO> getUsersByRole(String role) {
        List<UserDTO> rep = keycloak.realm(realm)
            .roles()
            .get(role)
            .getUserMembers()
            .stream()
            .map(user ->{
                List<String> groups = keycloak.realm(realm)
                .users()
                .get(user.getId())
                .groups()
                .stream()
                .map(g -> g.getName())
                .toList();
                return new UserDTO(
                    user.getId(),
                    user.getUsername(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getEmail(),
                    user.getCreatedTimestamp() != null ? Instant.ofEpochMilli(user.getCreatedTimestamp()) : null,
                    Boolean.TRUE.equals(user.isEnabled()),
                    groups
                );
            })
            .toList();

            return rep;
    }
    
    public UserDTO getUserById(String id) {
        UserRepresentation user = users().get(id).toRepresentation();
        List<String> groups = keycloak.realm(realm)
                        .users()
                        .get(user.getId())
                        .groups()
                        .stream()
                        .map(g -> g.getName())
                        .toList();
        return new UserDTO(
            user.getId(),
            user.getUsername(),
            user.getFirstName(),
            user.getLastName(),
            user.getEmail(),
            user.getCreatedTimestamp() != null ? Instant.ofEpochMilli(user.getCreatedTimestamp()) : null,
            Boolean.TRUE.equals(user.isEnabled()),
            groups
        );
    }

//Create user goes through 2 steps
//Step 1 : Create user
//Step 2 : Reset password
    public String createUser(CreateUserRequest createUserRequest){
        List<String> groupNames = this.getUserGroups();
        List<String> roles = this.getUserRoles();
        if(roles.contains("ADMIN") && !roles.contains("SUPERADMIN")){
            createUserRequest = new CreateUserRequest(
                createUserRequest.username(),
                createUserRequest.email(),
                createUserRequest.firstname(),
                createUserRequest.lastname(),
                createUserRequest.password(),
                createUserRequest.enabled(),
                groupNames
            );
        }

        UserRepresentation user = new UserRepresentation();
        user.setUsername(createUserRequest.username());
        user.setEmail(createUserRequest.email());
        user.setFirstName(createUserRequest.firstname());
        user.setLastName(createUserRequest.lastname());
        user.setEnabled(createUserRequest.enabled());
        user.setRequiredActions(List.of("UPDATE_PASSWORD"));
        
        Response response = users().create(user);

        if(response.getStatus() != 201){
            throw new RuntimeException("Failed to add user: "+response.getStatus()+" "+response.getStatusInfo().getReasonPhrase());
        }

        String userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");

        resetPassword(userId, createUserRequest.password(), true);

        if(createUserRequest.groups()!=null && !createUserRequest.groups().isEmpty()){
            assignGroupsByName(userId, createUserRequest.groups());
        }
        if(roles.contains("SUPERADMIN")){
            assignRealmRoles(userId, List.of("ADMIN"));
        } else {
            assignRealmRoles(userId, List.of("USER"));
        }

        auditService.log("CREATE_USER",  null, Map.of("userId", userId, "username", createUserRequest.username()));

        return userId;
    }  
    public void resetPassword(String userId, String password, boolean temporary){
        CredentialRepresentation credential= new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(temporary);

        users().get(userId).resetPassword(credential);
        auditService.log("RESET_PASSWORD",  null, Map.of("userId", userId));
    } 
    public UserDTO patchUser(String userId, CreateUserRequest createUserRequest){
        UserRepresentation user = users().get(userId).toRepresentation();
        user.setUsername(createUserRequest.username());
        user.setEmail(createUserRequest.email());
        user.setFirstName(createUserRequest.firstname());
        user.setLastName(createUserRequest.lastname());
        user.setEnabled(createUserRequest.enabled());

        users().get(userId).update(user);

        if(createUserRequest.groups()!=null && !createUserRequest.groups().isEmpty()){
            assignGroupsByName(userId, createUserRequest.groups());
        }
        auditService.log("PATCH_USER",  null, Map.of("userId", userId, "username", createUserRequest.username()));
        return getUserById(userId);
    }
    public void requiredActionUpdatePassword(String userId, List<String> requiredActions){
        UserRepresentation user = users().get(userId).toRepresentation();
        user.setRequiredActions(requiredActions.stream().map(RequiredActions::fromString).map(RequiredActions::getAction).toList());
        users().get(userId).update(user);
        auditService.log("UPDATE_REQUIRED_ACTIONS",  null, Map.of("userId", userId, "requiredActions", requiredActions.toString()));
    }
    public void deleteRequiredAction(String userId, String requiredAction){
        UserRepresentation user = users().get(userId).toRepresentation();
        List<String> updatedActions = user.getRequiredActions().stream()
                .filter(action -> !action.equalsIgnoreCase(requiredAction))
                .toList();
        user.setRequiredActions(updatedActions);
        users().get(userId).update(user);
        auditService.log("DELETE_REQUIRED_ACTION",  null, Map.of("userId", userId, "requiredAction", requiredAction));
    }
    public UserDTO disableOrEnableUser(String userId, boolean enabled){
        UserRepresentation user = users().get(userId).toRepresentation();
        user.setEnabled(enabled);
        users().get(userId).update(user);
        auditService.log("UPDATE_USER_STATUS",  null, Map.of("userId", userId, "enabled", enabled));
        return getUserById(userId);
    }
    public void sessionRevoke(String userId){
        users().get(userId).logout();
        auditService.log("REVOKE_USER_SESSION",  null, Map.of("userId", userId));
    }
    
    public void sessionRevokeAll(){
        List<String> roles = getUserRoles();
        SecurityUtils securityUtils = new SecurityUtils();
        if (roles.contains("SUPERADMIN")) {
            users().list().forEach(user -> users().get(user.getId()).logout());
            auditService.log("REVOKE_ALL_USER_SESSIONS", securityUtils.currentUserId(), Map.of("scope", "realm"));
            return;
        }

        String groupName = getUserGroups().stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "L'utilisateur authentifié n'appartient à aucun groupe — impossible de scoper la révocation."));

        GroupRepresentation group = groupService.findByName(groupName)
                .orElseThrow(() -> new RuntimeException("Group not found: " + groupName));

        List<UserRepresentation> members = keycloak.realm(realm).groups().group(group.getId()).members();
        members.forEach(user -> users().get(user.getId()).logout());

        auditService.log("REVOKE_ALL_USER_SESSIONS", securityUtils.currentUserId()
        ,
            Map.of("scope", "group", "groupName", groupName, "userCount", members.size()));
    }

    private void assignGroupsByName(String userId, List<String> groupNames) {

        GroupsResource groupsResource = keycloak.realm(realm).groups();

        for (String groupName : groupNames) {

            Optional<GroupRepresentation> groupOpt =
                    groupsResource.groups().stream()
                            .filter(g -> g.getName().equalsIgnoreCase(groupName))
                            .findFirst();

            if (groupOpt.isEmpty()) {
                // groupService.createIfNotExist(groupName);
                CreateOrganizationRequest org = new CreateOrganizationRequest(groupNames.get(0));
                organizationService.create(org);
                groupOpt = groupsResource.groups().stream()
                            .filter(g -> g.getName().equalsIgnoreCase(groupName))
                            .findFirst();
            }

            users().get(userId).joinGroup(groupOpt.get().getId());
        }
        auditService.log("ASSIGN_GROUPS_TO_USER",  null, Map.of("userId", userId, "groupNames", groupNames.toString()));
    }
    private void assignRealmRoles(String userId, List<String> roleNames) {
        List<RoleRepresentation> rolesToAssign = keycloak.realm(realm)
                .roles()
                .list()
                .stream()
                .filter(r -> roleNames.contains(r.getName()))
                .toList();

        users().get(userId).roles().realmLevel().add(rolesToAssign);
        auditService.log("ASSIGN_REALM_ROLES_TO_USER",  null, Map.of("userId", userId, "roleNames", roleNames.toString()));
    }

//Use specific keycloak connection
    public String createUser(CreateUserRequest createUserRequest,Keycloak keycloak,String realm){

        UserRepresentation user = new UserRepresentation();
        user.setUsername(createUserRequest.username());
        user.setEmail(createUserRequest.email());
        user.setFirstName(createUserRequest.firstname());
        user.setLastName(createUserRequest.lastname());
        user.setEnabled(createUserRequest.enabled());
        user.setRequiredActions(List.of("UPDATE_PASSWORD"));
        
        Response response = users(keycloak,realm).create(user);

        if(response.getStatus() != 201){
            throw new RuntimeException("Failed to add user: "+response.getStatus());
        }

        String userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");

        resetPassword(userId, createUserRequest.password(), true,keycloak,realm);

        if(createUserRequest.groups()!=null && !createUserRequest.groups().isEmpty()){
            assignGroupsByName(userId, createUserRequest.groups(),keycloak);
        }

        auditService.log("CREATE_USER",  null, Map.of("userId", userId, "username", createUserRequest.username()));
        return userId;
    }
    public void resetPassword(String userId, String password, boolean temporary, Keycloak keycloak, String realm){
        CredentialRepresentation credential= new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(temporary);

        users(keycloak,realm).get(userId).resetPassword(credential);
        auditService.log("RESET_PASSWORD",  null, Map.of("userId", userId));
    }
    private void assignGroupsByName(String userId, List<String> groupNames,Keycloak keycloak) {

        GroupsResource groupsResource = keycloak.realm(realm).groups();

        for (String groupName : groupNames) {

            Optional<GroupRepresentation> groupOpt =
                    groupsResource.groups().stream()
                            .filter(g -> g.getName().equalsIgnoreCase(groupName))
                            .findFirst();

            if (groupOpt.isEmpty()) {
                groupService.createIfNotExist(groupName);
                groupOpt = groupsResource.groups().stream()
                            .filter(g -> g.getName().equalsIgnoreCase(groupName))
                            .findFirst();
            }

            users(keycloak,realm).get(userId).joinGroup(groupOpt.get().getId());
        }
        auditService.log("ASSIGN_GROUPS_TO_USER",  null, Map.of("userId", userId, "groupNames", groupNames.toString()));
    }
    
    public KeycloakUser mysqlSaveKeycloakUser(KeycloakUser user){
        auditService.log("CREATE_USER",  null, Map.of("userId", user.getId(), "username", user.getUsername()));
        return this.keycloakUserRepository.save(user);
    }

    public Optional<KeycloakUser> mysqlGetKeycloakUserById(UUID id){
        auditService.log("GET_USER",  null, Map.of("userId", id));
        return this.keycloakUserRepository.findById(id);
    }

    // public List<RealmAdminDto> mysqlGetUserFromRealm(String realmName){
    //     KeycloakClient filterRealm = clientService.findClientByRealmName(realmName);
    //     // filterRealm.setClientAdminUser(null);
    //     return this.keycloakUserRepository.findByKeycloakClient(filterRealm)
    //         .stream()
    //         .map(user -> new RealmAdminDto(
    //             filterRealm.getRealmName(),
    //             filterRealm.getClientId(),
    //             user.getUsername(),
    //             user.getFirstname(),
    //             user.getLastname(),
    //             user.getEmail()
    //         ))
    //         .toList();
    // }
    public void deleteUser(String userId){
        users().delete(userId);
        auditService.log("DELETE_USER",  null, Map.of("userId", userId));
    }

}

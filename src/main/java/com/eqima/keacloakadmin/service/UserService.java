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

import com.eqima.keacloakadmin.dto.UserDTO;
import com.eqima.keacloakadmin.entity.KeycloakUser;
import com.eqima.keacloakadmin.entity.RequiredActions;
import com.eqima.keacloakadmin.repository.KeycloakUserRepository;
import com.eqima.keacloakadmin.request.CreateUserRequest;

import jakarta.ws.rs.core.Response;

@Service
public class UserService {
    
    private final Keycloak keycloak;

    private final KeycloakUserRepository keycloakUserRepository;

    private final GroupService groupService;

    @Value("${keycloak.realm}")
    private String realm;

    public UserService(@Qualifier("keycloakClient") Keycloak keycloakClient, KeycloakUserRepository keycloakUserRepository, GroupService groupService) {
        this.keycloak = keycloakClient;
        this.keycloakUserRepository = keycloakUserRepository;
        this.groupService = groupService;
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
        String groupName = getUserGroups().get(0);
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

        return userId;
    }  
    public void resetPassword(String userId, String password, boolean temporary){
        CredentialRepresentation credential= new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(temporary);

        users().get(userId).resetPassword(credential);
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

        return getUserById(userId);
    }
    public void requiredActionUpdatePassword(String userId, List<String> requiredActions){
        UserRepresentation user = users().get(userId).toRepresentation();
        user.setRequiredActions(requiredActions.stream().map(RequiredActions::fromString).map(RequiredActions::getAction).toList());
        users().get(userId).update(user);
    }
    public void deleteRequiredAction(String userId, String requiredAction){
        UserRepresentation user = users().get(userId).toRepresentation();
        List<String> updatedActions = user.getRequiredActions().stream()
                .filter(action -> !action.equalsIgnoreCase(requiredAction))
                .toList();
        user.setRequiredActions(updatedActions);
        users().get(userId).update(user);
    }
    public void disableOrEnableUser(String userId, boolean enabled){
        UserRepresentation user = users().get(userId).toRepresentation();
        user.setEnabled(enabled);
        users().get(userId).update(user);
    }
    public void sessionRevoke(String userId){
        users().get(userId).logout();
    }
    public void sessionRevokeAll(){
        users().list().forEach(user -> {
            users().get(user.getId()).logout();
        });
    }
    private void assignGroupsByName(String userId, List<String> groupNames) {

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

            users().get(userId).joinGroup(groupOpt.get().getId());
        }
    }
    private void assignRealmRoles(String userId, List<String> roleNames) {
        List<RoleRepresentation> rolesToAssign = keycloak.realm(realm)
                .roles()
                .list()
                .stream()
                .filter(r -> roleNames.contains(r.getName()))
                .toList();

        users().get(userId).roles().realmLevel().add(rolesToAssign);
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

        return userId;
    }
    public void resetPassword(String userId, String password, boolean temporary, Keycloak keycloak, String realm){
        CredentialRepresentation credential= new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(temporary);

        users(keycloak,realm).get(userId).resetPassword(credential);
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
    }
    
    public KeycloakUser mysqlSaveKeycloakUser(KeycloakUser user){
        return this.keycloakUserRepository.save(user);
    }

    public Optional<KeycloakUser> mysqlGetKeycloakUserById(UUID id){
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
    }

}

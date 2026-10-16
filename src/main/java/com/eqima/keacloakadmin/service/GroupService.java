package com.eqima.keacloakadmin.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.eqima.keacloakadmin.dto.UserDTO;
import com.eqima.keacloakadmin.request.CreateGroupRequest;
import com.eqima.keacloakadmin.util.SecurityUtils;

@Service
public class GroupService {

    private final Keycloak keycloak;

    @Value("${keycloak.realm}")
    private String realm;

    private final AuditService auditService;

    public GroupService(@Qualifier("keycloakClient") Keycloak keycloakClient, AuditService auditService) {
        this.keycloak = keycloakClient;
        this.auditService = auditService;
    }

    public GroupRepresentation createGroup(CreateGroupRequest request) {
        GroupRepresentation group = new GroupRepresentation();
        group.setName(request.groupName().toUpperCase());
        keycloak.realm(realm).groups().add(group);
        auditService.log("CREATE_GROUP",  null, Map.of("groupName", request.groupName().toUpperCase()));
        return findByName(request.groupName().toUpperCase()).orElseThrow(() -> new RuntimeException("Failed to create group"));
    }

    public Optional<GroupRepresentation> findByName(String groupName) {
        return keycloak.realm(realm)
                .groups()
                .groups()
                .stream()
                .filter(g -> g.getName().equalsIgnoreCase(groupName))
                .findFirst();
    }

    public Optional<GroupRepresentation> createIfNotExist(String groupName) {
        Optional<GroupRepresentation> existingGroup = findByName(groupName);
        if (existingGroup.isEmpty()) {
            createGroup(new CreateGroupRequest(groupName));
            existingGroup = findByName(groupName);
        }
        auditService.log("CREATE_GROUP_IF_NOT_EXIST",  null, Map.of("groupName", groupName.toUpperCase()));
        return existingGroup;
    }

    public List<GroupRepresentation> getAllGroups() {
        return keycloak.realm(realm).groups().groups();
    }

    public GroupRepresentation getGroupById(String groupId) {
        return keycloak.realm(realm).groups().group(groupId).toRepresentation();
    }

    public void deleteGroup(String groupId) {
        keycloak.realm(realm).groups().group(groupId).remove();
        auditService.log("DELETE_GROUP",  null, Map.of("groupId", groupId));
    }

    public void updateUserGroups(String userId, List<String> groupIds) {
        // Remove user from all current groups
        List<GroupRepresentation> currentGroups = keycloak.realm(realm).users().get(userId).groups();
        for (GroupRepresentation group : currentGroups) {
            keycloak.realm(realm).users().get(userId).leaveGroup(group.getId());
        }

        // Add user to the new groups
        for (String groupId : groupIds) {
            keycloak.realm(realm).users().get(userId).joinGroup(groupId);
        }
        auditService.log("UPDATE_USER_GROUPS",  null, Map.of("userId", userId, "groupIds", groupIds.toString()));
    }

    public void removeUserFromGroup(String userId, String groupId) {
        keycloak.realm(realm).users().get(userId).leaveGroup(groupId);
        auditService.log("REMOVE_USER_FROM_GROUP",  null, Map.of("userId", userId, "groupId", groupId));
    }

    public List<UserDTO> getAllGroupsMembers(String groupId, int page, int size) {
        int first = Math.max(page, 0) * Math.max(size, 0);
        String groupName = getGroupById(groupId).getName();
        return keycloak.realm(realm).groups().group(groupId).members(first, size).stream()
            .map(user -> new UserDTO(user.getId(), user.getUsername(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getCreatedTimestamp() != null ? Instant.ofEpochMilli(user.getCreatedTimestamp()) : null, user.isEnabled(), List.of(groupName)))
                .toList();
    }
}

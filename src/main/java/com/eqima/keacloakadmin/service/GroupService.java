package com.eqima.keacloakadmin.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.eqima.keacloakadmin.models.dto.UserDTO;
import com.eqima.keacloakadmin.models.request.CreateGroupRequest;
import com.eqima.keacloakadmin.util.SecurityUtils;

import jakarta.ws.rs.core.Response;

@Service
public class GroupService {

    private final Keycloak keycloak;
    private final SecurityUtils securityUtils;

    @Value("${keycloak.realm}")
    private String realm;

    private final AuditService auditService;

    public GroupService(@Qualifier("keycloakClient") Keycloak keycloakClient, AuditService auditService, SecurityUtils securityUtils) {
        this.keycloak = keycloakClient;
        this.auditService = auditService;
        this.securityUtils = securityUtils;
    }

    public GroupRepresentation createGroup(CreateGroupRequest request) {
        String name = request.groupName().trim().toUpperCase(Locale.ROOT);
        GroupRepresentation group = new GroupRepresentation();
        group.setName(name);

        String id;
        try (Response response = keycloak.realm(realm).groups().add(group)) {
            if (response.getStatus() == 409) {
                throw new IllegalStateException("Group already exists: " + name);
            }
            id = CreatedResponseUtil.getCreatedId(response); // throws if status isn't 201
        }
        auditService.log("CREATE_GROUP", null, Map.of("groupName", name));
        return getGroupById(id);
    }

    public Optional<GroupRepresentation> findByName(String groupName) {
        return keycloak.realm(realm).groups()
                .groups(groupName, true, 0, 1, false) // search, exact, first, max, brief
                .stream()
                .findFirst();
    }

    public GroupRepresentation createIfNotExist(String groupName) {
        GroupRepresentation existingGroup =  findByName(groupName.trim().toUpperCase(Locale.ROOT))
            .orElseGet(() -> createGroup(new CreateGroupRequest(groupName)));
        auditService.log("CREATE_GROUP_IF_NOT_EXIST",  null, Map.of("groupName", groupName.toUpperCase()));
        return existingGroup;
        
    }

    // public List<GroupRepresentation> getAllGroups() {
    //     List<GroupRepresentation> all = keycloak.realm(realm).groups().groups();

    //     if (securityUtils.isSuperAdmin()) {
    //         return all;
    //     }
    //     if (securityUtils.isAdmin()) {
    //         Set<String> allowed = allowedGroupNames();
    //         return all.stream()
    //                 .filter(g -> allowed.contains(g.getName().toUpperCase(Locale.ROOT)))
    //                 .toList();
    //     }
    //     throw new AccessDeniedException("Not allowed to list groups");
    // }

    public List<GroupRepresentation> listAllRaw() {
        return keycloak.realm(realm).groups().groups();
    }

    public List<GroupRepresentation> getAllGroups() {
        List<GroupRepresentation> all = listAllRaw();
        if (securityUtils.isSuperAdmin()) return all;
        if (securityUtils.isAdmin()) {
            Set<String> allowed = allowedGroupNames();
            return all.stream().filter(g -> allowed.contains(g.getName().toUpperCase(Locale.ROOT))).toList();
        }
        throw new AccessDeniedException("Not allowed to list groups");
    }

    private Set<String> allowedGroupNames() {
        return securityUtils.organizations().stream()   // top-level segment of each token group path
                .map(n -> n.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    public GroupRepresentation getGroupById(String groupId) {
        return keycloak.realm(realm).groups().group(groupId).toRepresentation();
    }

    public void deleteGroup(String groupId) {
        keycloak.realm(realm).groups().group(groupId).remove();
        auditService.log("DELETE_GROUP",  null, Map.of("groupId", groupId));
    }

    public void updateUserGroups(String userId, List<String> groupIds) {
        UserResource user = keycloak.realm(realm).users().get(userId);

        Set<String> current = user.groups().stream()
                .map(GroupRepresentation::getId)
                .collect(Collectors.toSet());
        Set<String> target = new HashSet<>(groupIds);

        current.stream().filter(id -> !target.contains(id)).forEach(user::leaveGroup);
        target.stream().filter(id -> !current.contains(id)).forEach(user::joinGroup);

        auditService.log("UPDATE_USER_GROUPS", null, Map.of("userId", userId, "groupIds", groupIds.toString()));
    }

    // GroupService — nouvelle méthode, à côté de updateUserGroups (inchangée, gardée pour son usage existant ailleurs)
    public void addUserToGroup(String userId, String groupId) {
        keycloak.realm(realm).users().get(userId).joinGroup(groupId);
        auditService.log("ADD_USER_TO_GROUP", userId, Map.of("userId", userId, "groupId", groupId));
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

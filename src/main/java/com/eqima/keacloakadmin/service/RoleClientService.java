package com.eqima.keacloakadmin.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.eqima.keacloakadmin.dto.PagedResult;
import com.eqima.keacloakadmin.dto.RoleClientDto;
import com.eqima.keacloakadmin.dto.UserDTO;
import com.eqima.keacloakadmin.exception.ResourceNotFoundException;
import com.eqima.keacloakadmin.request.AssignClientRoleToGroup;
import com.eqima.keacloakadmin.request.AssignClientRoleToUserRequest;
import com.eqima.keacloakadmin.request.ClientRoleRequest;
import com.eqima.keacloakadmin.request.CreateClientRoleRequest;
import com.eqima.keacloakadmin.util.SecurityUtils;

@Service
public class RoleClientService {

    private static final Set<String> DEFAULT_CLIENTS = Set.of(
        "account",
        "account-console",
        "admin-cli",
        "broker",
        "realm-management",
        "security-admin-console"
    );

    private final Keycloak keycloak;
    private final UserService userService;

    private final AuditService auditService;

    @Value("${keycloak.realm}")
    private String realmName;

    public RoleClientService(@Qualifier("keycloakClient") Keycloak keycloak, UserService userService, AuditService auditService) {
        this.keycloak = keycloak;
        this.userService = userService;
        this.auditService = auditService;
    }

    public ClientResource getClientResource(String clientUuid) {
        return keycloak.realm(realmName).clients().get(clientUuid);
    }
    public UserResource getUserResource(String userUuid) {
        return keycloak.realm(realmName).users().get(userUuid);
    }

//Create a client role
    public RoleClientDto createClientRole(CreateClientRoleRequest request) {
        ClientResource clientResource = getClientResource(request.clientId());
        RoleRepresentation role = new RoleRepresentation();
        role.setName(request.roleName());
        clientResource.roles().create(role);

        auditService.log("CREATE_CLIENT_ROLE",  null, Map.of("clientId", request.clientId(), "roleName", request.roleName()));
        
        return new RoleClientDto(
            clientResource.toRepresentation().getId(),
            clientResource.toRepresentation().getClientId(),
            clientResource.roles().get(request.roleName()).toRepresentation().getId(),
            request.roleName(),
            true
        );
    }

    public RoleClientDto createRoleClient(CreateClientRoleRequest request) {
        ClientRepresentation client = keycloak.realm(realmName)
            .clients()
            .findByClientId(request.clientId())
            .stream().findFirst()
            .orElseThrow(() -> new ResourceNotFoundException(
                "Client inexistant : " + request.clientId()));

        RoleRepresentation role = new RoleRepresentation();
        role.setName(request.roleName());

        keycloak.realm(realmName).clients().get(client.getId()).roles().create(role);

        RoleRepresentation created = keycloak.realm(realmName).clients()
            .get(client.getId()).roles().get(request.roleName()).toRepresentation();

        auditService.log("CREATE_CLIENT_ROLE",  null, Map.of("clientId", request.clientId(), "roleName", request.roleName()));

        return new RoleClientDto(
            client.getId(), client.getClientId(),
            created.getId(), created.getName(), created.isComposite()
        );
    }
//Remove a client role
    public String removeClientRole(String clientUuid, String roleUuid) {
        ClientResource clientResource = keycloak.realm(realmName).clients().get(clientUuid);
        String roleName = clientResource.roles().get(roleUuid).toRepresentation().getName();
        clientResource.roles().deleteRole(roleUuid);

        auditService.log("REMOVE_CLIENT_ROLE",  null, Map.of("clientUuid", clientUuid, "roleUuid", roleUuid, "roleName", roleName));
        return roleName;
    }

    public void deleteRoleClient(String clientUuid, String roleUuid) {
        RoleRepresentation role = keycloak.realm(realmName).clients()
            .get(clientUuid).roles().list()
            .stream()
            .filter(r -> roleUuid.equals(r.getId()))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException(
                "Rôle introuvable : " + roleUuid));

        keycloak.realm(realmName).clients()
            .get(clientUuid).roles().get(role.getName()).remove();

        auditService.log("REMOVE_CLIENT_ROLE",  null, Map.of("clientUuid", clientUuid, "roleUuid", roleUuid, "roleName", role.getName()));
    }

//Get all roles of a client
    public List<RoleClientDto> getClientRoles(String clientUuid) {
        ClientResource clientResource = keycloak.realm(realmName).clients().get(clientUuid);
        return clientResource.roles().list().stream()
                .map(role -> new RoleClientDto(
                    clientResource.toRepresentation().getId(),
                    clientResource.toRepresentation().getClientId(),
                    role.getId(),
                    role.getName(),
                    true)
                )
                .toList();
    }
//Get all effective (assigned) client roles for a user
    public List<RoleClientDto> getAllEffectiveClientRolesForUser(String userId) {
        List<ClientRepresentation> allClients = keycloak.realm(realmName).clients().findAll().stream()
            .filter(client -> !DEFAULT_CLIENTS.contains(client.getClientId()))
            .toList();
        UserResource userResource = getUserResource(userId);

        return allClients.stream()
            .flatMap(client -> {
                try {
                    List<RoleRepresentation> effectiveRoles = userResource.roles()
                            .clientLevel(client.getId())
                            .listEffective();

                    if (effectiveRoles == null || effectiveRoles.isEmpty()) {
                        return Stream.empty();
                    }

                    return effectiveRoles.stream()
                            .map(role -> new RoleClientDto(
                                    client.getId(),
                                    client.getClientId(),
                                    role.getId(),
                                    role.getName(),
                                    true
                            ));
                } catch (Exception e) {
                    return Stream.empty();
                }
            })
            .collect(Collectors.toList());
    }
//Get effective (assigned) client roles for a user on a specific client
    public List<RoleClientDto> getEffectiveClientRolesForUser(String userUuid, String clientUuid) {
        UserResource userResource = keycloak.realm(realmName).users().get(userUuid);
        ClientRepresentation client = keycloak.realm(realmName).clients().get(clientUuid).toRepresentation();
        return userResource.roles().clientLevel(clientUuid).listEffective().stream()
                .map(role -> new RoleClientDto(
                    client.getId(),
                    client.getClientId(),
                    role.getId(),
                    role.getName(),
                    true
                ))
                .toList();
    }
//Get all available (non-assigned) client roles for a user
    public List<RoleClientDto> getAllAvailableClientRolesForUser(String userUuid) {
        List<ClientRepresentation> allClients = keycloak.realm(realmName).clients().findAll().stream()
            .filter(client -> !DEFAULT_CLIENTS.contains(client.getClientId()))
            .toList();
        UserResource userResource = getUserResource(userUuid);
        return allClients.stream()
            .flatMap(client -> {
                try {
                    List<RoleRepresentation> allRoles = keycloak.realm(realmName)
                            .clients()
                            .get(client.getId())
                            .roles()
                            .list();

                    if (allRoles == null || allRoles.isEmpty()) {
                        return Stream.empty();
                    }

                    Set<String> effectiveRoleNames = userResource.roles()
                            .clientLevel(client.getId())
                            .listEffective()
                            .stream()
                            .map(RoleRepresentation::getName)
                            .collect(Collectors.toSet());

                    return allRoles.stream()
                            .filter(role -> !effectiveRoleNames.contains(role.getName()))
                            .map(role -> new RoleClientDto(
                                    client.getId(),
                                    client.getClientId(),
                                    role.getId(),
                                    role.getName(),
                                    false
                            ));
                } catch (Exception e) {
                    return Stream.empty();
                }
            })
            .collect(Collectors.toList());
    }
//Get available (non-assigned) client roles for a user on a specific client
    public List<RoleClientDto> getAvailableClientRolesForUser(String userUuid, String clientUuid) {
        UserResource userResource = getUserResource(userUuid);
        ClientRepresentation clientRepresentation = getClientResource(clientUuid).toRepresentation();
        return userResource.roles().clientLevel(clientUuid).listAvailable().stream()
                .map(role -> new RoleClientDto(
                    clientRepresentation.getId(),
                    clientRepresentation.getClientId(),
                    role.getId(),
                    role.getName(),
                false))
                .toList();
    }

//Assign client role to user
    public void assignClientRoleToUser(AssignClientRoleToUserRequest request) {
        ClientResource clientResource;
        RoleRepresentation role;
        UserResource userResource;
        for (ClientRoleRequest clientRoleRequest : request.clientRoles()) {
            clientResource = getClientResource(clientRoleRequest.clientUuid());
            role = clientResource.roles().get(clientRoleRequest.roleName()).toRepresentation();
            userResource = getUserResource(request.userUuid());
            userResource.roles().clientLevel(clientResource.toRepresentation().getId()).add(List.of(role));
        }
        auditService.log("ASSIGN_CLIENT_ROLE_TO_USER",  null, Map.of("userUuid", request.userUuid(), "clientRoles", request.clientRoles().toString()));
    }
//Remove client role from user
    public void removeClientRoleFromUser(AssignClientRoleToUserRequest request) {
        ClientResource clientResource;
        RoleRepresentation role;
        UserResource userResource;
        for (ClientRoleRequest clientRoleRequest : request.clientRoles()) {
            clientResource = getClientResource(clientRoleRequest.clientUuid());
            role = clientResource.roles().get(clientRoleRequest.roleName()).toRepresentation();
            userResource = getUserResource(request.userUuid());
            userResource.roles().clientLevel(clientResource.toRepresentation().getId()).remove(List.of(role));
        }
        auditService.log("REMOVE_CLIENT_ROLE_FROM_USER",  null, Map.of("userUuid", request.userUuid(), "clientRoles", request.clientRoles().toString()));
    }

//Assign client role to group
    public void assignClientRoleToGroup(AssignClientRoleToGroup request) {
        for (ClientRoleRequest clientRoleRequest : request.clientRoles()) {
            Optional<GroupRepresentation> group = keycloak.realm(realmName).groups()
                .groups()
                .stream()
                .filter(g -> g.getName().equalsIgnoreCase(request.groupName()))
                .findFirst();
            if(group.isEmpty()) {
                throw new ResourceNotFoundException("Group not found: " + request.groupName());
            }
            ClientResource clientResource = getClientResource(clientRoleRequest.clientUuid());
            RoleRepresentation role = clientResource.roles().get(clientRoleRequest.roleName()).toRepresentation();
            keycloak.realm(realmName).groups().group(group.get().getId()).roles().clientLevel(clientResource.toRepresentation().getId()).add(List.of(role));
        }
        auditService.log("ASSIGN_CLIENT_ROLE_TO_GROUP",  null, Map.of("groupName", request.groupName(), "clientRoles", request.clientRoles().toString()));
    }
//Remove client role from group
    public void removeClientRoleFromGroup(AssignClientRoleToGroup request) {
        Optional<GroupRepresentation> group = keycloak.realm(realmName).groups()
            .groups()
            .stream()
            .filter(g -> g.getName().equalsIgnoreCase(request.groupName()))
            .findFirst();
        if(group.isEmpty()) {
            throw new ResourceNotFoundException("Group not found: " + request.groupName());
        }
        for (ClientRoleRequest clientRoleRequest : request.clientRoles()) {
            ClientResource clientResource = getClientResource(clientRoleRequest.clientUuid());
            RoleRepresentation role = clientResource.roles().get(clientRoleRequest.roleName()).toRepresentation();
            keycloak.realm(realmName).groups().group(group.get().getId()).roles().clientLevel(clientResource.toRepresentation().getId()).remove(List.of(role));
        }
        auditService.log("REMOVE_CLIENT_ROLE_FROM_GROUP",  null, Map.of("groupName", request.groupName(), "clientRoles", request.clientRoles().toString()));
    }

//Get all client roles for a user
    public List<RoleClientDto> getAllClientRolesForUser(String userUuid, String clientSearch, String roleSearch){
        // 1. Construire la liste complète avec flag assigned
        List<RoleClientDto> effectiveRoles = getAllEffectiveClientRolesForUser(userUuid);
        List<RoleClientDto> availableRoles = getAllAvailableClientRolesForUser(userUuid);
        List<RoleClientDto> allRoles = Stream.concat(effectiveRoles.stream(), availableRoles.stream())
                .collect(Collectors.toList());

        // 2. Filtrage
        List<RoleClientDto> filtered = allRoles.stream()
                .filter(r -> roleSearch == null || roleSearch.isBlank()
                        || r.roleName().toLowerCase().contains(roleSearch.toLowerCase()))
                .filter(r -> clientSearch == null || clientSearch.isBlank()
                        || r.clientId().toLowerCase().contains(clientSearch.toLowerCase()))
                .collect(Collectors.toList());

        return filtered;
    }
//Get user client roles with pagination and search
    public PagedResult<RoleClientDto> getAllClientRolesForUser(
        String userUuid,
        int page,
        int size,
        String clientSearch,   // filtre sur le clientId
        String roleSearch    // filtre sur le nom du rôle
    ) {
        List<RoleClientDto> filtered = getAllClientRolesForUser(userUuid, clientSearch, roleSearch);

        // 3. Pagination
        int totalElements = filtered.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);

        List<RoleClientDto> pageContent = filtered.subList(fromIndex, toIndex);

        return new PagedResult<RoleClientDto>(pageContent, page, size, totalElements, totalPages);
    }
    // public void assignClientRole(AssignClientRoleToUserRequest request) {
    //     if (request.clientRoles() == null || request.clientRoles().isEmpty()) return;
    //     List<String> roles = userService.getUserRoles();
    //     if (roles.contains("SUPERADMIN")) {
    //         UserDTO user=userService.getUserById(request.userUuid());
    //         assignClientRoleToGroup(new AssignClientRoleToGroup(user.groups().get(0), request.clientRoles()));
    //         return;
    //     } else {
    //         assignClientRoleToUser(request);
    //     }
    // }
    
    public void assignClientRoles(AssignClientRoleToUserRequest req) {
        if (req.clientRoles() == null || req.clientRoles().isEmpty()) return;

        RealmResource realm = keycloak.realm(realmName);
        UserResource user = realm.users().get(req.userUuid());

        Map<String, List<RoleRepresentation>> byClient = new HashMap<>();
        for (ClientRoleRequest r : req.clientRoles()) {
            RoleRepresentation role = realm.clients().get(r.clientUuid())
                .roles().list().stream()
                .filter(x -> x.getName().equals(r.roleName()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Rôle '" + r.roleName() + "' introuvable sur le client " + r.clientUuid()));
            byClient.computeIfAbsent(r.clientUuid(), k -> new ArrayList<>()).add(role);
        }

        byClient.forEach((clientUuid, roles) ->
            user.roles().clientLevel(clientUuid).add(roles));
        auditService.log("ASSIGN_CLIENT_ROLE_TO_USER",  null, Map.of("userUuid", req.userUuid(), "clientRoles", req.clientRoles().toString()));
    }
    public void removeAssignedClientRole(AssignClientRoleToUserRequest request) {
        if (request.clientRoles() == null || request.clientRoles().isEmpty()) return;
        List<String> roles = userService.getUserRoles();
        if (roles.contains("SUPERADMIN")) {
            UserDTO user=userService.getUserById(request.userUuid());
            removeClientRoleFromGroup(new AssignClientRoleToGroup(user.groups().get(0), request.clientRoles()));
            return;
        } else {
            removeClientRoleFromUser(request);
        }
        auditService.log("REMOVE_CLIENT_ROLE_FROM_USER",  null, Map.of("userUuid", request.userUuid(), "clientRoles", request.clientRoles().toString()));
    }
}

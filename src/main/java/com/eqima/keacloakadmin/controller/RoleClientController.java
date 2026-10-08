package com.eqima.keacloakadmin.controller;

import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

import com.eqima.keacloakadmin.dto.PagedResult;
import com.eqima.keacloakadmin.dto.RoleClientDto;
import com.eqima.keacloakadmin.request.AssignClientRoleToUserRequest;
import com.eqima.keacloakadmin.request.CreateClientRoleRequest;
import com.eqima.keacloakadmin.service.RoleClientService;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;




@RestController
@RequestMapping("/keycloak/roles")
public class RoleClientController {
    private final RoleClientService roleClientService;

    public RoleClientController(RoleClientService roleClientService) {
        this.roleClientService = roleClientService;
    }

//Create new client role
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleClientDto createRoleClient(@RequestBody CreateClientRoleRequest request) {
        return roleClientService.createRoleClient(request);
    }

//Remove a client role
    @DeleteMapping("/client/{clientUuid}/role/{roleUuid}")
    public Map<String, String> deleteRoleClient(@PathVariable String clientUuid, @PathVariable String roleUuid) {
        roleClientService.deleteRoleClient(clientUuid, roleUuid);
        return Map.of("delete_id", roleUuid);
    }

//Get all roles of a client
    @GetMapping("/{clientUuid}")
    public List<RoleClientDto> getClientRoles(@PathVariable String clientUuid) {
        return roleClientService.getClientRoles(clientUuid);
    }

//Get all assigned client roles for a user
    @GetMapping("/effective/{userUuid}")
    public List<RoleClientDto> getAllAssignedClientRolesForUser(@PathVariable String userUuid) {
        return roleClientService.getAllEffectiveClientRolesForUser(userUuid);
    }
//Get all available client roles for a user
    @GetMapping("/available/{userUuid}")
    public List<RoleClientDto> getAvailableClientRolesForUser(@PathVariable String userUuid) {
        return roleClientService.getAllAvailableClientRolesForUser(userUuid);
    }

//Get all paginated client roles for a user with search
    @GetMapping("/user/{userUuid}/{page}")
    public PagedResult<RoleClientDto> searchClientRolesForUser(
            @PathVariable String userUuid,
            @RequestParam(required = false) String clientSearch,
            @RequestParam(required = false) String roleSearch,
            @PathVariable int page,
            @RequestParam(defaultValue = "10") int size) {
        return roleClientService.getAllClientRolesForUser(userUuid, page-1, size, clientSearch, roleSearch);
    }

//Get all client roles for a user with search (non paginated)
    @GetMapping("/user/{userUuid}")
    public List<RoleClientDto> searchClientRolesForUser(
            @PathVariable String userUuid,
            @RequestParam(required = false) String clientSearch,
            @RequestParam(required = false) String roleSearch) {
        return roleClientService.getAllClientRolesForUser(userUuid, clientSearch, roleSearch);
    }
    

//Assign role
    @PostMapping("/assign")
    public Map<String, String> assignClientRole(@RequestBody AssignClientRoleToUserRequest request) {
        roleClientService.assignClientRoles(request);
        return Map.of("message", "Role(s) assigned successfully");
    }

//Remove assigned role
    @PostMapping("/remove")
    public Map<String, String> removeAssignedClientRole(@RequestBody AssignClientRoleToUserRequest request) {
        roleClientService.removeAssignedClientRole(request);
        return Map.of("message", "Role(s) removed successfully");
    }
}

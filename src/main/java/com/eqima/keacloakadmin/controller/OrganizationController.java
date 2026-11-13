package com.eqima.keacloakadmin.controller;

import com.eqima.keacloakadmin.models.dto.OrganizationDto;
import com.eqima.keacloakadmin.models.dto.UserDTO;
import com.eqima.keacloakadmin.models.request.CreateOrganizationRequest;
import com.eqima.keacloakadmin.models.request.UpdateOrganizationRequest;
import com.eqima.keacloakadmin.service.GroupService;
import com.eqima.keacloakadmin.service.OrganizationService;
import com.eqima.keacloakadmin.service.UserScopeService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/governance/organizations")
@Validated
public class OrganizationController {

    private final OrganizationService organizationService;
    private final GroupService groupService;
    private final UserScopeService userScope;

    public OrganizationController(OrganizationService organizationService, GroupService groupService, UserScopeService userScope) {
        this.organizationService = organizationService;
        this.groupService = groupService;
        this.userScope = userScope;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationDto create(@Valid @RequestBody CreateOrganizationRequest request) {
        return organizationService.create(request);
    }

    @GetMapping
    public List<OrganizationDto> list() {
        return organizationService.list();
    }

    @GetMapping("/{id}")
    public OrganizationDto getById(@PathVariable UUID id) {
        userScope.assertOrganizationInScope(id);
        return organizationService.getById(id);
    }

    @PatchMapping("/{id}")
    public OrganizationDto update(@PathVariable UUID id, @RequestBody UpdateOrganizationRequest request) {
        userScope.assertOrganizationInScope(id);
        return organizationService.update(id, request);
    }

    @PutMapping("/{id}/members/{userId}")
    public void attachUser(@PathVariable UUID id, @PathVariable String userId) {
        userScope.assertOrganizationInScope(id);
        userScope.assertNotSuperAdminTarget(userId);
        organizationService.attachUser(id, userId);
    }

    // OrganizationController
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        userScope.assertOrganizationInScope(id);
        organizationService.delete(id);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void detachUser(@PathVariable UUID id, @PathVariable String userId) {
        userScope.assertOrganizationInScope(id);
        userScope.assertNotSuperAdminTarget(userId);
        organizationService.detachUser(id, userId);
    }

    @GetMapping("/{id}/members")
    public List<UserDTO> getMethodName(@RequestParam int page, @RequestParam int size, @PathVariable String id) {
        userScope.assertGroupInScope(id);
        return groupService.getAllGroupsMembers(id, page, size);
    }
}

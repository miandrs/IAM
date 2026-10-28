package com.eqima.keacloakadmin.controller;

import com.eqima.keacloakadmin.models.dto.OrganizationDto;
import com.eqima.keacloakadmin.models.dto.UserDTO;
import com.eqima.keacloakadmin.models.request.CreateOrganizationRequest;
import com.eqima.keacloakadmin.models.request.UpdateOrganizationRequest;
import com.eqima.keacloakadmin.service.GroupService;
import com.eqima.keacloakadmin.service.OrganizationService;

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

    public OrganizationController(OrganizationService organizationService, GroupService groupService) {
        this.organizationService = organizationService;
        this.groupService = groupService;
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
        return organizationService.getById(id);
    }

    @PatchMapping("/{id}")
    public OrganizationDto update(@PathVariable UUID id, @RequestBody UpdateOrganizationRequest request) {
        return organizationService.update(id, request);
    }

    @PutMapping("/{id}/members/{userId}")
    public void attachUser(@PathVariable UUID id, @PathVariable String userId) {
        organizationService.attachUser(id, userId);
    }

    // OrganizationController
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        organizationService.delete(id);
    }

    @GetMapping("/{id}/members")
    public List<UserDTO> getMethodName(@RequestParam int page, @RequestParam int size, @PathVariable String id) {
        return groupService.getAllGroupsMembers(id, page, size);
    }
}

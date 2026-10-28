package com.eqima.keacloakadmin.controller;

import com.eqima.keacloakadmin.models.dto.BusinessProfileDto;
import com.eqima.keacloakadmin.models.dto.ProfileRoleMappingDto;
import com.eqima.keacloakadmin.models.request.CreateBusinessProfileRequest;
import com.eqima.keacloakadmin.models.request.CreateRoleMappingRequest;
import com.eqima.keacloakadmin.models.request.UpdateBusinessProfileRequest;
import com.eqima.keacloakadmin.service.GouvernanceService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/governance/profiles")
@Validated
public class BusinessProfileController {

    private final GouvernanceService gouvernanceService;

    public BusinessProfileController(GouvernanceService gouvernanceService) {
        this.gouvernanceService = gouvernanceService;
    }

    @GetMapping
    public List<BusinessProfileDto> list() {
        return gouvernanceService.listProfiles();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BusinessProfileDto create(@Valid @RequestBody CreateBusinessProfileRequest req) {
        return gouvernanceService.createProfile(req);
    }

    @GetMapping("/{id}")
    public BusinessProfileDto getById(@PathVariable UUID id) {
        return gouvernanceService.getProfile(id);
    }

    @PatchMapping("/{id}")
    public BusinessProfileDto update(@PathVariable UUID id, @RequestBody UpdateBusinessProfileRequest req) {
        return gouvernanceService.updateProfile(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        gouvernanceService.deleteProfile(id);
    }

    @GetMapping("/{id}/role-mappings")
    public List<ProfileRoleMappingDto> listRoleMappings(@PathVariable UUID id) {
        return gouvernanceService.listRoleMappings(id);
    }

    @PostMapping("/{id}/role-mappings")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileRoleMappingDto createRoleMapping(@PathVariable UUID id, @Valid @RequestBody CreateRoleMappingRequest req) {
        return gouvernanceService.createRoleMapping(id, req);
    }

    @DeleteMapping("/{id}/role-mappings/{mappingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoleMapping(@PathVariable UUID id, @PathVariable UUID mappingId) {
        gouvernanceService.deleteRoleMapping(mappingId);
    }
}

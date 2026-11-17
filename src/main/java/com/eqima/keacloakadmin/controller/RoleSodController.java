package com.eqima.keacloakadmin.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.eqima.keacloakadmin.models.dto.DerivedRoleSodRuleDto;
import com.eqima.keacloakadmin.models.dto.RoleSodRuleDto;
import com.eqima.keacloakadmin.models.request.CreateRoleSodRuleRequest;
import com.eqima.keacloakadmin.service.RoleSodService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/policy/role-sod-rules")
@Validated
public class RoleSodController {

    private final RoleSodService roleSodService;

    public RoleSodController(RoleSodService roleSodService) {
        this.roleSodService = roleSodService;
    }

    @GetMapping
    public List<RoleSodRuleDto> list() {
        return roleSodService.listRules();
    }

    /** Règles de profils (A ⇄ B) appliquées aux rôles — lecture seule, rien n'est stocké. */
    @GetMapping("/derived")
    public List<DerivedRoleSodRuleDto> listDerived() {
        return roleSodService.listDerivedRules();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleSodRuleDto create(@Valid @RequestBody CreateRoleSodRuleRequest request) {
        return roleSodService.createRule(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        roleSodService.deleteRule(id);
    }
}

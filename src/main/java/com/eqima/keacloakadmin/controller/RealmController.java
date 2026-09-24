package com.eqima.keacloakadmin.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eqima.keacloakadmin.dto.RealmRepresentationDto;
import com.eqima.keacloakadmin.request.CreateRealmRequest;
import com.eqima.keacloakadmin.service.RealmService;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;


@RestController
@RequestMapping("/keycloak/realms")
public class RealmController {
    private final RealmService realmService;

    public RealmController(RealmService realmService) {
        this.realmService = realmService;
    }

    @PostMapping
    public String postNewRealms(@RequestBody CreateRealmRequest realmRequest) throws Exception {
        return realmService.createRealm(realmRequest);
    }

    @GetMapping
    public List<RealmRepresentationDto> getRealms() {
        return realmService.fetchRealm();
    }
    
}

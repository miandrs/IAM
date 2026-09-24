package com.eqima.keacloakadmin.controller;

import org.springframework.web.bind.annotation.RestController;

import com.eqima.keacloakadmin.dto.ClientRepresentationDto;
import com.eqima.keacloakadmin.dto.ClientRequest;
import com.eqima.keacloakadmin.entity.KeycloakClient;
import com.eqima.keacloakadmin.request.CreateClientRequest;
import com.eqima.keacloakadmin.service.ClientService;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
@RequestMapping("/keycloak/clients")
@Validated
public class ClientController {
    private final ClientService clientService;

    public ClientController(ClientService clientService){
        this.clientService = clientService;
    }

//In three steps
//step 1 : create the keycloak client in keycloak
//step 2 : grant user management roles
//step 3 : save the client information and encrypted credentials in MySQL database
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> createNewClient(@RequestBody CreateClientRequest clientRequest) throws Exception{
        String uuid = clientService.createClient(clientRequest);
        //clientService.addUserManagementRoles(clientRequest, uuid);
        //clientService.mysqlSaveClient(clientRequest, uuid);
        return Map.of("id",uuid);
    }
//Get all clients in a realm
    @GetMapping
    public List<ClientRepresentationDto> getAllClients() {
        return clientService.fetchClients();
    }

//Testing to retrieve client from access token
    @GetMapping("/mysql")
    public KeycloakClient getClient() throws Exception {
        return clientService.findClientByAccessTokenIssuer();
    }
    
    @GetMapping("/{realm}")
    public List<ClientRepresentationDto> getClientByRealm(@PathVariable String realm) {
        return clientService.fetchClientsForRealm(realm);
    }

    @PatchMapping("/{clientId}/update") 
    public KeycloakClient updateClient(@PathVariable String clientId, @RequestBody ClientRequest clientRequest) {
        clientService.updateClient(clientRequest, clientId);
        return clientService.findClientByRealmName(clientRequest.realmName());
    }

    @PostMapping("/{clientId}/secret/rotate")
    public KeycloakClient rotateClientSecret(@PathVariable String clientId) throws Exception {
        return clientService.rotateSecret(clientId);
    }

    @GetMapping("/{id}/secret")
    public String getClientSecret(@PathVariable String id) throws Exception {
        return clientService.getClientSecret(id);
    }
    
}

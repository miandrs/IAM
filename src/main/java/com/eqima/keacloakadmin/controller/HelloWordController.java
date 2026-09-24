package com.eqima.keacloakadmin.controller;

import org.springframework.web.bind.annotation.RestController;

import com.eqima.keacloakadmin.exception.ResourceNotFoundException;
import com.eqima.keacloakadmin.service.EncryptionService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;



@RestController
public class HelloWordController {

    @Autowired
    private EncryptionService encryptionService;

    @GetMapping
    public String getHelloWorld() {
        return "{ \"message\": \"Bonjour sur EQIMA Plateforme!\"}";
    }

    @PostMapping("encrypt")
    public String encryptText(@RequestBody String entity) throws Exception{
        return encryptionService.encrypt(entity);
    }

    @PostMapping("decrypt")
    public String decryptText(@RequestBody String entity) throws Exception{
        return encryptionService.decrypt(entity);
    }

    @GetMapping("exception")
    public ResponseEntity<?> resourceNotFoundExceptionTest() {
        throw new ResourceNotFoundException("Oops, resource not found exception example thrown");
    }
    
    
    
    
}

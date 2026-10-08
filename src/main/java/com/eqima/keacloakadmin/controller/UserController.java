package com.eqima.keacloakadmin.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.eqima.keacloakadmin.dto.PasswordReset;
import com.eqima.keacloakadmin.dto.RequiredActionRequest;
import com.eqima.keacloakadmin.dto.UserDTO;
import com.eqima.keacloakadmin.request.CreateUserRequest;
import com.eqima.keacloakadmin.service.UserService;

import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;



@RestController
@RequestMapping("/keycloak/users")
@Validated
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }
    
    @GetMapping
    public List<UserDTO> getTenants() {
        return userService.getAllUsers();
    }

    @GetMapping("{id}")
    public UserDTO getTenantById(@PathVariable String id) {
        return userService.getUserById(id);
    }
    // @GetMapping("realm/{realmName}")
    // public List<RealmAdminDto> getUserByRealm(@PathVariable String realmName) {
    //     return userService.mysqlGetUserFromRealm(realmName);
    // }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> createuser(@RequestBody @Valid CreateUserRequest request) {
        String id = userService.createUser(request);
        return Map.of("id",id);
    }
    
    @DeleteMapping("{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
    }
    
    @GetMapping("/authenticated")
    public Jwt getAuthenticatedUser(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return jwt;
    }

    @PostMapping("/{id}")
    public UserDTO postEnableorDisableUser(@RequestBody boolean enable, @PathVariable String id) {
        return userService.disableOrEnableUser(id, enable);
    }
    
    @DeleteMapping ("/{id}/sessions")
    public String deleteSessions(@PathVariable String id) {
        userService.sessionRevoke(id);
        return "User sessions revoked successfully";
    }

    @PutMapping("/{id}/password")
    public String putPassword(@PathVariable String id, @RequestBody PasswordReset password) {
        userService.resetPassword(id, password.newPassword(), password.temporary());
        return "User password reset successfully";
    }

    @PostMapping("/{id}/required-actions")
    public String postRequiredActions(@PathVariable String id, @RequestBody RequiredActionRequest request) {
        userService.requiredActionUpdatePassword(id, request.requiredActions());
        return "Required actions updated successfully";
    }
    
    @DeleteMapping ("/{id}/required-actions/{action}")
    public String deleteRequiredActions(@PathVariable String id, @PathVariable String action) {
        userService.deleteRequiredAction(id, action);
        return "Required action deleted successfully";
    }
}

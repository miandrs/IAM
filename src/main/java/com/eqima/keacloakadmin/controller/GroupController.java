package com.eqima.keacloakadmin.controller;

import org.springframework.web.bind.annotation.RestController;

import com.eqima.keacloakadmin.dto.UserDTO;
import com.eqima.keacloakadmin.request.CreateGroupRequest;
import com.eqima.keacloakadmin.service.GroupService;

import java.util.List;

import org.keycloak.representations.idm.GroupRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;





@RestController
@RequestMapping("keycloak/groups")
public class GroupController {

    private final GroupService groupService;
    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public String createEmptyGroup(@RequestBody CreateGroupRequest request) {
        groupService.createGroup(request);
        return request.groupName();
    }
    
    @GetMapping
    public List<GroupRepresentation> getAllGroups() {
        return groupService.getAllGroups();
    }

    @GetMapping("/{id}")
    public GroupRepresentation getGroupById(@PathVariable String id) {
        return groupService.getGroupById(id);
    }
    
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGroup(@PathVariable String id) {
        groupService.deleteGroup(id);   
    }
    
    @PutMapping("/{id}/members/{userId}")
    public String putMethodName(@PathVariable String id, @PathVariable String userId) {
        groupService.updateUserGroups(userId, List.of(id));
        return "User added to group successfully";
    }

    @DeleteMapping("/{id}/members/{userId}")
    public String deleteGroupMember(@PathVariable String id, @PathVariable String userId) {
        groupService.removeUserFromGroup(userId, id);
        return "User removed from group successfully";
    }

    @GetMapping("/{id}/members")
    public List<UserDTO> getMethodName(@RequestParam int page, @RequestParam int size, @PathVariable String id) {
        return groupService.getAllGroupsMembers(id, page, size);
    }
    
}

package com.eqima.keacloakadmin.service;

import com.eqima.keacloakadmin.models.dto.OrganizationDto;
import com.eqima.keacloakadmin.models.entity.Organization;
import com.eqima.keacloakadmin.exception.ResourceNotFoundException;
import com.eqima.keacloakadmin.repository.OrganizationRepository;

import jakarta.transaction.Transactional;

import com.eqima.keacloakadmin.models.request.CreateGroupRequest;
import com.eqima.keacloakadmin.models.request.CreateOrganizationRequest;
import com.eqima.keacloakadmin.models.request.UpdateOrganizationRequest;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.GroupRepresentation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final GroupService groupService;
    private final Keycloak keycloak;
    private final AuditService auditService;

    @Value("${keycloak.realm}")
    private String realm;

    public OrganizationService(
        OrganizationRepository organizationRepository,
        GroupService groupService,
        @Qualifier("keycloakClient") Keycloak keycloak,
        AuditService auditService
    ) {
        this.organizationRepository = organizationRepository;
        this.groupService = groupService;
        this.keycloak = keycloak;
        this.auditService = auditService;
    }

    public OrganizationDto create(CreateOrganizationRequest req) {
        // réutilise createGroup tel quel — même nettoyage trim+uppercase, même
        // gestion du 409 si le nom existe déjà
        GroupRepresentation group = groupService.createGroup(new CreateGroupRequest(req.name()));

        Organization org = new Organization();
        org.setKeycloakGroupId(group.getId());
        org.setActive(true);
        Organization saved = organizationRepository.save(org);

        auditService.log("CREATE_ORGANIZATION", null,
            Map.of("organizationId", saved.getId().toString(), "keycloakGroupId", group.getId(), "name", group.getName()));

        return toDto(saved, group.getName());
    }

    // public List<OrganizationDto> list() {
    //     return organizationRepository.findAll().stream()
    //         .map(org -> toDto(org, resolveGroupName(org.getKeycloakGroupId())))
    //         .toList();
    // }
    public List<OrganizationDto> list() {
        reconcileWithKeycloak();

        Set<String> visibleGroupIds = groupService.getAllGroups().stream()
            .map(GroupRepresentation::getId)
            .collect(Collectors.toSet());

        return organizationRepository.findAll().stream()
            .filter(org -> visibleGroupIds.contains(org.getKeycloakGroupId()))
            .map(org -> toDto(org, resolveGroupName(org.getKeycloakGroupId())))
            .toList();
    }

    // Crée une ligne Organization pour tout groupe Keycloak qui n'en a pas encore —
    // capture les groupes créés avant Organization, ou créés par un autre chemin (console admin, etc.)
    private void reconcileWithKeycloak() {
        Set<String> tracked = organizationRepository.findAll().stream()
            .map(Organization::getKeycloakGroupId)
            .collect(Collectors.toSet());

        for (GroupRepresentation group : groupService.listAllRaw()) {
            if (!tracked.contains(group.getId())) {
                Organization org = new Organization();
                org.setKeycloakGroupId(group.getId());
                org.setActive(true);
                organizationRepository.save(org);
            }
        }
    }

    public OrganizationDto getById(UUID id) {
        Organization org = findOrgOrThrow(id);
        return toDto(org, resolveGroupName(org.getKeycloakGroupId()));
    }

    @Transactional
    public OrganizationDto update(UUID id, UpdateOrganizationRequest req) {
        Organization org = findOrgOrThrow(id);
        boolean wasActive = org.isActive();

        if (req.active() != null) {
            org.setActive(req.active());
        }
        Organization saved = organizationRepository.save(org);

        if (req.active() != null && req.active() != wasActive) {
            groupService.setGroupMembersEnabled(saved.getKeycloakGroupId(), req.active());
        }

        auditService.log("UPDATE_ORGANIZATION", null,
            Map.of("organizationId", id.toString(), "active", org.isActive()));

        return toDto(saved, resolveGroupName(org.getKeycloakGroupId()));
    }

    

    // Ajout pur — ne touche à aucun autre groupe de l'utilisateur.
    // Volontairement PAS groupService.updateUserGroups(), qui remplace la liste complète.
    public void attachUser(UUID organizationId, String userId) {
        Organization org = findOrgOrThrow(organizationId);
        keycloak.realm(realm).users().get(userId).joinGroup(org.getKeycloakGroupId());

        auditService.log("ATTACH_USER_TO_ORGANIZATION", null,
            Map.of("organizationId", organizationId.toString(), "userId", userId));
    }

    // OrganizationService
    public void delete(UUID id) {
        Organization org = findOrgOrThrow(id);
        groupService.deleteGroup(org.getKeycloakGroupId()); // supprime aussi le vrai groupe Keycloak
        organizationRepository.delete(org);
        auditService.log("DELETE_ORGANIZATION", null,
            Map.of("organizationId", id.toString(), "keycloakGroupId", org.getKeycloakGroupId()));
    }

    private Organization findOrgOrThrow(UUID id) {
        return organizationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Organisation inexistante : " + id));
    }

    private String resolveGroupName(String keycloakGroupId) {
        try {
            return groupService.getGroupById(keycloakGroupId).getName();
        } catch (Exception e) {
            // le groupe Keycloak a disparu alors que l'Organization Postgres existe encore —
            // signal de dérive, cf. le même risque déjà noté pour UserProfileAssignment
            return "(groupe Keycloak introuvable)";
        }
    }

    private OrganizationDto toDto(Organization org, String name) {
        return new OrganizationDto(org.getId(), org.getKeycloakGroupId(), name, org.isActive());
    }
}

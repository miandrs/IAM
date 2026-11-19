package com.eqima.keacloakadmin.models.dto;

import java.util.List;
import java.util.UUID;

/** D'où vient la demande : tenants (organisations du demandeur), profil, produits et rôles visés. */
public record AccessRequestOriginDto(
    List<Organization> organizations,
    Profile profile,
    List<Product> products,
    List<Role> roles
) {
    public record Organization(UUID id, String name) {}
    public record Profile(UUID id, String name) {}
    public record Product(String clientUuid, String clientId) {}
    public record Role(String clientUuid, String clientId, String roleName) {}
}

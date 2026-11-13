package com.eqima.keacloakadmin.models.entity;

public enum OrphanType {
    NO_ORGANIZATION,                  // compte Keycloak actif sans aucun groupe
    ORGANIZATION_INACTIVE_OR_UNTRACKED, // compte actif dont aucune organisation n'est active / suivie
    DISABLED_USER_ACTIVE_PROFILE,     // compte désactivé qui garde des profils actifs
    ASSIGNMENT_USER_MISSING,          // attribution active dont l'utilisateur Keycloak n'existe plus
    ACCESS_REQUEST_USER_MISSING,      // demande PENDING dont l'utilisateur n'existe plus
    ASSIGNMENT_PROFILE_MISSING        // attribution active dont le profil a été supprimé
}

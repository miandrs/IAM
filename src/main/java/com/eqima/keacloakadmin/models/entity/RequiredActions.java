package com.eqima.keacloakadmin.models.entity;

public enum RequiredActions {
    UPDATE_PASSWORD("UPDATE_PASSWORD"),
    VERIFY_EMAIL("VERIFY_EMAIL"),
    CONFIGURE_TOTP("CONFIGURE_TOTP"),
    UPDATE_PROFILE("UPDATE_PROFILE"),
    TERMS_AND_CONDITIONS("TERMS_AND_CONDITIONS");

    private final String action;

    RequiredActions(String action) {
        this.action = action;
    }

    public String getAction() {
        return action;
    }

    public static RequiredActions fromString(String action) {
        for (RequiredActions requiredAction : RequiredActions.values()) {
            if (requiredAction.getAction().equalsIgnoreCase(action)) {
                return requiredAction;
            }
        }
        throw new IllegalArgumentException("No enum constant for action: " + action);
    }
}

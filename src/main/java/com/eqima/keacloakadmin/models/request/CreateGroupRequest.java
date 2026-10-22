package com.eqima.keacloakadmin.models.request;

import jakarta.validation.constraints.NotBlank;

public record CreateGroupRequest(
    @NotBlank String groupName
) {

}

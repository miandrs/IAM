package com.eqima.keacloakadmin.request;

import jakarta.validation.constraints.NotBlank;

public record CreateGroupRequest(
    @NotBlank String groupName
) {

}

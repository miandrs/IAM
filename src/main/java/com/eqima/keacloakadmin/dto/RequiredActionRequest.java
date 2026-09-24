package com.eqima.keacloakadmin.dto;

import java.util.List;

public record RequiredActionRequest(
    List<String> requiredActions
) {

}

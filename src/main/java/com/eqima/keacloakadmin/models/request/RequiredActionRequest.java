package com.eqima.keacloakadmin.models.request;

import java.util.List;

public record RequiredActionRequest(
    List<String> requiredActions
) {

}

package com.eqima.keacloakadmin.models.request;

import java.util.Date;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateProjectRequest (
    @NotBlank String name,
    @NotBlank String description,
    @NotBlank String projectTypeId,
    @NotBlank String preparatorId,
    @NotNull Long amount,
    @NotNull Integer beneficiaries,
    @NotNull Date startDate,
    @NotNull Date endDate,
    @NotBlank String priority
) {}
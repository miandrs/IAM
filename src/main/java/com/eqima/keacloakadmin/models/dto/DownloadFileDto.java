package com.eqima.keacloakadmin.models.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class DownloadFileDto {
    private String contentType;
    private byte[] data;
}

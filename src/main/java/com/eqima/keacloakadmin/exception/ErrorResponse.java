package com.eqima.keacloakadmin.exception;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor 
public class ErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String message;
    private String error;
    private String path;
    public ErrorResponse(int status, String message, String error, String path) {
        this.status = status;
        this.message = message;
        this.error = error;
        this.path = path;
    }
}

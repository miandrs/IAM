package com.eqima.keacloakadmin.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler{

//Resource not found exception
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFountException(ResourceNotFoundException ex, WebRequest request){
        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            ex.getMessage(),
            "Elément introuvable ou inexistant.",
            request.getDescription(false)
        );
        return ResponseEntity.status(errorResponse.getStatus()).body(errorResponse);
    }

//Invalid request Exception
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequestException(InvalidRequestException ex, WebRequest request){
        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            ex.getMessage(),
            "Requête invalide.",
            request.getDescription(false)
        );
        return ResponseEntity.status(errorResponse.getStatus()).body(errorResponse);
    }

//Handle unexpected exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            ex.getMessage(),
            "Une erreur interne est survenue.",
            request.getDescription(true)
        );
        return ResponseEntity.status(errorResponse.getStatus()).body(errorResponse);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
            HttpStatus.CONFLICT.value(),
            "Cette opération entre en conflit avec des données existantes.",
            "Conflit.",
            request.getDescription(false)));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse(HttpStatus.CONFLICT.value(), ex.getMessage(), "Données dupliqué", request.getDescription(true)));
    }

    // Sans ce handler, le catch-all Exception.class ci-dessus transforme tout refus d'accès levé par un service en HTTP 500.
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse(HttpStatus.FORBIDDEN.value(), ex.getMessage(), "Accès refusé.", request.getDescription(false)));
    }
}

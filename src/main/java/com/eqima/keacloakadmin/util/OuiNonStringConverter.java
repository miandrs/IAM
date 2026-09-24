package com.eqima.keacloakadmin.util;

import com.opencsv.bean.AbstractBeanField;
import com.opencsv.exceptions.CsvConstraintViolationException;
import com.opencsv.exceptions.CsvDataTypeMismatchException;

public class OuiNonStringConverter extends AbstractBeanField<Boolean, String> {
    @Override
    protected Boolean convert(String value) throws CsvConstraintViolationException,CsvDataTypeMismatchException{
        if (value == null || value.isBlank()) {
            throw new CsvConstraintViolationException(
                "La valeur ne peut pas être vide ou nulle. Valeurs acceptées : 'Oui' ou 'Non'."
            );
        }

        String valeurNormalisee = value.trim();
        
        if (!valeurNormalisee.equalsIgnoreCase("Oui") && !valeurNormalisee.equalsIgnoreCase("Non")) {
            throw new CsvDataTypeMismatchException(
                valeurNormalisee,
                Boolean.class,
                "Valeur invalide : '" + valeurNormalisee + "'. Valeurs acceptées : 'Oui' ou 'Non'."
            );
        }

        return valeurNormalisee.equalsIgnoreCase("Oui");
    }

}

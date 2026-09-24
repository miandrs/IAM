package com.eqima.keacloakadmin.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.eqima.keacloakadmin.exception.InvalidRequestException;

@Service
public class EncryptionService {

    @Value("${encryption.secret-key}")
    private String secretKey;

    private SecretKeySpec createSecretKey() {
        byte[] decodedKey = secretKey.getBytes(StandardCharsets.UTF_8);
        if (decodedKey.length != 16 && decodedKey.length != 24 && decodedKey.length != 32) {
            throw new InvalidRequestException("Secret key must be 16, 24, or 32 bytes for AES. Got: " + decodedKey.length);
        }
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
    }

    public String encrypt(String data) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, createSecretKey());
        byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    public String decrypt(String encryptedData) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, createSecretKey());
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }
}

package com.carlink.privacy;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Encrypts {@code users.phone} transparently (security plan S-17): the entity
 * holds the plain E.164 number, the column holds {@link PhoneCipher} output.
 * Hibernate builds converters through Spring, so the cipher is injected.
 */
@Converter
public class EncryptedPhoneConverter implements AttributeConverter<String, String> {

    private final PhoneCipher cipher;

    public EncryptedPhoneConverter(PhoneCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return cipher.decrypt(dbData);
    }
}

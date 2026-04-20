package io.webcontify.identity.repository.entity;

import io.webcontify.identity.model.UserStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UserStatusConverter implements AttributeConverter<UserStatus, String> {

    @Override
    public String convertToDatabaseColumn(UserStatus status) {
        return status == null ? null : status.name();
    }

    @Override
    public UserStatus convertToEntityAttribute(String value) {
        return value == null ? null : UserStatus.valueOf(value);
    }
}

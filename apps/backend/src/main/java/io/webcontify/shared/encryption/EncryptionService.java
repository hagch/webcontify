package io.webcontify.shared.encryption;

public interface EncryptionService {

    String encrypt(String value);

    String decrypt(String encryptedValue);
}

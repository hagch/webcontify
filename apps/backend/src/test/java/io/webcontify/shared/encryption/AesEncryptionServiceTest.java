package io.webcontify.shared.encryption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AesEncryptionServiceTest {

    private AesEncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String base64Key = Base64.getEncoder().encodeToString(key);
        encryptionService = new AesEncryptionService(base64Key);
    }

    @Test
    void shouldEncryptAndDecryptRoundtrip() {
        String plaintext = "my-secret-smtp-password";

        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void shouldEncryptAndDecryptUnicodeText() {
        String plaintext = "Passwort mit Umlauten: aeoeu-ss";

        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void shouldEncryptAndDecryptEmptyString() {
        String plaintext = "";

        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void shouldProduceCiphertextDifferentFromPlaintext() {
        String plaintext = "my-secret";

        String encrypted = encryptionService.encrypt(plaintext);

        assertThat(encrypted).isNotEqualTo(plaintext);
    }

    @Test
    void shouldProduceDifferentCiphertextsForSamePlaintext() {
        String plaintext = "same-input";

        String first = encryptionService.encrypt(plaintext);
        String second = encryptionService.encrypt(plaintext);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void shouldProduceDifferentCiphertextsForDifferentPlaintexts() {
        String encrypted1 = encryptionService.encrypt("plaintext-one");
        String encrypted2 = encryptionService.encrypt("plaintext-two");

        assertThat(encrypted1).isNotEqualTo(encrypted2);
    }

    @Test
    void shouldThrowOnNullEncrypt() {
        assertThatThrownBy(() -> encryptionService.encrypt(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowOnNullDecrypt() {
        assertThatThrownBy(() -> encryptionService.decrypt(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowOnInvalidCiphertext() {
        assertThatThrownBy(() -> encryptionService.decrypt("not-valid-base64-ciphertext!!!"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowOnTamperedCiphertext() {
        String encrypted = encryptionService.encrypt("secret");
        byte[] bytes = Base64.getDecoder().decode(encrypted);
        bytes[bytes.length - 1] ^= 0xFF;
        String tampered = Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> encryptionService.decrypt(tampered))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowOnTooShortCiphertext() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[5]);

        assertThatThrownBy(() -> encryptionService.decrypt(tooShort))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowOnInvalidKeyLength() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new AesEncryptionService(shortKey))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowOnEmptyKey() {
        assertThatThrownBy(() -> new AesEncryptionService(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldDecryptWithSameKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String base64Key = Base64.getEncoder().encodeToString(key);

        AesEncryptionService service1 = new AesEncryptionService(base64Key);
        AesEncryptionService service2 = new AesEncryptionService(base64Key);

        String encrypted = service1.encrypt("cross-instance-test");
        String decrypted = service2.decrypt(encrypted);

        assertThat(decrypted).isEqualTo("cross-instance-test");
    }
}

package io.webcontify.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.JwtException;

@ExtendWith(MockitoExtension.class)
class MultiRealmJwtDecoderTest {

    private static final String KEYCLOAK_BASE_URL = "http://auth.localhost";

    private MultiRealmJwtDecoder decoder;

    @BeforeEach
    void setUp() {
        decoder = new MultiRealmJwtDecoder(KEYCLOAK_BASE_URL);
    }

    // --- extractIssuer ---

    @Test
    void extractIssuer_validJwtWithIssClaim_returnsIssuer() {
        String token = buildToken("{\"iss\":\"http://auth.localhost/realms/test\",\"sub\":\"user-1\"}");

        String issuer = decoder.extractIssuer(token);

        assertThat(issuer).isEqualTo("http://auth.localhost/realms/test");
    }

    @Test
    void extractIssuer_jwtWithLessThanTwoParts_throwsJwtException() {
        String tokenWithOnePart = "onlyonepart";

        assertThatThrownBy(() -> decoder.extractIssuer(tokenWithOnePart))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("Invalid JWT format");
    }

    @Test
    void extractIssuer_jwtWithInvalidBase64Payload_throwsJwtException() {
        String header = base64UrlEncode("{\"alg\":\"RS256\"}");
        String invalidPayload = "not!valid!base64!!!";
        String token = header + "." + invalidPayload + ".signature";

        assertThatThrownBy(() -> decoder.extractIssuer(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("Failed to extract issuer from JWT");
    }

    @Test
    void extractIssuer_jwtPayloadMissingIssClaim_throwsJwtException() {
        String token = buildToken("{\"sub\":\"user-1\",\"exp\":9999999999}");

        assertThatThrownBy(() -> decoder.extractIssuer(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("JWT missing iss claim");
    }

    // --- validateIssuer ---

    @Test
    void validateIssuer_validIssuerWithCorrectPrefix_doesNotThrow() {
        String issuer = "http://auth.localhost/realms/my-tenant";

        // no exception expected
        decoder.validateIssuer(issuer);
    }

    @Test
    void validateIssuer_issuerWithWrongPrefix_throwsJwtException() {
        String issuer = "http://evil.com/realms/my-tenant";

        assertThatThrownBy(() -> decoder.validateIssuer(issuer))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("Untrusted issuer");
    }

    @Test
    void validateIssuer_issuerContainingPrefixAsSubstringNotAtStart_throwsJwtException() {
        String issuer = "http://evil.com/http://auth.localhost/realms/test";

        assertThatThrownBy(() -> decoder.validateIssuer(issuer))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("Untrusted issuer");
    }

    // --- helpers ---

    private String buildToken(String payloadJson) {
        String header = base64UrlEncode("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        String payload = base64UrlEncode(payloadJson);
        return header + "." + payload + ".fakesignature";
    }

    private String base64UrlEncode(String json) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}

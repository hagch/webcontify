package io.webcontify.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * JWT Decoder der Tokens von mehreren Keycloak Realms akzeptiert.
 * Validiert dass der Issuer mit der konfigurierten Keycloak Base URL beginnt
 * und cached JwtDecoder-Instanzen pro Realm.
 */
public class MultiRealmJwtDecoder implements JwtDecoder {

    private static final Logger log = LoggerFactory.getLogger(MultiRealmJwtDecoder.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String keycloakBaseUrl;
    private final String issuerPrefix;
    private final Map<String, JwtDecoder> decoderCache = new ConcurrentHashMap<>();

    public MultiRealmJwtDecoder(String keycloakBaseUrl) {
        this.keycloakBaseUrl = keycloakBaseUrl;
        this.issuerPrefix = keycloakBaseUrl + "/realms/";
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        String issuer = extractIssuer(token);
        validateIssuer(issuer);
        JwtDecoder decoder = decoderCache.computeIfAbsent(issuer, this::createDecoder);
        return decoder.decode(token);
    }

    String extractIssuer(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                throw new JwtException("Invalid JWT format");
            }
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = OBJECT_MAPPER.readValue(payload, Map.class);
            Object iss = claims.get("iss");
            if (iss == null) {
                throw new JwtException("JWT missing iss claim");
            }
            return iss.toString();
        } catch (JwtException e) {
            throw e;
        } catch (Exception e) {
            throw new JwtException("Failed to extract issuer from JWT", e);
        }
    }

    void validateIssuer(String issuer) {
        if (!issuer.startsWith(issuerPrefix)) {
            log.warn("Untrusted JWT issuer: {}, expected prefix: {}", issuer, issuerPrefix);
            throw new JwtException("Untrusted issuer: " + issuer);
        }
    }

    private JwtDecoder createDecoder(String issuer) {
        log.info("Creating JwtDecoder for issuer: {}", issuer);
        return JwtDecoders.fromIssuerLocation(issuer);
    }
}

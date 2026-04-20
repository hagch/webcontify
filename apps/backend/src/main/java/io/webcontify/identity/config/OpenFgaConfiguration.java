package io.webcontify.identity.config;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.configuration.ClientConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenFgaConfiguration {

    @Value("${openfga.api-url}")
    private String apiUrl;

    @Bean
    public OpenFgaClient openFgaClient() throws Exception {
        var config = new ClientConfiguration().apiUrl(apiUrl);
        return new OpenFgaClient(config);
    }
}

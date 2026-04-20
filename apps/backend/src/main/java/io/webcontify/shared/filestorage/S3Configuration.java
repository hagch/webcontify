package io.webcontify.shared.filestorage;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Configuration {

    @Value("${webcontify.s3.endpoint:}")
    private String endpoint;

    @Value("${webcontify.s3.public-endpoint:${webcontify.s3.endpoint:}}")
    private String publicEndpoint;

    @Value("${webcontify.s3.region:us-east-1}")
    private String region;

    @Value("${webcontify.s3.access-key:}")
    private String accessKey;

    @Value("${webcontify.s3.secret-key:}")
    private String secretKey;

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(region))
                .forcePathStyle(true);

        if (!accessKey.isEmpty() && !secretKey.isEmpty()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)));
        }

        if (!endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        // Use public endpoint for presigned URLs (browser-accessible)
        var presignerEndpoint = !publicEndpoint.isEmpty() ? publicEndpoint : endpoint;

        var builder = S3Presigner.builder()
                .region(Region.of(region))
                .serviceConfiguration(
                        software.amazon.awssdk.services.s3.S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .build());

        if (!accessKey.isEmpty() && !secretKey.isEmpty()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)));
        }

        if (!presignerEndpoint.isEmpty()) {
            builder.endpointOverride(URI.create(presignerEndpoint));
        }

        return builder.build();
    }
}

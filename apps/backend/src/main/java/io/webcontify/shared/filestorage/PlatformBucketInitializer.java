package io.webcontify.shared.filestorage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class PlatformBucketInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformBucketInitializer.class);

    private final FileStorageService fileStorageService;

    public PlatformBucketInitializer(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Ensuring platform S3 bucket exists");
        try {
            fileStorageService.createBucket("platform");
        } catch (Exception e) {
            log.warn("Failed to create platform bucket at startup — will retry on first use", e);
        }
    }
}

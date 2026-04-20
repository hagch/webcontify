package io.webcontify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic(
    systemName = "Webcontify",
    sharedModules = {"shared", "generated"}
)
@SpringBootApplication
public class WebcontifyApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebcontifyApplication.class, args);
    }
}

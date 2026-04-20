package io.webcontify;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithStructureTest {

    @Test
    void shouldHaveValidModuleStructure() {
        ApplicationModules.of(WebcontifyApplication.class).verify();
    }
}

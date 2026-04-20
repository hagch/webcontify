package io.webcontify.shared.util;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import java.util.UUID;

public final class UuidGenerator {

    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    private UuidGenerator() {}

    public static UUID generate() {
        return GENERATOR.generate();
    }
}

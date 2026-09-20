package com.ludwici.cobblemonstreamermode.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;

import java.nio.file.Path;

public final class PlatformPaths {
    private PlatformPaths() {
    }

    @ExpectPlatform
    public static Path configDir() {
        throw new AssertionError();
    }
}

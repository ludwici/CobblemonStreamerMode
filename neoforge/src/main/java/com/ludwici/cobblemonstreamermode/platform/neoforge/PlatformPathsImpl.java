package com.ludwici.cobblemonstreamermode.platform.neoforge;

import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class PlatformPathsImpl {
    private PlatformPathsImpl() {
    }

    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }
}

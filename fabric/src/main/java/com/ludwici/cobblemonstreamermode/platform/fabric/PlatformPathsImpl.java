package com.ludwici.cobblemonstreamermode.platform.fabric;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class PlatformPathsImpl {
    private PlatformPathsImpl() {
    }

    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }
}

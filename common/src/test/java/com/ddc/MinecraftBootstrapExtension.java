package com.ddc;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Boots Minecraft's registries once per test run.
 *
 * <p>Only tests that touch Minecraft types need this. The rules engine under {@code com.ddc.core}
 * deliberately does not, which is why its tests run without any of this machinery.
 */
public final class MinecraftBootstrapExtension implements BeforeAllCallback {

    private static boolean booted;

    @Override
    public void beforeAll(ExtensionContext context) {
        bootstrap();
    }

    public static synchronized void bootstrap() {
        if (booted) {
            return;
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        booted = true;
    }
}

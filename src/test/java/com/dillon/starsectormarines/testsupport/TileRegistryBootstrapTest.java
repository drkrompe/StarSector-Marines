package com.dillon.starsectormarines.testsupport;

import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guards the global test bootstrap: this class installs nothing of its own, so a
 * non-null {@link TileRegistry#installed()} proves {@link TileRegistryTestInstaller}
 * was auto-registered and fired. If this fails, the {@code junit-platform.properties}
 * autodetection flag or the {@code META-INF/services} registration regressed, and
 * gen tests would silently take the registry-less RNG-divergent path.
 */
public class TileRegistryBootstrapTest {

    @Test
    void registryInstalledGlobally() {
        TileRegistry reg = TileRegistry.installed();
        assertNotNull(reg, "TileRegistryTestInstaller should have installed a registry before any test");
        assertNotNull(reg.tile("nature.grass-1"), "nature sheet not loaded into the bootstrapped registry");
        assertNotNull(reg.tile("urban3.sidewalk"), "urban3 sheet not loaded into the bootstrapped registry");
    }
}

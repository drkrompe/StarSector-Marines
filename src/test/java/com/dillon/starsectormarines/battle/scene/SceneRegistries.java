package com.dillon.starsectormarines.battle.scene;

import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;

import java.nio.file.Path;

/**
 * What {@code onApplicationLoad} would have done, before any scene plays: every disk catalog the game loads
 * ({@link TileRegistryTestInstaller}), the same set {@code battleProbe} and every JUnit test stand on.
 *
 * <p>It used to install only the weapon and special-equipment registries an armed squad reads. A scene then played
 * without the hull specs, the armour, template, faction and mech catalogs, on their {@code installed() == null}
 * paths, and the same fixture played a different battle here than under the probe: a heart lane that broke at tick
 * 8341 in a probe reading broke at 19213 in its clip. Idempotent: a registry already installed is left alone, which
 * is what lets the snapshot runner call it for every suite in a row.
 */
public final class SceneRegistries {

    private SceneRegistries() {}

    public static void installArmoury(Path projectRoot) throws Exception {
        TileRegistryTestInstaller.install(projectRoot);
    }
}

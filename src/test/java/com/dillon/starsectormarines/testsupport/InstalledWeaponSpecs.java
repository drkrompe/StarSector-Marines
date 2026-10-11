package com.dillon.starsectormarines.testsupport;

import com.dillon.starsectormarines.battle.warship.VanillaWeaponLooks;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Lets headless runs resolve a vanilla weapon's look from the installed game's
 * own {@code .wpn} files, the way {@link InstalledHullSpecs} does for hulls.
 */
public final class InstalledWeaponSpecs {

    private InstalledWeaponSpecs() {}

    public static void install(Path starsectorCore) {
        if (starsectorCore == null) return;
        Path weapons = starsectorCore.resolve("data").resolve("weapons");
        VanillaWeaponLooks.useSource(id ->
                new JSONObject(Files.readString(weapons.resolve(id + ".wpn"))));
    }

    public static void install() {
        String dir = System.getProperty("starsectorDir");
        if (dir == null || dir.isBlank()) return;
        install(Paths.get(dir, "starsector-core"));
    }
}

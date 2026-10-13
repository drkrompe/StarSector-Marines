package com.dillon.starsectormarines.testsupport;

import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The weapon slots a loaded game would have supplied, read off the install.
 *
 * <p>An armed craft mounts its turret where its hull's {@code .ship} says, and headless there is no
 * {@code SettingsAPI} to ask, so {@link TurretSlotResolver} resolves every hull to no slots and the
 * craft flies unarmed. This is the stand-in that closes that, with the same degradation as
 * {@link InstalledHullSpecs}: no install means nothing is registered.
 *
 * <p>Deliberately separate from {@link InstalledHullSpecs#install()} and opt-in. Sizing a hull
 * cannot change what an existing scene does; arming every craft in one would, so a scene or test
 * that is about an armed craft asks for it by name.
 */
public final class InstalledWeaponSlots implements TurretSlotResolver.HullSpecSource {

    private final Path hullDir;

    private InstalledWeaponSlots(Path hullDir) {
        this.hullDir = hullDir;
    }

    /** Resolves weapon slots from {@code <starsectorCore>/data/hulls}; does nothing for a null path. */
    public static void install(Path starsectorCore) {
        if (starsectorCore == null) return;
        TurretSlotResolver.useHullSpecs(new InstalledWeaponSlots(starsectorCore.resolve("data").resolve("hulls")));
    }

    /** The same, for a caller that knows only the {@code starsectorDir} property the build sets on headless tasks. */
    public static void install() {
        String dir = System.getProperty("starsectorDir");
        if (dir == null || dir.isBlank()) return;
        install(Paths.get(dir, "starsector-core"));
    }

    @Override
    public JSONObject spec(String hullId) throws Exception {
        return new JSONObject(Files.readString(hullDir.resolve(hullId + ".ship")));
    }
}

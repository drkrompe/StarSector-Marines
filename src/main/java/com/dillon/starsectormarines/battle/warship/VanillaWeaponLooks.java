package com.dillon.starsectormarines.battle.warship;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Looks up how a vanilla weapon is drawn, by weapon id.
 *
 * <p>In the game the spec comes from the loaded data, so a mod's own weapon or
 * a re-tuned vanilla one resolves the same way. Outside the game there is no
 * settings API, so a tool installs a {@link Source} that reads the install's
 * files — the same shape as the hull-dimension seam — and without one an
 * unresolvable weapon is simply absent rather than an error: a beam that cannot
 * be looked up draws as the plain fallback.
 */
public final class VanillaWeaponLooks {

    private static final Logger LOG = Global.getLogger(VanillaWeaponLooks.class);

    /** Where a weapon's {@code .wpn} spec comes from. */
    @FunctionalInterface
    public interface Source {
        JSONObject load(String weaponId) throws Exception;
    }

    private static final Source GAME = id ->
            Global.getSettings().loadJSON("data/weapons/" + id + ".wpn");

    private static final VanillaBeamLook ABSENT = new VanillaBeamLook(
            "", null, null, null, 0f, null, null, 0f, 0f, null, null, null, null);

    private static volatile Source source = GAME;
    private static final Map<String, VanillaBeamLook> CACHE = new ConcurrentHashMap<>();

    private VanillaWeaponLooks() {}

    /** Replaces where specs are read from, and forgets what was cached. */
    public static void useSource(Source replacement) {
        source = replacement != null ? replacement : GAME;
        CACHE.clear();
    }

    /** The beam look of {@code weaponId}, or {@code null} for a non-beam or unknown weapon. */
    public static VanillaBeamLook beam(String weaponId) {
        if (weaponId == null || weaponId.isEmpty()) return null;
        VanillaBeamLook look = CACHE.computeIfAbsent(weaponId, VanillaWeaponLooks::resolve);
        return look == ABSENT ? null : look;
    }

    private static VanillaBeamLook resolve(String weaponId) {
        try {
            VanillaBeamLook look = VanillaBeamLook.fromSpec(weaponId, source.load(weaponId));
            return look != null ? look : ABSENT;
        } catch (Exception e) {
            LOG.warn("VanillaWeaponLooks: " + weaponId + " — "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return ABSENT;
        }
    }
}

package com.dillon.starsectormarines.battle.warship;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a vanilla weapon id to a {@link VanillaWeapon}, through
 * {@link VanillaData}. A weapon that is not in the table is absent, not an
 * error: a hull armed with something unknown fires one gun fewer.
 */
public final class VanillaWeapons {

    private static final Logger LOG = Global.getLogger(VanillaWeapons.class);
    private static final String TABLE = "data/weapons/weapon_data.csv";

    private static volatile Map<String, Map<String, String>> rows;
    private static final Map<String, VanillaWeapon> CACHE = new ConcurrentHashMap<>();
    private static final VanillaWeapon ABSENT = VanillaWeapon.of("", Map.of(), null, null);

    private VanillaWeapons() {}

    /** Forgets what was read, for a tool that has just changed where data comes from. */
    public static void clearCache() {
        rows = null;
        CACHE.clear();
    }

    public static VanillaWeapon get(String weaponId) {
        if (weaponId == null || weaponId.isEmpty()) return null;
        VanillaWeapon weapon = CACHE.computeIfAbsent(weaponId, VanillaWeapons::resolve);
        return weapon == ABSENT ? null : weapon;
    }

    private static VanillaWeapon resolve(String id) {
        try {
            Map<String, String> row = table().get(id);
            if (row == null) return ABSENT;
            JSONObject wpn = optional("data/weapons/" + id + ".wpn");
            String projectileId = wpn == null ? null : wpn.optString("projectileSpecId", null);
            JSONObject proj = projectileId == null ? null : projectile(projectileId);
            JSONObject behaviour = proj == null ? null : proj.optJSONObject("behaviorSpec");
            String warheadId = behaviour == null ? null : behaviour.optString("projectileSpec", null);
            JSONObject warhead = warheadId == null || warheadId.isBlank() ? null
                    : optional("data/weapons/proj/" + warheadId + ".proj");
            return VanillaWeapon.of(id, row, wpn, proj, warhead);
        } catch (Exception e) {
            LOG.warn("VanillaWeapons: " + id + " — " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return ABSENT;
        }
    }

    /**
     * The magazine a weapon carries, from the {@code ammo} column of the weapon
     * table; zero when the column is blank (the gun does not run out) or unreadable.
     */
    public static int ammo(String weaponId) {
        try {
            Map<String, String> row = table().get(weaponId);
            String v = row == null ? null : row.get("ammo");
            return v == null || v.isBlank() ? 0 : Math.max(0, (int) Float.parseFloat(v.trim()));
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * The weapon table's {@code hints} column for a weapon, split into its comma-separated
     * tokens (trimmed, as written); empty when the weapon is unknown or has none.
     */
    public static List<String> hints(String weaponId) {
        try {
            Map<String, String> row = table().get(weaponId);
            String v = row == null ? null : row.get("hints");
            if (v == null || v.isBlank()) return List.of();
            List<String> out = new ArrayList<>();
            for (String token : v.split(",")) {
                String t = token.trim();
                if (!t.isEmpty()) out.add(t);
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static Map<String, Map<String, String>> table() throws Exception {
        Map<String, Map<String, String>> t = rows;
        if (t == null) {
            t = new HashMap<>();
            for (Map<String, String> row : VanillaData.csv(TABLE)) {
                String id = row.get("id");
                if (id != null && !id.isBlank()) t.put(id, row);
            }
            rows = t;
        }
        return t;
    }

    /**
     * A projectile spec by the id a weapon names. The game files a few of them under a
     * different name from the id they declare (the Locust's {@code locust} is
     * {@code locust_srm.proj}), and the data seam cannot list a folder, so the one
     * known alternative spelling is tried when the plain one is absent.
     */
    private static JSONObject projectile(String id) {
        JSONObject proj = optional("data/weapons/proj/" + id + ".proj");
        return proj != null ? proj : optional("data/weapons/proj/" + id + "_srm.proj");
    }

    private static JSONObject optional(String path) {
        try {
            return VanillaData.json(path);
        } catch (Exception e) {
            return null;
        }
    }
}

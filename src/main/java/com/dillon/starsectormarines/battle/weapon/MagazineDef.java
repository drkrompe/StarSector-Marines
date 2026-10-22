package com.dillon.starsectormarines.battle.weapon;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * A handheld primary's detachable feed: how many rounds one load holds at each
 * equipment grade, how long a reload takes, how the trigger releases them, and
 * how much each round kicks the next one off its line.
 *
 * <p>A weapon without one keeps the cycle its {@code cooldown} describes and
 * never reloads; that is what separates the pulse and laser families from the
 * ballistic ones. With one, {@code cooldown} is the action's cycle between
 * rounds and the reload is the pause the magazine buys.
 *
 * <p>The size is authored per grade, worst first, so a better-made rifle can
 * carry more rounds as well as cycle faster: the grade is one of the ways a
 * weapon levels.
 *
 * @param sizeByGrade rounds per load, indexed by {@code EquipmentGrade.ordinal()}
 * @param reloadSeconds the reload at service grade for a regular; the grade's
 *     and the soldier's cooldown multipliers scale it as they scale the cycle
 * @param trigger how a held control trigger releases rounds
 * @param recoilPerShot accuracy fraction each round takes from the next
 * @param recoilMax the most accuracy fraction sustained fire can take away
 * @param recoilRecovery accuracy fraction recovered per second
 */
public record MagazineDef(int[] sizeByGrade, float reloadSeconds, Trigger trigger,
                          float recoilPerShot, float recoilMax, float recoilRecovery) {

    /** How the controlled marine's trigger releases rounds; AI fire follows the cycle either way. */
    public enum Trigger {
        /** One release per press of the trigger. */
        SEMI,
        /** Fires every cycle while the trigger is held. */
        AUTO
    }

    private static final Set<String> FIELDS =
            Set.of("size", "reload", "trigger", "recoil", "recoilMax", "recoilRecovery");

    public int size(EquipmentGrade grade) {
        return sizeByGrade[(grade != null ? grade : EquipmentGrade.SERVICE).ordinal()];
    }

    /**
     * Average seconds between trigger pulls over a whole load and its reload, for a weapon that cycles
     * every {@code cycleSeconds}, releases {@code roundsPerTrigger} rounds a pull and reloads in
     * {@code reloadSeconds}: what a fire rate is worth once the magazine has to be changed.
     */
    public float sustainedTriggerInterval(EquipmentGrade grade, float cycleSeconds, int roundsPerTrigger,
                                          float reloadSeconds) {
        int pulls = (size(grade) + roundsPerTrigger - 1) / Math.max(1, roundsPerTrigger);
        return ((pulls - 1) * cycleSeconds + Math.max(cycleSeconds, reloadSeconds)) / pulls;
    }

    /**
     * Reads {@code sim.magazine}: {@code size} is one count for every grade or an
     * array of one count per grade, worst first. Returns {@code null} when absent.
     */
    static MagazineDef parse(JSONObject magazine, String weaponId) throws JSONException {
        if (magazine == null) return null;
        var keys = magazine.keys();
        while (keys.hasNext()) {
            String key = String.valueOf(keys.next());
            if (!FIELDS.contains(key)) {
                throw new JSONException("Weapon '" + weaponId + "' magazine has unknown field '" + key + "'");
            }
        }
        int grades = EquipmentGrade.values().length;
        int[] sizes = new int[grades];
        Object size = magazine.get("size");
        if (size instanceof JSONArray array) {
            if (array.length() != grades) {
                throw new JSONException("Weapon '" + weaponId + "' magazine.size needs one count per grade ("
                        + grades + ")");
            }
            for (int i = 0; i < grades; i++) sizes[i] = array.getInt(i);
        } else {
            Arrays.fill(sizes, magazine.getInt("size"));
        }
        for (int s : sizes) {
            if (s < 1) throw new JSONException("Weapon '" + weaponId + "' magazine.size must be at least 1");
        }
        float reload = (float) magazine.getDouble("reload");
        if (!(reload > 0f)) throw new JSONException("Weapon '" + weaponId + "' magazine.reload must be positive");
        Trigger trigger;
        try {
            trigger = Trigger.valueOf(magazine.optString("trigger", "auto").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new JSONException("Weapon '" + weaponId + "' magazine.trigger must be semi or auto");
        }
        float recoil = (float) magazine.optDouble("recoil", 0.0);
        float recoilMax = (float) magazine.optDouble("recoilMax", recoil > 0f ? 0.5 : 0.0);
        float recovery = (float) magazine.optDouble("recoilRecovery", 1.0);
        if (recoil < 0f || recoilMax < 0f || recoilMax >= 1f || recovery < 0f) {
            throw new JSONException("Weapon '" + weaponId
                    + "' magazine recoil must be non-negative and recoilMax below 1");
        }
        return new MagazineDef(sizes, reload, trigger, recoil, recoilMax, recovery);
    }
}

package com.dillon.starsectormarines.battle.battery;

import com.dillon.starsectormarines.battle.warship.VanillaWeapon;
import com.dillon.starsectormarines.battle.warship.VanillaWeapons;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The ship-grade guns a fortress battery carries: a set of vanilla weapon ids taken from what the game's own station
 * modules mount, so a battery fights a ship at ship-versus-ship balance and its figures arrive with the game's data
 * rather than from a table of this mod's.
 *
 * <p>The ids are the only thing authored here. Range, damage, burst, cooldown and speed all come from
 * {@link VanillaWeapons}; a weapon the data does not list is simply absent, so a battery whose whole loadout is
 * unknown has no guns rather than failing to exist.
 */
public final class BatteryLoadout {

    private static final Map<String, BatteryLoadout> BY_ID = new LinkedHashMap<>();

    /** A station module's pair of heavy guns: {@code module_large1_lowtech} mounts two Hellbores. */
    public static final BatteryLoadout HELLBORE_PAIR = register(
            new BatteryLoadout("hellbore-pair", "module_large1_lowtech", List.of("hellbore", "hellbore")));

    /** A station citadel's main battery: {@code module_citadel1_lowtech} mounts a Gauss Cannon, a Mjolnir and a Heavy Mauler. */
    public static final BatteryLoadout CITADEL = register(
            new BatteryLoadout("citadel", "module_citadel1_lowtech", List.of("gauss", "mjolnir", "heavymauler")));

    private final String id;
    private final String sourceModule;
    private final List<String> weaponIds;

    private BatteryLoadout(String id, String sourceModule, List<String> weaponIds) {
        this.id = id;
        this.sourceModule = sourceModule;
        this.weaponIds = List.copyOf(weaponIds);
    }

    private static BatteryLoadout register(BatteryLoadout loadout) {
        BY_ID.put(loadout.id, loadout);
        return loadout;
    }

    /** The loadout with this id, or {@code null} when none is registered. */
    public static BatteryLoadout byId(String id) {
        return BY_ID.get(id);
    }

    public String id() {
        return id;
    }

    /** The vanilla station-module variant this gun set is taken from, for the reader who wants to check it. */
    public String sourceModule() {
        return sourceModule;
    }

    public List<String> weaponIds() {
        return weaponIds;
    }

    /** The resolved guns, one per mount, in mount order; a weapon the game data does not list is left out. */
    public List<VanillaWeapon> guns() {
        List<VanillaWeapon> out = new ArrayList<>();
        for (String weaponId : weaponIds) {
            VanillaWeapon w = VanillaWeapons.get(weaponId);
            if (w != null) out.add(w);
        }
        return out;
    }
}

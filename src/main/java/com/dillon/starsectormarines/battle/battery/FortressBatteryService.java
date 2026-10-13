package com.dillon.starsectormarines.battle.battery;

import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * State owner for a battle's fortress batteries: which exist, whose they are, and what the battle may therefore do.
 * Advanced by {@link FortressBatterySystem}.
 *
 * <p>It also answers the one question the command powers ask of it. While a battery hostile to the company's side
 * stands intact (held or contested), the fleet is held at standoff by surface-to-orbit fire control and Orbital
 * Barrage is withheld ({@link #orbitalBarrageLock}); capturing the battery lifts it, and destroying the battery lifts it
 * for good. Nothing about this is special to attackers or defenders: a battery holds against whoever is hostile to its
 * holder.
 */
public final class FortressBatteryService {

    /** Why Orbital Barrage cannot be committed while a hostile battery stands. */
    public static final String LOCK_REASON = "Fleet held at standoff by active surface-to-orbit fire control";

    private final List<FortressBattery> batteries = new ArrayList<>();
    private final List<FortressBattery> view = Collections.unmodifiableList(batteries);

    /** Adds a battery for a spawned structure; the holder starts as the structure's own side. */
    public FortressBattery register(long structureId, StructureDef structure, float x, float y, Faction owner) {
        BatteryLoadout loadout = BatteryLoadout.byId(structure.batteryLoadoutId);
        if (loadout == null) {
            throw new IllegalArgumentException("structure '" + structure.id + "' names unknown battery loadout '"
                    + structure.batteryLoadoutId + "'");
        }
        FortressBattery battery = new FortressBattery(structureId, structure, x, y, owner, loadout);
        batteries.add(battery);
        return battery;
    }

    public List<FortressBattery> batteries() {
        return view;
    }

    public boolean isEmpty() {
        return batteries.isEmpty();
    }

    /**
     * The reason Orbital Barrage is withheld from {@code side}, or {@code null} when it is not: some intact battery's
     * holder is hostile to that side.
     */
    public String orbitalBarrageLock(Faction side) {
        for (FortressBattery b : batteries) {
            if (!b.destroyed() && b.owner().hostileTo(side)) return LOCK_REASON;
        }
        return null;
    }
}

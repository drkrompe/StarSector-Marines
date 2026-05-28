package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.setup.BattleSetup;

import java.util.EnumMap;
import java.util.Map;

/**
 * Per-{@link Faction} catalogue of which {@link UnitType} represents the
 * bulk infantry, the stiffening elite, and the heavy mech for that side.
 * Centralises picks that {@code BattleSetup.allocateDefenders},
 * {@code AirSystem.tryDeboardMarine}, and {@code WalkInMeans.dispatch}
 * previously literal-typed.
 *
 * <p>Design rationale + per-faction table live in
 * {@code roadmap/reinforcement/faction-roster.md}. The short version:
 * marines bulk-spawn {@link UnitType#MARINE}, defenders bulk-spawn
 * {@link UnitType#MILITIA}, and the elite slot lets shuttle-drop / future
 * elite-roll paths pick a stiffer type without each call site re-deriving
 * the mapping. Static registry — same data shape across every battle.
 */
public final class FactionUnitRoster {

    private static final Map<Faction, FactionUnitRoster> REGISTRY =
            new EnumMap<>(Faction.class);
    static {
        REGISTRY.put(Faction.MARINE,
                new FactionUnitRoster(UnitType.MARINE, UnitType.MARINE_BLUE, null));
        REGISTRY.put(Faction.DEFENDER,
                new FactionUnitRoster(UnitType.MILITIA, UnitType.MARINE_RED, UnitType.HEAVY_MECH));
        // Civilians don't get reinforced; the entry exists so the lookup
        // never null-returns on infantry. Mech null is intentional —
        // civilian "armoured response" reads as nonsense.
        REGISTRY.put(Faction.CIVILIAN,
                new FactionUnitRoster(UnitType.MILITIA, UnitType.MILITIA, null));
    }

    private final UnitType infantry;
    private final UnitType elite;
    private final UnitType mech;

    private FactionUnitRoster(UnitType infantry, UnitType elite, UnitType mech) {
        this.infantry = infantry;
        this.elite = elite;
        this.mech = mech;
    }

    /** Bulk infantry type for this faction's roster. Never {@code null}. */
    public UnitType infantry() { return infantry; }

    /** Stiffening elite type — one tier above {@link #infantry()}. Never {@code null}. */
    public UnitType elite() { return elite; }

    /**
     * Heavy mech type, or {@code null} when this faction doesn't field
     * mechs. Marines don't today; defenders do.
     */
    public UnitType mech() { return mech; }

    public static FactionUnitRoster forFaction(Faction faction) {
        FactionUnitRoster roster = REGISTRY.get(faction);
        if (roster == null) {
            throw new IllegalArgumentException("no FactionUnitRoster for " + faction);
        }
        return roster;
    }
}

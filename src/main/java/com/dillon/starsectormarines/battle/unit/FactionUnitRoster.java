package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.setup.BattleSetup;

import java.util.EnumMap;
import java.util.Map;

/**
 * Per-battle-side compatibility catalogue of default {@link UnitType}s.
 * Player/story payloads and unprofiled legacy callers still need these stable
 * defaults. Campaign-target defenders instead freeze a
 * {@link com.dillon.starsectormarines.battle.setup.GroundRosterProfile} during
 * setup and every initial/reinforcement source consumes that profile.
 *
 * <p>The ownership contract lives in {@code reinforcement-nouns.md}. The short version:
 * marines bulk-spawn {@link UnitType#MARINE}; a defender without campaign
 * target context falls back to {@link UnitType#MILITIA}. This type does not
 * express campaign faction identity or equipment doctrine.
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

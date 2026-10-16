package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.modifier.UnitModifier;
import com.dillon.starsectormarines.battle.modifier.UnitStat;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.EnumSet;
import java.util.Set;

/**
 * Crippled legs, as a unit modifier: a mech whose legs are at zero has no walking speed, on the
 * autonomous route and under direct control alike. It can still pivot its hips and turn its
 * torso; those are turn rates, not speed. Written only by the damage path, through
 * {@link MechSections}. See {@code mechs-nouns.md}.
 */
public final class MechSectionModifier implements UnitModifier {

    private static final Set<UnitStat> STATS = EnumSet.of(UnitStat.MOVE_SPEED);

    private final UnitRosterService roster;

    public MechSectionModifier(UnitRosterService roster) {
        this.roster = roster;
    }

    @Override public String name(long unit) { return "unitModifierMechCrippled"; }

    @Override public Set<UnitStat> stats() { return STATS; }

    @Override
    public boolean appliesTo(long unit) {
        return crippled(roster.world(), unit);
    }

    @Override
    public float multiplier(long unit, UnitStat stat) {
        return stat == UnitStat.MOVE_SPEED ? 0f : 1f;
    }

    /** Whether {@code unit} is a mech whose legs are at zero. */
    public static boolean crippled(World world, long unit) {
        if (!world.hasMechLoadout(unit)) return false;
        MechLoadoutComponent loadout = world.mechLoadout(unit);
        MechSections sections = loadout.sections();
        return sections != null && sections.crippled();
    }
}

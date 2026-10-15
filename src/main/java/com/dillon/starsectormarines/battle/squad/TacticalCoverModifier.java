package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.command.CommandGroupIdentity;
import com.dillon.starsectormarines.battle.modifier.UnitModifier;
import com.dillon.starsectormarines.battle.modifier.UnitStat;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.marine.OfficerCommandProfile;

import java.util.EnumSet;
import java.util.Set;

/**
 * An officer's Tactical Cover specialty: rounds aimed at the marines of the squads they command
 * hit less often ({@code progression-nouns.md}). A command modifier read off the squad's frozen
 * officer profile, kept apart from the armour's own figure so the player can see which is which.
 */
public final class TacticalCoverModifier implements UnitModifier {

    private static final Set<UnitStat> STATS = EnumSet.of(UnitStat.INCOMING_ACCURACY);

    private final UnitRosterService roster;

    public TacticalCoverModifier(UnitRosterService roster) {
        this.roster = roster;
    }

    @Override public String name(long unit) { return "openingOfficerSpecialtyTacticalCover"; }

    @Override public Set<UnitStat> stats() { return STATS; }

    @Override
    public boolean appliesTo(long unit) {
        OfficerCommandProfile profile = profile(unit);
        return profile != null && profile.cover() > 0;
    }

    @Override
    public float multiplier(long unit, UnitStat stat) {
        OfficerCommandProfile profile = profile(unit);
        return profile != null ? profile.incomingAccuracyMultiplier() : 1f;
    }

    private OfficerCommandProfile profile(long unit) {
        if (!roster.squad().hasSquad(unit)) return null;
        Squad squad = roster.getSquad(roster.squad().squadId(unit));
        CommandGroupIdentity identity = squad != null ? squad.commandGroupIdentity : null;
        return identity != null ? identity.profile() : null;
    }
}

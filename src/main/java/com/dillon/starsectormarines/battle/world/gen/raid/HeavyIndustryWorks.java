package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;

import java.util.Set;

/**
 * Heavy industry's site (owner: accepted, roll it out): machine halls, a generator hall and stores
 * ({@link FortressProgram#works}) in a dense industrial quarter, with its yards beyond.
 */
public final class HeavyIndustryWorks implements RaidIndustry {

    @Override public String key() { return "heavy-industry"; }

    @Override public Set<String> industryIds() { return Set.of("heavyindustry", "orbitalworks"); }

    @Override public FortressProgram works(int machines) { return FortressProgram.works(machines); }
}

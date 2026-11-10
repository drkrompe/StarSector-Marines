package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.drone.FlockLivery;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMap;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;

import java.util.List;
import java.util.Objects;

/**
 * The inputs to *The Long Survey*'s chapter 1, The Fall ({@code long-survey-arc.md}): the Colony Breach rescue on
 * {@code map}'s owned seed with the hull's derelict watch, at {@code grade} in {@code livery}, in place of the swarm.
 */
public record HullFallBattleFixture(long seed, List<ShuttleAssignment> manifest, TargetProfile targetProfile,
                                    StoryMap map, RuinsGrade grade, FlockLivery livery) implements BattleFixture {

    public static final String KIND = "HULL_FALL";

    public HullFallBattleFixture {
        manifest = manifest == null ? List.of() : List.copyOf(manifest);
        targetProfile = Objects.requireNonNull(targetProfile, "targetProfile");
        map = Objects.requireNonNull(map, "map");
        grade = Objects.requireNonNull(grade, "grade");
        livery = Objects.requireNonNull(livery, "livery");
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public BattleSimulation build() {
        return BattleSetup.createHullFall(seed, manifest, targetProfile, map, grade, livery);
    }
}

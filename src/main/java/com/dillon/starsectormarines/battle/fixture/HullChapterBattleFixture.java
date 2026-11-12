package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.drone.FlockLivery;
import com.dillon.starsectormarines.battle.setup.LongSurveyBattle;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMap;

import java.util.List;
import java.util.Objects;

/**
 * The inputs to one of *The Long Survey*'s chapters 2 to 4 ({@code long-survey-arc.md}): {@code map}'s owned ground on
 * {@code seed}, fought on the world {@code targetProfile} states against the hull's watch in {@code livery}
 * ({@link LongSurveyBattle}).
 */
public record HullChapterBattleFixture(long seed, List<ShuttleAssignment> manifest, TargetProfile targetProfile,
                                       StoryMap map, FlockLivery livery) implements BattleFixture {

    public static final String KIND = "HULL_CHAPTER";

    public HullChapterBattleFixture {
        manifest = manifest == null ? List.of() : List.copyOf(manifest);
        targetProfile = Objects.requireNonNull(targetProfile, "targetProfile");
        map = Objects.requireNonNull(map, "map");
        livery = Objects.requireNonNull(livery, "livery");
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public BattleSimulation build() {
        return LongSurveyBattle.create(map, seed, manifest, targetProfile, livery);
    }
}

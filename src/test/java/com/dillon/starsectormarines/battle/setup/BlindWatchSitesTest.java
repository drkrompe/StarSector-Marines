package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.world.gen.BlindWatchSounding.Cell;
import com.dillon.starsectormarines.battle.world.gen.BlindWatchSounding.PostRole;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlindWatchSitesTest {
    private static final Cell SLIT = new Cell(0, 0), HAB = new Cell(1, 0), RIM_A = new Cell(2, 0), RIM_B = new Cell(3, 0),
            BOSS = new Cell(4, 0);

    private static Map<PostRole, List<Cell>> posts() {
        Map<PostRole, List<Cell>> posts = new EnumMap<>(PostRole.class);
        posts.put(PostRole.RIM, List.of(RIM_A, RIM_B));
        posts.put(PostRole.HAB, List.of(SLIT, HAB));
        posts.put(PostRole.WATCH_BOSS, List.of(BOSS));
        return posts;
    }

    @Test void theSlitsAndTheBossAreMannedFirstThenOnePostOfEachDutyInTurn() {
        assertEquals(Map.of(PostRole.HAB, List.of(SLIT)), BlindWatchSites.watch(posts(), Set.of(SLIT), 1));
        assertEquals(Map.of(PostRole.HAB, List.of(SLIT), PostRole.WATCH_BOSS, List.of(BOSS)),
                BlindWatchSites.watch(posts(), Set.of(SLIT), 2));
        assertEquals(Map.of(PostRole.RIM, List.of(RIM_A), PostRole.HAB, List.of(SLIT, HAB),
                PostRole.WATCH_BOSS, List.of(BOSS)), BlindWatchSites.watch(posts(), Set.of(SLIT), 4));
    }

    @Test void aWatchLargerThanThePostMansEveryPostOnce() {
        assertEquals(posts(), BlindWatchSites.watch(posts(), Set.of(SLIT), 40));
    }
}

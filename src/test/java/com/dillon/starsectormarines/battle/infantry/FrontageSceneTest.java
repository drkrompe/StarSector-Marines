package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.DefenseFrontage;
import com.dillon.starsectormarines.battle.infantry.FrontageScene.Approach;
import com.dillon.starsectormarines.battle.infantry.FrontageScene.Sample;
import com.dillon.starsectormarines.battle.infantry.FrontageScene.Scene;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plays the {@link FrontageScene} headless and asserts the behavior a player
 * would watch: a garrison that patrols while nothing is known, mans the wall
 * the assault is behind as belief arrives, and lets go once the fight is
 * inside. Everything here is measured on a production-stamped compound.
 */
class FrontageSceneTest {

    private static final long SEED = 20260828L;
    private static final int TICKS = 3600;
    /** Fine enough to catch the pre-entry window on the shortest approach; a coarse sample simply misses it. */
    private static final int SAMPLE_PERIOD = 20;

    @Test
    void aGeneratedCompoundHasAFrontageOnEveryWall() {
        Scene scene = FrontageScene.build(SEED, 8, 8, Approach.SOUTH);
        List<DefenseFrontage.Aperture> frontage =
                DefenseFrontage.forCompound(scene.primary(), scene.sim());

        assertFalse(frontage.isEmpty(),
                "the production compound filler stamps apertures; the derivation must find them");
        long windows = frontage.stream()
                .filter(a -> a.kind() == DefenseFrontage.Kind.WINDOW).count();
        long entrances = frontage.size() - windows;
        assertTrue(windows > 0, "perimeter firing windows should be frontage, got " + frontage.size());
        assertTrue(entrances > 0, "the filler punches gates; they should be frontage too");

        long facings = frontage.stream().map(DefenseFrontage.Aperture::facing).distinct().count();
        assertTrue(facings >= 2,
                "a compound in open ground is approachable from more than one side, got " + facings);
        System.out.printf("frontage: %d apertures (%d windows, %d entrances) across %d facings%n",
                frontage.size(), windows, entrances, facings);
    }

    @Test
    void theGarrisonStandsToAsTheAssaultClosesAndReleasesWhenItIsInside() {
        Scene scene = FrontageScene.build(SEED, 8, 8, Approach.SOUTH);
        List<Sample> samples = FrontageScene.play(scene, TICKS, SAMPLE_PERIOD);
        print(samples, "SOUTH");

        Sample first = samples.get(0);
        assertFalse("FrontageDefense".equals(first.goal()),
                "nothing is believed at tick 0, so the garrison should still be patrolling");

        List<Sample> standingTo = samples.stream()
                .filter(s -> "FrontageDefense".equals(s.goal())).toList();
        assertFalse(standingTo.isEmpty(),
                "an assault walking onto the compound must eventually man the wall");

        Sample stood = standingTo.get(0);
        assertTrue(stood.believedPressure() > 0f,
                "standing to must be driven by belief, not by the marines merely existing");
        assertFalse(stood.enemyInside(),
                "the whole point is that it happens before the compound is entered");

        assertTrue(standingTo.stream().anyMatch(s -> s.membersOnPost() > 0),
                "members must actually reach their posts, not just be assigned them");

        boolean releasedAfterEntry = samples.stream()
                .filter(Sample::enemyInside)
                .noneMatch(s -> "FrontageDefense".equals(s.goal()));
        assertTrue(releasedAfterEntry,
                "once marines are inside the compound the fight belongs to the room behaviors");
    }

    @Test
    void thePostsGoToTheWallTheThreatIsBehind() {
        for (Approach approach : List.of(Approach.SOUTH, Approach.NORTH,
                Approach.EAST, Approach.WEST)) {
            Scene scene = FrontageScene.build(SEED, 8, 8, approach);
            List<Sample> standing = FrontageScene.play(scene, TICKS, SAMPLE_PERIOD).stream()
                    .filter(s -> "FrontageDefense".equals(s.goal()))
                    .filter(s -> !s.enemyInside())
                    .toList();
            assertFalse(standing.isEmpty(), approach + ": garrison never stood to outside a breach");

            int facing = standing.stream().mapToInt(Sample::postsFacingThreat).sum();
            int total = standing.stream().mapToInt(Sample::aperturePosts).sum();
            System.out.printf("%-5s approach: %d/%d aperture posts across %d stand-to samples "
                            + "cover the side the assault is on%n",
                    approach, facing, total, standing.size());

            // Same compound and same seed every time; only where the assault is
            // differs, so this isolates what the influence field contributes.
            assertTrue(facing > 0, approach + ": no post ever covered the threatened side");
            assertTrue(facing * 2 >= total, approach
                    + ": most aperture posts should cover the threatened side, got "
                    + facing + "/" + total);
        }
    }

    @Test
    void aReserveIsHeldBackOffTheWall() {
        Scene scene = FrontageScene.build(SEED, 8, 8, Approach.SOUTH);
        Sample stood = firstStandTo(FrontageScene.play(scene, TICKS, SAMPLE_PERIOD));
        assertTrue(stood.reservePosts() >= 1,
                "an eight-marine garrison should not put every body on one wall");
        assertTrue(stood.aperturePosts() >= 1, "and it should still man the wall");
    }

    private static Sample firstStandTo(List<Sample> samples) {
        return samples.stream()
                .filter(s -> "FrontageDefense".equals(s.goal()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("garrison never stood to"));
    }

    private static void print(List<Sample> samples, String label) {
        System.out.println("=== frontage scene: " + label + " approach ===");
        System.out.println("tick  goal              ap/res  facing  on-post  pressure  marines@      inside");
        String previous = null;
        for (Sample s : samples) {
            boolean interesting = !s.goal().equals(previous) || "FrontageDefense".equals(s.goal());
            previous = s.goal();
            if (!interesting) continue;
            System.out.printf("%4d  %-17s %2d/%-3d %5d %7d  %8.3f  %5.1f,%-5.1f x%-2d  %s%n",
                    s.tick(), s.goal(), s.aperturePosts(), s.reservePosts(),
                    s.postsFacingThreat(), s.membersOnPost(), s.believedPressure(),
                    s.marineX(), s.marineY(), s.liveMarines(),
                    s.enemyInside() ? "INSIDE" : "");
        }
    }
}

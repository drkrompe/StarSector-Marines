package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.DefenseFrontage;
import com.dillon.starsectormarines.battle.infantry.FrontageScene.Approach;
import com.dillon.starsectormarines.battle.infantry.FrontageScene.Sample;
import com.dillon.starsectormarines.battle.infantry.FrontageScene.Scene;
import org.junit.jupiter.api.Test;

import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    /** One garrison per emitted node, so the compound is defended in layers rather than by one squad. */
    private static final int GARRISON_SQUADS = 4;
    private static final int GARRISON_SIZE = 8;
    /** Three assault squads spread along the approach edge. */
    private static final int ASSAULT_SQUADS = 3;
    private static final int ASSAULT_SIZE = 8;
    private static final int TICKS = 3600;
    /** Fine enough to catch the pre-entry window on the shortest approach; a coarse sample simply misses it. */
    private static final int SAMPLE_PERIOD = 20;

    @Test
    void aGeneratedCompoundHasAFrontageOnEveryWall() {
        Scene scene = FrontageScene.build(SEED, GARRISON_SQUADS, GARRISON_SIZE, ASSAULT_SQUADS, ASSAULT_SIZE, Approach.SOUTH);
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
        Scene scene = FrontageScene.build(SEED, GARRISON_SQUADS, GARRISON_SIZE, ASSAULT_SQUADS, ASSAULT_SIZE, Approach.SOUTH);
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

        List<Sample> breached = samples.stream().filter(Sample::enemyInside).toList();
        assertFalse(breached.isEmpty(), "the assault must enter a held zone during the scene");
        assertTrue(breached.stream().noneMatch(Sample::frontageRelevant),
                "once marines are inside, the frontage goal must yield to room behaviors; "
                        + "currentGoal may trail until the periodic replan");
    }

    @Test
    void thePostsGoToTheWallTheThreatIsBehind() {
        for (Approach approach : Approach.values()) {
            Scene scene = FrontageScene.build(SEED, GARRISON_SQUADS, GARRISON_SIZE, ASSAULT_SQUADS, ASSAULT_SIZE, approach);
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
        Scene scene = FrontageScene.build(SEED, GARRISON_SQUADS, GARRISON_SIZE, ASSAULT_SQUADS, ASSAULT_SIZE, Approach.SOUTH);
        Sample stood = firstStandTo(FrontageScene.play(scene, TICKS, SAMPLE_PERIOD));
        assertTrue(stood.reservePosts() >= 1,
                "an eight-marine garrison should not put every body on one wall");
        assertTrue(stood.aperturePosts() >= 1, "and it should still man the wall");
    }

    @Test
    void squadsDoNotSendMembersToOneAnothersPosts() {
        Scene scene = FrontageScene.build(SEED, GARRISON_SQUADS, GARRISON_SIZE,
                ASSAULT_SQUADS, ASSAULT_SIZE, Approach.SOUTH);
        List<Sample> samples = FrontageScene.play(scene, TICKS, SAMPLE_PERIOD);

        int worst = samples.stream().mapToInt(s -> s.crowding().postCollisions()).max().orElse(0);
        assertEquals(0, worst,
                "two squads assigned to one stance cell leaves one of them unable to reach its post");
    }

    @Test
    void theGarrisonsHoldDifferentLayers() {
        Scene scene = FrontageScene.build(SEED, GARRISON_SQUADS, GARRISON_SIZE,
                ASSAULT_SQUADS, ASSAULT_SIZE, Approach.SOUTH);
        List<Sample> samples = FrontageScene.play(scene, TICKS, SAMPLE_PERIOD);

        // Exactly one squad should be holding the compound's own perimeter; the
        // rest hold the shells of the buildings they garrison. If every squad
        // took compound scope they would all derive the same wall and compete
        // for the same apertures.
        Map<Integer, String> scopeBySquad = new LinkedHashMap<>();
        for (Sample sample : samples) {
            for (FrontageScene.SquadSample row : sample.garrisons()) {
                if (row.aperturePosts() > 0) scopeBySquad.put(row.squadId(), row.scope());
            }
        }
        long compoundScoped = scopeBySquad.values().stream().filter("COMPOUND"::equals).count();
        System.out.printf("garrison scopes: %s%n", scopeBySquad);
        assertEquals(1, compoundScoped,
                "exactly one garrison holds the perimeter, got " + scopeBySquad);
        assertTrue(scopeBySquad.size() > 1,
                "more than one garrison should have manned something, got " + scopeBySquad);
    }

    @Test
    void theGarrisonsDoNotCollapseOntoOneAnother() {
        Scene scene = FrontageScene.build(SEED, GARRISON_SQUADS, GARRISON_SIZE,
                ASSAULT_SQUADS, ASSAULT_SIZE, Approach.SOUTH);
        List<Sample> samples = FrontageScene.play(scene, TICKS, SAMPLE_PERIOD);

        // Asked of squads, not bodies. Infantry compresses to
        // SeparationSystem.INFANTRY_FORMATION_MIN_DISTANCE (0.75 cells) in a
        // tight passage by design, so several marines sharing one cell at a
        // doorway is the system working; four squads holding four layers of one
        // compound standing on the same ground would not be.
        List<Sample> standingTo = samples.stream()
                .filter(Sample::frontageRelevant)
                .filter(s -> s.crowding().minGarrisonGap() >= 0f)
                .toList();
        assertFalse(standingTo.isEmpty(), "no sample had two garrisons alive and standing to");

        double closest = standingTo.stream()
                .mapToDouble(s -> s.crowding().minGarrisonGap()).min().orElse(0d);
        double worstPacking = samples.stream()
                .mapToDouble(s -> s.crowding().packing()).max().orElse(0d);
        int worstStack = samples.stream()
                .mapToInt(s -> s.crowding().maxUnitsInCell()).max().orElse(0);
        System.out.printf(
                "crowding: garrison centroids never closer than %.1f cells; "
                        + "peak %.2f units per occupied cell, at most %d in one cell%n",
                closest, worstPacking, worstStack);

        // No distance threshold here on purpose. Two garrisons in adjacent
        // buildings both covering the same approach legitimately end up a few
        // cells apart, so any figure picked for "far enough" would be invented
        // rather than derived, and would fail the first time the compound's
        // geometry changed. What can be stated exactly is that they hold
        // separate ground at all, and that the force has not piled up: two
        // bodies per occupied cell across the whole battle would mean every
        // cell in use is doubly stacked.
        assertTrue(closest > 0d, "garrison squads must not share one centroid");
        assertTrue(worstPacking < 2d,
                "a force averaging two or more bodies per occupied cell has piled up, got "
                        + String.format("%.2f", worstPacking));
    }

    @Test
    void aRunPublishesItsMeasurementsAsJson() throws Exception {
        Scene scene = FrontageScene.build(SEED, GARRISON_SQUADS, GARRISON_SIZE,
                ASSAULT_SQUADS, ASSAULT_SIZE, Approach.SOUTH);
        List<Sample> samples = FrontageScene.play(scene, TICKS, SAMPLE_PERIOD);

        Path report = FrontageSceneReport.write("assault-from-top", scene, samples);
        assertTrue(Files.exists(report), "the scene must leave a machine-readable record");
        JSONObject root = new JSONObject(Files.readString(report));
        assertEquals(GARRISON_SQUADS, root.getJSONObject("force").getInt("garrisonSquads"));
        assertEquals(ASSAULT_SQUADS, root.getJSONObject("force").getInt("assaultSquads"));
        assertTrue(root.getJSONObject("frontage").getInt("apertures") > 0);
        assertEquals(samples.size(), root.getJSONArray("timeline").length());
        assertFalse(root.getJSONObject("events").getBoolean("standToWhileBreached"));
        System.out.println("scene report: " + report);
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

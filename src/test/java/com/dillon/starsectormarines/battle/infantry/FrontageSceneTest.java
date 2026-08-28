package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.DefenseFrontage;
import com.dillon.starsectormarines.battle.decision.goap.Planner;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.infantry.FrontageScene.Approach;
import com.dillon.starsectormarines.battle.infantry.FrontageScene.Sample;
import com.dillon.starsectormarines.battle.infantry.FrontageScene.Scene;
import org.junit.jupiter.api.Test;

import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
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

        assertTrue(samples.stream().anyMatch(Sample::enemyInside),
                "this scene is only evidence of the hand-off if the compound is entered");
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
    void thePerimeterGarrisonFallsBackInsteadOfStandingDown() {
        Scene scene = FrontageScene.build(SEED, GARRISON_SQUADS, GARRISON_SIZE,
                ASSAULT_SQUADS, ASSAULT_SIZE, Approach.SOUTH);
        List<Sample> samples = FrontageScene.play(scene, TICKS, SAMPLE_PERIOD);

        List<Sample> breached = samples.stream().filter(Sample::enemyInside).toList();
        assertFalse(breached.isEmpty(), "the assault must enter the compound during the scene");

        assertTrue(breached.stream().noneMatch(s -> "COMPOUND".equals(s.perimeterLayer())),
                "nobody should still be manning the outer wall once the compound is entered");
        long fellBack = breached.stream()
                .filter(s -> "STRUCTURE".equals(s.perimeterLayer())).count();
        System.out.printf("perimeter garrison: %d of %d breached samples held an inner layer%n",
                fellBack, breached.size());
        assertTrue(fellBack > 0,
                "losing the wall should move the garrison to the next envelope in, "
                        + "not end its defense");
    }

    @Test
    void theGarrisonsHoldDifferentLayers() {
        Scene scene = FrontageScene.build(SEED, GARRISON_SQUADS, GARRISON_SIZE,
                ASSAULT_SQUADS, ASSAULT_SIZE, Approach.SOUTH);
        List<Sample> samples = FrontageScene.play(scene, TICKS, SAMPLE_PERIOD);

        // A bounded few squads hold the compound's perimeter and the rest hold
        // the shells of the buildings they garrison. If every squad took the
        // compound they would all derive the same wall, and an assault through
        // it would find the buildings empty.
        Map<Integer, Set<String>> scopeBySquad = new LinkedHashMap<>();
        for (Sample sample : samples) {
            for (FrontageScene.SquadSample row : sample.garrisons()) {
                if ("none".equals(row.layer())) continue;
                scopeBySquad.computeIfAbsent(row.squadId(), id -> new LinkedHashSet<>())
                        .add(row.layer());
            }
        }
        long onPerimeter = scopeBySquad.values().stream()
                .filter(layers -> layers.contains("COMPOUND")).count();
        System.out.printf("garrison layers: %s%n", scopeBySquad);
        assertTrue(onPerimeter >= 1 && onPerimeter <= FrontageDefense.MAX_PERIMETER_GARRISONS,
                "between one and " + FrontageDefense.MAX_PERIMETER_GARRISONS
                        + " garrisons man the wall, got " + scopeBySquad);
        assertTrue(scopeBySquad.size() > onPerimeter,
                "somebody must still be holding the buildings, got " + scopeBySquad);
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
        assertFalse(root.getJSONObject("events").getBoolean("heldOuterWallWhileBreached"));
        assertTrue(root.getJSONObject("events").getBoolean("fellBackWhileBreached"));
        System.out.println("scene report: " + report);
    }

    @Test
    void aSimultaneousAssaultOnSeveralSidesIsCoveredOnEverySide() {
        for (List<Approach> axes : List.of(
                List.of(Approach.SOUTH),
                List.of(Approach.SOUTH, Approach.NORTH),
                List.of(Approach.SOUTH, Approach.EAST),
                List.of(Approach.SOUTH, Approach.NORTH, Approach.EAST, Approach.WEST))) {
            Scene scene = FrontageScene.build(SEED, GARRISON_SQUADS, GARRISON_SIZE,
                    1, ASSAULT_SIZE, axes);
            List<Sample> samples = FrontageScene.play(scene, TICKS, SAMPLE_PERIOD);

            // Scoped to samples where the perimeter is still the line: every
            // axis has attackers, apertures are manned, and the wall has not
            // yet been given up. An axis whose assault is dead needs no cover;
            // a garrison between plans is not aiming at all, which is a
            // different question; and once the compound has fallen and the
            // defense is inside the buildings, "is the north wall manned" has
            // stopped being the question worth asking.
            List<Sample> contested = samples.stream()
                    .filter(s -> s.mannedApertures() > 0)
                    .filter(s -> "COMPOUND".equals(s.perimeterLayer()))
                    .filter(s -> s.axes().stream().allMatch(a -> a.liveMarines() > 0))
                    .toList();
            assertFalse(contested.isEmpty(), axes + ": no sample had every axis live and manned");

            // Judged on believed threat, not on live truth. Standing to is
            // driven by the defender's own reports, so an approach it has not
            // observed is one it has no reason to picket — demanding cover
            // there would be demanding the garrison read the map instead of
            // its own contacts. The believed sides are the promise.
            // Posts are chosen at replan time and belief moves continuously, so
            // a side that becomes believed-threatened is uncovered until its
            // squad next replans. Demanding coverage at every instant would be
            // demanding that GOAP replan every tick. What the design owes is
            // that the gap closes: measure the longest unbroken stretch, not
            // whether any single sample was blind.
            long blind = contested.stream()
                    .filter(s -> s.coverageOfWeakestBelievedAxis() == 0).count();
            int longestBlindRun = 0;
            int run = 0;
            for (Sample sample : contested) {
                run = sample.coverageOfWeakestBelievedAxis() == 0 ? run + 1 : 0;
                longestBlindRun = Math.max(longestBlindRun, run);
            }
            float blindSeconds = longestBlindRun * SAMPLE_PERIOD * BattleSimulation.TICK_DT;
            System.out.printf("%d-axis %s: %d of %d contested samples left a believed side "
                            + "uncovered; longest unbroken gap %.1fs (replan period %.1fs)%n",
                    axes.size(), axes.stream().map(Approach::renderedEdge).toList(),
                    blind, contested.size(), blindSeconds, Planner.REPLAN_PERIOD);

            // Two replan periods: belief has to change, and then the squad has
            // to reach its next replan, and the sample grid can straddle both
            // ends of that.
            assertTrue(blindSeconds <= 2 * Planner.REPLAN_PERIOD, axes
                    + ": a believed-threatened side stayed uncovered for " + blindSeconds
                    + "s, longer than the garrison could take to notice and repost");
        }
    }

    @Test
    void everyCompoundShapeYieldsAUsableFrontage() {
        for (FrontageScene.Shape shape : FrontageScene.Shape.values()) {
            Scene scene = FrontageScene.build(SEED, GARRISON_SQUADS, GARRISON_SIZE,
                    1, ASSAULT_SIZE, List.of(Approach.SOUTH), shape);
            List<DefenseFrontage.Aperture> frontage =
                    DefenseFrontage.forCompound(scene.primary(), scene.sim());

            long windows = frontage.stream()
                    .filter(a -> a.kind() == DefenseFrontage.Kind.WINDOW).count();
            long facings = frontage.stream()
                    .map(DefenseFrontage.Aperture::facing).distinct().count();
            System.out.printf("%-8s: %2d nodes, %2d apertures (%d windows, %d entrances) "
                            + "across %d facings%n",
                    shape, scene.nodes().size(), frontage.size(), windows,
                    frontage.size() - windows, facings);

            assertFalse(frontage.isEmpty(), shape + ": no frontage derived");
            assertTrue(facings >= 2, shape + ": frontage on fewer than two facings");

            // The gate that a bounding-box footprint could plausibly fail on an
            // irregular shape: open ground outside the compound must never be
            // counted as interior. A concave notch is street, and a stance cell
            // standing in it would be a garrison posted outside its own wall.
            int streetZone = scene.sim().getZoneGraph().zoneIdAt(1, 1);
            List<Integer> held = FrontageDefense.heldZones(scene.perimeterGarrison(),
                    scene.sim());
            assertFalse(held.contains(streetZone),
                    shape + ": the open map was counted as compound interior");
            for (DefenseFrontage.Aperture aperture : frontage) {
                assertTrue(held.contains(scene.sim().getZoneGraph()
                                .zoneIdAt(aperture.stanceX(), aperture.stanceY())),
                        shape + ": stance cell outside the held interior at "
                                + aperture.stanceX() + "," + aperture.stanceY());
                assertFalse(held.contains(scene.sim().getZoneGraph()
                                .zoneIdAt(aperture.outsideX(), aperture.outsideY())),
                        shape + ": aperture opens onto held ground at "
                                + aperture.x() + "," + aperture.y());
            }
        }
    }

    @Test
    void everyCompoundShapeStandsToAndCoversTheApproach() {
        for (FrontageScene.Shape shape : FrontageScene.Shape.values()) {
            Scene scene = FrontageScene.build(SEED, GARRISON_SQUADS, GARRISON_SIZE,
                    1, ASSAULT_SIZE, List.of(Approach.SOUTH), shape);
            List<Sample> samples = FrontageScene.play(scene, TICKS, SAMPLE_PERIOD);

            List<Sample> standing = samples.stream()
                    .filter(s -> s.mannedApertures() > 0)
                    .filter(s -> "COMPOUND".equals(s.perimeterLayer()))
                    .filter(s -> s.axes().stream().allMatch(a -> a.liveMarines() > 0))
                    .toList();
            assertFalse(standing.isEmpty(), shape + ": the garrison never manned the wall");
            int facing = standing.stream().mapToInt(Sample::postsFacingThreat).sum();
            int total = standing.stream().mapToInt(Sample::aperturePosts).sum();
            double share = total == 0 ? 0d : (double) facing / total;
            System.out.printf("%-8s: %d/%d posts (%.0f%%) cover the approach across %d samples%n",
                    shape, facing, total, share * 100, standing.size());

            // A share rather than a majority. Allocation pickets every
            // threatened facing before massing, so on a footprint with few
            // apertures the pickets are most of the posts and no majority is
            // available to concentrate — a shape with a dozen apertures across
            // four facings cannot put half its handful of posts on one side and
            // still cover the others. What must hold on every shape is that the
            // approached side is the best-covered one.
            int approached = standing.stream()
                    .mapToInt(s -> s.axes().get(0).postsCovering()).sum();
            int best = 0;
            for (int axis = 0; axis < standing.get(0).axes().size(); axis++) {
                int index = axis;
                best = Math.max(best, standing.stream()
                        .mapToInt(s -> s.axes().get(index).postsCovering()).sum());
            }
            assertTrue(facing > 0, shape + ": no post ever covered the approach");
            assertEquals(approached, best,
                    shape + ": the approached side should be the best-covered one");
        }
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

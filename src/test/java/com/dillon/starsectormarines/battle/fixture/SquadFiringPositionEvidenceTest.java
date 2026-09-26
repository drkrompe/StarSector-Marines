package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in scorer integration evidence. Real infantry weapon profiles and spatial
 * indices, no generated map and no autonomous battle ticks. Candidate legality,
 * spread and reachability are measured; arrival, shots, damage and battle balance
 * are deliberately not claimed by this instrument.
 */
@Tag("squad-firing-evidence")
class SquadFiringPositionEvidenceTest {
    private static final String PROPERTY = "battle.targeting.squadFiringPositions";
    private static final int WIDTH = 40, HEIGHT = 28;
    private static final int ANCHOR_X = 14, ANCHOR_Y = 14;
    private static final float LEASH = 10f;
    private static final String[] WEAPONS = {
            WeaponRegistry.SMG_ID, WeaponRegistry.SMG_ID,
            WeaponRegistry.STARTER_PRIMARY_ID, WeaponRegistry.STARTER_PRIMARY_ID,
            WeaponRegistry.PULSE_RIFLE_ID, WeaponRegistry.PULSE_RIFLE_ID};

    @Test
    void comparesBoundedPositionsAcrossTargetMotionAndTopologyChanges() throws Exception {
        String previous = System.getProperty(PROPERTY);
        TickInnerProfile priorProfile = TickInnerProfile.currentIfBound();
        try {
            JSONObject report = new JSONObject().put("schemaVersion", 1)
                    .put("evidenceKind", "scorer-only; no autonomous movement or shots")
                    .put("timingSemantics", "selection wall time only; descriptive, includes JIT; no cross-host timing gate")
                    .put("topologyStageSemantics", "each arm blocks its own first selected cell; repair invariants, not identical-obstacle timing controls")
                    .put("arenaWidth", WIDTH).put("arenaHeight", HEIGHT)
                    .put("anchorX", ANCHOR_X).put("anchorY", ANCHOR_Y).put("leash", LEASH)
                    .put("control", run(false)).put("experiment", run(true));
            Path output = Path.of(System.getProperty("battle.squadFiringEvidence.outputDir",
                    "build/reports/performance/squad-firing-positions"));
            Files.createDirectories(output);
            Path summary = output.resolve("summary.json");
            Files.writeString(summary, report.toString(2), StandardCharsets.UTF_8);
            System.out.println("Squad firing-position scoring evidence: " + summary.toAbsolutePath());
        } finally {
            if (previous == null) System.clearProperty(PROPERTY);
            else System.setProperty(PROPERTY, previous);
            TickInnerProfile.setCurrent(priorProfile);
        }
    }

    private static JSONObject run(boolean shared) throws Exception {
        System.setProperty(PROPERTY, Boolean.toString(shared));
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) grid.setWalkableFloor(x, y);
        }
        try (BattleSimulation sim = new BattleSimulation(grid, new CellTopology(WIDTH, HEIGHT))) {
            int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
            long[] members = new long[WEAPONS.length];
            for (int i = 0; i < members.length; i++) {
                members[i] = sim.spawn(new EntitySpec("rifle-" + i, Faction.MARINE,
                        UnitType.MARINE, 5 + i % 2, 11 + i / 2 * 2)
                        .squad(squadId).primaryWeapon(WeaponRegistry.installed().require(WEAPONS[i])));
            }
            long target = sim.spawn(new EntitySpec("target", Faction.DEFENDER, UnitType.MARINE, 26, 14));
            Squad squad = sim.getSquad(squadId);
            squad.aliveMembers = members.length;
            squad.originalSize = members.length;
            squad.centroidX = 6f;
            squad.centroidY = 13.5f;
            refreshSpatial(sim, members, target);
            JSONArray stages = new JSONArray();
            TickInnerProfile profile = new TickInnerProfile();
            TickInnerProfile.setCurrent(profile);

            stages.put(stage("initial", 1, sim, squad, members, target, profile, true));
            stages.put(stage("reuse", 5, sim, squad, members, target, profile, true));
            sim.world().setPos(target, 26.8f, 14.8f);
            refreshSpatial(sim, members, target);
            stages.put(stage("subcell-target-motion", 9, sim, squad, members, target, profile, true));
            sim.world().setCellPos(target, 28, 16);
            refreshSpatial(sim, members, target);
            JSONObject moved = stage("target-cell-motion", 20, sim, squad, members, target, profile, true);
            stages.put(moved);
            JSONObject chosen = moved.getJSONArray("members").getJSONObject(0);
            int blockedX = chosen.getInt("x"), blockedY = chosen.getInt("y");
            grid.setWalkable(blockedX, blockedY, false);
            JSONObject repaired = stage("blocked-selected-cell", 24, sim, squad, members,
                    target, profile, true).put("blockedX", blockedX).put("blockedY", blockedY);
            stages.put(repaired);
            for (int i = 0; i < members.length; i++) {
                JSONObject selection = repaired.getJSONArray("members").getJSONObject(i);
                assertFalse(selection.getInt("x") == blockedX && selection.getInt("y") == blockedY,
                        "topology change must retire a blocked candidate");
            }
            // The picker and movement own different questions. A full barrier
            // leaves visible firing positions on its far side; report route
            // reachability separately instead of calling every pick actionable.
            for (int y = 0; y < HEIGHT; y++) grid.setWalkable(10, y, false);
            stages.put(stage("sealed-route-barrier", 28, sim, squad, members, target, profile, false));
            JSONObject initialCounters = stages.getJSONObject(0).getJSONObject("poolCounters");
            if (shared) {
                assertTrue(initialCounters.getInt("FIRING_POOL_BUILD") > 0,
                        "subject must exercise the experimental generator");
                assertTrue(initialCounters.getInt("FIRING_POOL_HIT") > 0,
                        "repeated requests must actually reuse shared positions");
                assertEquals(0, stages.getJSONObject(1).getJSONObject("poolCounters")
                        .getInt("FIRING_POOL_BUILD"), "reuse stage must not rebuild before expiry");
            } else {
                assertEquals(0, initialCounters.getInt("FIRING_POOL_BUILD"),
                        "control must retain individual scoring");
            }
            return new JSONObject().put("shared", shared).put("stages", stages);
        }
    }

    private static JSONObject stage(String name, int firstTick, BattleSimulation sim,
            Squad squad, long[] members, long target, TickInnerProfile profile,
            boolean requireAll) throws Exception {
        profile.reset();
        int[][] chosen = new int[members.length][];
        long elapsed = 0L;
        int nullObservations = 0;
        // Range-compatible pools may build on different ticks. Four rounds
        // expose bounded deferral while allowing all three weapon groups a turn.
        for (int round = 0; round < 4; round++) {
            for (int i = 0; i < members.length; i++) {
                long started = System.nanoTime();
                chosen[i] = sim.getTacticalScoring().findSquadFiringPositionWithin(
                        members[i], target, squad, firstTick + round,
                        ANCHOR_X, ANCHOR_Y, LEASH, TacticalScoring.ANY_ZONE);
                elapsed += System.nanoTime() - started;
                if (chosen[i] == null) nullObservations++;
            }
        }
        JSONObject counters = new JSONObject();
        for (TickInnerProfile.Bucket bucket : TickInnerProfile.Bucket.VALUES) {
            if (bucket.name().startsWith("FIRING_POOL_")
                    || bucket.name().startsWith("FIRING_INDIVIDUAL_")) {
                counters.put(bucket.name(), profile.countOf(bucket));
            }
        }
        JSONArray selections = new JSONArray();
        Set<Integer> unique = new HashSet<>();
        int selected = 0, reachable = 0;
        double minSeparation = Double.POSITIVE_INFINITY;
        for (int i = 0; i < members.length; i++) {
            long member = members[i];
            JSONObject entry = new JSONObject().put("member", i).put("weapon", WEAPONS[i])
                    .put("range", sim.world().attackRange(member));
            int[] cell = chosen[i];
            entry.put("selected", cell != null);
            if (cell != null) {
                selected++;
                int x = cell[0], y = cell[1];
                boolean legal = sim.getGrid().isWalkable(x, y)
                        && Math.hypot(x - ANCHOR_X, y - ANCHOR_Y) <= LEASH
                        && Math.hypot(x - sim.world().cellX(target), y - sim.world().cellY(target))
                        <= sim.world().attackRange(member)
                        && TacticalScoring.canShootPair(sim.getGrid(), x + .5f, y + .5f,
                        sim.world().x(target), sim.world().y(target), 0f, 0f);
                assertTrue(legal, name + " returned illegal firing cell for member " + i);
                int[] path = GridPathfinder.findPath(sim.getGrid(), sim.world().cellX(member),
                        sim.world().cellY(member), x, y);
                if (!Paths.isEmpty(path)) reachable++;
                entry.put("x", x).put("y", y).put("valid", legal)
                        .put("reachable", !Paths.isEmpty(path)).put("pathCells", Paths.cellCount(path));
                unique.add(y * WIDTH + x);
                for (int j = 0; j < i; j++) {
                    if (chosen[j] != null) minSeparation = Math.min(minSeparation,
                            Math.hypot(x - chosen[j][0], y - chosen[j][1]));
                }
            }
            selections.put(entry);
        }
        if (requireAll) {
            assertEquals(members.length, selected, name + " should offer every weapon group a legal position");
            assertEquals(members.length, reachable, name + " should keep selected cells reachable");
        }
        if (Boolean.getBoolean(PROPERTY)) {
            assertEquals(selected, unique.size(), "live shared assignments must be exclusive");
        }
        return new JSONObject().put("name", name).put("firstTick", firstTick)
                .put("calls", members.length * 4).put("selectionWallMs", elapsed / 1_000_000.0)
                .put("nullObservations", nullObservations).put("selectedValidPositions", selected)
                .put("reachablePositions", reachable).put("engageableFromSelectedCells", selected)
                .put("uniqueCells", unique.size())
                .put("minimumSelectedSeparation", Double.isFinite(minSeparation) ? minSeparation : 0.0)
                .put("poolCounters", counters).put("members", selections);
    }

    private static void refreshSpatial(BattleSimulation sim, long[] members, long target) {
        byte[] occupancy = sim.getOccupancyMap();
        Arrays.fill(occupancy, (byte) 0);
        for (long member : members) occupancy[sim.world().cellY(member) * WIDTH + sim.world().cellX(member)]++;
        occupancy[sim.world().cellY(target) * WIDTH + sim.world().cellX(target)]++;
        sim.getUnitIndex().rebuild(sim.getRoster());
        sim.getDestIndex().rebuild(sim.getRoster());
    }
}

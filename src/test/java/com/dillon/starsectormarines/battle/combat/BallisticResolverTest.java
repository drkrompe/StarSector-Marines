package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link BallisticResolver#resolve} against the "Resolution
 * algorithm" section of {@code roadmap/ballistics/stories/s1-resolver-core.md}.
 * Every scenario drives a {@link QueueRandom} stub so the exact roll each
 * event consumes is pinned — the geometry is chosen so a wrong walk order or
 * a mis-wired cover source changes the observable outcome, not just the
 * roll count.
 */
class BallisticResolverTest {

    private static final float EPS = 1e-3f;
    private static final int W = 24;
    private static final int H = 12;
    private static final int ROW = 5;
    /** Round speed used by every test — chosen so flightTime = distance / 10 reads cleanly. */
    private static final float VEL = 10f;

    private static BattleSimulation openArena() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static long spawn(BattleSimulation sim, Faction faction, int cellX) {
        return sim.spawn(new EntitySpec("u" + sim.liveUnitCount(), faction, UnitType.MARINE, cellX, ROW));
    }

    private static float cellCenter(int cellX) {
        return cellX + 0.5f;
    }

    private static float rowCenter() {
        return ROW + 0.5f;
    }

    /** Stub {@link Random} that hands back a pre-programmed sequence of {@code nextFloat()} results, in call order. */
    private static final class QueueRandom extends Random {
        private final ArrayDeque<Float> queue = new ArrayDeque<>();

        QueueRandom(float... values) {
            for (float v : values) queue.add(v);
        }

        @Override
        public float nextFloat() {
            if (queue.isEmpty()) throw new IllegalStateException("QueueRandom exhausted");
            return queue.poll();
        }
    }

    // ---- event ordering: nearer doodad rolls before farther unit ----

    @Test
    void nearerDoodadStopsTheRoundBeforeTheFartherUnitIsEverConsidered() {
        BattleSimulation sim = openArena();
        DoodadService doodads = new DoodadService(sim.getGrid());
        doodads.addDoodad(new Doodad(5, ROW, new TileManifest.TileFrame(0, 0), false, Doodad.COVER_MED));
        long shooter = spawn(sim, Faction.MARINE, 2);
        long target = spawn(sim, Faction.DEFENDER, 10);
        BallisticResolver resolver = new BallisticResolver(sim.getGrid(), doodads, sim.getUnitIndex(), sim.getRoster());

        // angle, offsetR (spread=0, both irrelevant), doodad crossing roll
        // (blocks: 0f < the level-2 block chance 0.30). The crossing check
        // keys on the doodad's own-cell level only (getDoodadLevelOnCell),
        // so it fires exactly once, at cell 5 — see the DoodadService class
        // doc for why the facing-bled neighbor cover is a separate concern.
        QueueRandom rng = new QueueRandom(0f, 0f, 0f);
        BallisticResolver.Resolution res = resolver.resolve(shooter, target, 1f, 0f, VEL, rng);

        assertEquals(BallisticResolver.StopKind.DOODAD_BLOCK, res.kind());
        assertEquals(0L, res.victimId(), "a doodad stop never records a victim");
        assertEquals(cellCenter(5), res.endX(), EPS);
        assertEquals(rowCenter(), res.endY(), EPS);
        assertEquals((cellCenter(5) - cellCenter(2)) / VEL, res.flightTime(), EPS,
                "the round never travels past the nearer doodad, so flightTime tracks the doodad's distance, not the target's");
    }

    // ---- wall hard-stop caps the ray ----

    @Test
    void wallHardCapsTheRayRegardlessOfWhatIsBeyondIt() {
        BattleSimulation sim = openArena();
        NavigationGrid grid = sim.getGrid();
        grid.setWalkable(6, ROW, false);
        DoodadService doodads = new DoodadService(grid);
        long shooter = spawn(sim, Faction.MARINE, 2);
        long target = spawn(sim, Faction.DEFENDER, 10);
        BallisticResolver resolver = new BallisticResolver(grid, doodads, sim.getUnitIndex(), sim.getRoster());

        QueueRandom rng = new QueueRandom(0f, 0f);
        BallisticResolver.Resolution res = resolver.resolve(shooter, target, 1f, 0f, VEL, rng);

        assertEquals(BallisticResolver.StopKind.WALL, res.kind());
        assertEquals(0L, res.victimId());
        assertEquals(cellCenter(6), res.endX(), EPS, "wall stop point is the wall cell's center");
        assertEquals(rowCenter(), res.endY(), EPS);
        assertEquals((cellCenter(6) - cellCenter(2)) / VEL, res.flightTime(), EPS);
    }

    // ---- muzzle-clearance: skips the adjacent friendly, not an adjacent enemy ----

    @Test
    void muzzleClearanceSkipsAnAdjacentFriendlyAndTheRoundReachesTheRealTarget() {
        BattleSimulation sim = openArena();
        DoodadService doodads = new DoodadService(sim.getGrid());
        long shooter = spawn(sim, Faction.MARINE, 2);
        spawn(sim, Faction.MARINE, 3); // adjacent squadmate, well inside FRIENDLY_MUZZLE_CLEARANCE
        long farTarget = spawn(sim, Faction.DEFENDER, 12);
        BallisticResolver resolver = new BallisticResolver(sim.getGrid(), doodads, sim.getUnitIndex(), sim.getRoster());

        QueueRandom rng = new QueueRandom(0f, 0f, /*cover*/ 0.99f, /*hit*/ 0f);
        BallisticResolver.Resolution res = resolver.resolve(shooter, farTarget, 1f, 0f, VEL, rng);

        assertEquals(BallisticResolver.StopKind.UNIT_HIT, res.kind());
        assertEquals(farTarget, res.victimId(), "the skipped friendly never produced a contact event");
        assertTrue(res.hitIntended());
    }

    @Test
    void muzzleClearanceDoesNotExemptAnAdjacentEnemy() {
        BattleSimulation sim = openArena();
        DoodadService doodads = new DoodadService(sim.getGrid());
        long shooter = spawn(sim, Faction.MARINE, 2);
        long closeEnemy = spawn(sim, Faction.DEFENDER, 3); // adjacent, same distance as the friendly test above
        long farTarget = spawn(sim, Faction.DEFENDER, 12);
        BallisticResolver resolver = new BallisticResolver(sim.getGrid(), doodads, sim.getUnitIndex(), sim.getRoster());

        // cover roll (never blocks, level 0), incidental hit roll (0 < INCIDENTAL_HIT_CHANCE).
        QueueRandom rng = new QueueRandom(0f, 0f, 0.99f, 0f);
        BallisticResolver.Resolution res = resolver.resolve(shooter, farTarget, 1f, 0f, VEL, rng);

        assertEquals(BallisticResolver.StopKind.UNIT_HIT, res.kind());
        assertEquals(closeEnemy, res.victimId(), "an enemy at the same range as the exempted friendly must still be considered");
        assertFalse(res.hitIntended(), "closeEnemy is not the locked target");
        assertFalse(res.friendlyHit());
    }

    // ---- a unit entirely behind the shooter (both ray-circle roots negative) never contacts ----

    @Test
    void enemyDirectlyBehindTheShooterProducesNoContactAndTheRoundReachesTheRealTarget() {
        BattleSimulation sim = openArena();
        DoodadService doodads = new DoodadService(sim.getGrid());
        // One cell directly behind the shooter, opposite the firing
        // direction — close enough that gatherAlongSegment's margin query
        // still surfaces it (it sits right at the segment's start point),
        // but both ray-circle roots are negative: it must never contact.
        long behindEnemy = spawn(sim, Faction.DEFENDER, 1);
        long shooter = spawn(sim, Faction.MARINE, 2);
        long target = spawn(sim, Faction.DEFENDER, 10);
        BallisticResolver resolver = new BallisticResolver(sim.getGrid(), doodads, sim.getUnitIndex(), sim.getRoster());

        // angle, offsetR (spread=0, irrelevant), cover roll (no block), hit
        // roll (hits). No roll is consumed for behindEnemy at all — it's
        // skipped before any event is created, so a queue sized for only the
        // real target's contact must not exhaust.
        QueueRandom rng = new QueueRandom(0f, 0f, 0.99f, 0f);
        BallisticResolver.Resolution res = resolver.resolve(shooter, target, 1f, 0f, VEL, rng);

        assertEquals(BallisticResolver.StopKind.UNIT_HIT, res.kind());
        assertEquals(target, res.victimId(),
                "the enemy behind the shooter must never produce a contact — both its ray-circle roots are negative");
        assertTrue(res.hitIntended());
        assertTrue(sim.getRoster().isAliveById(behindEnemy), "sanity: behindEnemy exists and is alive, just never contacted");
    }

    // ---- failed target roll flies on and can graze the unit behind ----

    @Test
    void failedTargetRollFliesOnAndCanGrazeTheUnitBehind() {
        BattleSimulation sim = openArena();
        DoodadService doodads = new DoodadService(sim.getGrid());
        long shooter = spawn(sim, Faction.MARINE, 2);
        long target = spawn(sim, Faction.DEFENDER, 8);
        long behind = spawn(sim, Faction.DEFENDER, 10);
        BallisticResolver resolver = new BallisticResolver(sim.getGrid(), doodads, sim.getUnitIndex(), sim.getRoster());

        // target: cover roll (no block), hit roll MISSES (finalAccuracy=0).
        // behind: cover roll (no block), hit roll HITS (INCIDENTAL_HIT_CHANCE).
        QueueRandom rng = new QueueRandom(0f, 0f, 0.99f, 0.5f, 0.99f, 0f);
        BallisticResolver.Resolution res = resolver.resolve(shooter, target, 0f, 0f, VEL, rng);

        assertEquals(BallisticResolver.StopKind.UNIT_HIT, res.kind());
        assertEquals(behind, res.victimId(), "the missed round kept flying and grazed the unit behind the target");
        assertFalse(res.hitIntended(), "behind is not the locked target");
    }

    // ---- cover-clip uses grid cover only; a doodad next to the victim must not double-roll ----

    @Test
    void coverClipReadsGridCoverOnlyNotAnAdjacentDoodadsCover() {
        BattleSimulation sim = openArena();
        NavigationGrid grid = sim.getGrid();
        DoodadService doodads = new DoodadService(grid);
        // Sits directly on the shooter-target line, one cell short of the target — the
        // target's west neighbor, so it contributes DoodadService west-facing cover to
        // the target's own cell (same shape as CoverAccuracyResolverTest's directional case).
        doodads.addDoodad(new Doodad(9, ROW, new TileManifest.TileFrame(0, 0), false, Doodad.COVER_MED));
        long shooter = spawn(sim, Faction.MARINE, 2);
        long target = spawn(sim, Faction.DEFENDER, 10);
        BallisticResolver resolver = new BallisticResolver(grid, doodads, sim.getUnitIndex(), sim.getRoster());

        // Sanity: the grid itself carries no wall cover here, but the doodad's
        // neighbor-cover contribution IS non-zero — otherwise this test would be vacuous.
        assertEquals(0, grid.getCoverAt(10, ROW, -8, 0));
        assertTrue(doodads.getDoodadCoverAt(10, ROW, -8, 0) > 0);

        // The doodad sits directly on the ray too (same row, one cell short
        // of the target), so the crossing check fires exactly once, at cell
        // 9 — its own footprint (getDoodadLevelOnCell, no neighbor bleed).
        // Rolls to miss (0.99 >= the level-2 block chance 0.30) so the round
        // reaches the target. The cell-10 bleed (the target's own cell, from
        // the doodad's east-facing neighbor contribution) is never consulted
        // by the crossing check at all — that's the facing array, a
        // different concept from the own-cell level array.
        //
        // cover-clip roll at the target: 0.10 would block under a (wrong) doodad-cover
        // reading (level 2 -> 0.30 chance) but must NOT block against the real grid
        // cover of 0 (0.10 < 0f is false).
        // hit roll: guaranteed (finalAccuracy=1).
        QueueRandom rng = new QueueRandom(0f, 0f, 0.99f, 0.10f, 0f);
        BallisticResolver.Resolution res = resolver.resolve(shooter, target, 1f, 0f, VEL, rng);

        assertEquals(BallisticResolver.StopKind.UNIT_HIT, res.kind(),
                "cover-clip must ignore the neighboring doodad's cover — a COVER_CLIP result here means it double-counted");
        assertEquals(target, res.victimId());
        assertTrue(res.hitIntended());
    }

    // ---- doodad crossings key on the doodad's own cell, not neighbor-bled cover ----

    @Test
    void rayAlongTheAdjacentRowNeverCrossesTheCratesOwnCellAndRollsNoDoodadBlock() {
        BattleSimulation sim = openArena();
        DoodadService doodads = new DoodadService(sim.getGrid());
        doodads.addDoodad(new Doodad(5, ROW, new TileManifest.TileFrame(0, 0), false, Doodad.COVER_MED));
        // Both shooter and target sit one row south of the crate — the ray
        // never enters the crate's own cell (row ROW), only its bled
        // neighbor cover would show up on row ROW+1 under the old
        // getDoodadCoverAt(x, y) reading. getDoodadLevelOnCell must return 0
        // for every cell on this row, so no crossing event is ever created.
        long shooter = sim.spawn(new EntitySpec("shooter", Faction.MARINE, UnitType.MARINE, 2, ROW + 1));
        long target = sim.spawn(new EntitySpec("target", Faction.DEFENDER, UnitType.MARINE, 10, ROW + 1));
        BallisticResolver resolver = new BallisticResolver(sim.getGrid(), doodads, sim.getUnitIndex(), sim.getRoster());

        // angle, offsetR (irrelevant), cover roll (no grid cover), hit roll
        // (hits). No doodad-crossing roll queued — if walkDoodadCrossings
        // wrongly emitted one, this queue would either exhaust early or feed
        // the wrong float into the wrong check.
        QueueRandom rng = new QueueRandom(0f, 0f, 0.99f, 0f);
        BallisticResolver.Resolution res = resolver.resolve(shooter, target, 1f, 0f, VEL, rng);

        assertEquals(BallisticResolver.StopKind.UNIT_HIT, res.kind());
        assertEquals(target, res.victimId());
    }

    @Test
    void oneLevelTwoCrateOnTheRayConsumesExactlyOneCrossingRollAtTheTuningAnchor() {
        BattleSimulation sim = openArena();
        DoodadService doodads = new DoodadService(sim.getGrid());
        doodads.addDoodad(new Doodad(5, ROW, new TileManifest.TileFrame(0, 0), false, Doodad.COVER_MED));
        long shooter = spawn(sim, Faction.MARINE, 2);
        long target = spawn(sim, Faction.DEFENDER, 10);
        BallisticResolver resolver = new BallisticResolver(sim.getGrid(), doodads, sim.getUnitIndex(), sim.getRoster());

        // angle, offsetR (irrelevant), single crossing roll pinned just below
        // the level-2 tuning anchor (0.30) so it blocks. Only 3 values are
        // queued — a second crossing roll (the old neighbor-bleed
        // double-count) would exhaust the queue.
        QueueRandom blockRng = new QueueRandom(0f, 0f, 0.2999f);
        BallisticResolver.Resolution blocked = resolver.resolve(shooter, target, 1f, 0f, VEL, blockRng);
        assertEquals(BallisticResolver.StopKind.DOODAD_BLOCK, blocked.kind());
        assertEquals(cellCenter(5), blocked.endX(), EPS);

        // A roll landing exactly at the anchor must NOT block — pins 0.30 as
        // the exact boundary. Round then reaches the target normally.
        QueueRandom missRng = new QueueRandom(0f, 0f, 0.30f, 0.99f, 0f);
        BallisticResolver.Resolution missed = resolver.resolve(shooter, target, 1f, 0f, VEL, missRng);
        assertEquals(BallisticResolver.StopKind.UNIT_HIT, missed.kind());
        assertEquals(target, missed.victimId());
    }

    // ---- overshoot endpoint when everything misses ----

    @Test
    void overshootEndpointWhenNothingStopsTheRound() {
        BattleSimulation sim = openArena();
        DoodadService doodads = new DoodadService(sim.getGrid());
        long shooter = spawn(sim, Faction.MARINE, 2);
        long target = spawn(sim, Faction.DEFENDER, 8);
        BallisticResolver resolver = new BallisticResolver(sim.getGrid(), doodads, sim.getUnitIndex(), sim.getRoster());

        QueueRandom rng = new QueueRandom(0f, 0f, 0.99f, 0.5f);
        BallisticResolver.Resolution res = resolver.resolve(shooter, target, 0f, 0f, VEL, rng);

        assertEquals(BallisticResolver.StopKind.OVERSHOOT, res.kind());
        assertEquals(0L, res.victimId());
        float expectedDist = (cellCenter(8) - cellCenter(2)) + BallisticResolver.OVERSHOOT_CELLS;
        assertEquals(cellCenter(2) + expectedDist, res.endX(), EPS);
        assertEquals(rowCenter(), res.endY(), EPS);
        assertEquals(expectedDist / VEL, res.flightTime(), EPS);
    }

    // ---- block-chance mapping 15/30/45 ----

    @Test
    void blockChanceByLevelMatchesTheTuningNeutralAnchor() {
        assertArrayEquals(new float[]{0f, 0.15f, 0.30f, 0.45f}, BallisticResolver.BLOCK_CHANCE_BY_LEVEL, EPS);
    }
}

package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.component.BattleComponents;
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

    @Test
    void targetEvasionMultipliesTheLockedHitRoll() {
        BattleSimulation sim = openArena();
        DoodadService doodads = new DoodadService(sim.getGrid());
        long shooter = spawn(sim, Faction.MARINE, 2);
        long target = sim.spawn(new EntitySpec("evasive", Faction.DEFENDER,
                UnitType.MARINE, 8, ROW).armor(0f, 0f, 1f, 0.5f));
        BallisticResolver resolver = new BallisticResolver(
                sim.getGrid(), doodads, sim.getUnitIndex(), sim.getRoster());

        QueueRandom rng = new QueueRandom(0.75f, 0f, 0f);
        BallisticResolver.Resolution result = resolver.resolve(
                shooter, target, 1f, 0f, VEL, rng);

        assertEquals(BallisticResolver.StopKind.OVERSHOOT, result.kind(),
                "a 75% roll misses when target armor halves an otherwise certain hit");
    }

    /** Directly stamps {@code MOVEMENT_VEL_X/Y} — the columns {@code MovementService.velX/velY} read — bypassing a real movement-pass tick so a scene can pin an exact applied velocity for the time-domain solve. */
    private static void setVelocity(BattleSimulation sim, long id, float vx, float vy) {
        BattleComponents c = sim.getBattleComponents();
        sim.getEntityWorld().setFloat(id, c.MOVEMENT, BattleComponents.MOVEMENT_VEL_X, vx);
        sim.getEntityWorld().setFloat(id, c.MOVEMENT, BattleComponents.MOVEMENT_VEL_Y, vy);
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

        // On-target roll + centered lateral/elevation, then doodad crossing roll
        // (blocks: 0f < the level-2 block chance 0.30). The crossing check
        // keys on the doodad's own-cell level only (getDoodadLevelOnCell),
        // so it fires exactly once, at cell 5 — see the DoodadService class
        // doc for why the facing-bled neighbor cover is a separate concern.
        QueueRandom rng = new QueueRandom(0f, 0.5f, 0.5f, 0f);
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

        QueueRandom rng = new QueueRandom(0f, 0.5f, 0.5f);
        BallisticResolver.Resolution res = resolver.resolve(shooter, target, 1f, 0f, VEL, rng);

        assertEquals(BallisticResolver.StopKind.WALL, res.kind());
        assertEquals(0L, res.victimId());
        assertEquals(cellCenter(6), res.endX(), EPS, "wall stop point is the wall cell's center");
        assertEquals(rowCenter(), res.endY(), EPS);
        assertEquals((cellCenter(6) - cellCenter(2)) / VEL, res.flightTime(), EPS);

        BallisticResolver.Resolution elevated = resolver.resolve(
                shooter, target, 0f, 0f, VEL,
                new QueueRandom(0.5f, 0.25f, 0f));
        assertEquals(BallisticResolver.StopKind.WALL, elevated.kind(),
                "structural walls remain full-height hard stops");
        assertTrue(elevated.endZ() > 0f);
    }

    @Test
    void elevatedRoundClearsLowDoodadButTallDoodadCatchesTheSamePath() {
        BattleSimulation sim = openArena();
        NavigationGrid grid = sim.getGrid();
        grid.setWalkable(7, ROW, false);
        DoodadService doodads = new DoodadService(grid);
        TileManifest.TileFrame frame = new TileManifest.TileFrame(0, 0);
        doodads.addDoodad(new Doodad(5, ROW, frame, false,
                Doodad.COVER_MED, 0.20f));
        long shooter = spawn(sim, Faction.MARINE, 2);
        long target = spawn(sim, Faction.DEFENDER, 10);
        BallisticResolver resolver = new BallisticResolver(
                grid, doodads, sim.getUnitIndex(), sim.getRoster());

        BallisticResolver.Resolution cleared = resolver.resolve(
                shooter, target, 0f, 0f, VEL,
                new QueueRandom(0.5f, 0.25f, 0f));
        assertEquals(BallisticResolver.StopKind.WALL, cleared.kind(),
                "the three-value queue proves the cleared doodad consumed no block roll");
        assertTrue(cleared.endZ() > 0.20f);

        doodads.addDoodad(new Doodad(5, ROW, frame, false,
                Doodad.COVER_MED, 0.30f));
        BallisticResolver.Resolution caught = resolver.resolve(
                shooter, target, 0f, 0f, VEL,
                new QueueRandom(0.5f, 0.25f, 0f, 0f));
        assertEquals(BallisticResolver.StopKind.DOODAD_BLOCK, caught.kind());
        assertTrue(caught.endZ() > 0.20f);
        assertTrue(caught.endZ() < 0.30f);
    }

    @Test
    void directionalCoverOnlyRollsWhenTheRoundIntersectsItsCatchBand() {
        BattleSimulation sim = openArena();
        NavigationGrid grid = sim.getGrid();
        DoodadService doodads = new DoodadService(grid);
        long shooter = spawn(sim, Faction.MARINE, 2);
        long target = sim.spawn(new EntitySpec(
                "mech", Faction.DEFENDER, UnitType.HEAVY_MECH, 10, ROW));
        grid.setCoverAtFacing(10, ROW, NavigationGrid.FACING_W,
                3, 0.25f);
        BallisticResolver resolver = new BallisticResolver(
                grid, doodads, sim.getUnitIndex(), sim.getRoster());

        BallisticResolver.Resolution cleared = resolver.resolve(
                shooter, target, 1f, 1f, VEL,
                new QueueRandom(0f, 0.5f, 0.9166667f));
        assertEquals(BallisticResolver.StopKind.UNIT_HIT, cleared.kind(),
                "an elevated on-target round clears low edge cover without consuming a roll");
        assertTrue(cleared.endZ() > 0.25f);

        grid.setCoverAtFacing(10, ROW, NavigationGrid.FACING_W,
                3, 0.65f);
        BallisticResolver.Resolution caught = resolver.resolve(
                shooter, target, 1f, 1f, VEL,
                new QueueRandom(0f, 0.5f, 0.9166667f, 0f));
        assertEquals(BallisticResolver.StopKind.COVER_CLIP, caught.kind());
        assertTrue(caught.endZ() < 0.65f);
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

        QueueRandom rng = new QueueRandom(0f, 0.5f, 0.5f, /*cover*/ 0.99f);
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

        // No cover roll at level 0; incidental hit roll (0 < INCIDENTAL_HIT_CHANCE).
        QueueRandom rng = new QueueRandom(0f, 0.5f, 0.5f, 0f);
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

        // On-target commit + centered axes, then cover roll (no block).
        // No roll is consumed for behindEnemy at all — it's
        // skipped before any event is created, so a queue sized for only the
        // real target's contact must not exhaust.
        QueueRandom rng = new QueueRandom(0f, 0.5f, 0.5f, 0.99f);
        BallisticResolver.Resolution res = resolver.resolve(shooter, target, 1f, 0f, VEL, rng);

        assertEquals(BallisticResolver.StopKind.UNIT_HIT, res.kind());
        assertEquals(target, res.victimId(),
                "the enemy behind the shooter must never produce a contact — both its ray-circle roots are negative");
        assertTrue(res.hitIntended());
        assertTrue(sim.getRoster().isAliveById(behindEnemy), "sanity: behindEnemy exists and is alive, just never contacted");
    }

    // ---- the committed target miss visibly clears the silhouette ----

    @Test
    void failedTargetRollAuthorsAVisibleMissWithoutASecondTargetRoll() {
        BattleSimulation sim = openArena();
        DoodadService doodads = new DoodadService(sim.getGrid());
        long shooter = spawn(sim, Faction.MARINE, 2);
        long target = spawn(sim, Faction.DEFENDER, 8);
        BallisticResolver resolver = new BallisticResolver(sim.getGrid(), doodads, sim.getUnitIndex(), sim.getRoster());

        // Accuracy miss, pure +lateral direction, minimum clearance. The
        // three-value queue proves the target never consumes a hidden second
        // hit roll after aim has already committed the miss.
        QueueRandom rng = new QueueRandom(0.5f, 0f, 0f);
        BallisticResolver.Resolution res = resolver.resolve(shooter, target, 0f, 0f, VEL, rng);

        assertEquals(BallisticResolver.StopKind.OVERSHOOT, res.kind());
        assertEquals(0L, res.victimId());
        assertTrue(Math.abs(res.endY() - rowCenter()) > UnitType.MARINE.radius,
                "the authored miss must visibly pass outside the target's lateral silhouette");
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
        // Intended accuracy was already guaranteed by the commit roll.
        QueueRandom rng = new QueueRandom(0f, 0.5f, 0.5f, 0.99f, 0.10f);
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

        // On-target commit + centered axes, then cover roll (no grid cover).
        // No doodad-crossing roll queued — if walkDoodadCrossings
        // wrongly emitted one, this queue would either exhaust early or feed
        // the wrong float into the wrong check.
        QueueRandom rng = new QueueRandom(0f, 0.5f, 0.5f, 0.99f);
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

        // On-target commit + centered axes, single crossing roll pinned just below
        // the level-2 tuning anchor (0.30) so it blocks. Only 3 values are
        // queued — a second crossing roll (the old neighbor-bleed
        // double-count) would exhaust the queue.
        QueueRandom blockRng = new QueueRandom(0f, 0.5f, 0.5f, 0.2999f);
        BallisticResolver.Resolution blocked = resolver.resolve(shooter, target, 1f, 0f, VEL, blockRng);
        assertEquals(BallisticResolver.StopKind.DOODAD_BLOCK, blocked.kind());
        assertEquals(cellCenter(5), blocked.endX(), EPS);

        // A roll landing exactly at the anchor must NOT block — pins 0.30 as
        // the exact boundary. Round then reaches the target normally.
        QueueRandom missRng = new QueueRandom(0f, 0.5f, 0.5f, 0.30f, 0.99f);
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

        QueueRandom rng = new QueueRandom(0.5f, 0.25f, 0f);
        BallisticResolver.Resolution res = resolver.resolve(shooter, target, 0f, 0f, VEL, rng);

        assertEquals(BallisticResolver.StopKind.OVERSHOOT, res.kind());
        assertEquals(0L, res.victimId());
        float expectedDist = (cellCenter(8) - cellCenter(2)) + BallisticResolver.OVERSHOOT_CELLS;
        assertEquals(cellCenter(2) + expectedDist, res.endX(), EPS);
        assertEquals(rowCenter(), res.endY(), EPS);
        assertTrue(res.endZ() > UnitType.MARINE.hitHalfHeight,
                "a pure elevation miss must visibly fly above the target");
        assertEquals(expectedDist / VEL, res.flightTime(), EPS);

        BallisticResolver.Resolution low = resolver.resolve(
                shooter, target, 0f, 0f, VEL,
                new QueueRandom(0.5f, 0.75f, 0f));
        assertEquals(BallisticResolver.StopKind.OVERSHOOT, low.kind());
        assertTrue(low.endZ() < -UnitType.MARINE.hitHalfHeight,
                "the same target-plane model must author low misses too");
    }

    @Test
    void tallIncidentalBodyCanCatchAnElevatedRoundThatClearsAShortTarget() {
        BattleSimulation sim = openArena();
        DoodadService doodads = new DoodadService(sim.getGrid());
        long shooter = spawn(sim, Faction.MARINE, 2);
        long shortTarget = sim.spawn(new EntitySpec(
                "short", Faction.DEFENDER, UnitType.SWARM_RUNNER, 8, ROW));
        long tallBehind = sim.spawn(new EntitySpec(
                "tall", Faction.DEFENDER, UnitType.HEAVY_MECH, 10, ROW));
        BallisticResolver resolver = new BallisticResolver(
                sim.getGrid(), doodads, sim.getUnitIndex(), sim.getRoster());

        // Miss the runner straight upward at minimum clearance. At the
        // runner plane Z=0.5 clears its 0.3 half-height; farther downrange
        // the same ray is still inside the mech's 0.8 half-height.
        QueueRandom rng = new QueueRandom(0.5f, 0.25f, 0f, 0f);
        BallisticResolver.Resolution res = resolver.resolve(
                shooter, shortTarget, 0f, 0f, VEL, rng);

        assertEquals(BallisticResolver.StopKind.UNIT_HIT, res.kind());
        assertEquals(tallBehind, res.victimId());
        assertFalse(res.hitIntended());
        assertTrue(res.endZ() > UnitType.SWARM_RUNNER.hitHalfHeight);
        assertTrue(res.endZ() < UnitType.HEAVY_MECH.hitHalfHeight);
    }

    // ---- block-chance mapping 15/30/45 ----

    @Test
    void blockChanceByLevelMatchesTheTuningNeutralAnchor() {
        assertArrayEquals(new float[]{0f, 0.15f, 0.30f, 0.45f}, BallisticResolver.BLOCK_CHANCE_BY_LEVEL, EPS);
    }

    // ---- S2: time-domain contact solve + shooter lead ----
    // roadmap/ballistics/stories/s2-moving-targets.md, "Tests" section.

    // ---- stationary regression: w = 0 collapses to S1's math exactly ----

    @Test
    void stationaryUnitContactsCollapseExactlyToS1sDistanceDomainMath() {
        BattleSimulation sim = openArena();
        DoodadService doodads = new DoodadService(sim.getGrid());
        long shooter = spawn(sim, Faction.MARINE, 2);
        long target = spawn(sim, Faction.DEFENDER, 10);
        assertTrue(sim.getRoster().movement().has(target),
                "target carries MOVEMENT (MARINE is a mover type) — this proves w=0 collapses the "
                        + "time-domain solve to S1's math, not that a non-mover skipped extrapolation entirely");
        BallisticResolver resolver = new BallisticResolver(sim.getGrid(), doodads, sim.getUnitIndex(), sim.getRoster());

        QueueRandom rng = new QueueRandom(0f, 0.5f, 0.5f, 0.99f);
        BallisticResolver.Resolution res = resolver.resolve(shooter, target, 1f, 0f, VEL, rng);

        assertEquals(BallisticResolver.StopKind.UNIT_HIT, res.kind());
        assertEquals(target, res.victimId());
        assertTrue(res.hitIntended());
        // Contact is at the near EDGE of the target's radius (0.3), not its
        // center — exactly S1's ray-circle solve: entry = dist - radius.
        assertEquals(cellCenter(10) - UnitType.MARINE.radius, res.endX(), EPS);
        assertEquals(rowCenter(), res.endY(), EPS);
        assertEquals((cellCenter(10) - UnitType.MARINE.radius - cellCenter(2)) / VEL, res.flightTime(), EPS);
    }

    // ---- perpendicular mover: lead connects where the raw aim point would miss ----

    @Test
    void perpendicularMoverConnectsWithLeadAndTheUnledRayWouldHaveMissed() {
        BattleSimulation sim = openArena();
        DoodadService doodads = new DoodadService(sim.getGrid());
        long shooter = spawn(sim, Faction.MARINE, 2); // (2.5, 5.5)
        // Fire-tick position (10.5, 11.5), dist=10 from the shooter, walking
        // north (-y) at 0.5 c/s. One-step lead over tLead=dist/v=1.0s places
        // the aim point at (10.5, 11.0) — off the mover's fire-tick
        // position, but close enough to its actual position at contact time
        // that the exact time-domain quadratic finds a real root within its
        // 0.3-cell radius.
        long mover = sim.spawn(new EntitySpec("mover", Faction.DEFENDER, UnitType.MARINE, 10, 11));
        setVelocity(sim, mover, 0f, -0.5f);
        float roundVelocity = 10f;
        BallisticResolver resolver = new BallisticResolver(sim.getGrid(), doodads, sim.getUnitIndex(), sim.getRoster());

        // On-target commit + centered axes, then cover roll (no cover).
        QueueRandom rng = new QueueRandom(0f, 0.5f, 0.5f, 0.99f);
        BallisticResolver.Resolution res = resolver.resolve(shooter, mover, 1f, 0f, roundVelocity, rng);

        assertEquals(BallisticResolver.StopKind.UNIT_HIT, res.kind(),
                "the led ray must contact the mover — a raw (unled) aim at the same geometry does not, per the companion check below");
        assertEquals(mover, res.victimId());
        assertTrue(res.hitIntended());
        assertEquals(10.267f, res.endX(), 1e-2f);
        assertEquals(10.840f, res.endY(), 1e-2f);
        assertEquals(0.9425f, res.flightTime(), 1e-3f);

        // Companion check: firing at the mover's RAW fire-tick position
        // (10.5, 11.5) instead of the led point — same shooter, same mover
        // motion — never intersects its 0.3-cell radius (disc < 0). This is
        // the same quadratic resolve() solves internally
        // (roadmap/ballistics/stories/s2-moving-targets.md, "Time-domain
        // contact solve"), evaluated here against the unled direction to
        // prove lead and extrapolation only balance as a pair: extrapolation
        // without lead systematically misses a lateral mover.
        float fromX = 2.5f, fromY = 5.5f;
        float u0x = 10.5f, u0y = 11.5f;
        float wx = 0f, wy = -0.5f;
        float rawDx = u0x - fromX, rawDy = u0y - fromY;
        float rawDist = (float) Math.sqrt(rawDx * rawDx + rawDy * rawDy);
        float rawDirX = rawDx / rawDist, rawDirY = rawDy / rawDist;
        float relVelX = wx - roundVelocity * rawDirX;
        float relVelY = wy - roundVelocity * rawDirY;
        float r0x = u0x - fromX, r0y = u0y - fromY;
        float a = relVelX * relVelX + relVelY * relVelY;
        float b = 2f * (r0x * relVelX + r0y * relVelY);
        float c = r0x * r0x + r0y * r0y - UnitType.MARINE.radius * UnitType.MARINE.radius;
        float disc = b * b - 4f * a * c;
        assertTrue(disc < 0f, "firing at the raw (unled) position must not intersect the mover's circle at any time");
    }

    // ---- mover entering the corridor: gathered (widened margin) and contacted mid-flight ----

    @Test
    void moverEnteringTheCorridorDuringASlowRoundsFlightIsGatheredAndContacted() {
        BattleSimulation sim = openArena();
        DoodadService doodads = new DoodadService(sim.getGrid());
        long shooter = spawn(sim, Faction.MARINE, 2); // (2.5, 5.5)
        // Locked target far downrange on the shooter's row, stationary — aim
        // direction is pinned exactly horizontal, keeping this scenario's
        // geometry simple.
        long farTarget = spawn(sim, Faction.DEFENDER, 20);
        // Incidental candidate at (7.5, 7.3): 1.8 cells off the ray at fire
        // time — outside S1's flat GATHER_MARGIN_CELLS (1.0) plus radius, so
        // S1's gather would never have surfaced it. Walking toward the row
        // (-y) at 1.5 c/s, it enters the ray's corridor mid-flight.
        long candidate = sim.spawn(new EntitySpec("candidate", Faction.DEFENDER, UnitType.MARINE, 7, 7));
        sim.world().setPos(candidate, 7.5f, 7.3f);
        setVelocity(sim, candidate, 0f, -1.5f);
        float roundVelocity = 5f; // slow round — long flight, wide exposure window
        BallisticResolver resolver = new BallisticResolver(sim.getGrid(), doodads, sim.getUnitIndex(), sim.getRoster());

        // On-target commit + centered axes, then candidate's cover roll (no grid cover
        // anywhere here), candidate's hit roll (INCIDENTAL_HIT_CHANCE=0.35;
        // it is not the locked target). The candidate's contact time (~1.0s)
        // is well before the far target's (~3.5s), so the round never
        // reaches the far target's event — no rolls queued for it.
        QueueRandom rng = new QueueRandom(0f, 0.5f, 0.5f, 0f);
        BallisticResolver.Resolution res = resolver.resolve(shooter, farTarget, 1f, 0f, roundVelocity, rng);

        assertEquals(BallisticResolver.StopKind.UNIT_HIT, res.kind());
        assertEquals(candidate, res.victimId(), "the corridor-entering candidate must be gathered and contacted before the round ever reaches the far target");
        assertFalse(res.hitIntended(), "candidate is not the locked target");
        assertEquals(1.0f, res.flightTime(), 1e-2f);
        assertEquals(7.5f, res.endX(), 1e-2f);
        assertEquals(5.5f, res.endY(), 1e-2f);
    }

    // ---- mover leaving cover: edge-clip lookup uses the extrapolated cell, not the fire-tick cell ----

    @Test
    void moverLeavingCoverByContactTimeSkipsTheEdgeClipRoll() {
        BattleSimulation sim = openArena();
        NavigationGrid grid = sim.getGrid();
        // Cover facing WEST (the shooter's direction) at the victim's
        // FIRE-TICK cell only — level 3, a 0.45 block chance if (wrongly)
        // read at the fire-tick cell.
        grid.setCoverAtFacing(10, ROW, NavigationGrid.FACING_W, 3);
        assertEquals(0, grid.getCoverAt(12, ROW, -10, 0), "sanity: no cover baked at the cell the victim extrapolates into");
        DoodadService doodads = new DoodadService(grid);
        long shooter = spawn(sim, Faction.MARINE, 2); // (2.5, 5.5)
        long mover = spawn(sim, Faction.DEFENDER, 10); // fire-tick cell (10, ROW) — the covered cell
        setVelocity(sim, mover, 2f, 0f); // sprinting east, deeper into the corridor
        float roundVelocity = 10f;
        BallisticResolver resolver = new BallisticResolver(grid, doodads, sim.getUnitIndex(), sim.getRoster());

        // On-target commit + centered axes, then cover-clip roll pinned at 0.1 — WOULD
        // block under the (wrong) fire-tick-cell reading (0.1 < 0.45) but
        // must NOT block under the correct extrapolated-cell reading (cover
        // 0 there, so any roll fails to trigger it).
        QueueRandom rng = new QueueRandom(0f, 0.5f, 0.5f, 0.1f);
        BallisticResolver.Resolution res = resolver.resolve(shooter, mover, 1f, 0f, roundVelocity, rng);

        assertEquals(BallisticResolver.StopKind.UNIT_HIT, res.kind(),
                "a COVER_CLIP result here means the edge-clip lookup wrongly used the mover's fire-tick cell instead of its extrapolated cell");
        assertEquals(mover, res.victimId());
        assertTrue(res.hitIntended());
    }

    // ---- degenerate pacing: a ~= 0 must never NaN, and never falsely contacts ----

    @Test
    void degeneratePacingUnitProducesNoContactAndNoNaN() {
        BattleSimulation sim = openArena();
        DoodadService doodads = new DoodadService(sim.getGrid());
        long shooter = spawn(sim, Faction.MARINE, 2); // (2.5, 5.5)
        long farTarget = spawn(sim, Faction.DEFENDER, 20); // stationary, pins the aim direction horizontal
        // Exactly paces the round's own velocity vector (10, 0) — relative
        // velocity is the zero vector, so a < 1e-6 in the quadratic. Not
        // already overlapping (r0 = (8, 0), well outside the 0.3 radius), so
        // c > 0: must skip without ever computing a sqrt.
        long pacer = spawn(sim, Faction.DEFENDER, 10);
        setVelocity(sim, pacer, 10f, 0f);
        float roundVelocity = 10f;
        BallisticResolver resolver = new BallisticResolver(sim.getGrid(), doodads, sim.getUnitIndex(), sim.getRoster());

        // On-target commit + centered axes; the pacer produces no event at all
        // (skipped before any roll), so only the far target's cover+hit
        // roll is queued — a queue sized for the pacer too would exhaust
        // wrong if it wrongly produced an event.
        QueueRandom rng = new QueueRandom(0f, 0.5f, 0.5f, 0.99f);
        BallisticResolver.Resolution res = resolver.resolve(shooter, farTarget, 1f, 0f, roundVelocity, rng);

        assertEquals(BallisticResolver.StopKind.UNIT_HIT, res.kind());
        assertEquals(farTarget, res.victimId(), "the pacing unit must never register a contact");
        assertFalse(Float.isNaN(res.endX()));
        assertFalse(Float.isNaN(res.endY()));
        assertFalse(Float.isNaN(res.flightTime()));
        assertTrue(sim.getRoster().isAliveById(pacer), "sanity: the pacer exists and is alive, just never contacted");
    }

    // ---- behind-shooter regression, re-asserted in the time domain ----

    @Test
    void enemyBehindTheShooterStillProducesNoContactWhileMoving() {
        BattleSimulation sim = openArena();
        DoodadService doodads = new DoodadService(sim.getGrid());
        // One cell directly behind the shooter, drifting perpendicular (+y)
        // at 1 c/s — a genuinely non-degenerate quadratic (a > 0, real
        // roots exist) whose EXIT root still lands behind the shooter
        // (sExit < 0), so it must be skipped exactly as S1's stationary
        // case was, not false-clamped to s=0.
        long behindEnemy = spawn(sim, Faction.DEFENDER, 1);
        setVelocity(sim, behindEnemy, 0f, 1f);
        long shooter = spawn(sim, Faction.MARINE, 2);
        long target = spawn(sim, Faction.DEFENDER, 10);
        BallisticResolver resolver = new BallisticResolver(sim.getGrid(), doodads, sim.getUnitIndex(), sim.getRoster());

        // On-target commit + centered axes, then cover roll (no block).
        // No roll is consumed for behindEnemy — it's skipped before
        // any event is created.
        QueueRandom rng = new QueueRandom(0f, 0.5f, 0.5f, 0.99f);
        BallisticResolver.Resolution res = resolver.resolve(shooter, target, 1f, 0f, VEL, rng);

        assertEquals(BallisticResolver.StopKind.UNIT_HIT, res.kind());
        assertEquals(target, res.victimId(), "the moving enemy behind the shooter must never produce a contact — its exit root is still negative");
        assertTrue(res.hitIntended());
        assertTrue(sim.getRoster().isAliveById(behindEnemy), "sanity: behindEnemy exists and is alive, just never contacted");
    }

    // ---- muzzle clearance and wall cap stay DISTANCE tests, evaluated at contact time, even for a moving contact ----

    @Test
    void friendlyMuzzleClearanceUsesContactTimeDistanceEvenWhileMoving() {
        BattleSimulation sim = openArena();
        DoodadService doodads = new DoodadService(sim.getGrid());
        long shooter = spawn(sim, Faction.MARINE, 2); // (2.5, 5.5)
        long farTarget = spawn(sim, Faction.DEFENDER, 10);
        // Friendly at fire-tick distance 3.0 cells (outside the 2.0-cell
        // FRIENDLY_MUZZLE_CLEARANCE), but closing on the shooter at 5 c/s:
        // by contact time it has closed to 1.8 cells (v*sEntry), inside the
        // clearance. A naive fire-tick-distance check would have let this
        // contact through; the correct v*sEntry check must skip it.
        long friendly = spawn(sim, Faction.MARINE, 5);
        setVelocity(sim, friendly, -5f, 0f);
        BallisticResolver resolver = new BallisticResolver(sim.getGrid(), doodads, sim.getUnitIndex(), sim.getRoster());

        // On-target commit + centered axes — friendly is skipped before any
        // contact roll; farTarget's cover roll does not block.
        QueueRandom rng = new QueueRandom(0f, 0.5f, 0.5f, 0.99f);
        BallisticResolver.Resolution res = resolver.resolve(shooter, farTarget, 1f, 0f, VEL, rng);

        assertEquals(BallisticResolver.StopKind.UNIT_HIT, res.kind());
        assertEquals(farTarget, res.victimId(), "the closing friendly must still be muzzle-clearance-skipped at its contact-time distance");
        assertTrue(res.hitIntended());
    }

    @Test
    void wallCapStillSkipsAMovingContactPastTheWall() {
        BattleSimulation sim = openArena();
        NavigationGrid grid = sim.getGrid();
        grid.setWalkable(6, ROW, false);
        DoodadService doodads = new DoodadService(grid);
        long shooter = spawn(sim, Faction.MARINE, 2); // (2.5, 5.5)
        // 8 cells out, closing on the shooter at 3 c/s — even accounting for
        // that motion, its contact-time distance (v*sEntry ~= 5.92) is still
        // well past the wall at 4.0 cells, so the wall must still cap the
        // ray before this contact is ever recorded.
        long target = spawn(sim, Faction.DEFENDER, 10);
        setVelocity(sim, target, -3f, 0f);
        BallisticResolver resolver = new BallisticResolver(grid, doodads, sim.getUnitIndex(), sim.getRoster());

        QueueRandom rng = new QueueRandom(0f, 0.5f, 0.5f);
        BallisticResolver.Resolution res = resolver.resolve(shooter, target, 1f, 0f, VEL, rng);

        assertEquals(BallisticResolver.StopKind.WALL, res.kind());
        assertEquals(0L, res.victimId());
        assertEquals(cellCenter(6), res.endX(), EPS);
        assertEquals(rowCenter(), res.endY(), EPS);
        assertEquals((cellCenter(6) - cellCenter(2)) / VEL, res.flightTime(), EPS);
    }
}

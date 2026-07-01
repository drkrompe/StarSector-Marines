package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.render2d.BattleCamera;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Headless pin for Phase 2 of the live-appearance epic
 * ({@code roadmap/ecs-migration/stories/live-appearance.md}): {@link UnitRenderService}
 * now reads the {@code SPRITE} component {@code battle.appearance.FacingSystem} authors,
 * instead of deriving facing/frame per render. Exercised with an <em>unloaded</em>
 * {@link BattleSprites} (no {@code ensure*} call, so every sprite-cache lookup misses and
 * every {@code getTextureWidth}/GL call is avoided) — {@code UnitRenderService},
 * {@link RenderContext}, {@link DrawList}, and {@link BattleCamera} all construct and run
 * with no OpenGL context, so this suite runs in the plain JUnit/Gradle harness.
 *
 * <p>Every test uses a single-entity arena (no keep-alive units): with only one unit in
 * the whole {@code BattleSimulation}, {@link UnitRenderService#collect}'s other five
 * sweeps (footprint / turret / hub / dead-sprite / HP-bar) are guaranteed structural
 * no-ops for a non-combatant sheet-drawn type, so {@link RenderLayer#UNITS}' draw count
 * pins {@link UnitRenderService}'s live-sprite sweep in isolation — {@code DrawCommand}'s
 * fields are package-private to {@code render2d} (unreadable from here), so the count is
 * the only observable surface a same-package-but-different-package test can assert on.
 */
public class UnitRenderServiceLiveSweepTest {

    private static BattleSimulation openArena(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        CellTopology topology = new CellTopology(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, topology);
    }

    /**
     * Faction.MARINE (a fog-of-war contributor, so it can be swept to VIS_VISIBLE
     * without running the full tick loop) + a non-combatant sheet-drawn type (no HP
     * bar, no footprint, no death pose) — the combination that isolates every
     * assertion below to {@code sweepLiveSprites} alone.
     */
    private static Entity spawnSubject(BattleSimulation sim, int cellX, int cellY) {
        Entity subject = new Entity("subject", Faction.MARINE, UnitType.CIVILIAN, cellX, cellY);
        sim.addUnit(subject);
        return subject;
    }

    private static RenderContext newContext(BattleSimulation sim) {
        BattleCamera cam = new BattleCamera(40, 40);
        cam.setViewport(0f, 0f, 800f, 600f, 32f);
        // layout is unread by any UnitRenderService sweep (only ctx.sim / ctx.camera /
        // ctx.alphaMult are), so null stands in without a PositionAPI to construct one.
        return new RenderContext(sim, cam, null, 1f, 0f, false,
                new HighlightOverlay(), new Selection());
    }

    private static int collectUnitsLayer(BattleSimulation sim) {
        UnitRenderService renderService = new UnitRenderService(new BattleSprites());
        DrawList out = new DrawList();
        renderService.collect(newContext(sim), out);
        return out.count(RenderLayer.UNITS);
    }

    @Test
    public void liveUnitEmitsExactlyOneFallbackQuad() {
        BattleSimulation sim = openArena(40, 40);
        spawnSubject(sim, 10, 10);
        // Force one fog-of-war visibility sweep directly (simTickIndex % 3 == 0)
        // without running the full sim tick loop — a contributor faction is
        // unconditionally marked VIS_VISIBLE by the sweep, and allocate's
        // spawn-time SPRITE_INDEX seed (south-idle) already makes the row drawable.
        sim.getFogOfWar().tick(3, sim.getRoster());

        assertEquals(1, collectUnitsLayer(sim),
                "a live sheet-drawn unit draws exactly the sweepLiveSprites fallback quad "
                        + "(no cache loaded -> colored SOLID_RECT)");
    }

    @Test
    public void fullyDrainedCorpseEmitsNothingFromTheLiveSweep() {
        BattleSimulation sim = openArena(40, 40);
        Entity subject = spawnSubject(sim, 10, 10);

        sim.applyDamage(subject, 100_000f, 1f, 0f);
        sim.advance(BattleSimulation.TICK_DT); // drains the death mailbox -> corpse transmute

        assertFalse(sim.getRoster().isLive(subject.entityId), "the unit is gone from the roster");
        assertEquals(0, collectUnitsLayer(sim),
                "a fully-transmuted corpse's entity no longer matches liveSprites (lacks HEALTH), "
                        + "so the live sweep draws nothing for it (the dead sweep is also silent here: "
                        + "no sprite cache loaded, and CIVILIAN carries no death-pose sheet at all)");
    }

    @Test
    public void releasedButNotYetTransmutedRowEmitsNothingFromTheLiveSweep() {
        BattleSimulation sim = openArena(40, 40);
        Entity subject = spawnSubject(sim, 10, 10);

        // Serial applyDamage resolves inline (DamageResolver.resolve): HEALTH.hp goes
        // <= 0 and the roster releases the dense-array slot SYNCHRONOUSLY, but the
        // death-dispatcher drain that transmutes the world row to a corpse is
        // deferred to the next advance() call. Collecting BEFORE any advance() pins
        // the Phase-2 handoff notes' landmine directly: a released-but-not-yet-
        // transmuted row still matches liveSprites (it still carries HEALTH, hp <= 0)
        // and its roster.indexOf resolves to INVALID_INDEX, which
        // FogOfWarService.getUnitVisibility tolerantly reads as VIS_VISIBLE — so the
        // hp-gate (not the visibility gate) is the only thing standing between this
        // row and a stale draw.
        sim.applyDamage(subject, 100_000f, 1f, 0f);
        assertFalse(sim.getRoster().isLive(subject.entityId),
                "serial applyDamage releases the roster slot inline, before any death drain");

        assertEquals(0, collectUnitsLayer(sim),
                "the hp<=0 gate must skip this row even though it still matches liveSprites "
                        + "and its released roster index would otherwise read as tolerantly visible");
    }
}

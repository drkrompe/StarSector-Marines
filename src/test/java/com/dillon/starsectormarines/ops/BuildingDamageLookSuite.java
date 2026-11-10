package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.Landform;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.Fonts;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * Stood-up buildings under damage ({@code voids.md}, buildings in depth): an open verdant town and a frozen crevasse
 * town, each intact, damaged and destroyed, the damage laid through the map damage layer before play
 * ({@code BattleSimulation.applyInitialDamage}): battered walls in a ring round the view near its busiest block, and
 * a destroyed building on that ring, collapsed and burnt. Each state whole ({@code <case>-<state>-whole.png}) and at play zoom
 * ({@code -play.png}), and a pan
 * along the damaged street ({@code <case>-damaged-pan-NN.png}). Look preset; nothing is played.
 */
public final class BuildingDamageLookSuite implements SnapshotSuite {

    private static final int PAN_FRAMES = 60;
    /** Cells the play-zoom view stands off the damaged block. */
    private static final float VIEW_OFFSET = 16f;
    private static final float PAN_CELLS = 14f;
    /** Cells off the view's centre where the damage falls: a wall there stands about a cell deep at play zoom. */
    private static final float RING_CELLS = 15f;

    private record Case(String id, SurfacePalette palette, Landform landform) { }

    private static final List<Case> CASES = List.of(
            new Case("open-town", SurfacePalette.VERDANT, null),
            new Case("frozen-crevasse-town", SurfacePalette.FROZEN, new Landform.Shelter(true)));

    @Override public String id() { return "building-damage-look"; }

    @Override public String label() { return "Stood-up buildings intact, damaged and destroyed, look preset"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            DustLedgerMissionPreviewSuite.installFonts(context, Fonts.ORBITRON_12_BOLD, Fonts.INSIGNIA_15_AA);
            List<SnapshotArtifact> out = new ArrayList<>();
            long seed = Long.getLong("snapshot.buildingDamage.seed", 7L);
            for (Case c : CASES) {
                float[] block = null;
                for (String state : new String[]{"intact", "damaged", "destroyed"}) {
                    try (BattleSimulation sim = create(c, seed)) {
                        if (block == null) block = BuildingFacesLookSuite.busiest(sim);
                        // The view stands off the block, so its walls turn to the camera.
                        float vx = block[0] - VIEW_OFFSET, vy = block[1] - VIEW_OFFSET * .5f;
                        if (!state.equals("intact")) {
                            sim.applyInitialDamage(damage(sim, vx, vy, state.equals("destroyed")));
                        }
                        int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
                        String id = c.id() + "-" + state;
                        out.add(new SnapshotArtifact(id + "-whole.png", BattleReviewFrameRenderer.look(context.modRoot(),
                                1920, 1080, w / 2f, h / 2f, Math.max(w, h * 16f / 9f) + 4f).render(sim, "")));
                        out.add(new SnapshotArtifact(id + "-play.png", BattleReviewFrameRenderer.look(context.modRoot(),
                                1920, 1080, vx, vy, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).render(sim, "")));
                        if (state.equals("damaged")) {
                            for (int f = 0; f < PAN_FRAMES; f++) {
                                float t = f / (PAN_FRAMES - 1f) * 2f - 1f;
                                out.add(new SnapshotArtifact(String.format(Locale.ROOT, "%s-damaged-pan-%02d.png",
                                        c.id(), f), BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080,
                                        vx + t * PAN_CELLS, vy + t * PAN_CELLS * .4f,
                                        BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).render(sim, "")));
                            }
                        }
                    }
                }
            }
            return out;
        }
    }

    private static BattleSimulation create(Case c, long seed) {
        TargetProfile market = new TargetProfile(5, 5, 2, 1, "independent",
                EnumSet.of(EconomicFunction.HABITATION, EconomicFunction.MINING), c.palette(), SettlementLink.ROAD);
        return BattleSetup.createPlaceholder(seed, BattleSetup.defaultManifest(), false, OperationTier.ESTABLISHED,
                RiskLevel.MEDIUM, MissionType.ASSAULT, market, FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                PrecinctPlan.Sprawl.BALANCED, c.landform());
    }

    /**
     * Battered walls in a ring round the view, where the walls turned to the camera stand tallest
     * ({@link #RING_CELLS}), and when {@code destroyed} the building nearest that ring collapsed and burnt.
     */
    private static DamagePlan damage(BattleSimulation sim, float vx, float vy, boolean destroyed) {
        int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
        List<DamagePlan.Stroke> strokes = new ArrayList<>();
        List<DamagePlan.Point> ring = new ArrayList<>();
        for (int k = 0; k <= 32; k++) {
            double a = k * Math.PI * 2 / 32;
            ring.add(new DamagePlan.Point(clamp(vx + (float) Math.cos(a) * RING_CELLS, w),
                    clamp(vy + (float) Math.sin(a) * RING_CELLS * .6f, h)));
        }
        strokes.add(new DamagePlan.Stroke(DamagePlan.Mode.BATTERED, 5, ring));
        if (destroyed) {
            Building near = nearest(sim, new float[]{vx, vy});
            // Ruined row by row over its whole footprint, so the building is destroyed, not only holed.
            if (near != null) for (int y = near.minY; y <= near.maxY + 1; y += 2) {
                strokes.add(new DamagePlan.Stroke(DamagePlan.Mode.RUIN, 1.6, List.of(
                        new DamagePlan.Point(clamp(near.minX, w), clamp(y, h)),
                        new DamagePlan.Point(clamp(near.maxX, w), clamp(y, h)))));
            }
        }
        // Battered twice, so the street's walls are badly hurt (each layer spends its battering once).
        DamagePlan.Layer again = new DamagePlan.Layer("look damage, again", true, List.of(strokes.get(0)));
        return new DamagePlan(w, h, 11L, List.of(new DamagePlan.Layer("look damage", true, strokes), again));
    }

    private static Building nearest(BattleSimulation sim, float[] at) {
        Building best = null;
        float bestD = Float.MAX_VALUE;
        for (Building b : sim.getBuildings().all()) {
            if (b.cellsX.length < 30 || b.cellsX.length > 400) continue;
            float dx = (b.minX + b.maxX + 1) / 2f - at[0], dy = (b.minY + b.maxY + 1) / 2f - at[1];
            float off = Math.abs((float) Math.sqrt(dx * dx + dy * dy) - RING_CELLS);
            if (off < bestD) { bestD = off; best = b; }
        }
        return best;
    }

    private static double clamp(float v, int span) {
        return Math.max(0, Math.min(span - 1, v));
    }
}

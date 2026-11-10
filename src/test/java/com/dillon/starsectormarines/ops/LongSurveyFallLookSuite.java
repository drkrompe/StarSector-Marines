package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.derelict.DerelictSecurityDirector;
import com.dillon.starsectormarines.battle.derelict.DerelictWalkerFrame;
import com.dillon.starsectormarines.battle.derelict.FallenHull;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Long Survey's first chapter, The Fall, launched as the debug mission player launches it (LS1 on its owned seed),
 * played a short while for its strippers and printed walkers to walk out, and looked at through the look preset at
 * 1920x1080: the hull roofed on its trench ({@code 1-hull.png}, closer {@code 1b-hull-close.png}), the forge hall with
 * the roof lifted ({@code 2-forge.png}), and the first stripper and hauler out on the claim, set side by side on open
 * ground clear of the hull for the frame alone ({@code 3-stripper-hauler.png}: stripper west, hauler east). The fallen
 * hull's looks in their places (`art-source/fallen-hull`). Nothing is judged; the battle is not stepped after the
 * walkers are moved.
 * Opt-in; {@code -Dsnapshot.longSurveyFall.seconds} sets how long it plays first (default 150).
 */
public final class LongSurveyFallLookSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;

    @Override public String id() { return "long-survey-fall-look"; }

    @Override public String label() { return "The Fall's hull, forge and salvage walkers in play, through the look preset"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SnapshotArtifact> out = new ArrayList<>();
        int ticks = Math.round(Float.parseFloat(System.getProperty("snapshot.longSurveyFall.seconds", "150"))
                / BattleSimulation.TICK_DT);
        try (HeadlessStrings strings = new HeadlessStrings(context.modRoot())) {
            var mission = DebugMissionCatalog.Scenario.LONG_SURVEY_FALL.mission();
            var ops = new MarineOpsContext(null);
            ops.resetDebugPreset(mission);
            var preset = DebugMissionCatalog.preset(mission);
            var lift = Collections.nCopies(preset.boats(), DebugMissionCatalog.maxFitted(preset.boat()));
            var plan = MissionLaunch.captureScenario(ops, mission, lift, FlybyRoster.EMPTY, List.of(), List.of(), null,
                    mission.battleSeed);
            try (var prepared = MissionLaunch.finishSimulation(ops, mission, FlybyRoster.EMPTY, plan,
                    plan.builder().get())) {
                BattleSimulation sim = prepared.simulation();
                for (int t = 0; t < ticks; t++) {
                    int before = sim.getSimTickIndex();
                    sim.advance(BattleSimulation.TICK_DT);
                    if (sim.getSimTickIndex() == before) break;
                }
                DerelictSecurityDirector director = sim.derelictSecurity();
                FallenHull hull = director == null ? null : director.hull();
                if (hull == null) {
                    log("no fallen hull stands in The Fall");
                    return out;
                }
                World world = sim.world();
                long forge = hull.forgeId();
                float fx = sim.resolveUnit(forge) != 0L ? world.renderX(forge) : 0f;
                float fy = sim.resolveUnit(forge) != 0L ? world.renderY(forge) : 0f;
                log("forge %d at (%.1f, %.1f), lit %s, printed %d", forge, fx, fy, hull.forge().lit(),
                        hull.forge().printed());
                if (fx != 0f) {
                    out.add(new SnapshotArtifact("1-hull.png", BattleReviewFrameRenderer.look(context.modRoot(), W, H,
                            fx - 18f, fy, 110f).render(sim, "")));
                    out.add(new SnapshotArtifact("1b-hull-close.png", BattleReviewFrameRenderer.look(context.modRoot(),
                            W, H, fx - 30f, fy, 48f).render(sim, "")));
                    out.add(new SnapshotArtifact("2-forge.png", BattleReviewFrameRenderer.look(context.modRoot(), W,
                            H, fx - 4f, fy, 34f).withoutRoofs().render(sim, "")));
                }
                long stripper = first(sim, director, DerelictWalkerFrame.STRIPPER);
                long hauler = first(sim, director, DerelictWalkerFrame.HAULER);
                if (stripper != 0L && hauler != 0L) {
                    int[] spot = openGround(sim, world.cellX(stripper), world.cellY(stripper), fx, fy);
                    world.setPos(stripper, spot[0] - 2.5f, spot[1] + .5f);
                    world.setPos(hauler, spot[0] + 3.5f, spot[1] + .5f);
                    log("stripper %d and hauler %d set at (%d, %d); drawn at (%.1f, %.1f) and (%.1f, %.1f)", stripper,
                            hauler, spot[0], spot[1], world.renderX(stripper), world.renderY(stripper),
                            world.renderX(hauler), world.renderY(hauler));
                    out.add(new SnapshotArtifact("3-stripper-hauler.png", BattleReviewFrameRenderer.look(
                            context.modRoot(), W, H, spot[0] + .5f, spot[1] + .5f, 16f).withoutRoofs().render(sim, "")));
                } else {
                    log("stripper %d, hauler %d: not both out", stripper, hauler);
                }
            }
        }
        return out;
    }

    /** The first member of {@code frame}'s first walker squad still standing, or 0. */
    private static long first(BattleSimulation sim, DerelictSecurityDirector director, DerelictWalkerFrame frame) {
        for (DerelictSecurityDirector.Walker w : director.walkers()) {
            if (w.frame() != frame || sim.squadMemberCount(w.squadId()) <= 0) continue;
            long id = sim.squadMemberAt(w.squadId(), 0);
            log("%s %d at (%.1f, %.1f), variant %s", frame, id, sim.world().renderX(id), sim.world().renderY(id),
                    sim.identity().mechVariant(id));
            return id;
        }
        return 0L;
    }

    /**
     * A cell of open ground near {@code (x, y)}, walked outward away from the forge at {@code (fx, fy)}, with a
     * 14 x 8 cell patch round it walkable and under no building: somewhere the two walkers stand in plain light.
     */
    private static int[] openGround(BattleSimulation sim, int x, int y, float fx, float fy) {
        float dx = x - fx, dy = y - fy;
        float len = (float) Math.max(1e-3, Math.hypot(dx, dy));
        dx /= len;
        dy /= len;
        for (int step = 0; step < 60; step++) {
            int cx = Math.round(x + dx * step), cy = Math.round(y + dy * step);
            if (clear(sim, cx, cy)) return new int[]{cx, cy};
        }
        return new int[]{x, y};
    }

    private static boolean clear(BattleSimulation sim, int cx, int cy) {
        for (int yy = cy - 4; yy <= cy + 4; yy++) {
            for (int xx = cx - 7; xx <= cx + 7; xx++) {
                if (!sim.getGrid().inBounds(xx, yy) || !sim.getGrid().isWalkable(xx, yy)) return false;
                if (sim.getTopology().getBuildingId(xx, yy) != 0 || sim.getTopology().isWall(xx, yy)) return false;
            }
        }
        return true;
    }

    private static void log(String format, Object... args) {
        System.out.printf("    [look/long-survey-fall] " + format + "%n", args);
    }
}

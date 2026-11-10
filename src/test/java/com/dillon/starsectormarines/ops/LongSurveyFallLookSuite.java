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
 * 1920x1080: the hull roofed on its trench ({@code 1-hull.png}), the forge hall with the roof lifted
 * ({@code 2-forge.png}), and the first stripper and hauler out on the claim ({@code 3-stripper.png},
 * {@code 4-hauler.png}). The fallen hull's looks in their places (`art-source/fallen-hull`). Nothing is judged.
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
                walker(context, sim, director, DerelictWalkerFrame.STRIPPER, "3-stripper.png", out);
                walker(context, sim, director, DerelictWalkerFrame.HAULER, "4-hauler.png", out);
            }
        }
        return out;
    }

    private static void walker(SnapshotContext context, BattleSimulation sim, DerelictSecurityDirector director,
                               DerelictWalkerFrame frame, String name, List<SnapshotArtifact> out) throws Exception {
        for (DerelictSecurityDirector.Walker w : director.walkers()) {
            if (w.frame() != frame || sim.squadMemberCount(w.squadId()) <= 0) continue;
            long id = sim.squadMemberAt(w.squadId(), 0);
            float x = sim.world().renderX(id), y = sim.world().renderY(id);
            log("%s %d at (%.1f, %.1f), variant %s, loadout %s", frame, id, x, y, sim.identity().mechVariant(id),
                    sim.world().hasMechLoadout(id) ? sim.world().mechLoadout(id).variant : null);
            out.add(new SnapshotArtifact(name, BattleReviewFrameRenderer.look(context.modRoot(), W, H, x, y, 24f)
                    .withoutRoofs().render(sim, "")));
            return;
        }
        log("no %s out", frame);
    }

    private static void log(String format, Object... args) {
        System.out.printf("    [look/long-survey-fall] " + format + "%n", args);
    }
}

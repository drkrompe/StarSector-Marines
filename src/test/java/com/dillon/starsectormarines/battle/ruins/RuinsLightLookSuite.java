package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.ruins.PowerGraph.Branch;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import com.dillon.starsectormarines.battle.world.model.NightLighting;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import com.dillon.starsectormarines.campaign.ruins.RuinsOccupant;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * A ruins site's light ({@link RuinsLighting}) through the look preset at 1920x1080: the plant dark at landing, with only
 * its emergency strips and the daylight through its open roof; partly lit, the hall restarted and its layer's junction
 * thrown to the store, so the hall and the store burn while the way on stays dark; and lit, every junction on the way
 * on. The power is routed through the works' own rules, not drawn; the roofs are off so the interior shows.
 * {@code -Dsnapshot.ruinsLight.seed} picks the site (default 31), {@code -Dsnapshot.ruinsLight.grade} the grade
 * (default EXTENSIVE), {@code -Dsnapshot.ruinsLight.focus} where the camera stands: {@code hall} (default), {@code entrance} (the first way in), {@code court} (the first light well inside) or
 * {@code works}, {@code -Dsnapshot.ruinsLight.roofed} the darkness under the roof, for a dimmer start to compare
 * (default {@link RuinsLighting#ROOFED_DARKNESS}), and {@code -Dsnapshot.ruinsLight.warm=true} renders the hall's
 * restart instead, one renderer stepping its clock, so the lamps are seen striking as they warm up. {@code -Psnapshot=ruins-light-look}.
 */
public final class RuinsLightLookSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;

    @Override public String id() { return "ruins-light-look"; }

    @Override public String label() { return "Ruins light: the plant dark, partly lit and lit, look preset"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        long seed = Long.getLong("snapshot.ruinsLight.seed", 31L);
        RuinsGrade grade = RuinsGrade.valueOf(System.getProperty("snapshot.ruinsLight.grade", "EXTENSIVE"));
        String focus = System.getProperty("snapshot.ruinsLight.focus", "hall");
        List<SnapshotArtifact> out = new ArrayList<>();
        try (BattleSimulation sim = BattleSetup.createRuins(seed, BattleSetup.defaultManifest(), false,
                OperationTier.ESTABLISHED, RiskLevel.MEDIUM, profile(), FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                grade, RuinsOccupant.NOBODY)) {
            for (int t = 0; t < Math.round(5f / BattleSimulation.TICK_DT); t++) sim.advance(BattleSimulation.TICK_DT);
            RuinsWorks works = sim.getRuinsWorks();
            String roofed = System.getProperty("snapshot.ruinsLight.roofed");
            NightLighting night = sim.getTopology().nightLighting();
            if (roofed != null && night != null) {
                sim.getTopology().setNightLighting(new NightLighting(night.darkness(), Float.parseFloat(roofed),
                        night.lamps(), night.poweredCircuits()));
            }
            RuinsDelve delve = works.delve();
            RuinsDelve.Cell stand = delve.interior() == null ? null : delve.interior().worksStand();
            float cx, cy;
            RuinsDelve.Area entrance = delve.interior() == null || delve.interior().entrances().isEmpty() ? null
                    : delve.interior().entrances().get(0);
            RuinsDelve.Court court = null;
            for (RuinsDelve.Layer layer : delve.layers()) {
                if (court == null && layer.court() != null && delve.interior() != null
                        && delve.interior().contains(layer.index())) court = layer.court();
            }
            if (focus.equals("works") && stand != null) {
                cx = stand.x();
                cy = stand.y();
            } else if (focus.equals("entrance") && entrance != null) {
                cx = entrance.centreX();
                cy = entrance.centreY();
            } else if (focus.equals("court") && court != null) {
                cx = court.ground().centreX();
                cy = court.ground().centreY();
            } else {
                cx = works.hall().x();
                cy = works.hall().y();
            }
            if (Boolean.getBoolean("snapshot.ruinsLight.warm")) {
                // One renderer across the restart, its clock stepped, so the lamps strike and flicker as they warm up.
                BattleReviewFrameRenderer renderer = BattleReviewFrameRenderer.look(context.modRoot(), W, H, cx, cy,
                        BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).withoutRoofs();
                out.add(new SnapshotArtifact("warm-before.png", renderer.atClock(100f).render(sim, "")));
                works.graph().restart();
                sim.advance(BattleSimulation.TICK_DT);
                for (float t : new float[] {0f, 0.4f, 0.9f, 1.4f, 2.5f}) {
                    out.add(new SnapshotArtifact(String.format("warm-%.1fs.png", t),
                            renderer.atClock(100.01f + t).render(sim, "")));
                }
                return out;
            }
            out.add(frame(context, sim, cx, cy, "01-dark.png"));
            works.graph().restart();
            works.graph().throwJunction(delve.powerHall().layer(), Branch.STORE);
            sim.advance(BattleSimulation.TICK_DT);
            out.add(frame(context, sim, cx, cy, "02-partly-lit.png"));
            for (int j = 0; j < works.graph().junctionCount(); j++) works.graph().throwJunction(j, Branch.ONWARD);
            sim.advance(BattleSimulation.TICK_DT);
            out.add(frame(context, sim, cx, cy, "03-lit.png"));
        }
        return out;
    }

    private static SnapshotArtifact frame(SnapshotContext context, BattleSimulation sim, float cx, float cy,
                                          String name) throws Exception {
        return new SnapshotArtifact(name, BattleReviewFrameRenderer.look(context.modRoot(), W, H, cx, cy,
                BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).withoutRoofs().render(sim, ""));
    }

    private static TargetProfile profile() {
        return new TargetProfile(0, 0, 0, 0, "", EnumSet.noneOf(EconomicFunction.class), SurfacePalette.ARID,
                SettlementLink.NONE, 0f, 0f, false, false, false, GroundDefences.NONE);
    }
}

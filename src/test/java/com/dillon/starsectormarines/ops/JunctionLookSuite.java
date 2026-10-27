package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.JunctionPressure;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.JunctionCompound;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Open the Junction for judging the mission's difficulty: the whole map marked with every placement and trigger (the
 * site garrisons and trenches, the reserve and the Rill road column, the bonded warehouse's garrison and where it
 * sallies, the technicals' road), then the battle played under autonomous command with the evidence's two-Hermes
 * company through the look preset at the moments that decide it: the sally, the technicals on the field, and the end.
 * Opt-in; seed {@code -Dsnapshot.junction.seed} (default 9102). 1920x1080.
 */
public final class JunctionLookSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;
    /** Ticks after an event its stills wait, so the bodies it moved are on screen. */
    private static final int SETTLE = 150;
    private static final int MAX_TICKS = 12_000;

    @Override public String id() { return "junction"; }

    @Override public boolean inAll() { return false; }

    @Override public String label() { return "Open the Junction: placements and triggers marked, then the fight"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        long seed = Long.getLong("snapshot.junction.seed", 9102L);
        List<SnapshotArtifact> out = new ArrayList<>();
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            TargetProfile profile = new TargetProfile(0, 0, 0, 0, "", Set.of(), SurfacePalette.VERDANT, SettlementLink.ROAD);
            List<ShuttleAssignment> manifest = List.of(new ShuttleAssignment(ShuttleType.HERMES, 3, 4),
                    new ShuttleAssignment(ShuttleType.HERMES, 3, 4));
            var layout = JunctionCompound.generate(seed, profile);
            try (BattleSimulation sim = BattleSetup.createJunction(seed, manifest, profile)) {
                float w = JunctionCompound.WIDTH, h = JunctionCompound.HEIGHT;
                float whole = Math.max(w, h * 16f / 9f);
                BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W, FRAME_H,
                        w / 2f, h / 2f, whole);
                sim.advance(BattleSimulation.TICK_DT);
                out.add(new SnapshotArtifact("00-plan.png", look.renderMarked(sim, plan(sim, layout))));
                play(sim, look, out, w / 2f, h / 2f, whole);
            }
        }
        return out;
    }

    private static ReviewAnnotations plan(BattleSimulation sim, JunctionCompound.Layout layout) {
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        var records = new ArrayList<>(sim.getCompoundService().getRecords());
        for (var record : records) sim.getCompoundService().captureZoneId(record, sim);
        for (int i = 0; i < records.size(); i++) {
            var node = records.get(i).node;
            marks.box(node.left, node.bottom, node.right, node.top, (i + 1) + ". " + JunctionCompound.SITE_NAMES.get(i),
                    i == 0 ? ReviewStyle.LANDING : ReviewStyle.KEEP);
        }
        for (LandingPad pad : sim.getMarineLandingPads())
            marks.box(pad.left(), pad.bottom(), pad.right(), pad.top(), "Landing", ReviewStyle.LANDING);
        JunctionPressure pressure = sim.getJunctionPressure();
        for (Squad squad : sim.getSquads()) {
            if (squad.aliveMembers <= 0 || pressure.garrison().contains(squad.id)) continue;
            if (squad.faction == Faction.DEFENDER)
                marks.label(squad.centroidX, squad.centroidY, "Raiders x" + squad.aliveMembers, ReviewStyle.FRONT);
        }
        var reserve = layout.reserve();
        marks.label(reserve.interiorAnchorX + .5f, reserve.interiorAnchorY - 6f,
                "Reserve: first blow on the first gain once a second site is contested (45 s at most)", ReviewStyle.DUG_IN);
        marks.label(JunctionCompound.RILL_ROAD_X - 20f, JunctionCompound.RILL_ROAD_Y + 10f,
                "Rill column x12: second blow on the second gain (45 s after the first at most)", ReviewStyle.DUG_IN);
        var warehouse = pressure.warehouse();
        int garrison = 0;
        for (int id : pressure.garrison()) garrison += sim.squadMemberCount(id);
        marks.box(warehouse.left, warehouse.bottom, warehouse.right, warehouse.top,
                "Bonded warehouse: raiders x" + garrison + ", sprint with each blow", ReviewStyle.FRONT);
        List<ReviewAnnotation.Point> road = List.of(new ReviewAnnotation.Point(189.5f, 81.5f),
                new ReviewAnnotation.Point(172f, 64f), new ReviewAnnotation.Point(146f, 59f),
                new ReviewAnnotation.Point(110f, 59f));
        marks.polyline(road, "2 technicals follow the Rill column down the main road", ReviewStyle.DUG_IN);
        return marks.build();
    }

    private static void play(BattleSimulation sim, BattleReviewFrameRenderer look, List<SnapshotArtifact> out,
                             float cx, float cy, float whole) throws Exception {
        JunctionPressure pressure = sim.getJunctionPressure();
        int sally = -1, second = -1, technicals = -1;
        for (int tick = 0; tick < MAX_TICKS && !sim.isComplete(); tick++) {
            sim.advance(BattleSimulation.TICK_DT);
            int now = sim.getSimTickIndex();
            if (sally < 0 && pressure.sallied()) sally = now;
            if (second < 0 && pressure.secondBlowTick() >= 0) second = now;
            if (second >= 0 && now == second + SETTLE) stills(out, look, sim, "02-second-blow", cx, cy, whole, cx, cy);
            if (technicals < 0 && !pressure.technicalIds().isEmpty() && pressure.technicalIds().get(0) != 0L) technicals = now;
            if (sally >= 0 && now == sally + SETTLE) stills(out, look, sim, "01-first-blow", cx, cy, whole,
                    cx, cy);
            if (technicals >= 0 && now == technicals + 4 * SETTLE) {
                var hull = sim.convoy().body(pressure.technicalIds().get(0));
                stills(out, look, sim, "03-technicals-on-the-road", cx, cy, whole, hull.x, hull.y);
            }
        }
        stills(out, look, sim, "04-end-" + (sim.isComplete() ? String.valueOf(sim.getWinner()).toLowerCase() : "unresolved"),
                cx, cy, whole, cx, cy);
    }

    /** The whole map and a play-zoom frame on the moment's place. */
    private static void stills(List<SnapshotArtifact> out, BattleReviewFrameRenderer look, BattleSimulation sim,
                               String name, float cx, float cy, float whole, float fx, float fy) throws Exception {
        look.refocus(cx, cy, whole);
        out.add(new SnapshotArtifact(name + "-whole.png", look.render(sim, "", ReviewAnnotations.NONE)));
        look.refocus(fx, fy, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS);
        out.add(new SnapshotArtifact(name + "-close.png", look.render(sim, "", ReviewAnnotations.NONE)));
    }
}

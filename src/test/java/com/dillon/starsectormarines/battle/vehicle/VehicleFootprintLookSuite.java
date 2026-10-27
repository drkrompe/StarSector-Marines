package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Each hull's footprint against its sprite: the hulls parked in a row on look ground and drawn through the look
 * preset, with the rectangle the simulation collides, routes and is struck by ({@link VehicleType#visualLengthCells}
 * by {@link VehicleType#visualWidthCells}) outlined over each. {@code footprints.png} has the row at play zoom, and
 * {@code <type>.png} each hull close, 16 cells across. Opt-in: a look review, not part of {@code all}.
 */
public final class VehicleFootprintLookSuite implements SnapshotSuite {

    static final List<VehicleType> TYPES = List.of(VehicleType.SUPPLY_TRUCK, VehicleType.HEAVY_APC,
            VehicleType.ESCORT_APC, VehicleType.LASER_TANK, VehicleType.BREAKTHROUGH_TANK,
            VehicleType.DOZER_BREACHER, VehicleType.CARGO_CARRIER, VehicleType.HOVER_SCOUT);
    /** The window, and the spacing of hulls along it, cells. */
    static final int SITE_W = 64, SITE_H = 24;
    static final float SPACING = 7f, CLOSE_CELLS = 16f;

    @Override public String id() { return "vehicle-footprint-look"; }

    @Override public String label() { return "Each hull's collision footprint outlined over its sprite"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        LookGround ground = LookGround.verdant();
        MapResult map = ground.generate();
        LookReview.Placement placement = new LookReview.Placement(ground, map,
                LookGround.openSite(map, SITE_W, SITE_H));
        BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
        List<Long> hulls = new ArrayList<>();
        for (int i = 0; i < TYPES.size(); i++) {
            float[] at = placement.openNear(4f + i * SPACING, SITE_H / 2f);
            long id = sim.convoy().spawn(TYPES.get(i), Faction.MARINE, VehicleMission.deployed(at[0], at[1]));
            sim.convoy().body(id).teleport(at[0], at[1], 0f);
            hulls.add(id);
        }
        for (int t = 0; t < 30; t++) sim.advance(BattleSimulation.TICK_DT);
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        for (long hull : hulls) {
            GroundBody body = sim.convoy().body(hull);
            VehicleType type = sim.convoy().vehicleType(hull);
            marks.polyline(outline(body.x, body.y, body.facingDegrees, type.visualLengthCells,
                    type.visualWidthCells), null, ReviewStyle.KEEP);
            System.out.printf(Locale.ROOT, "    [vehicle-footprint-look] %s at %.1f,%.1f footprint %.2f x %.2f%n",
                    type, body.x, body.y, type.visualLengthCells, type.visualWidthCells);
        }
        ReviewAnnotations annotations = marks.build();
        List<SnapshotArtifact> out = new ArrayList<>();
        GroundBody first = sim.convoy().body(hulls.get(0));
        GroundBody last = sim.convoy().body(hulls.get(hulls.size() - 1));
        out.add(new SnapshotArtifact("footprints.png", BattleReviewFrameRenderer.look(context.modRoot(),
                LookReview.WIDTH, LookReview.HEIGHT, (first.x + last.x) / 2f, first.y,
                last.x - first.x + 2f * SPACING).renderMarked(sim, annotations)));
        for (long hull : hulls) {
            GroundBody body = sim.convoy().body(hull);
            String name = sim.convoy().vehicleType(hull).name().toLowerCase(Locale.ROOT).replace('_', '-');
            out.add(new SnapshotArtifact(name + ".png", BattleReviewFrameRenderer.look(context.modRoot(),
                    LookReview.WIDTH, LookReview.HEIGHT, body.x, body.y, CLOSE_CELLS).renderMarked(sim, annotations)));
        }
        return out;
    }

    /** The closed outline of a hull's footprint at its pose, as annotation points (each is drawn at its cell's centre). */
    static List<ReviewAnnotation.Point> outline(float x, float y, float facingDeg, float length, float width) {
        double rad = Math.toRadians(facingDeg);
        float fx = (float) -Math.sin(rad), fy = (float) Math.cos(rad), sx = fy, sy = -fx;
        float hl = length / 2f, hw = width / 2f;
        List<ReviewAnnotation.Point> points = new ArrayList<>();
        float[][] corners = {{hl, hw}, {hl, -hw}, {-hl, -hw}, {-hl, hw}, {hl, hw}};
        for (float[] c : corners) {
            points.add(new ReviewAnnotation.Point(x - .5f + fx * c[0] + sx * c[1], y - .5f + fy * c[0] + sy * c[1]));
        }
        return points;
    }
}

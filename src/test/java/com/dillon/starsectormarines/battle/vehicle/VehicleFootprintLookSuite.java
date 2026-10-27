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
 * Each hull's footprint against its sprite: every {@link VehicleType} parked in rows on look ground and drawn through
 * the look preset, each named with its footprint's size and outlined with the rectangle the simulation collides,
 * routes and is struck by ({@link VehicleType#visualLengthCells} by {@link VehicleType#visualWidthCells}).
 * {@code footprints.png} has them all, {@code row-<n>.png} each row closer, and {@code <type>.png} each hull close,
 * 16 cells across. Opt-in: a look review, not part of {@code all}.
 */
public final class VehicleFootprintLookSuite implements SnapshotSuite {

    /** Hulls a row, the spacing along a row and between rows, cells. */
    static final int PER_ROW = 6;
    static final float SPACING = 7f, ROW_SPACING = 11f, CLOSE_CELLS = 16f;

    @Override public String id() { return "vehicle-footprint-look"; }

    @Override public String label() { return "Every hull's collision footprint outlined over its sprite"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        VehicleType[] types = VehicleType.values();
        int rows = (types.length + PER_ROW - 1) / PER_ROW;
        float rowWidth = PER_ROW * SPACING, height = rows * ROW_SPACING;
        LookGround ground = LookGround.verdant();
        MapResult map = ground.generate();
        LookReview.Placement placement = new LookReview.Placement(ground, map,
                LookGround.openSite(map, (int) rowWidth + 8, (int) height + 4));
        BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
        List<Long> hulls = new ArrayList<>();
        for (int i = 0; i < types.length; i++) {
            // Top row first, west to east.
            float x = placement.site().x(4f + SPACING * (.5f + i % PER_ROW));
            float y = placement.site().y(2f + height - ROW_SPACING * (.5f + i / PER_ROW));
            long id = sim.convoy().spawn(types[i], Faction.MARINE, VehicleMission.deployed(x, y));
            sim.convoy().body(id).teleport(x, y, 0f);
            hulls.add(id);
        }
        for (int t = 0; t < 30; t++) sim.advance(BattleSimulation.TICK_DT);
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        for (long hull : hulls) {
            GroundBody body = sim.convoy().body(hull);
            VehicleType type = sim.convoy().vehicleType(hull);
            marks.polyline(outline(body.x, body.y, body.facingDegrees, type.visualLengthCells,
                    type.visualWidthCells), null, ReviewStyle.KEEP);
            marks.label(body.x - .5f, body.y - .5f - type.visualLengthCells / 2f - 1.2f,
                    String.format(Locale.ROOT, "%s %.2f x %.2f", name(type), type.visualLengthCells,
                            type.visualWidthCells), ReviewStyle.APPROACH);
            System.out.printf(Locale.ROOT, "    [vehicle-footprint-look] %s at %.1f,%.1f footprint %.2f x %.2f%n",
                    type, body.x, body.y, type.visualLengthCells, type.visualWidthCells);
        }
        ReviewAnnotations annotations = marks.build();
        List<SnapshotArtifact> out = new ArrayList<>();
        float centreX = placement.site().x(4f + rowWidth / 2f);
        out.add(new SnapshotArtifact("footprints.png", BattleReviewFrameRenderer.look(context.modRoot(),
                LookReview.WIDTH, LookReview.HEIGHT, centreX, placement.site().y(2f + height / 2f),
                Math.max(rowWidth + 4f, (height + 4f) * LookReview.WIDTH / LookReview.HEIGHT))
                .renderMarked(sim, annotations)));
        for (int row = 0; row < rows; row++) {
            float y = placement.site().y(2f + height - ROW_SPACING * (.5f + row));
            out.add(new SnapshotArtifact("row-" + (row + 1) + ".png", BattleReviewFrameRenderer.look(context.modRoot(),
                    LookReview.WIDTH, LookReview.HEIGHT, centreX, y, rowWidth + 2f).renderMarked(sim, annotations)));
        }
        for (long hull : hulls) {
            GroundBody body = sim.convoy().body(hull);
            out.add(new SnapshotArtifact(name(sim.convoy().vehicleType(hull)) + ".png",
                    BattleReviewFrameRenderer.look(context.modRoot(), LookReview.WIDTH, LookReview.HEIGHT,
                            body.x, body.y, CLOSE_CELLS).renderMarked(sim, annotations)));
        }
        return out;
    }

    private static String name(VehicleType type) {
        return type.name().toLowerCase(Locale.ROOT).replace('_', '-');
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

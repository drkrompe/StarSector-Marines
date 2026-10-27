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
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.image.BufferedImage;
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
    /** How far each hull is driven up through its slot, cells, and how long it has driven when it is shot. */
    static final float DRIVE_CELLS = 8f;
    static final int DRIVE_TICKS = 55;

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
        drive(context, sim, hulls, centreX, placement.site().y(2f + height / 2f), rowWidth, height, out);
        return out;
    }

    /**
     * Every hull backed off its slot and driven up through it over the grass, so what its running gear throws can be
     * judged against its tracks and wheels: {@code driving.png} all of them, {@code drive-<type>.png} each close, both
     * plain, and {@code gear-<type>.png} the close one with its gear lines ({@link RunningGear}) marked.
     */
    private static void drive(SnapshotContext context, BattleSimulation sim, List<Long> hulls, float centreX,
                              float centreY, float rowWidth, float height, List<SnapshotArtifact> out)
            throws Exception {
        for (long hull : hulls) {
            GroundBody body = sim.convoy().body(hull);
            float x = body.x, y = body.y;
            body.teleport(x, y - DRIVE_CELLS / 2f, 0f);
            sim.getVehicleMoveOrderService().requestMove(hull, (int) x, (int) (y + DRIVE_CELLS / 2f));
        }
        ReviewImpactFx fx = new ReviewImpactFx(context.modRoot());
        for (int t = 0; t < DRIVE_TICKS; t++) {
            sim.advance(BattleSimulation.TICK_DT);
            fx.feed(sim);
        }
        ReviewAnnotations.Builder gear = ReviewAnnotations.builder();
        for (long hull : hulls) {
            GroundBody body = sim.convoy().body(hull);
            VehicleType type = sim.convoy().vehicleType(hull);
            RunningGear g = type.runningGear();
            System.out.printf(Locale.ROOT, "    [vehicle-footprint-look] %s driving at %.2f cells/s%n", type, body.speed);
            if (!g.grounded()) continue;
            for (int s = -1; s <= 1; s += 2) {
                gear.polyline(gearLine(body, g, s), null, ReviewStyle.LANDING);
            }
        }
        ReviewAnnotations gearMarks = gear.build();
        out.add(new SnapshotArtifact("driving.png", BattleReviewFrameRenderer.look(context.modRoot(),
                LookReview.WIDTH, LookReview.HEIGHT, centreX, centreY,
                Math.max(rowWidth + 4f, (height + 4f) * LookReview.WIDTH / LookReview.HEIGHT))
                .render(sim, "", ReviewAnnotations.NONE, fx)));
        for (long hull : hulls) {
            GroundBody body = sim.convoy().body(hull);
            String name = name(sim.convoy().vehicleType(hull));
            BattleReviewFrameRenderer close = BattleReviewFrameRenderer.look(context.modRoot(), LookReview.WIDTH,
                    LookReview.HEIGHT, body.x, body.y - 1.5f, CLOSE_CELLS);
            out.add(new SnapshotArtifact("drive-" + name + ".png", close.render(sim, "", ReviewAnnotations.NONE, fx)));
            BufferedImage marked = close.renderMarked(sim, gearMarks, fx);
            out.add(new SnapshotArtifact("gear-" + name + ".png", marked));
        }
    }

    /** One side's track or wheel line, front contact to rear contact, as annotation points. */
    static List<ReviewAnnotation.Point> gearLine(GroundBody body, RunningGear gear, int side) {
        double rad = Math.toRadians(body.facingDegrees);
        float fx = (float) -Math.sin(rad), fy = (float) Math.cos(rad), sx = fy, sy = -fx;
        float ox = body.x - .5f + sx * gear.halfGaugeCells() * side, oy = body.y - .5f + sy * gear.halfGaugeCells() * side;
        return List.of(new ReviewAnnotation.Point(ox + fx * gear.frontCells(), oy + fy * gear.frontCells()),
                new ReviewAnnotation.Point(ox - fx * gear.rearCells(), oy - fy * gear.rearCells()));
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

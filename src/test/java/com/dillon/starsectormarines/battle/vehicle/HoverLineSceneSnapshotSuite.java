package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * The hover line scene's frames, from the same run that produced its verdicts: every loop at 1920x1080 and 100%
 * scale, whole map plus a full-size close view on the action. A scene's ground has no art, so open water is marked
 * cell by cell, and every hull is labelled. A handful of close frames per loop are also written as PNG stills.
 *
 * <p>One look shot on real ground, {@code skimmer-line}: Tri-Tachyon's skimmer line, light, medium and heavy
 * ({@code convoy-nouns.md}, the hover line), driving east in file toward a rifle section that fires on them, so the
 * heavy skimmer's laser answers and its frontal shield stands up toward the riflemen. Nothing is judged.
 */
public final class HoverLineSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public HoverLineSceneSnapshotSuite() {
        super(new HoverLineScene(15), 1920, 1080);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }

    /** The look shot's window, its length in ticks, and a pool deep enough that nobody falls in it. */
    static final int LOOK_W = 56, LOOK_H = 30, LOOK_TICKS = 600;
    static final float DEEP = 5000f;

    @Override protected List<LookReview.Shot> lookShots() {
        return List.of(LookReview.Shot.of("skimmer-line", LOOK_W, LOOK_H, HoverLineSceneSnapshotSuite::skimmerLine,
                placement -> List.of(LookReview.View.followAtPlay(placement, "light", "medium", "heavy"),
                        LookReview.View.follow("close-heavy", placement, 16f, "heavy"),
                        LookReview.View.follow("close-line", placement, 24f, "light", "medium", "heavy")),
                LOOK_TICKS));
    }

    /**
     * The light, medium and heavy skimmers in file at the window's west end, each ordered east past its middle, and a
     * held rifle section at its east end, in reach of them as they come.
     */
    private static BattleSimulation skimmerLine(LookReview.Placement placement) {
        float[] post = placement.openNear(LOOK_W - 12f, LOOK_H / 2f);
        BattleSimulation sim = placement.holdouts(placement.builder())
                .squad("riflemen").faction(Faction.DEFENDER).type(UnitType.MARINE).size(4)
                .at((int) post[0] - placement.site().x0(), (int) post[1] - placement.site().y0())
                .health(DEEP).stationary().done()
                .build().sim();
        String[] names = {"light", "medium", "heavy"};
        VehicleType[] types = {VehicleType.HOVER_SCOUT, VehicleType.DRONE_SKIMMER, VehicleType.HEAVY_SKIMMER};
        for (int i = 0; i < 3; i++) {
            float[] start = placement.openNear(6f + i * 5f, LOOK_H / 2f + (i - 1) * 4f);
            long hull = sim.convoy().spawn(types[i], Faction.MARINE, VehicleMission.deployed(start[0], start[1]));
            sim.convoy().body(hull).teleport(start[0], start[1], -90f);
            sim.world().setMaxHp(hull, DEEP);
            sim.world().setHp(hull, DEEP);
            placement.subject(names[i], hull);
            float[] goal = placement.openNear(LOOK_W / 2f + 4f, LOOK_H / 2f + (i - 1) * 4f);
            sim.getVehicleMoveOrderService().requestMove(hull, (int) goal[0], (int) goal[1]);
        }
        return sim;
    }

    @Override protected float[] closeView(String loopId) {
        return switch (loopId) {
            case HoverLineScene.RIVER -> new float[] {31f, (HoverLineScene.RIVER_Y0 + HoverLineScene.RIVER_Y1) / 2f, 44f};
            case HoverLineScene.SKIMMER, HoverLineScene.SKIMMER_LOSS, HoverLineScene.SKIMMER_REBUILD, HoverLineScene.SENSORS, HoverLineScene.HALO ->
                    new float[] {25f, HoverLineScene.SKIMMER_Y, 36f};
            default -> new float[] {32f, HoverLineScene.ROAD_Y, 36f};
        };
    }

    @Override protected float closeViewScale() { return 1f; }

    @Override protected ReviewAnnotations annotations(BattleSimulation simulation) {
        List<ReviewAnnotation.Cell> water = new ArrayList<>();
        List<ReviewAnnotation.Cell> wall = new ArrayList<>();
        for (int y = 0; y < HoverLineScene.HEIGHT; y++) {
            for (int x = 0; x < HoverLineScene.WIDTH; x++) {
                if (simulation.getTopology().getGroundKind(x, y) == GroundKind.WATER) {
                    water.add(new ReviewAnnotation.Cell(x, y));
                } else if (x < HoverLineScene.HOLDOUT_X0 && !simulation.getGrid().isWalkable(x, y)) {
                    wall.add(new ReviewAnnotation.Cell(x, y));
                }
            }
        }
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        if (!water.isEmpty()) marks.cells(water, "RIVER", ReviewStyle.ROUTE);
        // A scene's wall has no art: mark the one the sensor pair looks past.
        if (!wall.isEmpty()) marks.cells(wall, "WALL", ReviewStyle.NOTE);
        for (long id : simulation.getConvoyVehicleIds()) {
            GroundBody body = simulation.convoy().body(id);
            VehicleType type = simulation.convoy().vehicleType(id);
            String name = switch (type) {
                case HOVER_SCOUT -> "HOVER SCOUT";
                case DRONE_SKIMMER -> "DRONE SKIMMER";
                case HEAVY_SKIMMER -> "HEAVY SKIMMER";
                case SCOUT_CAR -> "SPOTTER PICKUP";
                default -> "APC";
            };
            marks.label(body.x, body.y - 2.6f, name, ReviewStyle.NOTE);
        }
        return marks.build();
    }

    @Override protected int[] stillFrames(String loopId) {
        if (!loopId.endsWith("-close")) return new int[0];
        return new int[] {0, 6, 8, 10, 12, 14, 15, 20, 30, 45, 60, 79, 82, 90};
    }
}

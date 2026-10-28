package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * The vehicle ram and hull-contact loops, from the same run that produced their verdicts. A scene's walls have no
 * art, so the cells they stand on are outlined; the close view is on the stretch of road where the hulls meet what
 * is in it, at the full 1920x1080. Two look shots on real ground ({@code colony-breach-nouns.md}, A standing
 * vehicle): a Runner pack striking a supply truck parked in the open from its sides, and the same pack clawing at the
 * truck parked across the only gap through a fence line, a rifle pair behind it. The truck carries no gun, so what is
 * seen is the swarm's doing.
 */
public final class VehicleRamSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public VehicleRamSceneSnapshotSuite() {
        super(new VehicleRamScene(), 1920, 1080);
    }

    /** Look shots: window, ticks, the pack's size and how far east of the truck it starts, cells. */
    static final int LOOK_W = 56, LOOK_H = 30, LOOK_TICKS = 900, PACK = 6;
    static final float PACK_OFF = 11f, DEEP = 5000f;

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }

    @Override protected List<LookReview.Shot> lookShots() {
        return List.of(
                LookReview.Shot.of("rav-strike-parked-truck", LOOK_W, LOOK_H, placement -> stage(placement, false),
                        VehicleRamSceneSnapshotSuite::views, LOOK_TICKS),
                LookReview.Shot.of("rav-claw-barricade-truck", LOOK_W, LOOK_H, placement -> stage(placement, true),
                        VehicleRamSceneSnapshotSuite::views, LOOK_TICKS),
                LookReview.Shot.of("ram-barricade-line", LOOK_W, LOOK_H, VehicleRamSceneSnapshotSuite::ramLine,
                        placement -> List.of(LookReview.View.followAtPlay(placement, "apc", "tank"),
                                LookReview.View.follow("close-apc", placement, 16f, "apc"),
                                LookReview.View.follow("close-tank", placement, 16f, "tank")), RAM_TICKS)
                        .withStills(RAM_STILLS));
    }

    /** How long the hulls drive at the barricade line, ticks. */
    static final int RAM_TICKS = 420;
    /** The recorded frames kept as stills (a frame every 6 ticks): closing, at the line, through it, beyond. */
    static final int[] RAM_STILLS = {24, 31, 37, 50};

    /**
     * A sandbag and fence line across the whole map through the middle of the window, no gap in it, and an APC and a
     * gun tank west of it ordered across ({@code BarricadeRam}). Under {@code -Dbattle.vehicle.ramBarricades=false}
     * they are held at it.
     */
    private static BattleSimulation ramLine(LookReview.Placement placement) {
        float[] at = placement.openNear(LOOK_W / 2f, LOOK_H / 2f);
        int lx = (int) at[0] - placement.site().x0(), ly = (int) at[1] - placement.site().y0();
        BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
        TileRegistry registry = TileRegistry.installed();
        int x = (int) at[0];
        for (int y = 0; y < sim.getGrid().getHeight(); y++) {
            if (!sim.getGrid().isWalkable(x, y)) continue;
            sim.getGrid().setWalkable(x, y, false);
            sim.addDoodad(new Doodad(x, y, registry.doodad(Math.abs(y - (int) at[1]) <= 6
                    ? "doodad.sandbag-straight-e" : "doodad.industrial-fence-straight-v")));
        }
        String[] names = {"apc", "tank"};
        VehicleType[] types = {VehicleType.HEAVY_APC, VehicleType.BREAKTHROUGH_TANK};
        for (int i = 0; i < 2; i++) {
            float[] start = placement.openNear(lx - 16f, ly + (i == 0 ? -3f : 3f));
            long hull = sim.convoy().spawn(types[i], Faction.MARINE, VehicleMission.deployed(start[0], start[1]));
            sim.convoy().body(hull).teleport(start[0], start[1], -90f);
            sim.world().setMaxHp(hull, DEEP);
            sim.world().setHp(hull, DEEP);
            placement.subject(names[i], hull);
            float[] goal = placement.openNear(lx + 16f, ly + (i == 0 ? -3f : 3f));
            sim.getVehicleMoveOrderService().requestMove(hull, (int) goal[0], (int) goal[1]);
        }
        return sim;
    }

    private static List<LookReview.View> views(LookReview.Placement placement) {
        return List.of(LookReview.View.followAtPlay(placement, "truck"),
                LookReview.View.follow("close", placement, 16f, "truck"));
    }

    /**
     * A supply truck parked in the middle of the window, a Runner pack east of it. As a barricade, a fence line runs
     * the whole map's height through the truck, which stands across its one gap, and a rifle pair holds west of it.
     */
    private static BattleSimulation stage(LookReview.Placement placement, boolean barricade) {
        float[] at = placement.openNear(LOOK_W / 2f, LOOK_H / 2f);
        int lx = (int) at[0] - placement.site().x0(), ly = (int) at[1] - placement.site().y0();
        SceneBuilder builder = placement.holdouts(placement.builder());
        if (barricade) {
            int mapH = placement.map().grid.getHeight();
            builder.wall(lx, -placement.site().y0(), lx, ly - 2)
                    .wall(lx, ly + 2, lx, mapH - placement.site().y0());
            float[] held = placement.openNear(lx - 14f, ly);
            builder.squad("held").faction(Faction.MARINE).type(UnitType.MARINE).size(2)
                    .at((int) held[0] - placement.site().x0(), (int) held[1] - placement.site().y0())
                    .health(DEEP).stationary().done();
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        if (barricade) {
            TileRegistry registry = TileRegistry.installed();
            for (int y = placement.site().y0(); y < placement.site().y0() + LOOK_H; y++) {
                if (Math.abs(y - (int) at[1]) >= 2) {
                    sim.addDoodad(new Doodad((int) at[0], y, registry.doodad("doodad.industrial-fence-straight-v")));
                }
            }
        }
        long truck = sim.convoy().spawn(VehicleType.SUPPLY_TRUCK, Faction.MARINE,
                VehicleMission.deployed(at[0], at[1]));
        sim.convoy().body(truck).teleport(at[0], at[1], barricade ? 0f : -90f);
        sim.world().setMaxHp(truck, DEEP);
        sim.world().setHp(truck, DEEP);
        placement.subject("truck", truck);
        for (int i = 0; i < PACK; i++) {
            float[] spot = placement.openNear(lx + PACK_OFF + (i % 3) * 2f, ly - 3f + (i / 3) * 6f);
            sim.spawn(new EntitySpec("Runner " + i, Faction.DEFENDER, UnitType.SWARM_RUNNER, (int) spot[0],
                    (int) spot[1]).role(UnitRole.SWARM_PRESSURE).health(DEEP));
        }
        return sim;
    }

    @Override
    protected ReviewAnnotations annotations(BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        List<ReviewAnnotation.Cell> walls = new ArrayList<>();
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (!grid.isWalkable(x, y)) walls.add(new ReviewAnnotation.Cell(x, y));
            }
        }
        return walls.isEmpty() ? ReviewAnnotations.NONE
                : ReviewAnnotations.builder().cells(walls, "wall", ReviewStyle.NOTE).build();
    }

    @Override
    protected float[] closeView(String loopId) {
        return new float[]{33f, 13f, 30f};
    }

    @Override
    protected float closeViewScale() {
        return 1f;
    }
}

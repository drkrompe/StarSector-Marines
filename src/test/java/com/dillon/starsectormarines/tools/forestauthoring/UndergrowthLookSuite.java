package com.dillon.starsectormarines.tools.forestauthoring;

import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestBrush;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestField;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.gen.forest.Undergrowth;
import com.dillon.starsectormarines.battle.world.gen.forest.UndergrowthBrush;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.model.RockDamage;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.tools.tilesetauthoring.UndergrowthReskinTemplate;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Forest-floor growth, drawn. Look shots on real verdant ground: a mature wood with its gaps grown over in
 * undergrowth, crossed by a breakthrough tank that flattens it; and open ground scattered with the floor plants, field
 * stones in all three states, a few undergrowth patches and a fire team for scale. Plus a catalog frame of every species in its three states and
 * four turns, and the three reskin guides.
 */
public final class UndergrowthLookSuite implements SnapshotSuite {

    private static final int SHOT_W = 64, SHOT_H = 36;
    private static final int WOOD_X0 = 16, WOOD_X1 = 48, WOOD_REACH = 40;
    /** The tank lane, window-local rows: no mature crown grows in it, only undergrowth. */
    private static final int LANE_Y0 = SHOT_H / 2 - 3, LANE_Y1 = SHOT_H / 2 + 3;
    private static final String[] PLANTS = {"nature.shrub-1", "nature.shrub-2", "nature.shrub-3",
            "nature.tuft-1", "nature.tuft-2", "nature.tuft-3"};

    public String id() { return "undergrowth-look"; }
    public String label() { return "Forest undergrowth and floor plants"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        List<SnapshotArtifact> frames = new ArrayList<>();
        for (int state = 0; state < 3; state++)
            frames.add(new SnapshotArtifact("guide-" + UndergrowthReskinTemplate.STATES[state] + ".png",
                    UndergrowthReskinTemplate.render(state)));
        frames.add(catalog(context));
        frames.addAll(LookReview.record(context.modRoot(), wood()));
        frames.addAll(LookReview.record(context.modRoot(), floor()));
        for (SurfacePalette palette : SurfacePalette.values())
            frames.addAll(LookReview.record(context.modRoot(), stones(palette)));
        return frames;
    }

    /** Every species standing, damaged and flattened, in its four quarter turns, on plain grass. */
    private static SnapshotArtifact catalog(SnapshotContext context) throws Exception {
        int w = 4 * 6 + 2, h = 3 * 4 * 5 + 2;
        NavigationGrid grid = new NavigationGrid(w * 2, h);
        CellTopology topology = new CellTopology(w * 2, h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w * 2; x++) {
            grid.setWalkableFloor(x, y); topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
        }
        List<UndergrowthBrush.Placement> placements = new ArrayList<>();
        for (Undergrowth growth : Undergrowth.values()) for (int state = 0; state < 3; state++)
            for (int turns = 0; turns < 4; turns++)
                placements.add(new UndergrowthBrush.Placement(1 + turns * 6 + (growth.ordinal() % 2) * w,
                        1 + (growth.ordinal() / 2 * 3 + state) * 5, growth, turns));
        List<Doodad> placed = new ArrayList<>();
        UndergrowthBrush.stamp(placements, grid, topology, placed, null);
        DoodadService service = new DoodadService(grid);
        placed.forEach(service::addDoodad);
        try (NavigationService navigation = new NavigationService(grid, topology, false)) {
            MapEditor editor = new MapEditor(navigation);
            editor.setDoodads(service);
            for (var p : placements) {
                int state = (p.y() - 1) / 5 % 3;
                int cx = p.x() + p.width() / 2, cy = p.y() + p.height() / 2;
                if (state == 1) editor.damageWall(cx, cy, 20);
                if (state == 2) editor.crush(cx, cy);
            }
        }
        return new SnapshotArtifact("catalog.png", new HeadlessBattleMapRenderer(context.modRoot()).render(
                new MapResult(grid, topology, 0, 0, w * 2 - 1, h - 1, List.of(), service.getDoodads()), 32, 32));
    }

    private static LookReview.Shot wood() {
        return LookReview.Shot.of("wood", SHOT_W, SHOT_H, placement -> {
            plantWood(placement);
            BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
            float[] start = placement.openNear(6f, SHOT_H / 2f);
            float[] goal = placement.openNear(SHOT_W - 5f, SHOT_H / 2f);
            long tank = sim.convoy().spawn(VehicleType.BREAKTHROUGH_TANK, Faction.MARINE,
                    VehicleMission.deployed(start[0], start[1]));
            sim.convoy().body(tank).teleport(start[0], start[1], -90f);
            placement.subject("tank", tank);
            sim.getVehicleMoveOrderService().requestMove(tank, (int) goal[0], (int) goal[1]);
            return sim;
        }, placement -> List.of(LookReview.View.play(placement.site().x(SHOT_W / 2f), placement.site().y(SHOT_H / 2f)),
                LookReview.View.follow("close", placement, 24f, "tank")), 900).withStills(0, 30, 50, 80);
    }

    private static LookReview.Shot floor() {
        return LookReview.Shot.of("floor", SHOT_W, SHOT_H, placement -> {
            MapResult map = placement.map();
            TileRegistry tiles = TileRegistry.installed();
            Random random = new Random(5L);
            List<UndergrowthBrush.Placement> patches = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                Undergrowth kind = Undergrowth.values()[i % Undergrowth.values().length];
                var p = new UndergrowthBrush.Placement(placement.site().x0() + 6 + random.nextInt(SHOT_W - 12),
                        placement.site().y0() + 3 + random.nextInt(SHOT_H - 8), kind, random.nextInt(4));
                if (UndergrowthBrush.fits(p, map.grid, map.topology, null)) patches.add(p);
            }
            UndergrowthBrush.stamp(patches, map.grid, map.topology, map.doodads, null);
            for (int y = placement.site().y0(); y < placement.site().y0() + SHOT_H; y++)
                for (int x = placement.site().x0(); x < placement.site().x0() + SHOT_W; x++) {
                    if (random.nextInt(9) != 0 || !map.grid.isWalkable(x, y) || map.topology.isFixture(x, y)
                            || map.topology.getGroundKind(x, y) != CellTopology.GroundKind.GRASS
                            || map.topology.getNatureOverlayIndex(x, y) >= 0) continue;
                    map.topology.setNatureOverlayIndex(x, y, tiles.tile(PLANTS[random.nextInt(PLANTS.length)]).index);
                }
            scatterStones(placement, random);
            var builder = placement.holdouts(placement.builder());
            for (int i = 0; i < 4; i++) {
                float[] at = placement.openNear(SHOT_W / 2f - 3 + i * 2, SHOT_H / 2f + (i % 2));
                builder.unit("marine-" + i, Faction.MARINE, UnitType.MARINE,
                        (int) at[0] - placement.site().x0(), (int) at[1] - placement.site().y0(), spec -> spec.moveSpeed(0f));
            }
            return builder.build().sim();
        }, placement -> List.of(LookReview.View.play(placement.site().x(SHOT_W / 2f), placement.site().y(SHOT_H / 2f)),
                LookReview.View.close("close", placement.site().x(SHOT_W / 2f), placement.site().y(SHOT_H / 2f), 24f)),
                12).withStills(1);
    }

    /** A biome's own generated ground with its field-stone set, a fire team for scale. */
    private static LookReview.Shot stones(SurfacePalette palette) {
        return LookReview.Shot.of("stones-" + palette.name().toLowerCase(Locale.ROOT), SHOT_W, SHOT_H, placement -> {
            scatterStones(placement, new Random(11L));
            var builder = placement.holdouts(placement.builder());
            for (int i = 0; i < 4; i++) {
                float[] at = placement.openNear(SHOT_W / 2f - 3 + i * 2, SHOT_H / 2f + (i % 2));
                builder.unit("marine-" + i, Faction.MARINE, UnitType.MARINE,
                        (int) at[0] - placement.site().x0(), (int) at[1] - placement.site().y0(), spec -> spec.moveSpeed(0f));
            }
            return builder.build().sim();
        }, placement -> List.of(LookReview.View.play(placement.site().x(SHOT_W / 2f), placement.site().y(SHOT_H / 2f)),
                LookReview.View.close("close", placement.site().x(SHOT_W / 2f), placement.site().y(SHOT_H / 2f), 24f)),
                12).withStills(1).on(LookGround.of(palette, palette == SurfacePalette.FROZEN ? 3L : 7L));
    }

    /**
     * Field stones of the ground's own set on its open wild ground, a third already cracked or shattered, so every
     * state shows in the frame.
     */
    private static void scatterStones(LookReview.Placement placement, Random random) {
        MapResult map = placement.map();
        TileRegistry tiles = TileRegistry.installed();
        SurfacePalette palette = placement.ground().surface();
        RockDamage rocks = new RockDamage(map.grid, map.doodads);
        for (int y = placement.site().y0() + 2; y < placement.site().y0() + SHOT_H - 2; y++)
            for (int x = placement.site().x0() + 2; x < placement.site().x0() + SHOT_W - 2; x++) {
                if (random.nextInt(28) != 0 || !map.grid.isWalkable(x, y) || map.topology.isFixture(x, y)
                        || map.topology.getGroundKind(x, y) != palette.inlandGround()
                        || map.topology.getNatureOverlayIndex(x, y) >= 0) continue;
                String group = RockDamage.GROUPS.get(random.nextInt(RockDamage.GROUPS.size()));
                map.topology.setNatureOverlayIndex(x, y, tiles.tile(palette.stones().stone(group)).index);
                int hit = random.nextInt(6);
                if (hit < 2) rocks.damage(x, y, hit == 0 ? 10 : 1000, map.topology, tiles);
            }
    }

    /**
     * Mature trees across the window between {@link #WOOD_X0} and {@link #WOOD_X1}, and far beyond its top and bottom
     * so the tank cannot simply drive round, except along a lane through the middle as wide as a tank needs; then the
     * lane and the gaps between the crowns grown over in a seeded mix of undergrowth, leaving some floor open.
     */
    private static void plantWood(LookReview.Placement placement) {
        MapResult map = placement.map();
        Random random = new Random(29L);
        TreeCanopy[] mature = {TreeCanopy.OAK, TreeCanopy.BIRCH, TreeCanopy.PINE_LARGE, TreeCanopy.CEDAR};
        List<int[]> spots = new ArrayList<>();
        for (int y = -WOOD_REACH; y < SHOT_H + WOOD_REACH; y += 2)
            for (int x = WOOD_X0; x < WOOD_X1; x += 2) spots.add(new int[] {x, y});
        Collections.shuffle(spots, random);
        for (int[] spot : spots) {
            TreeCanopy canopy = mature[random.nextInt(mature.length)];
            int turns = random.nextInt(4);
            if (spot[1] < LANE_Y1 && spot[1] + canopy.height(turns) > LANE_Y0) continue;
            var tree = new ForestField.Tree(placement.site().x0() + spot[0], placement.site().y0() + spot[1],
                    canopy, turns);
            try {
                ForestBrush.stampTrees(List.of(tree), map.grid, map.topology, map.doodads, null, false);
            } catch (IllegalArgumentException occupied) {
                // A road, a building, the map edge or a tree already there: the next spot is tried.
            }
        }
        Undergrowth[] kinds = Undergrowth.values();
        for (int pass = 0; pass < 3; pass++) for (int[] spot : spots) {
            if (random.nextInt(3) == 0) continue;
            Undergrowth kind = kinds[random.nextInt(kinds.length)];
            var p = new UndergrowthBrush.Placement(placement.site().x0() + spot[0], placement.site().y0() + spot[1],
                    kind, random.nextInt(4));
            if (UndergrowthBrush.fits(p, map.grid, map.topology, null))
                UndergrowthBrush.stamp(List.of(p), map.grid, map.topology, map.doodads, null);
        }
    }
}

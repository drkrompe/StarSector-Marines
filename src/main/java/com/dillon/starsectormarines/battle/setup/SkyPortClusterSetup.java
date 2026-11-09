package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.GroundMarineArrival;
import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.VoidBackdrop;
import com.dillon.starsectormarines.i18n.Strings;

import java.util.List;

/**
 * A disposable diagnostic battle on a sky port tower cluster (`voids.md`): three tower roofs over the city drop,
 * every cell between them void, joined by walkways of different widths, with lower towers standing in the drop all
 * round. Tower A, where the company lands, reaches tower B by a two-cell catwalk and tower C by a catwalk that turns
 * a corner over the drop; B and C are joined by a six-cell skybridge a vehicle could cross. Each roof carries a
 * landing pad or blocks of plant. No enemy and no end: the Diagnostics tab of the debug missions lists it, and
 * {@code gradlew playBattle -Pbattle=sky-port} opens it straight away.
 *
 * <p>The look suite and the {@code walkway-crossing} behaviour scene lay the same ground through {@link #lay}.
 */
public final class SkyPortClusterSetup {

    public static final long SEED = 11L;
    public static final int W = 240, H = 170;
    /** The towers' roofs, inclusive: {@code {x0, y0, x1, y1}}. */
    public static final int[] A = {30, 60, 80, 105}, B = {120, 95, 165, 145}, C = {115, 10, 160, 50};
    /** The walkways, inclusive: A to B, B to C, and the dogleg from A to C in its two legs. */
    public static final int[] CATWALK = {81, 99, 119, 100}, SKYBRIDGE = {136, 51, 141, 94};
    public static final int[][] DOGLEG = {{70, 30, 71, 59}, {72, 30, 114, 31}};
    private static final int[][] WALKWAYS = {CATWALK, SKYBRIDGE, DOGLEG[0], DOGLEG[1]};
    /**
     * A lone roof in the far corner, reached by no walkway and out of sight of the cluster: where a scene keeps
     * the other side, which a battle needs on the map to run.
     */
    public static final int[] OUTPOST = {231, 161, 238, 168};
    /** The landing pads, inclusive. */
    private static final int[][] PADS = {{54, 70, 74, 90}, {138, 26, 154, 44}};
    /** Where the company lands: tower A, beside its pad. */
    private static final int ARRIVAL_X = 62, ARRIVAL_Y = 96;
    /** How far below the roofs the city lies, in metres. */
    public static final float DEPTH = 400f;

    /** Vanilla hulls flying the bays and skyways; the last three, the freighters, stand parked on the terraces. */
    public static final List<String> CRAFT = List.of(
            "graphics/ships/aeroshuttle/aeroshuttle_base.png", "graphics/ships/hermes/hermes_base.png",
            "graphics/ships/hound/hound_base.png", "graphics/ships/wayfarer/wayfarer.png",
            "graphics/ships/buffalo/buffalo_base.png", "graphics/ships/tarsus/tarsus_base.png",
            "graphics/ships/mule/mule_base.png");

    /** A map's grid and topology, laid and not yet a battle. */
    public record Ground(NavigationGrid grid, CellTopology topology) { }

    private SkyPortClusterSetup() { }

    public static BattleSimulation create(long seed) {
        Ground ground = lay(true);
        BattleSimulation sim = new BattleSimulation(ground.grid(), ground.topology(), seed);
        sim.setSurface(SurfacePalette.ARID);
        sim.setGroundMarineArrival(new GroundMarineArrival(ARRIVAL_X, ARRIVAL_Y,
                SquadCommandClaim.mission("sky-port", "Company walks the towers")));
        sim.playerCommandsCompany();
        sim.addObjective(new WalkTheTowers());
        return sim;
    }

    /**
     * The industrial hive sky port seen from its roofs: slender towers stepping out only a little as they fall, so
     * the drop between them stays open, and lower towers standing round them in it. {@code depth} scales the
     * terraces and neighbours with it; {@code eye} is the camera's height as a multiple of the view's width.
     */
    public static VoidBackdrop backdrop(float depth, float eye) {
        float f = depth / 400f;
        return industrial(depth, eye, List.of(new VoidBackdrop.Tier(50f * f, 4f),
                new VoidBackdrop.Tier(120f * f, 10f), new VoidBackdrop.Tier(210f * f, 18f),
                new VoidBackdrop.Tier(300f * f, 28f)), List.of(
                new VoidBackdrop.Tower(180, 40, 215, 80, 50f * f), new VoidBackdrop.Tower(88, 115, 108, 160, 45f * f),
                new VoidBackdrop.Tower(0, 115, 22, 150, 90f * f), new VoidBackdrop.Tower(185, 110, 230, 160, 140f * f),
                new VoidBackdrop.Tower(85, 0, 105, 22, 65f * f), new VoidBackdrop.Tower(0, 10, 25, 45, 55f * f),
                new VoidBackdrop.Tower(175, 0, 205, 25, 180f * f), new VoidBackdrop.Tower(40, 125, 70, 160, 55f * f),
                new VoidBackdrop.Tower(-40, 60, -5, 100, 120f * f), new VoidBackdrop.Tower(245, 70, 280, 105, 70f * f)));
    }

    /** The industrial hive city, facade and terrace roof, with the given terraces and neighbouring towers. */
    public static VoidBackdrop industrial(float depth, float eye, List<VoidBackdrop.Tier> tiers,
                                         List<VoidBackdrop.Tower> neighbours) {
        return new VoidBackdrop("graphics/battle/voids/industrial-city-floor.png", depth, Math.max(96f, depth),
                .42f, .41f, .40f,
                "graphics/battle/voids/industrial-facade.png", 32f,
                "graphics/battle/voids/industrial-terrace-roof.png", 40f, tiers, eye, CRAFT, .2f, neighbours);
    }

    /**
     * The cluster's ground with no one on it, under {@link #backdrop} at {@link #DEPTH}; {@code catwalk} false
     * leaves the drop where the catwalk from A to B would be, so A reaches B only round by C.
     */
    public static Ground lay(boolean catwalk) {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                String finish = !catwalk && inside(x, y, CATWALK) ? null : finish(x, y);
                if (finish != null) {
                    grid.setWalkableFloor(x, y);
                    topology.setGroundKind(x, y, GroundKind.STRIPED);
                    topology.setSurface(x, y, topology.surfaceIndex(finish));
                } else {
                    grid.setWalkable(x, y, false);
                    grid.setTag(x, y, CellTag.SEE_THROUGH, true);
                    grid.setTag(x, y, CellTag.NO_EDGE_COVER, true);
                    grid.setTag(x, y, CellTag.NATURAL_SOLID, true);
                    topology.setGroundKind(x, y, GroundKind.VOID);
                }
            }
        }
        block(grid, topology, 36, 66, 48, 78);
        block(grid, topology, 38, 88, 46, 98);
        block(grid, topology, 128, 118, 144, 134);
        block(grid, topology, 148, 100, 160, 110);
        block(grid, topology, 150, 126, 158, 138);
        block(grid, topology, 121, 15, 133, 27);
        topology.setVoidBackdrop(backdrop(DEPTH, 1f));
        return new Ground(grid, topology);
    }

    /** What a cell is laid with, or null where it is void. */
    private static String finish(int x, int y) {
        for (int[] w : WALKWAYS) if (inside(x, y, w)) return "ship-bay.grate-run";
        for (int[] t : new int[][]{A, B, C, OUTPOST}) {
            if (!inside(x, y, t)) continue;
            for (int[] pad : PADS) {
                if (!inside(x, y, pad)) continue;
                boolean rim = x == pad[0] || x == pad[2] || y == pad[1] || y == pad[3];
                return rim ? "apron.keep-clear" : "sky-port.pad-plate";
            }
            return "sky-port.deck-plate";
        }
        return null;
    }

    /** A walled block of roof plant: closed walls with one door. */
    private static void block(NavigationGrid grid, CellTopology topology, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                topology.setGroundKind(x, y, GroundKind.INDOOR);
                topology.setSurface(x, y, topology.surfaceIndex("loading-bay.staging-deck"));
                if (x != x0 && x != x1 && y != y0 && y != y1) continue;
                grid.setWalkable(x, y, false);
                grid.setWallHp(x, y, 400);
                topology.setTag(x, y, CellTopology.Tag.WALL, true);
                topology.setSurface(x, y, topology.surfaceIndex("installation.dark-concrete-wall"));
            }
        }
        int doorX = (x0 + x1) / 2;
        grid.setWalkableFloor(doorX, y0);
        grid.setDoorway(doorX, y0, true);
        topology.setTag(doorX, y0, CellTopology.Tag.WALL, false);
        topology.setSurface(doorX, y0, topology.surfaceIndex("loading-bay.staging-deck"));
    }

    /** Whether a cell lies inside an inclusive rectangle {@code {x0, y0, x1, y1}}. */
    public static boolean inside(int x, int y, int[] r) {
        return x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3];
    }

    /** The diagnostic's standing marker: no win, no loss. */
    private static final class WalkTheTowers implements Objective {
        @Override public Faction owningFaction() { return Faction.MARINE; }
        @Override public void tick(BattleView sim) { }
        @Override public boolean isComplete() { return false; }
        @Override public boolean isFailed() { return false; }
        @Override public String displayName() { return Strings.get("skyPortObjective"); }
    }
}

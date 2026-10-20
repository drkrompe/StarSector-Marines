package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.model.FrontDepth;
import com.dillon.starsectormarines.battle.world.gen.bsp.FortStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.TacticalRegionStage;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A Conquest trial ground: the smallest map a commander question needs, authored the way the campaign maps are and
 * populated by the production Conquest mission layer. One landing at the south edge, one battery fort, one objective
 * compound on the lane north of it, and behind that the one keep Conquest's victory law requires.
 *
 * <p>The fort stands either on the lane, between the landing and the objective, or {@link Placement#OFFSET} to the east
 * of it, inside sighting range of the lane but off the line of advance, which asks whether the marine commander engages
 * a target that is not in its way. Everything else is open ground, so the fort and the compounds are the only cover and
 * the only obstacles a route has to answer.
 */
public final class ConquestTrialGround {

    public static final int WIDTH = 240;
    public static final int HEIGHT = 300;
    /** The lane the landing, the objective and the keep all stand on. */
    public static final int LANE_X = 120;
    public static final int LANDING_Y = 18;
    public static final int FORT_Y = 120;
    /** How far east of the lane an offset fort's centre stands. Its works then sit clear of the lane. */
    public static final int DEFAULT_OFFSET = 75;

    /** Where the fort stands relative to the lane. */
    public enum Placement { ON_PATH, OFFSET }

    /**
     * Which way the fort's main gate turns, by the point it looks at. {@code LANDING} (the default) faces the landing, so the lane
     * squads meet the gate and its belt first; the cardinal facings turn the works so the lane meets another face of them.
     */
    public enum Facing {
        LANDING(LANE_X, LANDING_Y), NORTH(LANE_X, HEIGHT - 1), EAST(WIDTH - 1, FORT_Y), WEST(0, FORT_Y);

        final int x;
        final int y;

        Facing(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    /** The finished map, the fort as placed, and the two defender compounds. */
    public record Ground(MapResult map, FortStage.Placed fort, TacticalNode objective, TacticalNode keep) {}

    private ConquestTrialGround() {}

    /**
     * @param offset cells east of the lane for an {@link Placement#OFFSET} fort; ignored on the path
     * @param objectiveGarrison squad slots in the objective compound
     * @param keepGarrison squad slots in the keep
     */
    public static Ground generate(long seed, Placement placement, int offset, int objectiveGarrison, int keepGarrison) {
        return generate(seed, placement, offset, objectiveGarrison, keepGarrison, false);
    }

    /**
     * As above, the fort optionally carrying its anti-air yard.
     *
     * @param airDefence whether the fort is stamped with the optional anti-air aspect
     */
    public static Ground generate(long seed, Placement placement, int offset, int objectiveGarrison, int keepGarrison,
                                  boolean airDefence) {
        return generate(seed, placement, offset, objectiveGarrison, keepGarrison, airDefence, Facing.LANDING);
    }

    /** As above, with the main gate turned toward {@code facing}. */
    public static Ground generate(long seed, Placement placement, int offset, int objectiveGarrison, int keepGarrison,
                                  boolean airDefence, Facing facing) {
        GenContext c = new GenContext(new NavigationGrid(WIDTH, HEIGHT), new CellTopology(WIDTH, HEIGHT),
                new Random(seed), WIDTH, HEIGHT, seed);
        c.put(BspKeys.MARKET_PROFILE, TargetProfile.NEUTRAL);
        c.put(BspKeys.AXIS, TraversalAxis.SOUTH_TO_NORTH);
        int fortX = placement == Placement.ON_PATH ? LANE_X : LANE_X + offset;
        FortStage forts = FortStage.pinned(List.of(
                new FortStage.Pin(FortKind.BATTERY_FORT, fortX, FORT_Y, facing.x, facing.y, airDefence)));
        new GenRecipe("Conquest trial ground", List.of(
                ConquestTrialGround::open,
                ConquestTrialGround::buildings,
                new FinalizeStage(),
                new TacticalRegionStage(),
                forts)).run(c);
        if (forts.placed().size() != 1) throw new IllegalStateException("the trial fort did not stand");

        PointOfInterest objectiveRoom = c.pois.get(0);
        PointOfInterest keepRoom = c.pois.get(1);
        TacticalNode objective = compound(TacticalNode.Kind.ARMORY, objectiveRoom, 60, objectiveGarrison);
        TacticalNode keep = compound(TacticalNode.Kind.COMMAND_POST, keepRoom, 80, keepGarrison);
        // The compounds' sandbagged doors face the landing, as a generated base's do.
        CompoundPreparedGround.layForSites(c, List.of(objective, keep), LANE_X, LANDING_Y);
        List<TacticalNode> sites = new ArrayList<>();
        sites.add(new TacticalNode(TacticalNode.Kind.BEACHHEAD, LANE_X, LANDING_Y,
                LANE_X - 20, LANDING_Y - 10, LANE_X + 20, LANDING_Y + 12, Faction.MARINE, 20, 0));
        sites.add(objective);
        sites.add(keep);
        // The fort's own nodes: its token picket, or every belt nest under the static control.
        sites.addAll(c.tactical);

        int[][] claim = new int[WIDTH][HEIGHT];
        for (int x = keepRoom.left; x <= keepRoom.right; x++) {
            for (int y = keepRoom.top; y <= keepRoom.bottom; y++) claim[x][y] = 1;
        }
        FrontDepth front = FrontDepth.fromObjective(claim, 1, WIDTH, HEIGHT);
        MapResult map = new MapResult(c.grid, c.topology, LANE_X, LANDING_Y, keep.anchorX, keep.anchorY,
                c.pois, c.doodads, new TacticalMap(sites), c.get(BspKeys.BUILDINGS), c.defensePosts,
                RoadGraph.EMPTY, c.landingPads, List.of(), null, List.of(), List.of(), List.of(), List.of(), null,
                front, List.of());
        for (TacticalNode site : sites) requireRoute(map, site.anchorX, site.anchorY);
        return new Ground(map, forts.placed().get(0), objective, keep);
    }

    private static TacticalNode compound(TacticalNode.Kind kind, PointOfInterest room, int priority, int garrison) {
        return new TacticalNode(kind, room.interiorAnchorX, room.interiorAnchorY,
                room.left, room.top, room.right, room.bottom, Faction.DEFENDER, priority, garrison);
    }

    private static void requireRoute(MapResult map, int x, int y) {
        if (Paths.isEmpty(GridPathfinder.findPath(map.grid, LANE_X, LANDING_Y, x, y))) {
            throw new IllegalStateException("trial ground disconnected at " + x + "," + y);
        }
    }

    private static void open(GenContext c) {
        for (int y = 0; y < c.height; y++) {
            for (int x = 0; x < c.width; x++) {
                c.grid.setWalkableFloor(x, y);
                c.topology.setGroundKind(x, y, GroundKind.DIRT);
            }
        }
    }

    /** The objective on the lane north of the fort, and the keep behind it, both facing the landing. */
    private static void buildings(GenContext c) {
        BuildingStamp.T_WORKSHOP.place(c, LANE_X - 6, 200, 0);
        BuildingStamp.OFFSET_DEPOT.place(c, LANE_X - 5, 270, 0);
    }
}

package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.CompoundPreparedGround;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Random;

/**
 * The colony's holdouts: buildings away from the shelter where a few of the
 * people the Marines came for are holed up behind a militia fireteam
 * ({@code colony-breach-nouns.md}, Redoubt).
 *
 * <p>A holdout is built from what exists. {@link ColonyMilitia} chooses the
 * building and seats its garrison on the building's held place; this class
 * puts the holdout's own people inside, deeper than the militia's firing
 * cells, sandbags the doors with the compound prepared-ground aprons a held
 * base uses, and registers the holdout with the evacuation so its people join
 * the cohort and wait behind their own door.
 */
public final class ColonyRedoubts {

    /** Fewest holdouts beside the main redoubt: a colony has two to four redoubts in all. */
    public static final int MIN_HOLDOUTS = 1;
    /** Most holdouts beside the main redoubt. */
    public static final int MAX_HOLDOUTS = 3;
    /** Fewest of the cohort a holdout holds. */
    public static final int MIN_COHORT = 3;
    /** Most of the cohort a holdout holds. */
    public static final int MAX_COHORT = 4;
    /** Aprons laid at a holdout: the door toward the lift, and one more if the building has it. */
    static final int APRONS = 2;
    private static final long SEED_SALT = 0x524544554F425453L;
    private static final UnitType[] TRADES = {
            UnitType.CIVILIAN, UnitType.CIVILIAN, UnitType.ENGINEER, UnitType.CIVILIAN, UnitType.SCIENTIST};

    private ColonyRedoubts() {}

    /**
     * Whether the colony's outlying posts are redoubts with people in them.
     * {@code -Dbattle.colonyBreach.redoubts=false} is the control: the posts
     * hold militia only and the cohort is all in the shelter.
     */
    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty("battle.colonyBreach.redoubts", "true"));
    }

    /** How many holdouts this seed's colony has beside the main redoubt. */
    public static int holdoutsFor(long seed) {
        return MIN_HOLDOUTS + new Random(seed ^ SEED_SALT).nextInt(MAX_HOLDOUTS - MIN_HOLDOUTS + 1);
    }

    /**
     * How many of a map's places could be a holdout: a held place at the
     * place's interior anchor with room indoors for a fireteam and the most
     * people a holdout holds. A map question, asked before anything spawns.
     */
    public static int seatableSites(MapResult map) {
        if (map.tacticalMap == null || map.grid == null || map.topology == null) return 0;
        Set<Long> held = new HashSet<>();
        for (TacticalNode node : map.tacticalMap.all()) held.add(((long) node.anchorX << 32) | (node.anchorY & 0xFFFFFFFFL));
        int seats = ColonyMilitia.POST_SIZE + MAX_COHORT;
        int count = 0;
        for (PointOfInterest place : map.pointsOfInterest) {
            if (place == null || place.kind == PointOfInterest.Kind.LANDING_SITE) continue;
            if (!held.contains(((long) place.interiorAnchorX << 32) | (place.interiorAnchorY & 0xFFFFFFFFL))) continue;
            int building = map.topology.getBuildingId(place.interiorAnchorX, place.interiorAnchorY);
            int open = 0;
            for (int y = place.top + 1; y < place.bottom && open < seats; y++) {
                for (int x = place.left + 1; x < place.right && open < seats; x++) {
                    if (!map.grid.inBounds(x, y) || !map.grid.isWalkable(x, y)) continue;
                    if (building > 0 && map.topology.getBuildingId(x, y) != building) continue;
                    open++;
                }
            }
            if (open >= seats) count++;
        }
        return count;
    }

    /**
     * Makes a held building a holdout: its people inside, its doors
     * sandbagged, and the holdout registered with the evacuation.
     *
     * @param cells the building's open interior cells the garrison did not take
     * @return the holdout's redoubt number, or {@code -1} when it cannot be seated
     */
    static int fortify(BattleSimulation sim, PointOfInterest place, TacticalNode node, List<int[]> cells,
                       int garrisonSquadId, CivilianEvacuationPlacement placement, long seed) {
        Random rng = new Random(seed ^ SEED_SALT);
        int count = MIN_COHORT + rng.nextInt(MAX_COHORT - MIN_COHORT + 1);
        if (cells.size() < count) return -1;
        int number = sim.getCivilianRedoubts().size() + 1;
        List<Long> members = new ArrayList<>();
        // The garrison took the best-covered cells; the people go deepest in, the last of the list.
        for (int i = 0; i < count; i++) {
            int[] cell = cells.get(cells.size() - 1 - i);
            members.add(sim.spawn(new EntitySpec("Holdout " + number + "-" + (i + 1), Faction.CIVILIAN,
                    TRADES[rng.nextInt(TRADES.length)], cell[0], cell[1]).role(UnitRole.VIP)));
        }
        layAprons(sim, node, place, placement);
        return sim.addCivilianHoldout(place.anchorCellX, place.anchorCellY,
                place.left, place.top, place.right, place.bottom, garrisonSquadId, members);
    }

    /**
     * Sandbags the holdout's doors: first the one facing the lift, the way its
     * relief comes and its people leave, then the best other. A course is laid
     * on the live map as a generated base's is laid on its map: the cells stay
     * walkable and the props give the cover. Nothing is laid without the art.
     */
    private static void layAprons(BattleSimulation sim, TacticalNode node, PointOfInterest place,
                                  CivilianEvacuationPlacement placement) {
        TileRegistry registry = TileRegistry.installed();
        if (!CompoundPreparedGround.ENABLED || registry == null) return;
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        CompoundPreparedGround.KeepClear keepClear = sim::isInsideRescueOpeningProtectedZone;
        int dx = placement.liftX - place.centerX(), dy = placement.liftY - place.centerY();
        int towardX = Math.abs(dx) >= Math.abs(dy) ? Integer.signum(dx) : 0;
        int towardY = Math.abs(dx) >= Math.abs(dy) ? 0 : Integer.signum(dy);
        if (towardX == 0 && towardY == 0) towardX = 1;
        int laid = 0;
        int[][] facings = {{towardX, towardY}, {-towardX, -towardY}, {towardY, towardX}, {-towardY, -towardX}};
        List<CompoundPreparedGround.Entrance> used = new ArrayList<>();
        for (int[] facing : facings) {
            if (laid >= APRONS) break;
            for (CompoundPreparedGround.Entrance door
                    : CompoundPreparedGround.candidates(grid, topology, node, facing[0], facing[1])) {
                if (used.contains(door)) continue;
                CompoundPreparedGround.Apron apron = CompoundPreparedGround.plan(grid, topology, door, keepClear);
                if (apron == null) continue;
                used.add(door);
                lay(sim, registry, apron);
                laid++;
                break;
            }
        }
    }

    private static void lay(BattleSimulation sim, TileRegistry registry, CompoundPreparedGround.Apron apron) {
        for (CompoundPreparedGround.Bag bag : apron.bags()) {
            DoodadDef def = registry.doodad("doodad.fort-sandbag-" + CompoundPreparedGround.artName(bag));
            if (def == null || dressed(sim, bag.x(), bag.y())) continue;
            sim.getGrid().setSeeThrough(bag.x(), bag.y(), true);
            sim.getTopology().setFixture(bag.x(), bag.y(), true);
            sim.addDoodad(new Doodad(bag.x(), bag.y(), def));
        }
    }

    /** Whether a prop already stands on the cell; a course steps round dressing rather than stacking on it. */
    private static boolean dressed(BattleSimulation sim, int x, int y) {
        for (Doodad d : sim.getDoodads()) {
            if (x >= d.cellX && x < d.cellX + d.footprintCellsX
                    && y >= d.cellY && y < d.cellY + d.footprintCellsY) return true;
        }
        return false;
    }
}

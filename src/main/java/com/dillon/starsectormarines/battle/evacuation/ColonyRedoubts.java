package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
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
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.Comparator;
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
 * building and seats its house team on the building's held place; this class
 * puts the holdout's own people inside, deeper than the militia's firing
 * cells, throws the {@link RedoubtWorks} up round the block (barricade, firing
 * step, gate and gun) with a second militia team on the step, and registers
 * the holdout with the evacuation so its people join the cohort and wait
 * behind their own door. Where the works cannot be laid it falls back to
 * sandbagging the doors with the compound prepared-ground aprons a held base
 * uses.
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
    /**
     * Cells a holdout stands from every bore site the breach plans (`colony-breach-nouns.md`, Holdout): the first wave
     * keeps the outskirts, and the holdouts are chosen in relation to it, so none is sat on by a hole at its door.
     * Tunes how soon a holdout comes under the swarm; under a side objective's ring, since a holdout has walls and a
     * garrison.
     */
    public static final int BREACH_CLEARANCE = 56;
    /** Gun pits in a light holdout's corners: with the gun at its gate, two guns. */
    public static final int LIGHT_CORNER_GUNS = 1;
    /** Gun pits in a heavy holdout's corners: with the gun at its gate, three guns. */
    public static final int HEAVY_CORNER_GUNS = 2;
    /** Aprons laid at a holdout: the door toward the lift, and one more if the building has it. */
    static final int APRONS = 2;
    private static final long SEED_SALT = 0x524544554F425453L;
    private static final UnitType[] TRADES = {
            UnitType.CIVILIAN, UnitType.CIVILIAN, UnitType.ENGINEER, UnitType.CIVILIAN, UnitType.SCIENTIST};

    private ColonyRedoubts() {}

    /**
     * A holdout as set up: its redoubt number, its works (or {@code null} when
     * only aprons were laid), the gun at its gate (or {@code 0}), the team
     * on its firing step (or {@code -1}) and the guns in its corners.
     */
    record Holdout(int number, RedoubtWorks.Plan works, long gun, int lineSquadId, List<Long> cornerGuns) { }

    /**
     * Which of the holdouts ({@code centres}, in redoubt order) is the heavy one: the one nearest a bore the breach
     * plans, the one the first wave comes at soonest. {@code -1} with no holdout; the first with no bores.
     */
    static int heavyHoldout(List<int[]> centres, List<ColonyEruptionPlan.Site> bores) {
        int heavy = centres.isEmpty() ? -1 : 0;
        long nearest = Long.MAX_VALUE;
        for (int i = 0; i < centres.size(); i++) {
            for (ColonyEruptionPlan.Site site : bores) {
                long dx = site.x() - centres.get(i)[0], dy = site.y() - centres.get(i)[1];
                if (dx * dx + dy * dy < nearest) {
                    nearest = dx * dx + dy * dy;
                    heavy = i;
                }
            }
        }
        return heavy;
    }

    /**
     * Whether a holdout throws up works round its block.
     * {@code -Dbattle.colonyBreach.redoubtWorks=false} is the control: the
     * holdout is the bare building with sandbag aprons at its doors.
     */
    public static boolean worksEnabled() {
        return Boolean.parseBoolean(System.getProperty("battle.colonyBreach.redoubtWorks", "true"));
    }

    /**
     * Whether the colony's outlying posts are redoubts with people in them.
     * {@code -Dbattle.colonyBreach.redoubts=false} is the control: the posts
     * hold militia only and the cohort is all in the shelter.
     */
    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty("battle.colonyBreach.redoubts", "true"));
    }

    /** The middle of every holdout: each redoubt but the shelter. */
    public static List<int[]> holdoutCentres(List<CivilianRedoubt> redoubts) {
        List<int[]> out = new ArrayList<>();
        for (CivilianRedoubt r : redoubts) {
            if (!r.isMain()) out.add(new int[]{(r.left() + r.right()) / 2, (r.top() + r.bottom()) / 2});
        }
        return out;
    }

    /** Whether a holdout at ({@code x}, {@code y}) stands {@link #BREACH_CLEARANCE} from every one of {@code sites}. */
    public static boolean clearOfBores(int x, int y, List<ColonyEruptionPlan.Site> sites) {
        for (ColonyEruptionPlan.Site site : sites) {
            int dx = site.x() - x, dy = site.y() - y;
            if (dx * dx + dy * dy < BREACH_CLEARANCE * BREACH_CLEARANCE) return false;
        }
        return true;
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
     * Makes a held building a holdout: its people inside, the works round its
     * block with a team on the firing step, a gun at the gate and guns in its
     * corners (or, where no works fit, its doors sandbagged), and the holdout
     * registered with the evacuation.
     *
     * @param cells      the building's open interior cells the garrison did not take
     * @param keep       ground the works may not stand on
     * @param cornerGuns gun pits in the works' corners: {@link #LIGHT_CORNER_GUNS} or {@link #HEAVY_CORNER_GUNS}
     * @return the holdout, or {@code null} when it cannot be seated
     */
    static Holdout fortify(BattleSimulation sim, PointOfInterest place, TacticalNode node, List<int[]> cells,
                           int garrisonSquadId, CivilianEvacuationPlacement placement, RedoubtWorks.Keep keep,
                           int index, RiskLevel risk, Random militiaRng, long seed, int cornerGuns) {
        Random rng = new Random(seed ^ SEED_SALT);
        int count = MIN_COHORT + rng.nextInt(MAX_COHORT - MIN_COHORT + 1);
        if (cells.size() < count) return null;
        int number = sim.getCivilianRedoubts().size() + 1;
        List<Long> members = new ArrayList<>();
        // The garrison took the best-covered cells; the people go deepest in, the last of the list.
        for (int i = 0; i < count; i++) {
            int[] cell = cells.get(cells.size() - 1 - i);
            members.add(sim.spawn(new EntitySpec("Holdout " + number + "-" + (i + 1), Faction.CIVILIAN,
                    TRADES[rng.nextInt(TRADES.length)], cell[0], cell[1]).role(UnitRole.VIP)));
        }
        RedoubtWorks.Plan works = worksEnabled()
                ? RedoubtWorks.plan(sim, place, place.anchorCellX, place.anchorCellY,
                        placement.liftX, placement.liftY, RedoubtWorks.HOLDOUT_MARGIN, keep, seed, cornerGuns)
                : null;
        if (works == null) {
            layAprons(sim, node, place, placement);
            int plain = sim.addCivilianHoldout(place.anchorCellX, place.anchorCellY,
                    place.left, place.top, place.right, place.bottom, garrisonSquadId, members);
            return plain < 0 ? null : new Holdout(plain, null, 0L, -1, List.of());
        }
        RedoubtWorks.lay(sim, works);
        // A redoubt is the place relief is sent to: its barricade stands up to the swarm as works, a roadblock's does not.
        FortifiedWorks.mark(sim.getGrid(), works.left() - 1, works.top() - 1, works.right() + 1, works.bottom() + 1);
        long gun = works.gunX() >= 0
                ? ColonyMilitia.seatEmplacement(sim, new int[]{works.gunX(), works.gunY()}, "redoubt-gun-" + number)
                : 0L;
        List<Long> corners = new ArrayList<>();
        for (int[] c : works.corners()) {
            corners.add(ColonyMilitia.seatEmplacement(sim, c, "redoubt-corner-gun-" + number + "-" + (corners.size() + 1)));
        }
        int line = seatLine(sim, works.step(), works.gateX(), works.gateY(),
                works.left(), works.top(), works.right(), works.bottom(),
                "Redoubt Line " + (index + 1), "man the redoubt's firing step", risk, militiaRng);
        // Relief arrives at the gate: a Marine in it has reached the redoubt, and its people walk out through it.
        int registered = sim.addCivilianHoldout(works.gateX(), works.gateY(),
                works.left(), works.top(), works.right(), works.bottom(), garrisonSquadId, members);
        if (registered < 0) return null;
        if (line >= 0) sim.setCivilianHoldoutLine(registered, line, node);
        return new Holdout(registered, works, gun, line, List.copyOf(corners));
    }

    /**
     * A line: a militia fireteam on a firing step, the bags nearest the gate
     * first, ordered to man the step's run the way a commander mans a stretch
     * of trench, its claims kept to the run. Returns its squad, or {@code -1}
     * when the step is too short to seat it.
     */
    static int seatLine(BattleSimulation sim, List<RedoubtWorks.Bag> step, int gateX, int gateY,
                        int left, int top, int right, int bottom, String name, String reason,
                        RiskLevel risk, Random rng) {
        if (step.size() < ColonyMilitia.POST_SIZE) return -1;
        List<RedoubtWorks.Bag> bags = new ArrayList<>(step);
        bags.sort(Comparator.comparingInt((RedoubtWorks.Bag b) ->
                Math.abs(b.x() - gateX) + Math.abs(b.y() - gateY)));
        List<int[]> cells = new ArrayList<>();
        // Every other bag nearest the gate, so the team spreads along the step rather than bunching on it.
        for (int i = 0; i < bags.size() && cells.size() < ColonyMilitia.POST_SIZE; i += 2) {
            cells.add(new int[]{bags.get(i).x(), bags.get(i).y()});
        }
        for (int i = 1; i < bags.size() && cells.size() < ColonyMilitia.POST_SIZE; i += 2) {
            cells.add(new int[]{bags.get(i).x(), bags.get(i).y()});
        }
        int squadId = ColonyMilitia.seatTeam(sim, cells, name, risk, rng);
        RedoubtWorks.Bag first = bags.get(0);
        int run = sim.fightingPositions().runIdAt(first.x(), first.y());
        int centreX = (left + right) / 2, centreY = (top + bottom) / 2;
        int radius = Math.max(right - left, bottom - top) / 2 + 2;
        // A wall's posts are no run: the team holds the ground and fights from the slits.
        sim.assignSquadCommand(run >= 0 ? ObjectiveAssignment.defendRun(squadId, centreX, centreY, radius, run)
                        : ObjectiveAssignment.defendArea(squadId, centreX, centreY, radius),
                CommandAuthority.GARRISON, ColonyMilitia.ISSUER, reason);
        return squadId;
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

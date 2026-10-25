package com.dillon.starsectormarines.battle.staffed;

import com.dillon.starsectormarines.battle.ambient.AmbientTaskRoute;
import com.dillon.starsectormarines.battle.ambient.AmbientTaskService;
import com.dillon.starsectormarines.battle.ambient.BuildingWork;
import com.dillon.starsectormarines.battle.ambient.JobBoard;
import com.dillon.starsectormarines.battle.ambient.JobSite;
import com.dillon.starsectormarines.battle.ambient.StaffedCrew;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Puts a civilian crew to work in some of a populated map's ordinary buildings
 * ({@code staffed-buildings.md}).
 *
 * <p>The buildings are the ones generation made. Their work is read off what they
 * were stocked with ({@link BuildingWork}) and the crew walk it ({@link
 * StaffedCrew}); nothing is placed and the map's ground does not move. Which
 * buildings, and how many, is the caller's: a map staffs what suits it.
 *
 * <p><b>Civilian buildings only.</b> A building with a garrison's compound over
 * it is the garrison's: its stores are a defender's, worked if at all by the
 * garrison ({@code StructureWatch}), never by townsfolk. Nobody holds a civilian
 * warehouse, so a worker killed in one is not replaced.
 */
public final class StaffedBuildings {

    /** Fewest stores points a building needs before it is worth a crew. */
    static final int LEAST_STORES = 4;
    /** Most hands in one building, whatever its stock could keep busy. */
    static final int MOST_HANDS = 4;
    /**
     * Site ids are scoped above every room's, so a building's claim groups never meet a room's, and are
     * the building's own index above that, so staffing one kind and then another never reuses one.
     */
    private static final int SITE_ID_BASE = 900_000;
    private static final long SALT = 0x5354414646454400L;

    private StaffedBuildings() { }

    /** One building put to work, and who is working it; {@code site} is the place its claim groups answer to. */
    public record Staffed(BuildingWork.Kind kind, PointOfInterest building, JobSite site, List<FixtureTask> work,
                          List<Long> crew) { }

    /** The building as a place with work in it: its interior, answering to its kind's purpose. */
    record BuildingSite(int id, RoomPurpose purpose, int left, int top, int right, int bottom) implements JobSite {
        @Override public boolean contains(int cellX, int cellY) {
            return cellX > left && cellX < right && cellY > top && cellY < bottom;
        }
        @Override public int centreX() { return (left + right) / 2; }
        @Override public int centreY() { return (top + bottom) / 2; }
    }

    /**
     * Staff up to {@code most} of the map's civilian buildings of the given kinds.
     *
     * @param seed the battle's seed, which breaks ties between buildings of one
     *     size on a stream of its own, so staffing never moves another roll
     */
    public static List<Staffed> install(BattleSimulation sim, MapResult map, long seed,
                                        Set<BuildingWork.Kind> kinds, int most) {
        List<Staffed> staffed = new ArrayList<>();
        if (most <= 0 || kinds.isEmpty()) return staffed;
        List<Candidate> candidates = candidates(sim, map, kinds);
        // The seed breaks ties; the biggest buildings go first, since a crew reads best where there is floor to carry across.
        Collections.shuffle(candidates, new Random(seed ^ SALT));
        candidates.sort(Comparator.comparingInt((Candidate c) -> c.work.size()).reversed());
        for (Candidate candidate : candidates) {
            if (staffed.size() >= most) break;
            BuildingSite site = site(candidate);
            List<Long> crew = staff(sim, candidate, site);
            if (!crew.isEmpty()) staffed.add(new Staffed(candidate.kind, candidate.building, site, candidate.work, crew));
        }
        if (!staffed.isEmpty()) sim.ambientTasks().settle();
        return staffed;
    }

    /** A building that could be staffed; its index in the map's list is what scopes its claim groups. */
    private record Candidate(BuildingWork.Kind kind, PointOfInterest building, int index, List<FixtureTask> work) { }

    /** Every civilian building on the map whose stock makes it one of {@code kinds}, in map order. */
    static List<Candidate> candidates(BattleSimulation sim, MapResult map, Set<BuildingWork.Kind> kinds) {
        List<Candidate> found = new ArrayList<>();
        for (int index = 0; index < map.pointsOfInterest.size(); index++) {
            PointOfInterest building = map.pointsOfInterest.get(index);
            if (heldByGarrison(sim, building)) continue;
            List<BuildingWork.Fixture> fixtures = fixturesIn(map.doodads, building);
            NavigationGrid grid = map.grid;
            List<FixtureTask> work = BuildingWork.recover(fixtures, (x, y) ->
                    x > building.left && x < building.right && y > building.top && y < building.bottom
                            && grid.isWalkable(x, y) && !grid.isDoorway(x, y));
            if (building.kind == PointOfInterest.Kind.MEDICAL || building.kind == PointOfInterest.Kind.LABORATORY)
                continue;
            BuildingWork.Kind kind = BuildingWork.kindOf(fixtures, work,
                    building.kind == PointOfInterest.Kind.DEPOT, LEAST_STORES);
            if (kind != null && kinds.contains(kind)) found.add(new Candidate(kind, building, index, work));
        }
        return found;
    }

    private static BuildingSite site(Candidate candidate) {
        PointOfInterest b = candidate.building;
        return new BuildingSite(SITE_ID_BASE + candidate.index, candidate.kind.purpose(), b.left, b.top, b.right, b.bottom);
    }

    private static List<Long> staff(BattleSimulation sim, Candidate candidate, BuildingSite site) {
        // The claim groups exist before anybody is handed a route that names one.
        JobBoard.publish(sim.taskPoints(), candidate.work, site, null);
        int hands = Math.min(MOST_HANDS, StaffedCrew.hands(candidate.kind, candidate.work));
        List<Long> crew = new ArrayList<>();
        for (int member = 0; member < hands; member++) {
            AmbientTaskRoute route = StaffedCrew.member(site, candidate.kind, candidate.work, member);
            if (route == null) break;
            AmbientTaskRoute.Stop start = AmbientTaskService.standingPlace(route, 0f);
            long actor = sim.spawn(new EntitySpec(route.id(), Faction.CIVILIAN, UnitType.CIVILIAN,
                    (int) Math.floor(start.worldX()), (int) Math.floor(start.worldY())).role(UnitRole.FLEE));
            sim.ambientTasks().assign(actor, route);
            crew.add(actor);
        }
        return crew;
    }

    /** The anchor of any garrison compound inside the building, with no radius: the rule {@code StructureWatch} uses. */
    private static boolean heldByGarrison(BattleSimulation sim, PointOfInterest building) {
        CompoundService compounds = sim.getCompoundService();
        if (compounds == null) return false;
        for (CompoundService.Record record : compounds.getRecords()) {
            int x = record.node.anchorX, y = record.node.anchorY;
            if (x >= building.left && x <= building.right && y >= building.top && y <= building.bottom) return true;
        }
        return false;
    }

    private static List<BuildingWork.Fixture> fixturesIn(List<Doodad> doodads, PointOfInterest building) {
        List<BuildingWork.Fixture> fixtures = new ArrayList<>();
        for (Doodad d : doodads) {
            if (d.cellX <= building.left || d.cellX >= building.right
                    || d.cellY <= building.top || d.cellY >= building.bottom) continue;
            fixtures.add(new BuildingWork.Fixture(d.cellX, d.cellY, d.footprintCellsX, d.footprintCellsY,
                    d.definitionId()));
        }
        return fixtures;
    }
}

package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.setup.InfantryLoadoutRolls;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Mission-local militia fireteam sealed inside the civilian shelter.
 *
 * <p>An allied squad, not one of the player's: it is the shelter's own people,
 * commanded by garrison authority and outside the player's command pool
 * because of the side it is on rather than because of a flag that says so.
 */
public final class RescueShelterGarrison {

    public static final int MEMBER_COUNT = 4;
    private static final long LOADOUT_SEED_SALT = 0x5348454C54455247L;

    public final int squadId;
    private final long[] entityIds;

    private RescueShelterGarrison(int squadId, long[] entityIds) {
        this.squadId = squadId;
        this.entityIds = entityIds;
    }

    /**
     * Installs one four-person militia squad in unused walkable cells of the
     * exact residential compound selected for the evacuation cohort. Returns
     * {@code null} when that compound cannot hold the complete fireteam.
     */
    public static RescueShelterGarrison install(
            BattleSimulation sim, MapResult map,
            CivilianEvacuationPlacement placement, RiskLevel risk, long seed) {
        if (sim == null || map == null || placement == null) return null;
        PointOfInterest shelter = selectedShelter(
                map.pointsOfInterest, placement);
        if (shelter == null) return null;
        List<int[]> cells = spawnCells(sim, shelter, placement);
        if (cells.size() < MEMBER_COUNT) return null;

        Random rng = new Random(seed ^ LOADOUT_SEED_SALT);
        MarineLoadout[] loadouts = InfantryLoadoutRolls.defenderSquad(
                MEMBER_COUNT, UnitType.MILITIA, risk, rng);
        int squadId = sim.mintSquad(Faction.ALLY, UnitType.MILITIA);
        Squad squad = sim.getSquad(squadId);
        sim.registerShelterGuardSquad(squadId);
        sim.assignSquadCommand(ObjectiveAssignment.escort(
                        squad.id, placement.shelterX, placement.shelterY),
                CommandAuthority.GARRISON, "rescue-shelter-garrison",
                "hold civilian shelter");

        long[] ids = new long[MEMBER_COUNT];
        for (int i = 0; i < ids.length; i++) {
            int[] cell = cells.get(i);
            EntitySpec spec = new EntitySpec("Shelter Militia " + (i + 1),
                    Faction.ALLY, UnitType.MILITIA, cell[0], cell[1])
                    .squad(squadId);
            loadouts[i].seedInto(spec);
            ids[i] = sim.spawn(spec);
            if (squad.leaderId == 0L) squad.leaderId = ids[i];
        }
        squad.originalSize = MEMBER_COUNT;
        return new RescueShelterGarrison(squadId, ids);
    }

    public int size() {
        return entityIds.length;
    }

    public long entityId(int index) {
        if (index < 0 || index >= entityIds.length) {
            throw new IndexOutOfBoundsException(index);
        }
        return entityIds[index];
    }

    private static PointOfInterest selectedShelter(
            List<PointOfInterest> points,
            CivilianEvacuationPlacement placement) {
        for (PointOfInterest point : points) {
            if (point != null
                    && point.kind == PointOfInterest.Kind.RESIDENTIAL
                    && point.interiorAnchorX == placement.shelterX
                    && point.interiorAnchorY == placement.shelterY) {
                return point;
            }
        }
        return null;
    }

    private static List<int[]> spawnCells(
            BattleSimulation sim, PointOfInterest shelter,
            CivilianEvacuationPlacement placement) {
        Set<Integer> civilianCells = new HashSet<>();
        for (int i = 0; i < placement.spawnCount(); i++) {
            civilianCells.add(sim.getGrid().index(
                    placement.spawnX(i), placement.spawnY(i)));
        }
        List<int[]> cells = new ArrayList<>();
        int shelterBuilding = sim.getTopology().getBuildingId(
                placement.shelterX, placement.shelterY);
        for (int y = shelter.top + 1; y < shelter.bottom; y++) {
            for (int x = shelter.left + 1; x < shelter.right; x++) {
                if (!sim.getGrid().inBounds(x, y)
                        || !sim.getGrid().isWalkable(x, y)) continue;
                if (shelterBuilding > 0
                        && sim.getTopology().getBuildingId(x, y)
                        != shelterBuilding) continue;
                int cell = sim.getGrid().index(x, y);
                if (civilianCells.contains(cell)) continue;
                int cover = sim.getGrid().getCoverAt(x, y)
                        + sim.getDoodadCoverAt(x, y);
                int distance = Math.abs(x - placement.shelterX)
                        + Math.abs(y - placement.shelterY);
                cells.add(new int[]{x, y, cover, distance, cell});
            }
        }
        cells.sort(Comparator
                .comparingInt((int[] cell) -> cell[2]).reversed()
                .thenComparingInt(cell -> cell[3])
                .thenComparingInt(cell -> cell[4]));
        return cells;
    }
}

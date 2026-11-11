package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.world.gen.CordonedFacility;
import com.dillon.starsectormarines.battle.world.gen.IndustrialZone;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.LoadingBay;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.ServiceLane;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.ops.OperationTier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The machines of a disrupting raid's works ({@code raid-mission.md}, Break): the places a charge wrecks the
 * industry the player named.
 *
 * <p>On an industry's own Raid site ({@code RaidSiteTemplate}) the works is a walled compound and its machines
 * are its halls: its machine houses ({@link RoomPurpose#PARTS_CAGE}, as the industry's program lays them) and then
 * its other depots, each nearest the compound's seed first, within {@link #WORKS_REACH}. A machine is taken at its
 * building's interior anchor. Elsewhere, until every
 * industry has a site, the map's industrial ground stands in for it. Candidates are taken in this order, the first
 * source that has any leading:
 * <ol>
 *   <li>the machines an industry's site stands in the open ({@link MapResult#raidMachines}), a refinery's units;</li>
 *   <li>on a site, the works compound's halls;</li>
 *   <li>the industrial zone: the buildings standing on it, then its loading bays and the middle of its
 *       factory row's service lane;</li>
 *   <li>the cordoned facility's buildings;</li>
 *   <li>every other non-residential point of interest, nearest the machines already chosen.</li>
 * </ol>
 * A machine stands on walkable ground that is not a doorway, is reachable from the egress berth, and keeps
 * {@link #MIN_SPACING} cells from every other machine, so two never share one pocket.
 */
public record RaidWorksLayout(String targetId, String targetName, List<int[]> machines,
                              int egressCellX, int egressCellY) {

    /** Cells between two machines' centres, so each has its own pocket. UNTUNED. */
    public static final int MIN_SPACING = 7;

    public RaidWorksLayout {
        machines = List.copyOf(machines);
        if (machines.isEmpty()) throw new IllegalArgumentException("a works needs at least one machine");
    }

    /** Machines in the works by the raid's tier: a bigger industry takes more work to break fully. UNTUNED. */
    public static int machinesFor(OperationTier tier) {
        return switch (Objects.requireNonNull(tier, "tier")) {
            case FIRST_CONTRACT -> 3;
            case ESTABLISHED, VETERAN -> 4;
            case REINFORCED, FULL_STRENGTH -> 5;
        };
    }

    /** Cells from a works compound's seed within which its halls are looked for. UNTUNED. */
    public static final int WORKS_REACH = 32;

    public static RaidWorksLayout select(MapResult map, List<LandingPad> landingPads, int count) {
        return select(map, landingPads, count, null);
    }

    /**
     * @param worksSeed the works compound's seed on an industry's own Raid site, or {@code null} on a derived map
     */
    public static RaidWorksLayout select(MapResult map, List<LandingPad> landingPads, int count, int[] worksSeed) {
        Objects.requireNonNull(map, "map");
        if (landingPads == null || landingPads.isEmpty()) {
            throw new IllegalArgumentException("Raid requires at least one landing pad");
        }
        LandingPad egress = landingPads.get(0);
        Predicate<int[]> usable = cell -> map.grid.inBounds(cell[0], cell[1])
                && map.grid.isWalkable(cell[0], cell[1])
                && !map.grid.isDoorway(cell[0], cell[1])
                && GridPathfinder.findPath(map.grid, egress.centerX, egress.centerY, cell[0], cell[1]).length > 0;
        List<int[]> chosen = industryFirst(map.raidMachines, () -> worksSeed == null ? List.of()
                : compoundHalls(map.pointsOfInterest, worksSeed[0], worksSeed[1],
                        poi -> machineHouse(map.topology, poi)), count, usable);
        if (chosen.size() < count) chosen = pick(worksCandidates(map), count, MIN_SPACING, usable, chosen);
        if (chosen.size() < count) {
            chosen = pick(fallbackCandidates(map, chosen), count, MIN_SPACING, usable, chosen);
        }
        if (chosen.isEmpty()) throw new IllegalStateException("Raid map has no reachable works for a machine");
        return new RaidWorksLayout("RAID-WORKS", "the works", chosen, egress.centerX, egress.centerY);
    }

    /**
     * The machines an industry stands in the open ({@code industry}, in the map's frame) first, through the same
     * {@code usable} and {@link #MIN_SPACING} checks as any machine; any shortfall filled from the compound's
     * {@code halls}, asked for only when one is left.
     */
    static List<int[]> industryFirst(List<int[]> industry, Supplier<List<int[]>> halls, int count,
                                     Predicate<int[]> usable) {
        List<int[]> chosen = pick(industry, count, MIN_SPACING, usable, List.of());
        if (chosen.size() < count) chosen = pick(halls.get(), count, MIN_SPACING, usable, chosen);
        return chosen;
    }

    /**
     * Picks up to {@code count} cells in candidate order after {@code already}, skipping any that fail
     * {@code usable} or stand within {@code minSpacing} of one already taken.
     */
    static List<int[]> pick(List<int[]> candidates, int count, int minSpacing, Predicate<int[]> usable,
                            List<int[]> already) {
        List<int[]> out = new ArrayList<>(already);
        int spacingSquared = minSpacing * minSpacing;
        for (int[] cell : candidates) {
            if (out.size() >= count) break;
            boolean crowded = false;
            for (int[] taken : out) {
                int dx = taken[0] - cell[0], dy = taken[1] - cell[1];
                if (dx * dx + dy * dy < spacingSquared) {
                    crowded = true;
                    break;
                }
            }
            if (!crowded && usable.test(cell)) out.add(cell);
        }
        return out;
    }

    /**
     * A works compound's halls: the depots within {@link #WORKS_REACH} of its seed, nearest first. A compound packs
     * its halls round its seed and keeps a buffer of open ground before anyone else's building, so the nearest depots
     * are its own.
     */
    static List<int[]> compoundHalls(List<PointOfInterest> pois, int seedX, int seedY) {
        return compoundHalls(pois, seedX, seedY, poi -> false);
    }

    /**
     * A works compound's halls, its machine houses ({@code machine}) leading: within each group the depots within
     * {@link #WORKS_REACH} of its seed, nearest first by the building's middle. A works' other houses (its receiving
     * shed, its stores, its pump house) are taken only when the machine houses run out.
     */
    static List<int[]> compoundHalls(List<PointOfInterest> pois, int seedX, int seedY,
                                     Predicate<PointOfInterest> machine) {
        int reach = WORKS_REACH * WORKS_REACH;
        List<PointOfInterest> near = new ArrayList<>();
        for (PointOfInterest poi : pois) {
            if (poi.kind != PointOfInterest.Kind.DEPOT) continue;
            int dx = poi.centerX() - seedX, dy = poi.centerY() - seedY;
            if (dx * dx + dy * dy <= reach) near.add(poi);
        }
        near.sort(Comparator.<PointOfInterest>comparingInt(poi -> machine.test(poi) ? 0 : 1)
                .thenComparingInt(poi -> (poi.centerX() - seedX) * (poi.centerX() - seedX)
                        + (poi.centerY() - seedY) * (poi.centerY() - seedY)));
        List<int[]> out = new ArrayList<>();
        for (PointOfInterest poi : near) out.add(new int[]{poi.interiorAnchorX, poi.interiorAnchorY});
        return out;
    }

    /** Whether a building holds a machine room: any of its cells is a {@link RoomPurpose#PARTS_CAGE}. */
    static boolean machineHouse(CellTopology topology, PointOfInterest poi) {
        for (int y = poi.top; y <= poi.bottom; y++) {
            for (int x = poi.left; x <= poi.right; x++) {
                if (topology.inBounds(x, y) && topology.getRoomPurpose(x, y) == RoomPurpose.PARTS_CAGE) return true;
            }
        }
        return false;
    }

    private static List<int[]> worksCandidates(MapResult map) {
        List<int[]> out = new ArrayList<>();
        for (IndustrialZone zone : map.industrialZones) {
            float half = zone.width() / 2f + 12f;
            float x0 = Math.min(zone.startX(), zone.endX()) - half, x1 = Math.max(zone.startX(), zone.endX()) + half;
            float y0 = Math.min(zone.startY(), zone.endY()) - half, y1 = Math.max(zone.startY(), zone.endY()) + half;
            for (PointOfInterest poi : map.pointsOfInterest) {
                if (poi.kind == PointOfInterest.Kind.RESIDENTIAL) continue;
                if (poi.interiorAnchorX >= x0 && poi.interiorAnchorX <= x1
                        && poi.interiorAnchorY >= y0 && poi.interiorAnchorY <= y1) {
                    out.add(new int[]{poi.interiorAnchorX, poi.interiorAnchorY});
                }
            }
            for (LoadingBay bay : zone.bays()) out.add(new int[]{Math.round(bay.standX()), Math.round(bay.standY())});
            for (ServiceLane lane : zone.lanes()) {
                out.add(new int[]{(lane.gateAX() + lane.gateBX()) / 2, (lane.gateAY() + lane.gateBY()) / 2});
            }
        }
        for (CordonedFacility facility : map.cordonedFacilities) {
            for (CordonedFacility.Building building : facility.buildings()) {
                if (building.doorX() < 0) continue;
                out.add(new int[]{(building.left() + building.right()) / 2, (building.top() + building.bottom()) / 2});
            }
        }
        return out;
    }

    /** The map's other non-residential places, nearest the machines already chosen (or the defender's ground). */
    private static List<int[]> fallbackCandidates(MapResult map, List<int[]> chosen) {
        float cx, cy;
        if (chosen.isEmpty()) {
            cx = map.defenderSpawnX;
            cy = map.defenderSpawnY;
        } else {
            float sx = 0, sy = 0;
            for (int[] cell : chosen) {
                sx += cell[0];
                sy += cell[1];
            }
            cx = sx / chosen.size();
            cy = sy / chosen.size();
        }
        List<int[]> out = new ArrayList<>();
        for (PointOfInterest poi : map.pointsOfInterest) {
            if (poi.kind == PointOfInterest.Kind.RESIDENTIAL || poi.kind == PointOfInterest.Kind.LANDING_SITE) {
                continue;
            }
            out.add(new int[]{poi.interiorAnchorX, poi.interiorAnchorY});
        }
        float fx = cx, fy = cy;
        out.sort(Comparator.comparingDouble(cell -> Math.hypot(cell[0] - fx, cell[1] - fy)));
        return out;
    }
}

package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.List;

/** Frozen defender-owned Assault strongpoints and bounded security areas. */
public final class AssaultDefenderCommandFacts {

    public record Strongpoint(int index, TacticalNode.Kind kind, int areaIndex,
                              int anchorX, int anchorY, int rallyX, int rallyY,
                              int zoneId,
                              int left, int top, int right, int bottom,
                              int priority) { }

    public record Area(int index, int minX, int minY, int maxX, int maxY,
                       int rallyX, int rallyY, int priority,
                       List<Integer> strongpointIndexes) {
        public Area {
            strongpointIndexes = List.copyOf(strongpointIndexes);
        }

        public int width() { return maxX - minX + 1; }
        public int height() { return maxY - minY + 1; }
    }

    private final AssaultSectorLayout layout;
    private final List<Area> areas;
    private final List<Strongpoint> strongpoints;

    private AssaultDefenderCommandFacts(AssaultSectorLayout layout,
                                        List<Area> areas,
                                        List<Strongpoint> strongpoints) {
        this.layout = layout;
        this.areas = List.copyOf(areas);
        this.strongpoints = List.copyOf(strongpoints);
    }

    static AssaultDefenderCommandFacts freeze(BattleView sim,
                                               CommandTopology topology) {
        AssaultSectorLayout layout = AssaultSectorLayout.create(
                topology.width(), topology.height());
        List<Strongpoint> strongpoints = new ArrayList<>();
        TacticalMap tacticalMap = sim.getTacticalMap();
        if (tacticalMap != null) {
            List<TacticalNode> nodes = tacticalMap.all();
            for (TacticalNode node : nodes) {
                if (node.defaultGuard != Faction.DEFENDER) continue;
                int area = layout.sectorForCell(node.anchorX, node.anchorY);
                if (area < 0) continue;
                int[] rally = resolveRally(node, layout.sectors().get(area),
                        topology);
                int index = strongpoints.size();
                strongpoints.add(new Strongpoint(index, node.kind, area,
                        node.anchorX, node.anchorY, rally[0], rally[1],
                        topology.zoneIdAt(rally[0], rally[1]),
                        node.compoundLeft(), node.compoundTop(),
                        node.compoundRight(), node.compoundBottom(),
                        node.priorityScore));
            }
        }

        List<Area> areas = new ArrayList<>(layout.sectors().size());
        for (AssaultSectorLayout.Sector sector : layout.sectors()) {
            List<Integer> members = new ArrayList<>();
            Strongpoint primary = null;
            for (Strongpoint strongpoint : strongpoints) {
                if (strongpoint.areaIndex() != sector.index()) continue;
                members.add(strongpoint.index());
                if (primary == null || strongpoint.priority() > primary.priority()
                        || strongpoint.priority() == primary.priority()
                        && strongpoint.index() < primary.index()) {
                    primary = strongpoint;
                }
            }
            areas.add(new Area(sector.index(), sector.minX(), sector.minY(),
                    sector.maxX(), sector.maxY(),
                    primary != null ? primary.rallyX()
                            : nearestWalkableX(sector, topology),
                    primary != null ? primary.rallyY()
                            : nearestWalkableY(sector, topology),
                    primary != null ? primary.priority() : 0, members));
        }
        return new AssaultDefenderCommandFacts(layout, areas, strongpoints);
    }

    public AssaultSectorLayout layout() { return layout; }
    public List<Area> areas() { return areas; }
    public List<Strongpoint> strongpoints() { return strongpoints; }
    public Area area(int index) {
        return index >= 0 && index < areas.size() ? areas.get(index) : null;
    }

    private static int[] resolveRally(TacticalNode node,
                                      AssaultSectorLayout.Sector sector,
                                      CommandTopology topology) {
        for (TacticalNode.StandPosition stand : node.standPositions()) {
            if (contains(sector, stand.x(), stand.y())
                    && topology.isWalkable(stand.x(), stand.y())) {
                return new int[]{stand.x(), stand.y()};
            }
        }
        int[] compound = nearestWalkable(node.anchorX, node.anchorY,
                Math.max(sector.minX(), node.compoundLeft()),
                Math.max(sector.minY(), node.compoundTop()),
                Math.min(sector.maxX(), node.compoundRight()),
                Math.min(sector.maxY(), node.compoundBottom()), topology);
        if (compound != null) return compound;
        int[] area = nearestWalkable(node.anchorX, node.anchorY,
                sector.minX(), sector.minY(), sector.maxX(), sector.maxY(),
                topology);
        return area != null ? area : new int[]{sector.centerX(), sector.centerY()};
    }

    private static int nearestWalkableX(AssaultSectorLayout.Sector sector,
                                        CommandTopology topology) {
        int[] cell = nearestWalkable(sector.centerX(), sector.centerY(),
                sector.minX(), sector.minY(), sector.maxX(), sector.maxY(),
                topology);
        return cell != null ? cell[0] : sector.centerX();
    }

    private static int nearestWalkableY(AssaultSectorLayout.Sector sector,
                                        CommandTopology topology) {
        int[] cell = nearestWalkable(sector.centerX(), sector.centerY(),
                sector.minX(), sector.minY(), sector.maxX(), sector.maxY(),
                topology);
        return cell != null ? cell[1] : sector.centerY();
    }

    private static int[] nearestWalkable(int originX, int originY,
                                         int minX, int minY,
                                         int maxX, int maxY,
                                         CommandTopology topology) {
        int[] best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (!topology.isWalkable(x, y)) continue;
                int distance = Math.abs(x - originX) + Math.abs(y - originY);
                if (distance < bestDistance) {
                    best = new int[]{x, y};
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private static boolean contains(AssaultSectorLayout.Sector sector,
                                    int x, int y) {
        return x >= sector.minX() && x <= sector.maxX()
                && y >= sector.minY() && y <= sector.maxY();
    }
}

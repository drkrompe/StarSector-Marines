package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.AssaultSearchSnapshot.AssignmentReason;
import com.dillon.starsectormarines.battle.command.AssaultSearchSnapshot.Phase;
import com.dillon.starsectormarines.battle.command.AssaultSearchSnapshot.SectorState;
import com.dillon.starsectormarines.battle.command.AssaultSearchSnapshot.SectorStatus;
import com.dillon.starsectormarines.battle.command.AssaultSearchSnapshot.SquadDirective;
import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceService;
import com.dillon.starsectormarines.battle.infantry.PatrolMotion;
import com.dillon.starsectormarines.battle.squad.BeliefSource;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Belief-honest Marine commander for Assault's two-dimensional area search.
 * Public topology defines stable sectors and sweep legs; only the Marine
 * influence picture can turn a sector into suspected or active contact.
 */
public final class AssaultCommand implements
        AutonomousMissionCommand<AssaultCommandFrame, AssaultSearchSnapshot> {

    private static final float EXTERIOR_DOMINANCE_RATIO = 2f;
    private static final int FRESH_DIRECT_TICKS =
            CommanderInfluenceService.UPDATE_INTERVAL_TICKS;

    private final Map<Integer, Integer> squadSector = new HashMap<>();
    private final Map<Integer, Integer> squadLeg = new HashMap<>();
    private volatile AssaultSearchSnapshot searchSnapshot =
            AssaultSearchSnapshot.empty(Faction.MARINE);
    private List<SearchSector> sectors = List.of();
    private int topologyWidth = -1;
    private int topologyHeight = -1;
    private AssaultSectorLayout layout;
    private int sectorCols;
    private int sectorRows;

    @Override
    public Faction faction() { return Faction.MARINE; }

    @Override
    public String strategyId() { return "assault-attacker"; }

    public AssaultSearchSnapshot searchSnapshot() { return searchSnapshot; }

    @Override
    public CommandPlan<AssaultSearchSnapshot> plan(AssaultCommandFrame frame) {
        ensureSectors(frame.topology());
        updateReachedLegs(frame);
        SectorReports reports = reports(frame);
        boolean initialSearchComplete = sectors.stream().allMatch(SearchSector::complete);
        int[] loads = new int[sectors.size()];
        Map<Integer, SquadDirective> directives = new HashMap<>();
        List<CommandProposal> proposals = new ArrayList<>();
        int commandPool = 0;

        for (CommandSquadState squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            commandPool++;
            CommandDirective incumbent = squad.directive();
            if (incumbent != null && !strategyId().equals(incumbent.issuer())
                    && incumbent.authority().priority()
                    >= CommandAuthority.MISSION_COMMAND.priority()) {
                ObjectiveAssignment external = incumbent.assignment();
                directives.put(squad.squadId(), new SquadDirective(squad.squadId(),
                        -1, AssignmentReason.EXTERNAL_OWNERSHIP_PRESERVED,
                        external != null ? external.kind() : null,
                        external != null ? external.targetCellX() : -1,
                        external != null ? external.targetCellY() : -1));
                proposals.add(CommandProposal.retain(squad.squadId(),
                        CommandAuthority.MISSION_COMMAND,
                        AssignmentReason.EXTERNAL_OWNERSHIP_PRESERVED.name()));
                continue;
            }

            int sectorIndex = chooseSector(squad, loads, reports,
                    initialSearchComplete, frame.topology());
            if (sectorIndex < 0) {
                squadSector.remove(squad.squadId());
                squadLeg.remove(squad.squadId());
                AssignmentReason reason = AssignmentReason.NO_REACHABLE_SECTOR;
                directives.put(squad.squadId(), new SquadDirective(squad.squadId(),
                        -1, reason, null, -1, -1));
                proposals.add(squad.assignment() != null
                        ? CommandProposal.release(squad.squadId(),
                                CommandAuthority.MISSION_COMMAND, reason.name(),
                                CommandStabilityBreak.TARGET_UNREACHABLE)
                        : CommandProposal.retain(squad.squadId(),
                                CommandAuthority.MISSION_COMMAND, reason.name()));
                continue;
            }

            int priorSector = squadSector.getOrDefault(squad.squadId(), -1);
            int lane = loads[sectorIndex]++;
            SearchTarget target = chooseTarget(squad, sectorIndex, lane, reports,
                    initialSearchComplete, frame);
            if (target == null) {
                AssignmentReason reason = AssignmentReason.NO_REACHABLE_SECTOR;
                directives.put(squad.squadId(), new SquadDirective(squad.squadId(),
                        sectorIndex, reason, null, -1, -1));
                proposals.add(CommandProposal.retain(squad.squadId(),
                        CommandAuthority.MISSION_COMMAND, reason.name()));
                continue;
            }

            squadSector.put(squad.squadId(), sectorIndex);
            squadLeg.put(squad.squadId(), target.legIndex());
            ObjectiveAssignment assignment = ObjectiveAssignment.sweepSector(
                    squad.squadId(), target.cell().x(), target.cell().y());
            AssignmentReason reason = assignmentReason(squad, sectorIndex,
                    priorSector, loads[sectorIndex], reports, initialSearchComplete);
            directives.put(squad.squadId(), new SquadDirective(squad.squadId(),
                    sectorIndex, reason, assignment.kind(), target.cell().x(),
                    target.cell().y()));
            proposals.add(CommandProposal.assign(assignment,
                    CommandAuthority.MISSION_COMMAND, reason.name(),
                    stabilityBreak(squad, assignment, priorSector,
                            sectorIndex, frame)));
        }

        Phase phase = reports.hasActiveOrSuspected() ? Phase.CONVERGE
                : initialSearchComplete ? Phase.RECHECK : Phase.SEARCH;
        AssaultSearchSnapshot detail = buildSnapshot(frame, phase, reports,
                loads, directives, initialSearchComplete);
        List<String> objectives = detail.sectors().stream()
                .map(sector -> "sector-" + sector.index() + "=" + sector.status()
                        + ":" + sector.visitedLegs() + "/" + sector.totalLegs())
                .toList();
        return new CommandPlan<>(faction(), strategyId(), phase.name(), frame.tick(),
                frame.influence() != null ? frame.influence().updatedTick() : -1,
                commandPool, 0, objectives, proposals, detail);
    }

    @Override
    public CommanderSnapshot<AssaultSearchSnapshot> reconcile(
            CommanderSnapshot<AssaultSearchSnapshot> snapshot) {
        return snapshot.withDetail(snapshot.detail().reconcileStableDirectives(
                snapshot, searchSnapshot, strategyId()));
    }

    @Override
    public void publish(CommanderSnapshot<AssaultSearchSnapshot> snapshot) {
        searchSnapshot = snapshot.detail();
    }

    private void ensureSectors(CommandTopology topology) {
        if (topology.width() == topologyWidth && topology.height() == topologyHeight
                && !sectors.isEmpty()) return;
        topologyWidth = topology.width();
        topologyHeight = topology.height();
        layout = AssaultSectorLayout.create(topology.width(), topology.height());
        sectorCols = layout.columns();
        sectorRows = layout.rows();
        int exteriorZone = exteriorZone(topology);
        List<SearchSector> built = new ArrayList<>(sectorCols * sectorRows);
        for (AssaultSectorLayout.Sector sector : layout.sectors()) {
            List<Cell> legs = buildSweepLegs(topology, sector.minX(),
                    sector.maxX(), sector.minY(), sector.maxY(), exteriorZone);
            built.add(new SearchSector(sector.index(), sector.minX(),
                    sector.minY(), sector.maxX(), sector.maxY(), legs));
        }
        sectors = built;
        squadSector.clear();
        squadLeg.clear();
    }

    private void updateReachedLegs(AssaultCommandFrame frame) {
        float arrivalSq = PatrolMotion.ARRIVAL_RADIUS * PatrolMotion.ARRIVAL_RADIUS;
        for (CommandSquadState squad : frame.squads()) {
            Integer sectorIndex = squadSector.get(squad.squadId());
            Integer legIndex = squadLeg.get(squad.squadId());
            if (sectorIndex == null || legIndex == null || legIndex < 0
                    || sectorIndex < 0 || sectorIndex >= sectors.size()) continue;
            SearchSector sector = sectors.get(sectorIndex);
            if (legIndex >= sector.legs.size()) continue;
            Cell leg = sector.legs.get(legIndex);
            float dx = squad.centroidX() - (leg.x() + 0.5f);
            float dy = squad.centroidY() - (leg.y() + 0.5f);
            if (dx * dx + dy * dy <= arrivalSq) {
                sector.visited[legIndex] = true;
                sector.lastVisitedTick = frame.tick();
            }
        }
    }

    private int chooseSector(CommandSquadState squad, int[] loads,
                             SectorReports reports, boolean allComplete,
                             CommandTopology topology) {
        Integer sticky = squadSector.get(squad.squadId());
        if (sticky != null && sticky >= 0 && sticky < sectors.size()) {
            SearchSector sector = sectors.get(sticky);
            boolean stillUseful = reports.status(sticky) != SectorStatus.SEARCHED
                    || !sector.complete()
                    || allComplete && !arrivedAtAssignment(squad);
            if (stillUseful && nearestReachableLeg(squad, sector, topology) >= 0) {
                return sticky;
            }
        }
        int best = chooseAmong(squad, loads, reports, topology, allComplete, true);
        return best >= 0 ? best
                : chooseAmong(squad, loads, reports, topology, allComplete, false);
    }

    private int chooseAmong(CommandSquadState squad, int[] loads,
                            SectorReports reports, CommandTopology topology,
                            boolean allComplete, boolean uncoveredOnly) {
        int best = -1;
        long bestScore = Long.MAX_VALUE;
        for (SearchSector sector : sectors) {
            SectorStatus status = reports.status(sector.index);
            if (!allComplete && sector.complete() && status == SectorStatus.SEARCHED) continue;
            if (uncoveredOnly && loads[sector.index] > 0) continue;
            int leg = nearestReachableLeg(squad, sector, topology);
            if (leg < 0) continue;
            Cell cell = sector.legs.get(leg);
            int route = topology.routeLength(squad.anchorCellX(), squad.anchorCellY(),
                    cell.x(), cell.y());
            long statusRank = switch (status) {
                case ACTIVE -> 0;
                case SUSPECTED -> 1;
                case SEARCHING -> 2;
                case SEARCHED -> 3;
            };
            long loadPenalty = uncoveredOnly ? 0L : loads[sector.index] * 1_000_000L;
            long ageBias = allComplete
                    ? (long) Math.max(0, sector.lastVisitedTick + 1) * 100_000L
                    : 0;
            long score = loadPenalty + statusRank * 100_000L
                    + (long) route * 10L + ageBias;
            if (score < bestScore || score == bestScore && sector.index < best) {
                best = sector.index;
                bestScore = score;
            }
        }
        return best;
    }

    private SearchTarget chooseTarget(CommandSquadState squad, int sectorIndex,
                                      int lane, SectorReports reports,
                                      boolean allComplete,
                                      AssaultCommandFrame frame) {
        SearchSector sector = sectors.get(sectorIndex);
        ObjectiveAssignment incumbent = squad.directive() != null
                && strategyId().equals(squad.directive().issuer())
                ? squad.directive().assignment() : null;
        int incumbentLeg = incumbent != null
                ? legAt(sector, incumbent.targetCellX(), incumbent.targetCellY())
                : -1;
        if (incumbent != null && incumbent.kind() == AssignmentKind.SWEEP_SECTOR
                && incumbentLeg >= 0
                && squad.directive().isStableAt(frame.tick())
                && frame.topology().reachable(squad.anchorCellX(), squad.anchorCellY(),
                        incumbent.targetCellX(), incumbent.targetCellY())
                && !arrived(squad, incumbent.targetCellX(), incumbent.targetCellY())) {
            return new SearchTarget(new Cell(incumbent.targetCellX(),
                    incumbent.targetCellY()), incumbentLeg);
        }
        CommanderContact contact = reports.primaryContact(sectorIndex);
        if (contact != null) {
            int contactLeg = nearestLegTo(sector, contact.cellX(), contact.cellY(),
                    squad, frame.topology(), lane);
            if (contactLeg >= 0) {
                return new SearchTarget(sector.legs.get(contactLeg), contactLeg);
            }
        }
        int start = squadLeg.getOrDefault(squad.squadId(), -1) + 1 + lane;
        int leg = nextReachableLeg(squad, sector, start, !allComplete,
                frame.topology());
        return leg >= 0 ? new SearchTarget(sector.legs.get(leg), leg) : null;
    }

    private static AssignmentReason assignmentReason(CommandSquadState squad,
                                                     int sectorIndex,
                                                     int priorSector,
                                                     int sectorLoad,
                                                     SectorReports reports,
                                                     boolean allComplete) {
        if (reports.status(sectorIndex) == SectorStatus.ACTIVE && sectorLoad > 1) {
            return AssignmentReason.ACTIVE_CONTACT_REINFORCEMENT;
        }
        if (reports.status(sectorIndex) == SectorStatus.SUSPECTED && sectorLoad > 1) {
            return AssignmentReason.SUSPECTED_CONTACT_REINFORCEMENT;
        }
        if (allComplete) return AssignmentReason.SECTOR_RECHECK_ASSIGNED;
        if (priorSector == sectorIndex && squad.assignment() != null
                && squad.assignment().kind() == AssignmentKind.SWEEP_SECTOR) {
            return AssignmentReason.SECTOR_SEARCH_PRESERVED;
        }
        return AssignmentReason.SECTOR_SEARCH_ASSIGNED;
    }

    private CommandStabilityBreak stabilityBreak(CommandSquadState squad,
                                                  ObjectiveAssignment assignment,
                                                  int priorSector,
                                                  int sectorIndex,
                                                  AssaultCommandFrame frame) {
        CommandDirective incumbent = squad.directive();
        if (incumbent == null || incumbent.assignment() == null
                || !strategyId().equals(incumbent.issuer())
                || Objects.equals(incumbent.assignment(), assignment)) {
            return CommandStabilityBreak.NONE;
        }
        ObjectiveAssignment old = incumbent.assignment();
        if (old.targetCellX() >= 0 && arrived(squad, old.targetCellX(),
                old.targetCellY())) return CommandStabilityBreak.CONTEXT_INVALIDATED;
        if (priorSector >= 0 && priorSector != sectorIndex
                && sectors.get(priorSector).complete()) {
            return CommandStabilityBreak.CONTEXT_INVALIDATED;
        }
        if (!frame.topology().inBounds(old.targetCellX(), old.targetCellY())
                || !frame.topology().isWalkable(
                old.targetCellX(), old.targetCellY())
                || !frame.topology().reachable(
                squad.anchorCellX(), squad.anchorCellY(),
                old.targetCellX(), old.targetCellY())) {
            return CommandStabilityBreak.TARGET_UNREACHABLE;
        }
        return CommandStabilityBreak.NONE;
    }

    private AssaultSearchSnapshot buildSnapshot(
            AssaultCommandFrame frame, Phase phase, SectorReports reports,
            int[] loads, Map<Integer, SquadDirective> directives,
            boolean initialSearchComplete) {
        List<SquadDirective> directiveRows = new ArrayList<>(directives.values());
        directiveRows.sort(Comparator.comparingInt(SquadDirective::squadId));
        List<SectorState> sectorRows = new ArrayList<>(sectors.size());
        for (SearchSector sector : sectors) {
            int targetX = -1;
            int targetY = -1;
            for (SquadDirective directive : directiveRows) {
                if (directive.sectorIndex() == sector.index) {
                    targetX = directive.targetCellX();
                    targetY = directive.targetCellY();
                    break;
                }
            }
            sectorRows.add(new SectorState(sector.index, sector.minX, sector.minY,
                    sector.maxX - sector.minX + 1,
                    sector.maxY - sector.minY + 1,
                    reports.status(sector.index), sector.visitedCount(),
                    sector.legs.size(), reports.contactCount[sector.index],
                    reports.freshestTick[sector.index], loads[sector.index],
                    targetX, targetY));
        }
        List<AssaultSearchSnapshot.SquadState> squadRows = frame.squads().stream()
                .map(squad -> new AssaultSearchSnapshot.SquadState(squad.squadId(),
                        squad.aliveMembers(), squad.centroidX(), squad.centroidY(),
                        squad.currentZoneId(), squad.executionSuspension(),
                        squad.localContact()))
                .toList();
        return new AssaultSearchSnapshot(frame.tick(),
                frame.influence() != null ? frame.influence().updatedTick() : -1,
                frame.perspective(), phase, initialSearchComplete ? 2 : 1,
                sectorRows, squadRows, directiveRows);
    }

    private SectorReports reports(AssaultCommandFrame frame) {
        int count = sectors.size();
        int[] contacts = new int[count];
        int[] freshest = new int[count];
        java.util.Arrays.fill(freshest, -1);
        boolean[] active = new boolean[count];
        CommanderContact[] primary = new CommanderContact[count];
        CommanderInfluenceSnapshot influence = frame.influence();
        if (influence != null) {
            for (CommanderContact contact : influence.contacts()) {
                int sector = sectorForCell(contact.cellX(), contact.cellY());
                if (sector < 0) continue;
                contacts[sector]++;
                freshest[sector] = Math.max(freshest[sector], contact.observedTick());
                active[sector] |= contact.source() == BeliefSource.DIRECT
                        && frame.tick() - contact.observedTick() <= FRESH_DIRECT_TICKS;
                CommanderContact old = primary[sector];
                if (old == null || prefer(contact, old)) primary[sector] = contact;
            }
        }
        for (int i = 0; i < count; i++) {
            boolean reported = contacts[i] > 0;
            SearchSector sector = sectors.get(i);
            if (reported && !sector.reportedLastPulse) {
                java.util.Arrays.fill(sector.visited, false);
            }
            sector.reportedLastPulse = reported;
        }
        return new SectorReports(contacts, freshest, active, primary);
    }

    private static boolean prefer(CommanderContact candidate, CommanderContact old) {
        if (candidate.observedTick() != old.observedTick()) {
            return candidate.observedTick() > old.observedTick();
        }
        int confidence = Float.compare(candidate.confidence(), old.confidence());
        if (confidence != 0) return confidence > 0;
        return candidate.unitId() < old.unitId();
    }

    private int sectorForCell(int x, int y) {
        return layout != null ? layout.sectorForCell(x, y) : -1;
    }

    private int nearestReachableLeg(CommandSquadState squad, SearchSector sector,
                                    CommandTopology topology) {
        return nextReachableLeg(squad, sector, 0, false, topology);
    }

    private static int nextReachableLeg(CommandSquadState squad,
                                        SearchSector sector, int start,
                                        boolean unvisitedOnly,
                                        CommandTopology topology) {
        if (sector.legs.isEmpty()) return -1;
        for (int i = 0; i < sector.legs.size(); i++) {
            int index = Math.floorMod(start + i, sector.legs.size());
            if (unvisitedOnly && sector.visited[index]) continue;
            Cell cell = sector.legs.get(index);
            if (topology.reachable(squad.anchorCellX(), squad.anchorCellY(),
                    cell.x(), cell.y())) return index;
        }
        return -1;
    }

    private static int nearestLegTo(SearchSector sector, int x, int y,
                                    CommandSquadState squad,
                                    CommandTopology topology, int lane) {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < sector.legs.size(); i++) {
            Cell cell = sector.legs.get(i);
            if (topology.reachable(squad.anchorCellX(), squad.anchorCellY(),
                    cell.x(), cell.y())) candidates.add(i);
        }
        candidates.sort(Comparator.comparingInt((Integer index) -> {
            Cell cell = sector.legs.get(index);
            int dx = cell.x() - x;
            int dy = cell.y() - y;
            return dx * dx + dy * dy;
        }).thenComparingInt(Integer::intValue));
        return candidates.isEmpty() ? -1
                : candidates.get(Math.min(lane, candidates.size() - 1));
    }

    private static boolean arrived(CommandSquadState squad, int x, int y) {
        float dx = squad.centroidX() - (x + 0.5f);
        float dy = squad.centroidY() - (y + 0.5f);
        return dx * dx + dy * dy
                <= PatrolMotion.ARRIVAL_RADIUS * PatrolMotion.ARRIVAL_RADIUS;
    }

    private static boolean arrivedAtAssignment(CommandSquadState squad) {
        ObjectiveAssignment assignment = squad.assignment();
        return assignment != null && assignment.kind() == AssignmentKind.SWEEP_SECTOR
                && arrived(squad, assignment.targetCellX(),
                        assignment.targetCellY());
    }

    private static int legAt(SearchSector sector, int x, int y) {
        for (int i = 0; i < sector.legs.size(); i++) {
            Cell cell = sector.legs.get(i);
            if (cell.x() == x && cell.y() == y) return i;
        }
        return -1;
    }

    private static int exteriorZone(CommandTopology topology) {
        int largestId = -1;
        int largest = -1;
        int second = -1;
        for (CommandTopology.Zone zone : topology.zones()) {
            int size = zone.cellCount();
            if (size > largest) {
                second = largest;
                largest = size;
                largestId = zone.id();
            } else if (size > second) {
                second = size;
            }
        }
        return largestId >= 0 && (second <= 0
                || largest >= EXTERIOR_DOMINANCE_RATIO * second)
                ? largestId : -1;
    }

    private static List<Cell> buildSweepLegs(
            CommandTopology topology, int minX, int maxX, int minY, int maxY,
            int exteriorZone) {
        List<Cell> result = new ArrayList<>();
        int[][] samples = {
                {1, 2}, {3, 2}, {5, 2},
                {5, 5}, {3, 5}, {1, 5},
                {1, 8}, {3, 8}, {5, 8}
        };
        for (int[] sample : samples) {
            int x = minX + Math.max(0,
                    Math.round((maxX - minX) * sample[0] / 6f));
            int y = minY + Math.max(0,
                    Math.round((maxY - minY) * sample[1] / 10f));
            addDistinct(result, nearestWalkable(topology, x, y,
                    minX, maxX, minY, maxY));
        }
        for (CommandTopology.Zone zone : topology.zones()) {
            if (zone.id() == exteriorZone || zone.cellCount() == 0) continue;
            long sumX = 0;
            long sumY = 0;
            int inSector = 0;
            for (int cell : zone.cells()) {
                int x = cell % topology.width();
                int y = cell / topology.width();
                if (x < minX || x > maxX || y < minY || y > maxY) continue;
                sumX += x;
                sumY += y;
                inSector++;
            }
            if (inSector > 0) {
                addDistinct(result, nearestZoneCell(topology, zone,
                        Math.round((float) sumX / inSector),
                        Math.round((float) sumY / inSector),
                        minX, maxX, minY, maxY));
            }
        }
        return List.copyOf(result);
    }

    private static Cell nearestWalkable(CommandTopology topology, int targetX,
                                        int targetY, int minX, int maxX,
                                        int minY, int maxY) {
        Cell best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (!topology.isWalkable(x, y)) continue;
                int dx = x - targetX;
                int dy = y - targetY;
                int distance = dx * dx + dy * dy;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new Cell(x, y);
                }
            }
        }
        return best;
    }

    private static Cell nearestZoneCell(CommandTopology topology,
                                        CommandTopology.Zone zone,
                                        int targetX, int targetY,
                                        int minX, int maxX,
                                        int minY, int maxY) {
        Cell best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int cell : zone.cells()) {
            int x = cell % topology.width();
            int y = cell / topology.width();
            if (x < minX || x > maxX || y < minY || y > maxY) continue;
            int dx = x - targetX;
            int dy = y - targetY;
            int distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new Cell(x, y);
            }
        }
        return best;
    }

    private static void addDistinct(List<Cell> cells, Cell candidate) {
        if (candidate != null && !cells.contains(candidate)) cells.add(candidate);
    }

    public int sectorCount() { return sectors.size(); }
    public int sectorCols() { return sectorCols; }
    public int sectorRows() { return sectorRows; }
    public List<int[]> sweepLegsInSector(int sectorIndex) {
        if (sectorIndex < 0 || sectorIndex >= sectors.size()) return List.of();
        return sectors.get(sectorIndex).legs.stream()
                .map(cell -> new int[]{cell.x(), cell.y()}).toList();
    }

    private record Cell(int x, int y) { }
    private record SearchTarget(Cell cell, int legIndex) { }

    private static final class SearchSector {
        private final int index;
        private final int minX;
        private final int minY;
        private final int maxX;
        private final int maxY;
        private final List<Cell> legs;
        private final boolean[] visited;
        private int lastVisitedTick = -1;
        private boolean reportedLastPulse;

        private SearchSector(int index, int minX, int minY, int maxX, int maxY,
                             List<Cell> legs) {
            this.index = index;
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.legs = legs;
            this.visited = new boolean[legs.size()];
        }

        private boolean complete() {
            if (visited.length == 0) return true;
            for (boolean value : visited) if (!value) return false;
            return true;
        }

        private int visitedCount() {
            int count = 0;
            for (boolean value : visited) if (value) count++;
            return count;
        }
    }

    private final class SectorReports {
        private final int[] contactCount;
        private final int[] freshestTick;
        private final boolean[] active;
        private final CommanderContact[] primary;

        private SectorReports(int[] contactCount, int[] freshestTick,
                              boolean[] active, CommanderContact[] primary) {
            this.contactCount = contactCount;
            this.freshestTick = freshestTick;
            this.active = active;
            this.primary = primary;
        }

        private SectorStatus status(int sector) {
            if (active[sector]) return SectorStatus.ACTIVE;
            if (contactCount[sector] > 0) return SectorStatus.SUSPECTED;
            return sectors.get(sector).complete()
                    ? SectorStatus.SEARCHED : SectorStatus.SEARCHING;
        }

        private CommanderContact primaryContact(int sector) { return primary[sector]; }

        private boolean hasActiveOrSuspected() {
            for (int i = 0; i < contactCount.length; i++) {
                if (active[i] || contactCount[i] > 0) return true;
            }
            return false;
        }
    }
}

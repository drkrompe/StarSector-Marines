package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.fort.Fort;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Whether the side holding ground stands in its prepared positions: each sample counts, per faction,
 * the infantry squads by standing assignment kind, how many area-defence circles overlap fighting-position
 * cells, how many marines hold a claim and how many are dug in. Read off the simulation; it decides nothing.
 */
final class FortDefenderPositionProbe {

    private static final int SAMPLE_TICKS = 30;

    private static final class Tally {
        int samples;
        final Map<AssignmentKind, Integer> kinds = new EnumMap<>(AssignmentKind.class);
        int unassigned;
        int defendAreaSquads;
        int overlappingSquads;
        long claimedMarineSamples;
        long dugInMarineSamples;
        int peakClaimed;
        int peakDugIn;
        int peakOverlapping;
        /** Squad-samples whose garrison node stands its members on prepared ground (a belt garrison). */
        /** On-position marine-samples inside the works and out on the belt, and the distinct position cells each ever held. */
        long onWorksPositionSamples;
        long onBeltPositionSamples;
        final Set<Long> worksCellsEver = new HashSet<>();
        final Set<Long> beltCellsEver = new HashSet<>();
        long preparedGarrisonSquadSamples;
        int peakPreparedGarrisons;
        int peakInfantryMembers;
        int peakPreparedGarrisonMembers;
        /** Marine-samples standing on a fighting-position cell, claimed or not. */
        long onPositionMarineSamples;
        /** Squad-samples carrying a commander's run-scoped garrison order; distinct squads that ever did; peak at once. */
        long reactiveSquadSamples;
        int peakReactive;
        final Set<Integer> reactiveSquadsEver = new TreeSet<>();
        Set<Integer> reactiveNow = new TreeSet<>();
        int manEvents;
        int releaseEvents;
        long reactiveDugInMarineSamples;
        /** Per reactive squad: first ordered, first member on position, first member dug in, last sampled, samples, on-position member-samples, place. */
        final Map<Integer, int[]> timeline = new TreeMap<>();
        final Map<Integer, Map<String, Integer>> goals = new TreeMap<>();
        /** Every manning episode, in the order it began: a squad ordered twice has two. Fields as {@link #timeline}, then start distance and end kind. */
        final List<int[]> episodes = new ArrayList<>();
        /** The episode each reactive squad is in now. */
        final Map<Integer, int[]> openEpisode = new TreeMap<>();
        /** Squads with no standing assignment at all, counted per sample: the roving reserve. */
        long reserveSquadSamples;
        long infantrySquadSamples;
        /** Garrison squad-samples while the company stands at a fort, and those with no marine within {@link #IDLE_CELLS}. */
        long garrisonWhilePressed;
        long garrisonIdleWhilePressed;
        /** DEFEND_TRACK posts moved between two samples, and moves back to the post held before the last move. */
        int rallyRetargets;
        int rallyFlipBacks;
        final Map<Integer, int[]> rallyPost = new TreeMap<>();
        /** Marches (a new order cell at least {@link #MARCH_MIN_CELLS} away), per squad the one in progress. */
        final Map<Integer, int[]> march = new TreeMap<>();
        int marches;
        int marchesArrived;
        int longMarches;
        int longMarchesArrived;
        int marchMembersLost;
        int longMarchMembersLost;
        int marchesWiped;
        int longMarchesWiped;
    }

    /** A garrison with no marine this near (Chebyshev cells) is holding a stretch nobody is attacking. */
    private static final int IDLE_CELLS = 30;
    /** A marine this near (Chebyshev cells) a fort's centre means the fort is being attacked. */
    private static final int FORT_PRESSED_CELLS = 45;
    /** An order cell this far from the squad starts a march; one this near the squad's centroid ends it. */
    private static final int MARCH_MIN_CELLS = 30;
    private static final int ARRIVED_CELLS = 12;
    /** A march that takes at least this long, arrived or not, is a long one. */
    private static final int LONG_MARCH_TICKS = 1000;

    private final Map<Faction, Tally> tallies = new EnumMap<>(Faction.class);

    void sample(BattleSimulation sim) {
        if (sim.getSimTickIndex() % SAMPLE_TICKS != 0) return;
        FightingPositionIndex index = sim.fightingPositions();
        Map<Faction, int[]> now = new EnumMap<>(Faction.class);
        Map<Faction, Set<Integer>> reactiveThisSample = new EnumMap<>(Faction.class);
        List<float[]> marines = new ArrayList<>();
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.MARINE || squad.aliveMembers <= 0) continue;
            for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
                long member = sim.squadMemberAt(squad.id, i);
                if (sim.getRoster().isAliveById(member)) marines.add(new float[]{sim.world().x(member), sim.world().y(member)});
            }
        }
        boolean fortPressed = false;
        for (Fort fort : sim.forts().forts()) {
            float fx = 0f;
            float fy = 0f;
            int places = 0;
            for (Fort.Place place : fort.places()) {
                fx += place.x();
                fy += place.y();
                places++;
            }
            if (places == 0) continue;
            fx /= places;
            fy /= places;
            for (float[] m : marines) {
                if (Math.max(Math.abs(m[0] - fx), Math.abs(m[1] - fy)) <= FORT_PRESSED_CELLS) {
                    fortPressed = true;
                    break;
                }
            }
        }
        for (Squad squad : sim.getSquads()) {
            if (squad.isDroneSquad() || squad.isMechSquad() || squad.aliveMembers <= 0) continue;
            if (squad.faction != Faction.MARINE && squad.faction != Faction.DEFENDER && squad.faction != Faction.ALLY) continue;
            Tally tally = tallies.computeIfAbsent(squad.faction, f -> new Tally());
            int[] peak = now.computeIfAbsent(squad.faction, f -> new int[7]);
            peak[4] += squad.aliveMembers;
            ObjectiveAssignment assignment = squad.assignmentForExecution();
            if (squad.faction == Faction.DEFENDER) sampleMarch(tally, squad, assignment, sim.getSimTickIndex());
            if (squad.faction == Faction.DEFENDER && assignment != null && assignment.kind() == AssignmentKind.DEFEND_TRACK) {
                int[] post = tally.rallyPost.get(squad.id);
                int x = assignment.targetCellX();
                int y = assignment.targetCellY();
                if (post != null && (post[0] != x || post[1] != y)) {
                    tally.rallyRetargets++;
                    if (post[2] == x && post[3] == y) tally.rallyFlipBacks++;
                    tally.rallyPost.put(squad.id, new int[]{x, y, post[0], post[1]});
                } else if (post == null) {
                    tally.rallyPost.put(squad.id, new int[]{x, y, Integer.MIN_VALUE, Integer.MIN_VALUE});
                }
            } else if (squad.faction == Faction.DEFENDER) {
                tally.rallyPost.remove(squad.id);
            }
            if (assignment != null && assignment.isGarrison()) {
                if (squad.faction == Faction.DEFENDER && fortPressed) {
                    tally.garrisonWhilePressed++;
                    boolean near = false;
                    for (float[] m : marines) {
                        if (Math.max(Math.abs(m[0] - squad.centroidX), Math.abs(m[1] - squad.centroidY)) <= IDLE_CELLS) {
                            near = true;
                            break;
                        }
                    }
                    if (!near) tally.garrisonIdleWhilePressed++;
                }
                tally.reactiveSquadSamples++;
                peak[6]++;
                reactiveThisSample.computeIfAbsent(squad.faction, f -> new TreeSet<>()).add(squad.id);
                int[] line = tally.timeline.computeIfAbsent(squad.id, id -> new int[]{sim.getSimTickIndex(), -1, -1, 0, 0, 0, assignment.occupyRunId()});
                line[3] = sim.getSimTickIndex();
                int[] episode = tally.openEpisode.get(squad.id);
                if (episode == null) {
                    float dx = squad.centroidX - assignment.targetCellX();
                    float dy = squad.centroidY - assignment.targetCellY();
                    float ax = Math.abs(dx);
                    float ay = Math.abs(dy);
                    episode = new int[]{sim.getSimTickIndex(), -1, -1, 0, 0, 0, assignment.occupyRunId(),
                            Math.round(Math.max(ax, ay) + 0.41f * Math.min(ax, ay)), 0};
                    tally.openEpisode.put(squad.id, episode);
                    tally.episodes.add(episode);
                }
                episode[3] = sim.getSimTickIndex();
                episode[4]++;
                tally.goals.computeIfAbsent(squad.id, id -> new TreeMap<>()).merge(
                        (squad.currentGoal == null ? "none" : squad.currentGoal.name()) + (squad.hasDirectContactThisTick() ? "/contact" : ""), 1, Integer::sum);
                line[4]++;
                for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
                    long member = sim.squadMemberAt(squad.id, i);
                    if (index.isFightingPositionCell(sim.world().cellX(member), sim.world().cellY(member))) {
                        line[5]++;
                        episode[5]++;
                        if (line[1] < 0) line[1] = sim.getSimTickIndex();
                        if (episode[1] < 0) episode[1] = sim.getSimTickIndex();
                    }
                    if (sim.isDugIn(member)) {
                        tally.reactiveDugInMarineSamples++;
                        if (line[2] < 0) line[2] = sim.getSimTickIndex();
                        if (episode[2] < 0) episode[2] = sim.getSimTickIndex();
                    }
                }
            }
            tally.infantrySquadSamples++;
            if (assignment == null) {
                tally.unassigned++;
                tally.reserveSquadSamples++;
            } else {
                tally.kinds.merge(assignment.kind(), 1, Integer::sum);
                if (assignment.kind() == AssignmentKind.DEFEND_AREA && assignment.targetRadiusCells() >= 1) {
                    tally.defendAreaSquads++;
                    if (overlaps(index, assignment)) {
                        tally.overlappingSquads++;
                        peak[2]++;
                    }
                }
            }
            if (squad.assignedNode != null && !squad.assignedNode.standPositions().isEmpty()
                    && index.isFightingPositionCell(squad.assignedNode.standPositions().get(0).x(),
                    squad.assignedNode.standPositions().get(0).y())) {
                tally.preparedGarrisonSquadSamples++;
                peak[3]++;
                peak[5] += squad.aliveMembers;
            }
            for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
                long member = sim.squadMemberAt(squad.id, i);
                if (index.isFightingPositionCell(sim.world().cellX(member), sim.world().cellY(member))) {
                    tally.onPositionMarineSamples++;
                    int mx = sim.world().cellX(member), my = sim.world().cellY(member);
                    boolean works = false;
                    for (Fort fort : sim.forts().forts()) works |= fort.frame().isWorks(mx, my);
                    if (works) tally.onWorksPositionSamples++; else tally.onBeltPositionSamples++;
                    (works ? tally.worksCellsEver : tally.beltCellsEver).add(((long) my << 32) | mx);
                }
                if (sim.fightingPositionClaims().cellOf(member) != null) {
                    tally.claimedMarineSamples++;
                    peak[0]++;
                }
                if (sim.isDugIn(member)) {
                    tally.dugInMarineSamples++;
                    peak[1]++;
                }
            }
        }
        for (Map.Entry<Faction, int[]> entry : now.entrySet()) {
            Tally tally = tallies.get(entry.getKey());
            tally.samples++;
            tally.peakClaimed = Math.max(tally.peakClaimed, entry.getValue()[0]);
            tally.peakDugIn = Math.max(tally.peakDugIn, entry.getValue()[1]);
            tally.peakOverlapping = Math.max(tally.peakOverlapping, entry.getValue()[2]);
            tally.peakPreparedGarrisons = Math.max(tally.peakPreparedGarrisons, entry.getValue()[3]);
            tally.peakInfantryMembers = Math.max(tally.peakInfantryMembers, entry.getValue()[4]);
            tally.peakPreparedGarrisonMembers = Math.max(tally.peakPreparedGarrisonMembers, entry.getValue()[5]);
            tally.peakReactive = Math.max(tally.peakReactive, entry.getValue()[6]);
            Set<Integer> reactive = reactiveThisSample.getOrDefault(entry.getKey(), Set.of());
            for (int id : reactive) if (!tally.reactiveNow.contains(id)) tally.manEvents++;
            for (int id : tally.reactiveNow) {
                if (reactive.contains(id)) continue;
                tally.releaseEvents++;
                int[] episode = tally.openEpisode.remove(id);
                if (episode != null) episode[8] = endKind(sim, id);
            }
            tally.reactiveNow = new TreeSet<>(reactive);
            tally.reactiveSquadsEver.addAll(reactive);
        }
    }

    /**
     * Follows one squad's march: a new order cell at least {@link #MARCH_MIN_CELLS} from where it stands starts one;
     * reaching within {@link #ARRIVED_CELLS} of it, a new order, or the squad's death ends it. Fields: start tick, alive
     * at start, target x, target y, arrived tick.
     */
    private static void sampleMarch(Tally tally, Squad squad, ObjectiveAssignment assignment, int tick) {
        int[] m = tally.march.get(squad.id);
        boolean hasTarget = assignment != null && assignment.targetCellX() >= 0;
        if (m != null && (!hasTarget || m[2] != assignment.targetCellX() || m[3] != assignment.targetCellY())) {
            endMarch(tally, m, squad.aliveMembers, tick);
            tally.march.remove(squad.id);
            m = null;
        }
        if (!hasTarget) return;
        float gap = Math.max(Math.abs(squad.centroidX - assignment.targetCellX()), Math.abs(squad.centroidY - assignment.targetCellY()));
        if (m == null) {
            if (gap >= MARCH_MIN_CELLS) {
                tally.march.put(squad.id, new int[]{tick, squad.aliveMembers, assignment.targetCellX(), assignment.targetCellY(), -1});
            }
            return;
        }
        if (gap <= ARRIVED_CELLS) {
            m[4] = tick;
            endMarch(tally, m, squad.aliveMembers, tick);
            tally.march.remove(squad.id);
        }
    }

    private static void endMarch(Tally tally, int[] m, int aliveNow, int tick) {
        boolean arrived = m[4] >= 0;
        boolean longOne = tick - m[0] >= LONG_MARCH_TICKS;
        int lost = Math.max(0, m[1] - aliveNow);
        tally.marches++;
        tally.marchMembersLost += lost;
        if (arrived) tally.marchesArrived++;
        if (aliveNow <= 0) tally.marchesWiped++;
        if (longOne) {
            tally.longMarches++;
            tally.longMarchMembersLost += lost;
            if (arrived) tally.longMarchesArrived++;
            if (aliveNow <= 0) tally.longMarchesWiped++;
        }
    }

    /** 1 when the squad now holds an area defence that is not a garrison (a fall back or structure work), 2 for anything else. */
    private static int endKind(BattleSimulation sim, int squadId) {
        for (Squad squad : sim.getSquads()) {
            if (squad.id != squadId) continue;
            ObjectiveAssignment now = squad.assignmentForExecution();
            return now != null && now.kind() == AssignmentKind.DEFEND_AREA ? 1 : 2;
        }
        return 2;
    }

    private static boolean overlaps(FightingPositionIndex index, ObjectiveAssignment area) {
        int r = area.targetRadiusCells();
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dy * dy > r * r) continue;
                if (index.isFightingPositionCell(area.targetCellX() + dx, area.targetCellY() + dy)) return true;
            }
        }
        return false;
    }

    String describe() {
        StringBuilder out = new StringBuilder("- fighting-position use (sampled every " + SAMPLE_TICKS + " ticks):\n");
        for (Map.Entry<Faction, Tally> entry : tallies.entrySet()) {
            Tally t = entry.getValue();
            out.append(String.format(Locale.ROOT,
                    "  - %s: %d samples; DEFEND_AREA squad-samples %d (circle overlaps positions in %d);"
                            + " claimed marine-samples %d (peak %d), dug-in marine-samples %d (peak %d), peak overlapping squads %d;"
                            + " unassigned squad-samples %d; prepared-ground garrison squad-samples %d (peak %d squads, peak %d marines of %d infantry);"
                            + " on-position marine-samples %d; reactive-garrison squad-samples %d (peak %d, %d distinct squads, %d man events, %d release events, dug-in marine-samples %d); kinds %s%n",
                    entry.getKey(), t.samples, t.defendAreaSquads, t.overlappingSquads,
                    t.claimedMarineSamples, t.peakClaimed, t.dugInMarineSamples, t.peakDugIn, t.peakOverlapping,
                    t.unassigned, t.preparedGarrisonSquadSamples, t.peakPreparedGarrisons, t.peakPreparedGarrisonMembers, t.peakInfantryMembers, t.onPositionMarineSamples,
                    t.reactiveSquadSamples, t.peakReactive, t.reactiveSquadsEver.size(), t.manEvents, t.releaseEvents,
                    t.reactiveDugInMarineSamples, t.kinds));
            out.append(String.format(Locale.ROOT, "  - %s position split: on-position samples in the works %d (%d distinct cells), on the belt %d (%d distinct cells)%n",
                    entry.getKey(), t.onWorksPositionSamples, t.worksCellsEver.size(), t.onBeltPositionSamples, t.beltCellsEver.size()));
            if (entry.getKey() == Faction.DEFENDER) {
                out.append(String.format(Locale.ROOT,
                        "  - DEFENDER garrison focus: garrison squad-samples while the company is at a fort %d, of them with no marine within %d cells %d (%.0f%%);"
                                + " rally post moves %d, back to the post before %d;"
                                + " marches %d (arrived %d, wiped %d, members lost %d), of them %d ticks or longer %d (arrived %d, wiped %d, members lost %d)%n",
                        t.garrisonWhilePressed, IDLE_CELLS, t.garrisonIdleWhilePressed,
                        100.0 * t.garrisonIdleWhilePressed / Math.max(1, t.garrisonWhilePressed), t.rallyRetargets, t.rallyFlipBacks,
                        t.marches, t.marchesArrived, t.marchesWiped, t.marchMembersLost, LONG_MARCH_TICKS, t.longMarches,
                        t.longMarchesArrived, t.longMarchesWiped, t.longMarchMembersLost));
            }
            if (!t.episodes.isEmpty()) {
                int sent = t.episodes.size();
                int arrived = 0;
                int dugIn = 0;
                int enRouteReleased = 0;
                int toArea = 0;
                int stillOpen = 0;
                long arrivalTicks = 0;
                long startDistance = 0;
                for (int[] e : t.episodes) {
                    boolean open = t.openEpisode.containsValue(e);
                    startDistance += e[7];
                    if (e[1] >= 0) {
                        arrived++;
                        arrivalTicks += e[1] - e[0];
                    }
                    if (e[2] >= 0) dugIn++;
                    if (open) stillOpen++;
                    else if (e[1] < 0) enRouteReleased++;
                    if (!open && e[8] == 1) toArea++;
                }
                out.append(String.format(Locale.ROOT,
                        "  - %s manning episodes: sent %d, arrived %d (mean order to arrival %.0f ticks), dug in %d, released en route %d,"
                                + " ended in an area defence (fall back or structure work) %d, still open %d, mean start distance %.0f cells (octile);"
                                + " reserve squad-samples %d of %d infantry squad-samples (%.0f%%), DEFEND_TRACK squad-samples %d%n",
                        entry.getKey(), sent, arrived, arrived == 0 ? 0.0 : arrivalTicks / (double) arrived, dugIn, enRouteReleased,
                        toArea, stillOpen, startDistance / (double) sent, t.reserveSquadSamples, t.infantrySquadSamples,
                        100.0 * t.reserveSquadSamples / Math.max(1, t.infantrySquadSamples),
                        t.kinds.getOrDefault(AssignmentKind.DEFEND_TRACK, 0)));
                for (int[] e : t.episodes) {
                    out.append(String.format(Locale.ROOT,
                            "      episode: ordered %d, on position %d, dug in %d, last %d, start distance %d, end %s%n",
                            e[0], e[1], e[2], e[3], e[7], t.openEpisode.containsValue(e) ? "open" : e[8] == 1 ? "area" : "other"));
                }
            }
            for (Map.Entry<Integer, int[]> squadLine : t.timeline.entrySet()) {
                int[] l = squadLine.getValue();
                out.append(String.format(Locale.ROOT,
                        "    - reactive squad %d: ordered tick %d, first on position %d, first dug in %d, last sampled %d, %d samples, %d on-position member-samples, run %d%n",
                        squadLine.getKey(), l[0], l[1], l[2], l[3], l[4], l[5], l[6]));
                out.append("      goals ").append(t.goals.get(squadLine.getKey())).append(System.lineSeparator());
            }
        }
        return out.toString();
    }
}

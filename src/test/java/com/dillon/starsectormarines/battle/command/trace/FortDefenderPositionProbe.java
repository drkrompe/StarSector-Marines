package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

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
    }

    private final Map<Faction, Tally> tallies = new EnumMap<>(Faction.class);

    void sample(BattleSimulation sim) {
        if (sim.getSimTickIndex() % SAMPLE_TICKS != 0) return;
        FightingPositionIndex index = sim.fightingPositions();
        Map<Faction, int[]> now = new EnumMap<>(Faction.class);
        for (Squad squad : sim.getSquads()) {
            if (squad.isDroneSquad() || squad.isMechSquad() || squad.aliveMembers <= 0) continue;
            if (squad.faction != Faction.MARINE && squad.faction != Faction.DEFENDER && squad.faction != Faction.ALLY) continue;
            Tally tally = tallies.computeIfAbsent(squad.faction, f -> new Tally());
            int[] peak = now.computeIfAbsent(squad.faction, f -> new int[3]);
            ObjectiveAssignment assignment = squad.assignmentForExecution();
            if (assignment == null) {
                tally.unassigned++;
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
            for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
                long member = sim.squadMemberAt(squad.id, i);
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
        }
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
                            + " unassigned squad-samples %d; kinds %s%n",
                    entry.getKey(), t.samples, t.defendAreaSquads, t.overlappingSquads,
                    t.claimedMarineSamples, t.peakClaimed, t.dugInMarineSamples, t.peakDugIn, t.peakOverlapping,
                    t.unassigned, t.kinds));
        }
        return out.toString();
    }
}

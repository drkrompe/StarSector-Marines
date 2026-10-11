package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.goap.world.ApertureSpread;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Measures how bunched a side's squads are, as a side-channel of a replay.
 *
 * <p>Deliberately not part of {@link CommandTraceRecorder}: the canonical trace
 * is byte-compared between replays and schema-versioned, and a diagnostic that
 * is still being shaped should not move either. It reads the live simulation
 * every {@link #SAMPLE_EVERY_TICKS} ticks and never writes to it.
 *
 * <p>Three readings separate <em>where</em> a pile-up is made. Squads sharing
 * one assigned target says the allocation piled them on; squads linked within
 * {@link #LINK_RADIUS} cells says the paths did; the densest block of bodies
 * says what it looks like to the fight regardless of cause.
 */
final class SquadDensityProbe {

    static final int SAMPLE_EVERY_TICKS = 30;
    /** Two squads whose centroids are this close are standing on each other. */
    static final float LINK_RADIUS = 6f;
    /** Side of the square block in which bodies are counted. */
    static final int BLOCK = 8;

    /** One side at one instant. */
    record Reading(int tick, Faction faction, int squads, int bodies,
                   int linkedSquads, int largestCluster,
                   int maxSquadsOnOneTarget, int assignedSquads,
                   int densestBlock, String topTargets) { }

    private final List<Reading> readings = new ArrayList<>();
    /** What squads that stood within {@link #LINK_RADIUS} of another were doing, over the run. */
    private final Map<Faction, Map<String, Integer>> linkedProfile = new HashMap<>();

    void sample(BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        if (tick % SAMPLE_EVERY_TICKS != 0) return;
        for (Faction faction : new Faction[] {Faction.MARINE, Faction.DEFENDER}) {
            readings.add(read(sim, tick, faction, linkedProfile
                    .computeIfAbsent(faction, f -> new HashMap<>())));
        }
    }

    private static Reading read(BattleSimulation sim, int tick, Faction faction,
                                Map<String, Integer> profile) {
        List<float[]> centroids = new ArrayList<>();
        List<String> doing = new ArrayList<>();
        Map<String, Integer> perTarget = new HashMap<>();
        Map<Long, Integer> blocks = new HashMap<>();
        int bodies = 0;
        int assigned = 0;
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != faction) continue;
            int n = sim.squadMemberCount(squad.id);
            if (n <= 0) continue;
            float sx = 0f, sy = 0f;
            for (int i = 0; i < n; i++) {
                long unit = sim.squadMemberAt(squad.id, i);
                float x = sim.world().x(unit);
                float y = sim.world().y(unit);
                sx += x;
                sy += y;
                long key = ((long) ((int) x / BLOCK) << 32)
                        | ((int) y / BLOCK & 0xffffffffL);
                blocks.merge(key, 1, Integer::sum);
            }
            bodies += n;
            centroids.add(new float[] {sx / n, sy / n});
            doing.add(describe(sim, squad, sx / n, sy / n));
            ObjectiveAssignment task = squad.assignedObjective;
            if (task != null) {
                assigned++;
                perTarget.merge(targetKey(task), 1, Integer::sum);
            }
        }
        int[] cluster = clusters(centroids, LINK_RADIUS);
        boolean[] linked = linkedFlags(centroids, LINK_RADIUS);
        for (int i = 0; i < linked.length; i++) {
            if (linked[i]) profile.merge(doing.get(i), 1, Integer::sum);
        }
        int maxTarget = 0;
        for (int v : perTarget.values()) maxTarget = Math.max(maxTarget, v);
        int densest = 0;
        for (int v : blocks.values()) densest = Math.max(densest, v);
        return new Reading(tick, faction, centroids.size(), bodies,
                cluster[0], cluster[1], maxTarget, assigned, densest,
                topTargets(perTarget));
    }

    /** The three most-shared targets, as {@code key=squads}. */
    private static String topTargets(Map<String, Integer> perTarget) {
        return perTarget.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(3)
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(" "));
    }

    /**
     * What a squad is doing, in terms of where a clump forms: its current
     * action, and whether it stands in, near or far from the zone it was sent
     * to. A reading only, so the 18-cell "near" is an approximation.
     */
    private static String describe(BattleSimulation sim, Squad squad, float cx, float cy) {
        SquadPlan plan = squad.currentPlan;
        SquadPlan.Step step = plan != null ? plan.currentStep() : null;
        String action = step == null ? "no-plan"
                : step.action.name().replaceAll("\\[.*", "");
        ObjectiveAssignment task = squad.assignedObjective;
        String where = "no-zone";
        if (task != null && task.targetZoneId() >= 0) {
            if (sim.getZoneGraph().zoneIdAt((int) cx, (int) cy) == task.targetZoneId()) {
                where = "in-zone";
            } else {
                int[] cell = ApertureSpread.roomCell(
                        sim.getZoneGraph().zoneById(task.targetZoneId()),
                        sim.getGrid(), 0);
                if (cell != null) {
                    float dx = cell[0] + 0.5f - cx;
                    float dy = cell[1] + 0.5f - cy;
                    where = dx * dx + dy * dy <= 18 * 18 ? "near<=18" : "far>18";
                }
            }
        }
        return action + " / " + where;
    }

    /** For each point, whether another lies within the radius. */
    static boolean[] linkedFlags(List<float[]> points, float radius) {
        int n = points.size();
        boolean[] linked = new boolean[n];
        float r2 = radius * radius;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                float dx = points.get(i)[0] - points.get(j)[0];
                float dy = points.get(i)[1] - points.get(j)[1];
                if (dx * dx + dy * dy > r2) continue;
                linked[i] = true;
                linked[j] = true;
            }
        }
        return linked;
    }

    /** What a task points at: the zone, node or cell its kind is scoped to. */
    private static String targetKey(ObjectiveAssignment task) {
        TacticalNode node = task.targetNode();
        if (node != null) {
            return task.kind() + "@node" + node.anchorX + "," + node.anchorY;
        }
        if (task.targetZoneId() >= 0) {
            return task.kind() + "@zone" + task.targetZoneId();
        }
        return task.kind() + "@cell" + task.targetCellX() + ","
                + task.targetCellY();
    }

    /**
     * Single-linkage clustering of points under a radius.
     *
     * @return {points that have a neighbour within the radius, size of the
     *         largest connected cluster}
     */
    static int[] clusters(List<float[]> points, float radius) {
        int n = points.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        boolean[] linked = new boolean[n];
        float r2 = radius * radius;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                float dx = points.get(i)[0] - points.get(j)[0];
                float dy = points.get(i)[1] - points.get(j)[1];
                if (dx * dx + dy * dy > r2) continue;
                linked[i] = true;
                linked[j] = true;
                parent[find(parent, i)] = find(parent, j);
            }
        }
        int[] size = new int[n];
        int largest = n == 0 ? 0 : 1;
        int linkedCount = 0;
        for (int i = 0; i < n; i++) {
            if (linked[i]) linkedCount++;
            largest = Math.max(largest, ++size[find(parent, i)]);
        }
        return new int[] {linkedCount, largest};
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    List<Reading> readings() {
        return List.copyOf(readings);
    }

    String markdown(String runId) {
        StringBuilder out = new StringBuilder("# Squad density: ")
                .append(runId).append("\n\n");
        out.append("Sampled every ").append(SAMPLE_EVERY_TICKS)
                .append(" ticks. Linked = squad centroid within ")
                .append(LINK_RADIUS).append(" cells of another friendly squad; ")
                .append("block = ").append(BLOCK).append("x").append(BLOCK)
                .append(" cells.\n\n");
        for (Faction faction : new Faction[] {Faction.MARINE, Faction.DEFENDER}) {
            List<Reading> rows = readings.stream()
                    .filter(r -> r.faction == faction && r.squads > 1).toList();
            out.append("## ").append(faction).append("\n\n");
            if (rows.isEmpty()) {
                out.append("No samples with more than one squad.\n\n");
                continue;
            }
            out.append("| metric | mean | peak | peak tick |\n|---|---|---|---|\n");
            row(out, "squads", rows, Reading::squads);
            row(out, "linked squads", rows, Reading::linkedSquads);
            row(out, "linked share %", rows,
                    r -> 100 * r.linkedSquads / r.squads);
            row(out, "largest cluster (squads)", rows, Reading::largestCluster);
            row(out, "max squads on one target", rows,
                    Reading::maxSquadsOnOneTarget);
            row(out, "densest block (bodies)", rows, Reading::densestBlock);
            out.append("\nTimeline (tick: squads/linked/cluster/onTarget/block)\n\n");
            for (int i = 0; i < rows.size(); i += 10) {
                Reading r = rows.get(i);
                out.append(String.format(Locale.ROOT, "- %d: %d/%d/%d/%d/%d  %s%n",
                        r.tick, r.squads, r.linkedSquads, r.largestCluster,
                        r.maxSquadsOnOneTarget, r.densestBlock, r.topTargets));
            }
            out.append("Linked squads were doing (squad-samples):\n\n");
            linkedProfile.getOrDefault(faction, Map.of()).entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(8)
                    .forEach(e -> out.append("- ").append(e.getKey()).append(": ")
                            .append(e.getValue()).append('\n'));
            out.append('\n');
        }
        return out.toString();
    }

    private interface Metric { int of(Reading reading); }

    private static void row(StringBuilder out, String name,
                            List<Reading> rows, Metric metric) {
        double sum = 0;
        int peak = Integer.MIN_VALUE;
        int peakTick = 0;
        for (Reading r : rows) {
            int v = metric.of(r);
            sum += v;
            if (v > peak) {
                peak = v;
                peakTick = r.tick;
            }
        }
        out.append(String.format(Locale.ROOT, "| %s | %.1f | %d | %d |%n",
                name, sum / rows.size(), peak, peakTick));
    }
}

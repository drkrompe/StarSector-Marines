package com.dillon.starsectormarines.battle.command.influence;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Opt-in frozen-state evidence, never a second battle or a change to live command. */
public final class InfluenceResolutionEvidence {
    private static final int WARMUP_PAIRS = 5;
    private static final int MEASURED_PAIRS = 7;
    private static final int CONTROL_BLOCK = 8;
    private static final int SUBJECT_BLOCK = 16;

    private record Channel(String name, List<InfluenceSource> sources) { }
    private record Region(int index, int cells, double control, double subject) { }

    private InfluenceResolutionEvidence() { }

    /** Call only after advancing stops, outside all measured ticks and JFR recording. */
    public static JSONObject capture(BattleSimulation sim) throws JSONException {
        long started = System.nanoTime();
        NavigationGrid frozen = sim.getGrid().copyNavigationTopology();
        long gridCaptureNanos = System.nanoTime() - started;
        List<Channel> channels = new ArrayList<>();
        started = System.nanoTime();
        // Reuse production emitter filtering and belief aggregation. This temporary
        // service never refreshes, subscribes to deaths, or replaces live fields.
        try (CommanderInfluenceService source = new CommanderInfluenceService(frozen, sim.getRoster())) {
            for (Faction faction : new Faction[]{Faction.MARINE, Faction.DEFENDER}) {
                channels.add(new Channel(faction.name() + "_FRIENDLY",
                        List.copyOf(source.friendlySources(faction))));
                List<InfluenceSource> hostile = new ArrayList<>();
                for (CommanderContact contact : source.aggregateContacts(faction)) {
                    hostile.add(new InfluenceSource(contact.cellX(), contact.cellY(),
                            contact.confidence() * contact.strength()));
                }
                channels.add(new Channel(faction.name() + "_HOSTILE", List.copyOf(hostile)));
            }
        }
        long sourceCaptureNanos = System.nanoTime() - started;
        int[] sizes = {CONTROL_BLOCK, SUBJECT_BLOCK};
        long[][] topologyTimes = new long[2][MEASURED_PAIRS];
        long[][] propagationTimes = new long[2][MEASURED_PAIRS];
        InfluenceTopology[] topologies = new InfluenceTopology[2];
        for (int pair = -WARMUP_PAIRS; pair < MEASURED_PAIRS; pair++) {
            for (int order = 0; order < 2; order++) {
                int arm = (pair + WARMUP_PAIRS + order) & 1;
                started = System.nanoTime();
                InfluenceTopology topology = new InfluenceTopology(frozen, sizes[arm]);
                long topologyNanos = System.nanoTime() - started;
                started = System.nanoTime();
                for (Channel channel : channels) {
                    // Retaining one result scalar makes the work observably used.
                    float[] field = InfluenceFieldBuilder.propagate(topology, channel.sources());
                    checksum += field.length == 0 ? 0f : field[0];
                }
                long propagationNanos = System.nanoTime() - started;
                topologies[arm] = topology;
                if (pair >= 0) {
                    topologyTimes[arm][pair] = topologyNanos;
                    propagationTimes[arm][pair] = propagationNanos;
                }
            }
        }
        JSONArray arms = new JSONArray();
        for (int arm = 0; arm < 2; arm++) {
            InfluenceTopology topology = topologies[arm];
            long directedEdges = 0;
            for (int i = 0; i < topology.componentCount(); i++) directedEdges += topology.neighbors(i).length;
            arms.put(new JSONObject().put("blockSize", sizes[arm])
                    .put("blockCount", topology.blockCount()).put("componentCount", topology.componentCount())
                    .put("directedEdges", directedEdges).put("topologyBuild", timings(topologyTimes[arm]))
                    .put("fourChannelPropagation", timings(propagationTimes[arm])));
        }
        JSONArray comparisons = new JSONArray();
        for (Channel channel : channels) {
            InfluenceFieldBuilder.Work controlWork = new InfluenceFieldBuilder.Work();
            InfluenceFieldBuilder.Work subjectWork = new InfluenceFieldBuilder.Work();
            float[] control = InfluenceFieldBuilder.propagate(topologies[0], channel.sources(), controlWork);
            float[] subject = InfluenceFieldBuilder.propagate(topologies[1], channel.sources(), subjectWork);
            comparisons.put(new JSONObject().put("channel", channel.name())
                    .put("sourceCount", channel.sources().size())
                    .put("controlSourceGroups", controlWork.sourceGroups)
                    .put("subjectSourceGroups", subjectWork.sourceGroups)
                    .put("controlVisitedComponents", controlWork.visitedComponents)
                    .put("subjectVisitedComponents", subjectWork.visitedComponents)
                    .put("difference", compareFields(frozen, control, subject)));
        }
        JSONObject storage = compareSnapshotStorage(frozen);
        return new JSONObject().put("schema", 2).put("captureTick", sim.getSimTickIndex())
                .put("worldWidth", frozen.getWidth()).put("worldHeight", frozen.getHeight())
                .put("gridCaptureMs", millis(gridCaptureNanos)).put("sourceCaptureMs", millis(sourceCaptureNanos))
                .put("warmupPairs", WARMUP_PAIRS).put("measuredPairs", MEASURED_PAIRS)
                .put("semantics", "Same frozen navigation and production own-force/believed-hostile sources; "
                        + "alternating paired rebuilds, separate cold topology and four-channel propagation timings. "
                        + "16-cell attenuation scales graph hops to the 8-cell reference distance. "
                        + "No fields are published, no strategies rerun, and casualty memory/route costs remain unchanged. "
                        + "Timings are diagnostic: background battle workers may still finish already-submitted work. "
                        + "This is field cost and approximation evidence, not battle outcomes or whole-commander speedup. "
                        + "Topology still visits fine map cells; blocks and fine-connected components are different counts.")
                .put("arms", arms).put("channels", comparisons).put("isolatedWallProbes", wallProbes())
                .put("snapshotStorage", storage);
    }

    private static volatile double checksum;
    private static volatile NavigationGrid copiedGrid;

    private static JSONObject compareSnapshotStorage(NavigationGrid frozen) throws JSONException {
        long[][] times = new long[2][MEASURED_PAIRS];
        int flagsHash = Arrays.hashCode(frozen.getCellFlagsArray());
        int edgesHash = Arrays.hashCode(frozen.getEdgePassabilityArray());
        for (int pair = -WARMUP_PAIRS; pair < MEASURED_PAIRS; pair++) {
            for (int order = 0; order < 2; order++) {
                int arm = (pair + WARMUP_PAIRS + order) & 1;
                long started = System.nanoTime();
                NavigationGrid copy = arm == 0
                        ? frozen.copyNavigationTopology() : frozen.copyRoutingTopology();
                long elapsed = System.nanoTime() - started;
                // Escape the whole result, then check copied inputs outside the timed
                // allocation/copy envelope. Both arms read exactly the same frozen grid.
                copiedGrid = copy;
                int copiedFlagsHash = Arrays.hashCode(copy.getCellFlagsArray());
                int copiedEdgesHash = Arrays.hashCode(copy.getEdgePassabilityArray());
                checksum += copiedFlagsHash + (double) copiedEdgesHash;
                if (copiedFlagsHash != flagsHash || copiedEdgesHash != edgesHash) {
                    throw new AssertionError("Snapshot storage control changed routing inputs");
                }
                if (pair >= 0) times[arm][pair] = elapsed;
            }
        }
        copiedGrid = null;
        return new JSONObject().put("warmupPairs", WARMUP_PAIRS).put("measuredPairs", MEASURED_PAIRS)
                .put("semantics", "Independent of influence resolution: full ancillary storage versus routing-only "
                        + "storage copied from the same frozen fine grid, alternating arms per pair. "
                        + "Timing includes construction and the two array copies, excludes checksums. "
                        + "Routing input checksums match in every arm. Timing is diagnostic, not a whole-tick speedup.")
                .put("cellFlagsHash", flagsHash).put("edgePassabilityHash", edgesHash)
                .put("fullCopyNavigationTopology", timings(times[0]))
                .put("compactCopyRoutingTopology", timings(times[1]))
                .put("payload", snapshotPayload(frozen.getWidth(), frozen.getHeight()));
    }

    /** Per-cell backing-array payload from NavigationGrid's two constructor shapes, not heap size. */
    static JSONObject snapshotPayload(int width, int height) throws JSONException {
        long cells = (long) width * height;
        int requiredBytesPerCell = Long.BYTES + Byte.BYTES;
        int avoidedBytesPerCell = 2 * NavigationGrid.FACING_COUNT * (Byte.BYTES + Float.BYTES)
                + Integer.BYTES + Short.BYTES;
        return new JSONObject().put("cells", cells)
                .put("requiredPrimitiveBytesPerCell", requiredBytesPerCell)
                .put("avoidedPrimitiveBytesPerCell", avoidedBytesPerCell)
                .put("compactPerCellPrimitivePayloadBytes", cells * requiredBytesPerCell)
                .put("fullPerCellPrimitivePayloadBytes", cells * (requiredBytesPerCell + avoidedBytesPerCell))
                .put("avoidedPrimitivePayloadBytes", cells * avoidedBytesPerCell)
                .put("avoidedReferenceSlots", cells * 2)
                .put("semantics", "Exact per-cell array element payload: routing long flags + byte edges; "
                        + "omitted two cover bytes and two float heights per facing, int wall HP, short opacity, "
                        + "plus two barrier-reference slots per cell. Reference slot width is JVM-dependent and "
                        + "not converted to bytes. Excludes fixed arrays (including the topology change log), "
                        + "array/object headers, alignment and other objects. Not a measured total heap allocation.");
    }

    /** Cell-weighted comparison; region ranks use the same 16-cell footprint in both arms. */
    static JSONObject compareFields(NavigationGrid grid, float[] control, float[] subject) throws JSONException {
        int controlWidth = (grid.getWidth() + CONTROL_BLOCK - 1) / CONTROL_BLOCK;
        int subjectWidth = (grid.getWidth() + SUBJECT_BLOCK - 1) / SUBJECT_BLOCK;
        int subjectHeight = (grid.getHeight() + SUBJECT_BLOCK - 1) / SUBJECT_BLOCK;
        double[] regionControl = new double[subjectWidth * subjectHeight];
        double[] regionSubject = new double[regionControl.length];
        int[] regionCells = new int[regionControl.length];
        int count = 0, controlPositive = 0, subjectPositive = 0, supportChanged = 0;
        double absolute = 0, squared = 0, maximum = 0, controlSum = 0, subjectSum = 0;
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (!grid.isWalkable(x, y)) continue;
                double before = control[(y / CONTROL_BLOCK) * controlWidth + x / CONTROL_BLOCK];
                int region = (y / SUBJECT_BLOCK) * subjectWidth + x / SUBJECT_BLOCK;
                double after = subject[region];
                double difference = Math.abs(after - before);
                count++;
                absolute += difference;
                squared += difference * difference;
                maximum = Math.max(maximum, difference);
                controlSum += before;
                subjectSum += after;
                if (before > 0) controlPositive++;
                if (after > 0) subjectPositive++;
                if ((before > 0) != (after > 0)) supportChanged++;
                regionControl[region] += before;
                regionSubject[region] += after;
                regionCells[region]++;
            }
        }
        List<Region> regions = new ArrayList<>();
        for (int i = 0; i < regionCells.length; i++) {
            if (regionCells[i] > 0) regions.add(new Region(i, regionCells[i],
                    regionControl[i] / regionCells[i], regionSubject[i] / regionCells[i]));
        }
        Set<Integer> controlHot = hotspots(regions, false);
        Set<Integer> subjectHot = hotspots(regions, true);
        Set<Integer> union = new HashSet<>(controlHot);
        union.addAll(subjectHot);
        Set<Integer> intersection = new HashSet<>(controlHot);
        intersection.retainAll(subjectHot);
        regions.sort(Comparator.<Region>comparingDouble(region ->
                Math.abs(region.subject() - region.control())).reversed().thenComparingInt(Region::index));
        JSONArray changedRegions = new JSONArray();
        for (int i = 0; i < Math.min(10, regions.size()); i++) {
            Region region = regions.get(i);
            changedRegions.put(new JSONObject().put("worldX", (region.index() % subjectWidth) * SUBJECT_BLOCK)
                    .put("worldY", (region.index() / subjectWidth) * SUBJECT_BLOCK)
                    .put("walkableCells", region.cells()).put("controlMean", region.control())
                    .put("subjectMean", region.subject()));
        }
        return new JSONObject().put("walkableCells", count)
                .put("meanAbsoluteError", count == 0 ? 0 : absolute / count)
                .put("rootMeanSquareError", count == 0 ? 0 : Math.sqrt(squared / count))
                .put("maxAbsoluteError", maximum)
                .put("absoluteErrorOverControlMass", controlSum == 0 ? JSONObject.NULL : absolute / controlSum)
                .put("controlMean", count == 0 ? 0 : controlSum / count)
                .put("subjectMean", count == 0 ? 0 : subjectSum / count)
                .put("controlPositiveCells", controlPositive).put("subjectPositiveCells", subjectPositive)
                .put("supportChangedCells", supportChanged)
                .put("hotspotSemantics", "Top ceil(10% of positive regions) ranked by walkable-cell mean "
                        + "on common 16-cell footprints; ties use row-major order. Empty union is unavailable.")
                .put("controlHotRegions", controlHot.size()).put("subjectHotRegions", subjectHot.size())
                .put("hotRegionIntersection", intersection.size()).put("hotRegionUnion", union.size())
                .put("hotRegionJaccard", union.isEmpty() ? JSONObject.NULL : (double) intersection.size() / union.size())
                .put("largestMeanDifferenceRegions", changedRegions);
    }

    private static Set<Integer> hotspots(List<Region> regions, boolean subject) {
        List<Region> positive = new ArrayList<>();
        for (Region region : regions) if ((subject ? region.subject() : region.control()) > 0) positive.add(region);
        positive.sort(Comparator.<Region>comparingDouble(region ->
                subject ? region.subject() : region.control()).reversed().thenComparingInt(Region::index));
        Set<Integer> result = new HashSet<>();
        int size = (positive.size() + 9) / 10;
        for (int i = 0; i < size; i++) result.add(positive.get(i).index());
        return result;
    }

    static JSONArray wallProbes() throws JSONException {
        JSONArray probes = new JSONArray();
        for (boolean opening : new boolean[]{false, true}) {
            NavigationGrid grid = new NavigationGrid(32, 16);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 32; x++) if (x != 4) grid.setWalkableFloor(x, y);
            }
            if (opening) grid.setWalkableFloor(4, 3);
            for (int size : new int[]{CONTROL_BLOCK, SUBJECT_BLOCK}) {
                InfluenceTopology topology = new InfluenceTopology(grid, size);
                float[] field = InfluenceFieldBuilder.propagate(topology, List.of(new InfluenceSource(2, 3, 1f)));
                probes.put(new JSONObject().put("opening", opening).put("blockSize", size)
                        .put("sourceX", 2).put("sourceY", 3).put("wallX", 4)
                        .put("probeX", 10).put("probeY", 3).put("farProbeX", 18)
                        .put("graphReachableAcrossWall", graphReachable(topology, 2, 3, 10, 3))
                        .put("probeSharesSourceBlock", 2 / size == 10 / size)
                        .put("acrossWallValue", field[10 / size])
                        .put("farAcrossWallValue", field[18 / size]));
            }
        }
        return probes;
    }

    private static boolean graphReachable(InfluenceTopology topology, int x, int y, int goalX, int goalY) {
        int[] starts = topology.componentsForCell(x, y);
        int[] goals = topology.componentsForCell(goalX, goalY);
        boolean[] seen = new boolean[topology.componentCount()];
        int[] queue = new int[seen.length];
        int head = 0, tail = 0;
        for (int start : starts) { seen[start] = true; queue[tail++] = start; }
        while (head < tail) {
            int next = queue[head++];
            for (int goal : goals) if (goal == next) return true;
            for (int neighbor : topology.neighbors(next)) {
                if (!seen[neighbor]) { seen[neighbor] = true; queue[tail++] = neighbor; }
            }
        }
        return false;
    }

    private static JSONObject timings(long[] nanos) throws JSONException {
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        JSONArray samples = new JSONArray();
        for (long value : nanos) samples.put(millis(value));
        return new JSONObject().put("samplesMs", samples).put("medianMs", millis(sorted[sorted.length / 2]))
                .put("minMs", millis(sorted[0])).put("maxMs", millis(sorted[sorted.length - 1]));
    }

    private static double millis(long nanos) { return nanos / 1_000_000.0; }
}

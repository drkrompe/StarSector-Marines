package com.dillon.starsectormarines.battle.command.influence;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;

/** Topology-aware additive propagation into tactical blocks. */
final class InfluenceFieldBuilder {

    static final float ATTENUATION = 0.85f;
    static final float MIN_PROPAGATED_VALUE = 0.05f;

    private InfluenceFieldBuilder() {}

    static float[] propagate(InfluenceTopology topology, List<InfluenceSource> sources) {
        float[] result = new float[topology.blockCount()];
        int[] distance = new int[topology.componentCount()];
        int[] queue = new int[topology.componentCount()];
        float[] sourceBlocks = new float[topology.blockCount()];
        for (InfluenceSource source : sources) {
            if (source.magnitude() <= 0f) continue;
            int[] starts = topology.componentsForCell(source.cellX(), source.cellY());
            if (starts.length == 0) continue;
            float splitMagnitude = source.magnitude() / starts.length;
            for (int start : starts) {
                Arrays.fill(distance, -1);
                Arrays.fill(sourceBlocks, 0f);
                int head = 0;
                int tail = 0;
                queue[tail++] = start;
                distance[start] = 0;
                while (head < tail) {
                    int component = queue[head++];
                    int steps = distance[component];
                    float value = splitMagnitude * (float) Math.pow(ATTENUATION, steps);
                    if (value < MIN_PROPAGATED_VALUE) continue;
                    int block = topology.blockForComponent(component);
                    sourceBlocks[block] = Math.max(sourceBlocks[block], value);
                    for (int neighbor : topology.neighbors(component)) {
                        if (distance[neighbor] >= 0) continue;
                        distance[neighbor] = steps + 1;
                        queue[tail++] = neighbor;
                    }
                }
                for (int block = 0; block < result.length; block++) {
                    result[block] += sourceBlocks[block];
                }
            }
        }
        return result;
    }
}

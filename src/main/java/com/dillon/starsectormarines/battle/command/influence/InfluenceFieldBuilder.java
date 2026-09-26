package com.dillon.starsectormarines.battle.command.influence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

/** Topology-aware additive propagation into tactical blocks. */
final class InfluenceFieldBuilder {

    static final float ATTENUATION = 0.85f;
    static final float MIN_PROPAGATED_VALUE = 0.05f;
    private static final float[] ATTENUATION_BY_DISTANCE = attenuationByDistance();

    private InfluenceFieldBuilder() {}

    static float[] propagate(InfluenceTopology topology, List<InfluenceSource> sources) {
        float[] result = new float[topology.blockCount()];
        int[] distance = new int[topology.componentCount()];
        int[] visitedGeneration = new int[topology.componentCount()];
        int[] queue = new int[topology.componentCount()];
        float[] sourceBlocks = new float[topology.blockCount()];
        int[] touchedBlocks = new int[topology.blockCount()];

        // A squad commonly puts several same-strength soldiers in one fine
        // component. Their attenuation shape is identical, so traverse the
        // component graph once and retain the number of identical emitters.
        // Magnitude bits are part of the key so the per-emitter cutoff below
        // remains exactly the same as source-major propagation.
        Map<PropagationKey, Integer> groups = new LinkedHashMap<>();
        for (InfluenceSource source : sources) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            if (source.magnitude() <= 0f) continue;
            int[] starts = topology.componentsForCell(source.cellX(), source.cellY());
            if (starts.length == 0) continue;
            float splitMagnitude = source.magnitude() / starts.length;
            for (int start : starts) {
                PropagationKey key = new PropagationKey(start,
                        Float.floatToIntBits(splitMagnitude));
                groups.merge(key, 1, Integer::sum);
            }
        }

        int generation = 0;
        for (Map.Entry<PropagationKey, Integer> entry : groups.entrySet()) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            PropagationKey key = entry.getKey();
            float splitMagnitude = Float.intBitsToFloat(key.magnitudeBits());
            generation++;
            int head = 0;
            int tail = 0;
            int touchedCount = 0;
            queue[tail++] = key.component();
            visitedGeneration[key.component()] = generation;
            distance[key.component()] = 0;
            while (head < tail) {
                int component = queue[head++];
                int steps = distance[component];
                float value = splitMagnitude * attenuationAt(steps);
                if (value < MIN_PROPAGATED_VALUE) continue;
                int block = topology.blockForComponent(component);
                if (sourceBlocks[block] == 0f) touchedBlocks[touchedCount++] = block;
                sourceBlocks[block] = Math.max(sourceBlocks[block], value);
                for (int neighbor : topology.neighbors(component)) {
                    if (visitedGeneration[neighbor] == generation) continue;
                    visitedGeneration[neighbor] = generation;
                    distance[neighbor] = steps + 1;
                    queue[tail++] = neighbor;
                }
            }
            int emitterCount = entry.getValue();
            for (int i = 0; i < touchedCount; i++) {
                int block = touchedBlocks[i];
                result[block] += sourceBlocks[block] * emitterCount;
                sourceBlocks[block] = 0f;
            }
        }
        return result;
    }

    private static float[] attenuationByDistance() {
        float[] values = new float[64];
        for (int i = 0; i < values.length; i++) {
            values[i] = (float) Math.pow(ATTENUATION, i);
        }
        return values;
    }

    private static float attenuationAt(int steps) {
        return steps < ATTENUATION_BY_DISTANCE.length
                ? ATTENUATION_BY_DISTANCE[steps]
                : (float) Math.pow(ATTENUATION, steps);
    }

    private record PropagationKey(int component, int magnitudeBits) { }
}

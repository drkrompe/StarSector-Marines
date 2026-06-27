package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

import java.util.List;
import java.util.Random;

/**
 * Data-driven tunables for one {@link BlockKind}'s code filler — the {@code fillers}
 * half of the moddable-tilesets Phase 2 mapping JSON. Carries the pools + chances
 * a filler used to hardcode (e.g. {@code NatureZoneFiller}'s ground mix, plant /
 * rock scatter chances, and overlay pools); the carve/scatter <em>algorithm</em>
 * stays in the filler. Loaded by {@link GenMappingRegistry} from
 * {@code data/tilesets/*.mapping.json}.
 *
 * <p>The {@code pickXxx} helpers consume RNG in the exact shape the filler used
 * pre-migration (one {@link Random#nextFloat()} for the weighted ground pick; one
 * {@link Random#nextInt(int)} for a pool pick) so seeded generation is preserved.
 */
public final class FillerParams {

    private final GroundKind[] groundKinds;
    private final int[] groundWeights;
    private final int groundTotal;
    public final float plantChance;
    public final float rockChance;
    private final List<String> plantPool;
    private final List<String> rockPool;

    public FillerParams(List<GroundKind> groundKinds, List<Integer> groundWeights,
                        float plantChance, float rockChance,
                        List<String> plantPool, List<String> rockPool) {
        this.groundKinds = groundKinds.toArray(new GroundKind[0]);
        this.groundWeights = new int[groundWeights.size()];
        int acc = 0;
        for (int i = 0; i < groundWeights.size(); i++) {
            this.groundWeights[i] = groundWeights.get(i);
            acc += groundWeights.get(i);
        }
        this.groundTotal = acc;
        this.plantChance = plantChance;
        this.rockChance = rockChance;
        this.plantPool = List.copyOf(plantPool);
        this.rockPool = List.copyOf(rockPool);
    }

    /**
     * Weighted ground pick — one {@link Random#nextFloat()} compared against the
     * cumulative-weight thresholds, in pool order. Reproduces the former
     * {@code pickBaseGround} cumulative-threshold logic exactly (weights summing to
     * 100 give thresholds like {@code 0.80f}/{@code 0.95f}). Returns {@code fallback}
     * when no ground pool is authored (e.g. wetland, whose base is a carve).
     */
    public GroundKind pickGround(Random rng, GroundKind fallback) {
        if (groundKinds.length == 0 || groundTotal <= 0) return fallback;
        float roll = rng.nextFloat();
        int acc = 0;
        for (int i = 0; i < groundKinds.length; i++) {
            acc += groundWeights[i];
            if (roll < acc / (float) groundTotal) return groundKinds[i];
        }
        return groundKinds[groundKinds.length - 1];
    }

    /** Uniform pick from the plant overlay pool — one {@link Random#nextInt(int)} over the ordered ids. */
    public String pickPlantId(Random rng) { return plantPool.get(rng.nextInt(plantPool.size())); }

    /** Uniform pick from the rock overlay pool — one {@link Random#nextInt(int)} over the ordered ids. */
    public String pickRockId(Random rng) { return rockPool.get(rng.nextInt(rockPool.size())); }

    // ---- inspection (parity tests) ----
    public int groundEntries()            { return groundKinds.length; }
    public GroundKind groundKind(int i)   { return groundKinds[i]; }
    public int groundWeight(int i)        { return groundWeights[i]; }
    public List<String> plantPool()       { return plantPool; }
    public List<String> rockPool()        { return rockPool; }
}

package com.dillon.starsectormarines.battle.evacuation;

import java.util.List;

/**
 * Immutable, non-human Rescue pressure-director picture. Once the director is
 * {@code boreSourcing}, {@code targetPopulation} is the living bores'
 * capacities summed and {@code bores} carries every bore that has opened.
 */
public record SwarmPressureSnapshot(
        int revision,
        int tick,
        Phase phase,
        String pressureReason,
        int targetPopulation,
        int populationFloor,
        int liveRunners,
        int waveIndex,
        List<ApproachState> approaches,
        List<TargetState> targetContexts,
        List<WaveIntent> ownedWave,
        boolean boreSourcing,
        List<BoreState> bores) {

    public enum Phase { OPENING, COHORT_RELEASED, TERMINAL }
    public enum Approach { NORTH, EAST, SOUTH, WEST }
    public enum TargetContext { ROAMING, MARINE_SCREEN, COHORT_CONTACT }

    public record ApproachState(
            Approach approach,
            int liveRunners,
            int ownedWaveRunners) { }

    public record TargetState(TargetContext context, int runnerCount) { }

    public record WaveIntent(
            long runnerId,
            Approach approach,
            int spawnCellX,
            int spawnCellY,
            TargetContext targetContext,
            String reason) { }

    /**
     * One bore as the director sees it.
     *
     * @param key           {@code N} for a first-wave bore, {@code mother} for the mother
     * @param integrity     hit points and armour left as a share of full, 0 once collapsed
     * @param growth        growth points toward the next tier
     * @param biomassFed    biomass delivered over the battle, in colonists' bodies
     * @param bodiesSpawned bodies its surges have put on the field
     * @param sporeColonies  its spore colonies still alive
     * @param collapsedTick the tick it collapsed, {@code -1} while it lives
     */
    public record BoreState(
            String key,
            boolean mother,
            long entityId,
            int x,
            int y,
            BoreTier tier,
            boolean alive,
            float integrity,
            float growth,
            float biomassFed,
            int feeds,
            int surges,
            int bodiesSpawned,
            int sporeColonies,
            int nextSurgeTick,
            int eruptedTick,
            int collapsedTick) { }

    public SwarmPressureSnapshot {
        approaches = List.copyOf(approaches);
        targetContexts = List.copyOf(targetContexts);
        ownedWave = List.copyOf(ownedWave);
        bores = bores == null ? List.of() : List.copyOf(bores);
    }
}

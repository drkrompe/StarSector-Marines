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
        List<BoreState> bores,
        StageCounts livingByStage) {

    public enum Phase { OPENING, COHORT_RELEASED, TERMINAL }
    public enum Approach { NORTH, EAST, SOUTH, WEST }
    public enum TargetContext { ROAMING, MARINE_SCREEN, COHORT_CONTACT }

    public record ApproachState(
            Approach approach,
            int liveRunners,
            int ownedWaveRunners) { }

    public record TargetState(TargetContext context, int runnerCount) { }

    /** Bodies, or weights out of a hundred, by growth stage. */
    public record StageCounts(int whelps, int adults, int elders) {
        public static final StageCounts NONE = new StageCounts(0, 0, 0);

        /** From {@code {whelp, adult, elder}}. */
        public static StageCounts of(int[] byStage) {
            return new StageCounts(byStage[0], byStage[1], byStage[2]);
        }
    }

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
     * @param biomassFed    biomass delivered over the battle
     * @param bodiesSpawned bodies its surges have put on the field
     * @param sporeColonies  its spore colonies still alive
     * @param collapsedTick the tick it collapsed, {@code -1} while it lives
     * @param ageMix        the stage weights its next surge draws from, out of a hundred
     * @param released      the bodies its surges have released, by stage
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
            int collapsedTick,
            StageCounts ageMix,
            StageCounts released) { }

    /** A picture with no growth-stage counts. */
    public SwarmPressureSnapshot(int revision, int tick, Phase phase, String pressureReason, int targetPopulation,
                                 int populationFloor, int liveRunners, int waveIndex, List<ApproachState> approaches,
                                 List<TargetState> targetContexts, List<WaveIntent> ownedWave, boolean boreSourcing,
                                 List<BoreState> bores) {
        this(revision, tick, phase, pressureReason, targetPopulation, populationFloor, liveRunners, waveIndex,
                approaches, targetContexts, ownedWave, boreSourcing, bores, StageCounts.NONE);
    }

    public SwarmPressureSnapshot {
        livingByStage = livingByStage == null ? StageCounts.NONE : livingByStage;
        approaches = List.copyOf(approaches);
        targetContexts = List.copyOf(targetContexts);
        ownedWave = List.copyOf(ownedWave);
        bores = bores == null ? List.of() : List.copyOf(bores);
    }
}

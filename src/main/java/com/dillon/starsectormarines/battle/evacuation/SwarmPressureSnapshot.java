package com.dillon.starsectormarines.battle.evacuation;

import java.util.List;

/** Immutable, non-human Rescue pressure-director picture. */
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
        List<WaveIntent> ownedWave) {

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

    public SwarmPressureSnapshot {
        approaches = List.copyOf(approaches);
        targetContexts = List.copyOf(targetContexts);
        ownedWave = List.copyOf(ownedWave);
    }
}

package com.dillon.starsectormarines.battle.ui.comms;

import com.dillon.starsectormarines.battle.command.reinforcement.CounterattackSystem;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.BiomeKind;

/**
 * Converts the conquest counterattack state machine into player-POV comms
 * dispatches. It deliberately observes public state rather than owning any
 * gameplay callbacks: presentation remains downstream of simulation state.
 */
public final class CounterattackCommsPresenter {

    private static final float WARNING_DURATION_SEC = 8f;
    private static final float LAUNCH_DURATION_SEC = 6f;
    private static final float OUTCOME_DURATION_SEC = 9f;

    private CounterattackSystem observed;
    private CounterattackSystem.Phase previousPhase = CounterattackSystem.Phase.IDLE;
    private CounterattackSystem.Resolution previousResolution = CounterattackSystem.Resolution.NONE;

    public void update(BattleSimulation sim, BattleCommsFeed feed) {
        CounterattackSystem current = sim == null ? null : sim.getCounterattackSystem();
        if (current != observed) {
            observed = current;
            previousPhase = CounterattackSystem.Phase.IDLE;
            previousResolution = CounterattackSystem.Resolution.NONE;
        }
        if (current == null) return;

        CounterattackSystem.Phase phase = current.getPhase();
        CounterattackSystem.Resolution resolution = current.getResolution();
        BiomeKind slice = current.getBulgeSlice();

        if (slice != null) {
            if (phase == CounterattackSystem.Phase.TELEGRAPH
                    && previousPhase != CounterattackSystem.Phase.TELEGRAPH) {
                feed.post(warning(slice));
            } else if (isWaveUnderway(phase) && !isWaveUnderway(previousPhase)) {
                feed.post(launch(slice));
            }

            if (resolution != CounterattackSystem.Resolution.NONE
                    && resolution != previousResolution) {
                feed.post(outcome(slice, resolution));
            }
        }

        previousPhase = phase;
        previousResolution = resolution;
    }

    /** Handles both the ordinary ASSAULT frame and TELEGRAPH→RESOLVE when a high-speed frame advances across the one-tick ASSAULT phase. */
    private static boolean isWaveUnderway(CounterattackSystem.Phase phase) {
        return phase == CounterattackSystem.Phase.ASSAULT || phase == CounterattackSystem.Phase.RESOLVE;
    }

    static BattleCommsFeed.Notice warning(BiomeKind slice) {
        String district = districtName(slice);
        return new BattleCommsFeed.Notice(
                "COMMS OFFICER // COUNTERATTACK WARNING",
                "Enemy command traffic just spiked around the " + district
                        + ". They're massing a counterattack. Prepare to hold.",
                BattleCommsFeed.Tone.WARNING,
                WARNING_DURATION_SEC);
    }

    static BattleCommsFeed.Notice launch(BiomeKind slice) {
        String district = districtName(slice);
        return new BattleCommsFeed.Notice(
                "COMMS OFFICER // COUNTERATTACK INBOUND",
                "The enemy push is moving into the " + district
                        + ". Hold what you can; don't let them split the line.",
                BattleCommsFeed.Tone.DANGER,
                LAUNCH_DURATION_SEC);
    }

    static BattleCommsFeed.Notice outcome(BiomeKind slice, CounterattackSystem.Resolution resolution) {
        String district = districtName(slice);
        return switch (resolution) {
            case SUCCESS -> new BattleCommsFeed.Notice(
                    "COMMS OFFICER // LINE BREACHED",
                    "They've broken back into the " + district
                            + ". The front has shifted. Prepare to retake it.",
                    BattleCommsFeed.Tone.DANGER,
                    OUTCOME_DURATION_SEC);
            case FAILURE -> new BattleCommsFeed.Notice(
                    "COMMS OFFICER // LINE HOLDING",
                    "The counterattack in the " + district
                            + " has collapsed. Their reserve is spent; keep pushing.",
                    BattleCommsFeed.Tone.GOOD_NEWS,
                    OUTCOME_DURATION_SEC);
            case ABORTED -> new BattleCommsFeed.Notice(
                    "COMMS OFFICER // BUILDUP DISPERSING",
                    "Enemy traffic around the " + district
                            + " is breaking up. Our line is holding.",
                    BattleCommsFeed.Tone.STATUS,
                    OUTCOME_DURATION_SEC);
            case NONE -> throw new IllegalArgumentException("NONE is not an outcome");
        };
    }

    public static String districtName(BiomeKind slice) {
        return switch (slice) {
            case BEACH -> "beachhead";
            case PORT -> "port district";
            case CITY -> "city district";
            case FORTRESS_DISTRICT -> "fortress district";
            case OUTSKIRTS -> "outskirts";
        };
    }
}

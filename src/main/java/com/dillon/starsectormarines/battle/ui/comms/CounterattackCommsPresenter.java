package com.dillon.starsectormarines.battle.ui.comms;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.command.reinforcement.CounterattackSystem;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * Converts the conquest counterattack state machine into player-POV comms
 * dispatches. It deliberately observes public state rather than owning any
 * gameplay callbacks: presentation remains downstream of simulation state.
 *
 * <p>The district it names is the front band's own display name, taken from
 * {@link CounterattackSystem#getBulgeBandName}. It used to be a {@code switch}
 * here over the generator's biome kinds, which made the presentation tier the
 * second place that had to know what a front was made of.
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
        String district = current.getBulgeBandName();

        if (district != null) {
            if (phase == CounterattackSystem.Phase.TELEGRAPH
                    && previousPhase != CounterattackSystem.Phase.TELEGRAPH) {
                feed.post(warning(district));
            } else if (isWaveUnderway(phase) && !isWaveUnderway(previousPhase)) {
                feed.post(launch(district));
            }

            if (resolution != CounterattackSystem.Resolution.NONE
                    && resolution != previousResolution) {
                feed.post(outcome(district, resolution));
            }
        }

        previousPhase = phase;
        previousResolution = resolution;
    }

    /** Handles both the ordinary ASSAULT frame and TELEGRAPH→RESOLVE when a high-speed frame advances across the one-tick ASSAULT phase. */
    private static boolean isWaveUnderway(CounterattackSystem.Phase phase) {
        return phase == CounterattackSystem.Phase.ASSAULT || phase == CounterattackSystem.Phase.RESOLVE;
    }

    static BattleCommsFeed.Notice warning(String district) {
        return new BattleCommsFeed.Notice(
                Strings.get("battleCommsCounterWarningTitle"),
                Strings.format("battleCommsCounterWarningBody", district),
                BattleCommsFeed.Tone.WARNING,
                WARNING_DURATION_SEC);
    }

    static BattleCommsFeed.Notice launch(String district) {
        return new BattleCommsFeed.Notice(
                Strings.get("battleCommsCounterLaunchTitle"),
                Strings.format("battleCommsCounterLaunchBody", district),
                BattleCommsFeed.Tone.DANGER,
                LAUNCH_DURATION_SEC);
    }

    static BattleCommsFeed.Notice outcome(String district, CounterattackSystem.Resolution resolution) {
        return switch (resolution) {
            case SUCCESS -> new BattleCommsFeed.Notice(
                    Strings.get("battleCommsCounterSuccessTitle"),
                    Strings.format("battleCommsCounterSuccessBody", district),
                    BattleCommsFeed.Tone.DANGER,
                    OUTCOME_DURATION_SEC);
            case FAILURE -> new BattleCommsFeed.Notice(
                    Strings.get("battleCommsCounterFailureTitle"),
                    Strings.format("battleCommsCounterFailureBody", district),
                    BattleCommsFeed.Tone.GOOD_NEWS,
                    OUTCOME_DURATION_SEC);
            case ABORTED -> new BattleCommsFeed.Notice(
                    Strings.get("battleCommsCounterAbortedTitle"),
                    Strings.format("battleCommsCounterAbortedBody", district),
                    BattleCommsFeed.Tone.STATUS,
                    OUTCOME_DURATION_SEC);
            case NONE -> throw new IllegalArgumentException("NONE is not an outcome");
        };
    }
}

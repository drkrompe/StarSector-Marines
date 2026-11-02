package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * One "run to this tick or beat, then stop" in a played battle, as {@link PlayDiagnosis} drives it: decided a frame at a
 * time from what the battle's own time buttons show, so it plays through the same pause and speed the player has.
 *
 * <p>A frame can run up to {@link BattleSimulation#MAX_CATCH_UP_TICKS} ticks, so pausing when the target is reached would
 * overshoot it. It pauses that far short and steps the rest itself, which lands on the exact tick; those last ticks show
 * no effects, since the screen is paused and takes no frame's events. A press takes hold on the next frame, before that
 * frame's ticks, so nothing runs between deciding to pause and being paused.
 *
 * <p>The player's hands win: a speed they choose while it runs ends the run where it stands.
 */
final class PlayRunTo {

    static final String PAUSE = "battle-time-pause";
    /** Frames a press may take to show on the buttons before a different button means the player. */
    private static final int SETTLE_FRAMES = 2;

    enum State { RUNNING, ARRIVED, BEAT, COMPLETE, CANCELLED }

    /** What the host does this frame: a control to press, ticks to step. */
    record Next(String press, int steps) {
        static final Next NOTHING = new Next(null, 0);
    }

    private enum Phase { STARTING, RUNNING, STOPPING }

    private final int target;
    private final String speed;
    private Phase phase = Phase.STARTING;
    private State state = State.RUNNING;
    private String reason = "";
    private int settling;

    /**
     * @param target the tick to stop on, or {@link Integer#MAX_VALUE} to run until a beat
     * @param speed  the time button to run at
     */
    PlayRunTo(int target, String speed) {
        this.target = target;
        this.speed = speed;
    }

    State state() {
        return state;
    }

    String reason() {
        return reason;
    }

    int target() {
        return target;
    }

    void cancel(String why) {
        finish(State.CANCELLED, why);
    }

    private void finish(State ending, String why) {
        state = ending;
        reason = why;
    }

    /**
     * @param selected the time button showing as chosen, or null with none on screen
     * @param beat     a description of the beat being waited for, if it fired, or null
     */
    Next frame(int tick, boolean complete, String selected, String beat) {
        if (complete) {
            finish(State.COMPLETE, "the battle ended");
            return Next.NOTHING;
        }
        if (selected == null) {
            cancel("the battle's time controls are not on screen");
            return Next.NOTHING;
        }
        boolean paused = PAUSE.equals(selected);
        if (beat != null) {
            finish(State.BEAT, beat);
            return paused ? Next.NOTHING : new Next(PAUSE, 0);
        }
        int left = target - tick;
        // Only a press that could not take hold, under a modal say, gets here.
        if (left < 0) {
            finish(State.ARRIVED, "passed the target");
            return paused ? Next.NOTHING : new Next(PAUSE, 0);
        }
        boolean close = left <= BattleSimulation.MAX_CATCH_UP_TICKS;
        switch (phase) {
            case STARTING -> {
                if (close) return stopping(paused, left);
                phase = Phase.RUNNING;
                settling = SETTLE_FRAMES;
                return speed.equals(selected) ? Next.NOTHING : new Next(speed, 0);
            }
            case RUNNING -> {
                if (speed.equals(selected)) {
                    settling = 0;
                } else if (settling > 0) {
                    settling--;
                } else {
                    cancel("the player chose " + selected);
                    return Next.NOTHING;
                }
                return close ? stopping(paused, left) : Next.NOTHING;
            }
            default -> {
                return stopping(paused, left);
            }
        }
    }

    private Next stopping(boolean paused, int left) {
        if (paused) {
            finish(State.ARRIVED, "reached the target");
            return new Next(null, left);
        }
        if (phase == Phase.STOPPING) {
            if (settling-- > 0) return Next.NOTHING;
            cancel("the battle would not pause");
            return Next.NOTHING;
        }
        phase = Phase.STOPPING;
        settling = SETTLE_FRAMES;
        return new Next(PAUSE, 0);
    }
}

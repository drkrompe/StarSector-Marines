package com.dillon.starsectormarines.battle.command.objective;

/**
 * Who holds an entrance's apron ({@code ruins-expedition.md}, The entrance and the pull-out point). The company takes
 * it as a landing court is taken: a settled company combatant on it and no hostile, for an unbroken time. Unlike a
 * court it can be lost: hostiles on it with no company combatant there, for the same time, retake it, and the company
 * takes it back the same way. Any tick that breaks the condition starts that count again.
 */
final class ApronHold {

    private final float duration;
    private boolean everTaken;
    private boolean held;
    private float progress;

    ApronHold(float duration) {
        if (duration <= 0f) throw new IllegalArgumentException("hold duration must be positive");
        this.duration = duration;
    }

    /**
     * One tick on the apron.
     *
     * @param companySettled a settled company combatant stands on it (what taking needs)
     * @param companyPresent any company combatant stands on it (what stops a retake)
     * @param hostilePresent a hostile combatant stands on it
     * @return whether the apron changed hands on this tick
     */
    boolean tick(boolean companySettled, boolean companyPresent, boolean hostilePresent, float dt) {
        boolean pressing = held ? hostilePresent && !companyPresent : companySettled && !hostilePresent;
        if (!pressing) {
            progress = 0f;
            return false;
        }
        progress += dt;
        if (progress < duration) return false;
        progress = 0f;
        held = !held;
        if (held) everTaken = true;
        return true;
    }

    /** Whether the company holds the apron now. */
    boolean held() { return held; }
    /** Whether the company has ever taken it. */
    boolean everTaken() { return everTaken; }
    /** How far the side pressing it has got: the company while it is not held, the occupant while it is. */
    float progress() { return progress; }
    float duration() { return duration; }
}

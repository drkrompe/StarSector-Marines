package com.dillon.starsectormarines.battle.control;

import com.dillon.starsectormarines.marine.SpecialActivation;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;

import java.util.ArrayList;
import java.util.List;

/**
 * A controlled Marine's left button: the primary, then each carried direct-fire special in cycle
 * order (rocket launchers, then anti-materiel rifles, then the flamer). Q steps through the list and wraps to the
 * primary; there is no LINKED, since one pair of hands fires one weapon.
 *
 * <p>A picked special is under a <b>manual contract</b>. Picking it readies it, which takes its
 * authored aim time; a press then fires one round at the cursor once the wind-up is over and the
 * special's own cooldown and ammunition allow it. A press made while it is not yet ready stays armed
 * for as long as the button is held, so a click that lands a moment early is not lost, and a held
 * button never fires twice. The flamer is the exception, a held stream: a held button keeps it firing,
 * one charge each time its cooldown allows, until release or empty fuel. Sprinting lowers the weapon and readies it again afterwards. An empty
 * special keeps the pick: it never falls back to the primary on its own.
 *
 * <p>Pure state, stepped by {@link DirectControlSession}; the battle's equipment owners keep
 * ammunition, cooldown and the round itself.
 */
public final class MarineLeftTrigger {
    /** The primary's place in the list, and the pick a Marine is handed with. */
    public static final int PRIMARY = 0;

    private int pick = PRIMARY;
    private float windupSeconds;
    private float windupRemaining;
    /** Seconds since this pick last fired, or infinity before its first round. */
    private float sinceFired = Float.POSITIVE_INFINITY;
    private boolean armed;
    private boolean held;

    /**
     * The specials the left button cycles through, in cycle order: direct-fire items that spend
     * ammunition, and the flamer's held stream. A Marine's one special slot means this has at most one member today.
     */
    public static List<SpecialEquipmentDef> specials(SpecialEquipmentDef carried) {
        List<SpecialEquipmentDef> out = new ArrayList<>(1);
        if (carried != null && (carried.isDirectFireWeapon() || carried.isHeldStream())
                && carried.usesAmmunition()) out.add(carried);
        out.sort((a, b) -> Integer.compare(order(a.activation()), order(b.activation())));
        return out;
    }

    /** Rockets, then precision rifles, then the flamer. */
    private static int order(SpecialActivation activation) {
        return switch (activation) {
            case DIRECT_EXPLOSIVE -> 0;
            case DIRECT_PRECISION -> 1;
            default -> 2;
        };
    }

    /** The member the button is on: {@link #PRIMARY}, or a special's place in {@link #specials} plus one. */
    public int pick() { return pick; }

    public boolean onSpecial() { return pick != PRIMARY; }

    /** How far the readying has come, 0 to 1; 1 on the primary. */
    public float readiness() {
        return windupSeconds <= 0f ? 1f : 1f - Math.max(0f, windupRemaining) / windupSeconds;
    }

    public boolean ready() { return windupRemaining <= 0f; }

    /** Seconds since the picked special last fired, or infinity. */
    public float sinceFired() { return sinceFired; }

    /**
     * Moves the button to the next member of a list of {@code members} (primary included) and
     * readies a special for {@code aimSeconds}. Refused when there is nothing to cycle to.
     */
    public boolean cycle(int members, float aimSeconds) {
        if (members < 2) return false;
        return pick((pick + 1) % members, members, aimSeconds);
    }

    /** Puts the button on {@code member} of {@code members}, readying a special for {@code aimSeconds}. */
    public boolean pick(int member, int members, float aimSeconds) {
        if (member < 0 || member >= members) return false;
        if (member == pick) return true;
        pick = member;
        armed = false;
        sinceFired = Float.POSITIVE_INFINITY;
        windupSeconds = member == PRIMARY ? 0f : Math.max(0f, aimSeconds);
        windupRemaining = windupSeconds;
        return true;
    }

    /** Back on the primary with nothing armed: entry, handback, and a special that is no longer carried. */
    public void reset() {
        pick = PRIMARY;
        windupSeconds = 0f;
        windupRemaining = 0f;
        sinceFired = Float.POSITIVE_INFINITY;
        armed = false;
        held = false;
    }

    /** Pause, chrome or lost focus: an armed press is dropped, the readying is kept. */
    public void suspend() {
        armed = false;
        held = false;
    }

    /**
     * One tick of a picked special. {@code down} is the left button, {@code lowered} a sprint that
     * has the weapon down, and {@code canFire} whether the special's own cooldown, ammunition and
     * the aim point allow a round now. Returns whether a round leaves this tick; the caller fires it
     * and calls {@link #fired()} when it did.
     */
    public boolean tick(boolean down, boolean lowered, boolean canFire, float dt) {
        return tick(down, lowered, canFire, false, dt);
    }

    /**
     * As {@link #tick(boolean, boolean, boolean, float)}; {@code stream} keeps a held button armed after a
     * round, so the next leaves as soon as {@code canFire} allows it.
     */
    public boolean tick(boolean down, boolean lowered, boolean canFire, boolean stream, float dt) {
        boolean pressed = down && !held;
        held = down;
        if (!onSpecial()) return false;
        if (Float.isFinite(sinceFired)) sinceFired += dt;
        if (lowered) {
            windupRemaining = windupSeconds;
            armed = false;
            return false;
        }
        if (windupRemaining > 0f) windupRemaining = Math.max(0f, windupRemaining - dt);
        if (pressed) armed = true;
        if (!down) armed = false;
        if (!armed || windupRemaining > 0f || !canFire) return false;
        armed = stream;
        return true;
    }

    /** The round left: the follow-through clock starts. */
    public void fired() { sinceFired = 0f; }
}

package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;

import java.util.ArrayList;
import java.util.List;

/**
 * The derelict line's sizes (`derelict-watch.md`, Medium and large walkers). The plant's ways come in widths (one-cell
 * side-room and store doors, two-cell corridors and bulkheads, three-cell hall joins, four-cell cargo docks), and each
 * size is the walker its ways admit: a walker fits a way whose width is more than its body across.
 */
public enum DerelictWalkerClass {

    /** The MG-25 rounds walker: every way, one-cell doors too. */
    MEDIUM(MechVariant.SECURITY_ROUNDS),
    /** The MG-40 (line, siege, vault frames): two-cell ways and wider. */
    HEAVY(MechVariant.SECURITY_WALKER),
    /** The MG-70 hall frame: three-cell ways and wider; never a corridor. */
    LARGE(MechVariant.SECURITY_HALL);

    /** How long a hall frame stands against a dead door before a leaf tears out and the door is forced. UNTUNED. */
    public static final float FORCE_SECONDS = 8f;

    public final MechVariant chassis;

    DerelictWalkerClass(MechVariant chassis) {
        this.chassis = chassis;
    }

    /** The size of a derelict chassis. */
    public static DerelictWalkerClass of(MechVariant chassis) {
        for (DerelictWalkerClass size : values()) if (size.chassis == chassis) return size;
        throw new IllegalArgumentException("Not a derelict chassis: " + chassis);
    }

    /** Whether a walker of this size passes a way {@code cells} wide: its body across is less than the way. */
    public boolean fits(int cells) {
        return chassis.radius * 2f < cells;
    }

    /** The narrowest way this size passes, in whole cells. */
    public int narrowestWay() {
        int cells = 1;
        while (!fits(cells)) cells++;
        return cells;
    }

    /** Whether this size forces a dead door it cannot pass, rather than going round. */
    public boolean forcesDoors() {
        return this == LARGE;
    }

    /** Rounds walkers a site of {@code grade} stands: one at scattered, one a zone above it. UNTUNED. */
    public static int roundsWalkers(RuinsGrade grade, int zones) {
        return grade == RuinsGrade.SCATTERED ? 1 : zones;
    }

    /** Hall frames a site of {@code grade} stands, in its largest plant halls: one at extensive, two at vast. UNTUNED. */
    public static int hallFrames(RuinsGrade grade) {
        return switch (grade) {
            case SCATTERED, WIDESPREAD -> 0;
            case EXTENSIVE -> 1;
            case VAST -> 2;
        };
    }

    /**
     * The cells a hall frame opens when a door gives way to its ram (`derelict-watch.md`, Medium and large walkers): a
     * dead door's end cells, where its leaves stand; and a door too narrow for the frame torn wider, a jamb cell at
     * each end along its line at a time, until the frame fits through.
     */
    public static List<int[]> tornCells(RuinsDelve.LeafDoor door) {
        RuinsDelve.Area s = door.span();
        List<int[]> out = new ArrayList<>();
        if (door.stance() == RuinsDelve.DoorStance.DEAD) {
            out.add(new int[] {s.left(), s.bottom()});
            out.add(new int[] {s.right(), s.top()});
        }
        int width = door.length();
        for (int k = 1; !LARGE.fits(width); k++) {
            if (door.alongX()) {
                out.add(new int[] {s.left() - k, s.bottom()});
                out.add(new int[] {s.right() + k, s.bottom()});
            } else {
                out.add(new int[] {s.left(), s.bottom() - k});
                out.add(new int[] {s.left(), s.top() + k});
            }
            width += 2;
        }
        return out;
    }

    /**
     * A hall frame against a dead door: it must stand pressed against it {@link #FORCE_SECONDS} at a stretch, and then the
     * door is forced for good. Backing off starts the count again.
     */
    public static final class Ram {
        private float pressed;
        private boolean forced;

        /**
         * @param against whether the frame stands against the door this step
         * @return whether the door was forced by this step
         */
        public boolean tick(float dt, boolean against) {
            if (forced) return false;
            pressed = against ? pressed + dt : 0f;
            if (pressed < FORCE_SECONDS) return false;
            forced = true;
            return true;
        }

        public boolean forced() {
            return forced;
        }

        /** How far the door is toward forced, 0 to 1. */
        public float progress() {
            return forced ? 1f : Math.min(1f, pressed / FORCE_SECONDS);
        }
    }
}

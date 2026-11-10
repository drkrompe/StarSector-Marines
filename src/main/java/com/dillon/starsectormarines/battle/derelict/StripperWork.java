package com.dillon.starsectormarines.battle.derelict;

import java.util.List;

/**
 * A stripper's rule (`fallen-hull-nouns.md`, Stripper): a walker of the derelict line with cutting arms that cuts the
 * metal on the claim into feedstock and holds it for a hauler. The rules are here; the hull's director walks it and
 * hands its load on.
 *
 * <p>Each pulse: full ({@link #CAPACITY}), it waits where it stands for a hauler; otherwise it goes to the nearest
 * cuttable salvage on the claim and, within {@link #REACH_CELLS}, cuts at {@link #CUT_PER_SECOND}; with nothing on the
 * claim to cut it stands at its post.
 */
public final class StripperWork {

    /** The metal a stripper holds before it must hand it to a hauler. UNTUNED. */
    public static final float CAPACITY = 60f;
    /** Metal a second its arms cut. UNTUNED. */
    public static final float CUT_PER_SECOND = 4f;
    /** How near salvage, in cells (either axis, centre to centre), it cuts. */
    public static final float REACH_CELLS = 2f;

    public enum Kind {
        /** Nothing on the claim to cut: stand at the post. */
        STAND_BY,
        /** Walk to the salvage. */
        GO,
        /** Cut the salvage, in reach. */
        CUT,
        /** Full: wait where it stands for a hauler. */
        FULL
    }

    /** What the stripper does now, and the salvage it works (0 none). */
    public record Goal(Kind kind, float x, float y, long salvageId) { }

    private float load;

    public float load() {
        return load;
    }

    /**
     * The stripper's goal this pulse.
     *
     * @param claim the salvage on the claim, cuttable or not
     */
    public Goal decide(float x, float y, float postX, float postY, List<Salvage> claim) {
        if (load >= CAPACITY) return new Goal(Kind.FULL, x, y, 0L);
        Salvage best = null;
        float bestD = Float.MAX_VALUE;
        for (Salvage s : claim) {
            if (!s.cuttable()) continue;
            float dx = s.x - x;
            float dy = s.y - y;
            float d = dx * dx + dy * dy;
            if (best == null || d < bestD || (d == bestD && s.id < best.id)) {
                best = s;
                bestD = d;
            }
        }
        if (best == null) return new Goal(Kind.STAND_BY, postX, postY, 0L);
        boolean near = Math.max(Math.abs(best.x - x), Math.abs(best.y - y)) <= REACH_CELLS;
        return new Goal(near ? Kind.CUT : Kind.GO, best.x, best.y, best.id);
    }

    /** Cuts {@code seconds} from {@code salvage}, as much as it still has room for; returns the metal cut. */
    public float cut(float seconds, Salvage salvage) {
        float taken = salvage.cut(Math.min(CUT_PER_SECOND * seconds, CAPACITY - load));
        load += taken;
        return taken;
    }

    /** Hands up to {@code wanted} to a hauler; returns what it gave. */
    public float handOver(float wanted) {
        float given = Math.max(0f, Math.min(load, wanted));
        load -= given;
        return given;
    }
}

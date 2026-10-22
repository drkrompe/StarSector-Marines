package com.dillon.starsectormarines.battle.fire;

/** Small reads of the fire field for behaviour (`fire-nouns.md`, Pathing). */
public final class FireEffects {

    private FireEffects() { }

    /** Whether a cell is no place to run to: burning, or beside a burning cell. Null field, no fire. */
    public static boolean refusedAsRefuge(FireField field, int x, int y) {
        if (field == null || field.burningCount() == 0) return false;
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++)
                if (field.isBurning(x + dx, y + dy)) return true;
        return false;
    }
}

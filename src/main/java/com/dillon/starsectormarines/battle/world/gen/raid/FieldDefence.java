package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.world.gen.precinct.DefenceDoctrine;

import java.util.EnumMap;
import java.util.Map;

/**
 * What a refinery's open fields hand the shared doctrine to be armed by ({@code PrecinctDefence.stampField}): the
 * ground each guards (the process area with its spine; the tank farm), and its approaches, which stand in for a wall's
 * gates: where its spine and lanes meet the country, each stepping out of the field. Each approach is owed a post on
 * either side of it, so the way in is crossed by two fields of fire; the posts are the field's own, on top of the
 * works' wall's. Pure geometry; {@link RefineryGround} arms the fields.
 */
public final class FieldDefence {

    /** Posts an approach is owed: one on each side of it. */
    static final int PER_APPROACH = 2;

    private FieldDefence() { }

    /** The field's ground: the rectangle {@code (x0, y0)}..{@code (x1, y1)} on a map {@code w} by {@code h}. */
    public static boolean[][] mask(int w, int h, int x0, int y0, int x1, int y1) {
        boolean[][] out = new boolean[w][h];
        for (int x = Math.max(0, x0); x <= Math.min(w - 1, x1); x++) {
            for (int y = Math.max(0, y0); y <= Math.min(h - 1, y1); y++) out[x][y] = true;
        }
        return out;
    }

    /** The approach along a spine, columns {@code x0}..{@code x1}, where it meets the field's edge row. */
    public static DefenceDoctrine.Gate spineEnd(int x0, int x1, int edgeY, int outY) {
        return new DefenceDoctrine.Gate((x0 + x1) / 2, edgeY, 0, outY, (x1 - x0) / 2);
    }

    /** The approach along a cross lane, rows {@code y0}..{@code y1}, where it meets the field's edge column. */
    public static DefenceDoctrine.Gate laneMouth(int y0, int y1, int edgeX, int outX) {
        return new DefenceDoctrine.Gate(edgeX, (y0 + y1) / 2, outX, 0, (y1 - y0) / 2);
    }

    /** What a field is owed: {@link #PER_APPROACH} of {@code kind} per approach. */
    public static Map<DefensePostKind, Integer> owed(DefensePostKind kind, int approaches) {
        Map<DefensePostKind, Integer> out = new EnumMap<>(DefensePostKind.class);
        if (approaches > 0) out.put(kind, PER_APPROACH * approaches);
        return out;
    }
}

package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.fire.FireField;

/**
 * What a burning building shows on its stood-up walls ({@code fire-nouns.md}, Look, Buildings in depth), read from the
 * fire field at the three cells a face stands over: the wall cell itself, the room cell just inside it and the ground
 * just outside. Fire on the ground outside, or in the wall's own structure, licks up the face from its foot; a room
 * burning behind pours fire out of the face's windows, doors and breaches; a room or wall that has burnt chars the face
 * black. How hard each burns is the field's intensity, so a face takes fire as the field spreads to it and dies down
 * with it. GL-free: {@link StructureFire} draws what this decides.
 */
final class FaceFire {

    /** How high fire licks up a face, metres: a low fire at its foot, and the most a full-intensity fire reaches. */
    static final float LICK_METERS = 2.5f, LICK_METERS_HOT = 10f;
    /** The most of a face's height a lick reaches: a little over its eave, where the roof's own fire takes over. */
    static final float LICK_MOST = 1.2f;
    /** How much of the ground outside's heat licks up the face: less than the wall's own, which is the face. */
    static final float OUTSIDE_SHARE = .75f;
    /** How charred a face is once its room has burnt out, against 1 for a wall burnt in its own structure. */
    static final float ROOM_CHAR = .85f;
    /** The flame loop's rate, frames a second. */
    static final float FPS = 9f;
    /** Frames in a flame row's loop ({@code art-source/fx/fire-faces}). */
    static final int LOOP = 8;

    private FaceFire() { }

    /** How hot cell {@code idx} burns, 0 to 1: its intensity while it burns, nothing before or after; -1 is no cell. */
    static float heat(FireField field, int idx) {
        if (idx < 0 || field.stateAt(idx) != FireField.BURNING) return 0f;
        return field.intensityAt(idx) / 255f;
    }

    /** How hard fire licks up a face from its foot: its own structure burning, or the ground outside it. */
    static float lick(float wallHeat, float outsideHeat) {
        return Math.max(wallHeat, outsideHeat * OUTSIDE_SHARE);
    }

    /** How far up a face of {@code meters} a lick of {@code heat} reaches, as a share of its height. */
    static float reach(float heat, float meters) {
        if (heat <= 0f || meters <= 0f) return 0f;
        return Math.min(LICK_MOST, (LICK_METERS + (LICK_METERS_HOT - LICK_METERS) * heat) / meters);
    }

    /** How high fire rises over a wall's top, metres: a low fire, and the most a full-intensity one reaches. */
    static final float CROWN_METERS = 1.5f, CROWN_METERS_HOT = 7f;

    /**
     * How hard fire rises over a face's top: its own wall burning, or the room behind it once that room's roof has
     * burnt or fallen in and nothing holds the fire down; nothing while the roof holds.
     */
    static float crown(float wallHeat, float insideHeat, boolean roofOpen) {
        return Math.max(wallHeat, roofOpen ? insideHeat : 0f);
    }

    /** How far over a face of {@code meters} a crown of {@code heat} rises, as a share of its height. */
    static float crownReach(float heat, float meters) {
        if (heat <= 0f || meters <= 0f) return 0f;
        return (CROWN_METERS + (CROWN_METERS_HOT - CROWN_METERS) * heat) / meters;
    }

    /** How hard fire pours out of a face's openings: the room behind it burning, or its own wall. */
    static float pour(float insideHeat, float wallHeat) {
        return Math.max(insideHeat, wallHeat * .6f);
    }

    /**
     * How charred a face is, 0 clean to 1 black: by how far its wall and the room behind it have burnt. A cell
     * chars as its fuel is spent and is wholly charred once it has burnt out; the room's fire chars the face a little
     * less than the wall's own. -1 is no cell.
     */
    static float charred(FireField field, int wall, int inside) {
        return Math.max(charOf(field, wall, 1f), charOf(field, inside, ROOM_CHAR));
    }

    private static float charOf(FireField field, int idx, float most) {
        if (idx < 0) return 0f;
        byte state = field.stateAt(idx);
        if (state == FireField.BURNT) return most;
        if (state != FireField.BURNING) return 0f;
        return most * field.burnProgress(idx) / 255f;
    }

    /** The loop frame a flame of {@code seed} shows at {@code clock} seconds; each starts at its own frame. */
    static int frame(float clock, int seed) {
        float t = clock * FPS + BuildingFaces.hash(seed, 71) * LOOP;
        return Math.floorMod((int) Math.floor(t), LOOP);
    }

    /** How far a flame of {@code seed} has faded from {@link #frame} into the next, 0 to 1. */
    static float blend(float clock, int seed) {
        float t = clock * FPS + BuildingFaces.hash(seed, 71) * LOOP;
        return t - (float) Math.floor(t);
    }
}

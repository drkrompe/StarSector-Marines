package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.BattleCamera;

/**
 * Detail that is not collected because, at this framing, it could not be seen.
 *
 * <p>Every threshold here is stated in <b>screen pixels per cell</b> rather than
 * in camera zoom. Zoom 1 means cover-fit, so what it works out to in pixels
 * depends on the map: the canonical Conquest sits at 3.4 px/cell fully zoomed
 * out and the 280x168 map the renderer was measured against sits at 6.9, and a
 * threshold expressed as a zoom would gate one of them and not the other for no
 * reason anybody could see on screen. Pixels per cell is what a reader's eye
 * actually responds to, and a cell is a metre, so these numbers are answerable:
 * "a marine is eight pixels across" is a claim about the picture.
 *
 * <p><b>A framing gate, never an occlusion one.</b> What is withheld is
 * decoration on something drawn anyway — a body's shadow, a spent round's spark
 * — so nothing here can hide a unit, a wall, or anything else a player reads the
 * battle from. Paint order and world ratios are untouched
 * ({@code battle-render-nouns.md}, laws 6 and 9): a gated producer collects
 * nothing rather than draining to nothing, so its layer keeps its place in the
 * stack and simply has no commands in it.
 *
 * <p><b>What it is worth, measured.</b> On the whole-map frame {@code
 * renderEvidence} profiled, the layers gated here come to about a fifth of a
 * millisecond against a frame of two hundred and seventy-six — nothing. The
 * ground drain is the ceiling and {@code battle.render.groundMesh} is what
 * moves it. They are worth having anyway, and the reason is what the frame looks
 * like <em>after</em> that: a body shadow is a whole-sprite draw with its own
 * texture bind that cannot batch with anything, so a late-Conquest field of four
 * hundred casters is four hundred binds for four hundred three-pixel smudges,
 * and against a ground layer costing a millisecond that is most of the frame.
 *
 * <p>Turn the gates off for a control run with
 * {@code -Dbattle.render.zoomGates=false}.
 */
public final class ZoomDetail {

    private ZoomDetail() {}

    /** Control-run switch; see the class note. On by default. */
    public static final String PROPERTY = "battle.render.zoomGates";

    private static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    /**
     * Pixels per cell below which decoration attached to a body is not
     * collected: its cast shadow, and the impact particles thrown off it.
     *
     * <p>One threshold for both, because both are sized by the body they belong
     * to and stop reading at the same point. A ground body's shadow is an
     * ellipse about two and a half cells long under a body one cell across, and
     * an impact particle is a fraction of a cell; at eight pixels per cell the
     * marine is eight pixels, the shadow is a smudge and the spark is one or two
     * pixels lasting a frame.
     *
     * <p>Eight rather than something smaller because of where the framings
     * actually fall. The profile's three on the canonical map are 27.4, 12.3 and
     * 3.4 px/cell, and on the 280x168 control 54.9, 24.6 and 6.9 — so this keeps
     * the decoration at both framings a player fights at and drops it only with
     * the whole map on screen, and it makes the same cut on both maps.
     */
    public static final float BODY_DECORATION_MIN_CELL_PX = 8f;

    /**
     * Pixels per cell below which a smoke field is drawn as one puff instead of
     * nine.
     *
     * <p>Smoke is the one thing on this list that is not decoration: it blocks
     * sight, the simulation knows it does, and a player pulled back to read the
     * whole map still needs to see where it is. So it is thinned rather than
     * withheld — the field keeps its position, its footprint and its fade, and
     * loses only the scatter that makes it billow. Below the body threshold for
     * the same reason: a field is several cells across where a body is one, so
     * it goes on reading after everything sized to a body has stopped. At five
     * pixels per cell a four-cell field is twenty pixels and the nine puffs
     * inside it are one grey blob however they are drawn.
     */
    public static final float SMOKE_SCATTER_MIN_CELL_PX = 5f;

    /** Whether bodies cast their own shadows at this framing. */
    public static boolean bodyShadowsVisible(BattleCamera camera) {
        return bodyDecorationVisible(camera);
    }

    /** Whether one-tick impact particles are worth collecting at this framing. */
    public static boolean impactParticlesVisible(BattleCamera camera) {
        return bodyDecorationVisible(camera);
    }

    /** Whether a smoke field's scatter reads at this framing, or is one puff. */
    public static boolean smokeScatterVisible(BattleCamera camera) {
        return visibleAt(camera, SMOKE_SCATTER_MIN_CELL_PX);
    }

    /** Whether the gates are armed at all — for evidence that reports which run it was. */
    public static boolean enabled() {
        return ENABLED;
    }

    private static boolean bodyDecorationVisible(BattleCamera camera) {
        return visibleAt(camera, BODY_DECORATION_MIN_CELL_PX);
    }

    /**
     * A camera that cannot say how big a cell is gets everything, deliberately.
     * The gates exist to drop what cannot be read, and "we do not know" is not
     * that.
     */
    private static boolean visibleAt(BattleCamera camera, float minCellPx) {
        return !ENABLED || camera == null || camera.cellPxSize() >= minCellPx;
    }
}

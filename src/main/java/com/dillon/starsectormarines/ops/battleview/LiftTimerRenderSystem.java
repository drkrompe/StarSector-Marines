package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.LiftSchedule;
import com.dillon.starsectormarines.battle.air.LiftSchedule.DropOff;
import com.dillon.starsectormarines.battle.air.LiftSchedule.Phase;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.Fonts;

import java.awt.Color;
import java.util.List;

/**
 * The lift timer: over each of the player's drop-off points, a glass label
 * saying when the next lift sets down and how many more are behind it, with a
 * thin bar beneath that fills as the lift comes in.
 *
 * <p>It is ground-anchored and drawn only for drop-offs on screen; the player
 * looks at the landing zone to ask when the next lift arrives, and an
 * off-screen locator would compete with the mission's own. It marks nothing
 * on the ground itself: the pads are already there to see. Every figure comes
 * from {@link LiftSchedule}, which reads the transports themselves.
 */
public final class LiftTimerRenderSystem implements RenderSystem {
    /** How far above the drop-off's centre the label stands, so it clears the craft on the pads. */
    private static final float LIFT_CELLS = 3.5f;
    private static final float MIN_LIFT_PX = 40f;
    private static final float BAR_HEIGHT = 3f;
    private static final Color PAPER = new Color(229, 227, 215);
    private static final Color MUTED = new Color(180, 188, 185);

    @Override public RenderLayer layer() { return RenderLayer.MISSION_MARKERS; }

    @Override public void collect(RenderContext ctx, DrawList out) {
        collect(LiftSchedule.capture(ctx.sim, Faction.MARINE), ctx, out);
    }

    /** Also used by controlled presentation fixtures; it never advances the lifts. */
    public void collect(List<DropOff> dropOffs, RenderContext ctx, DrawList out) {
        var camera = ctx.camera;
        float alpha = ctx.alphaMult;
        for (DropOff point : dropOffs) {
            float x = camera.cellToScreenX(point.x());
            float y = camera.cellToScreenY(point.y());
            if (x < camera.vpX() || x > camera.vpX() + camera.vpW()
                    || y < camera.vpY() || y > camera.vpY() + camera.vpH()) continue;
            var next = point.next();
            boolean down = next.phase() == Phase.UNLOADING;
            Color tone = down ? BattlefieldMarkerPresentation.VALID : BattlefieldMarkerPresentation.PROGRESS;
            float r = tone.getRed() / 255f, g = tone.getGreen() / 255f, b = tone.getBlue() / 255f;
            String title = down ? Strings.get("battleLiftDown")
                    : Strings.format("battleLiftNext", clock(next.secondsToTouchdown()));
            String more = Strings.format("battleLiftMore", point.liftsToCome());
            String detail = point.marines() > 0
                    ? Strings.format("battleLiftDetail", Strings.format("battleLiftMarines", point.marines()), more)
                    : more;
            float width = Math.max(Fonts.ORBITRON_12_BOLD.measureWidth(title),
                    Fonts.INSIGNIA_15_AA.measureWidth(detail)) + 20;
            float top = Math.min(y + Math.max(MIN_LIFT_PX, LIFT_CELLS * camera.cellPxSize()) + 40,
                    camera.vpY() + camera.vpH() - 8);
            // Battle glass, as the mission markers draw it: haloed text and no panel.
            out.addCustom(layer(), new WorldLabel(Fonts.ORBITRON_12_BOLD, title, x, top, tone, alpha,
                    WorldLabel.GLASS_HALO));
            out.addCustom(layer(), new WorldLabel(Fonts.INSIGNIA_15_AA, detail, x, top - 16,
                    down ? PAPER : MUTED, alpha, WorldLabel.GLASS_HALO));
            // The bar's length is the whole turnaround; its fill is how much of it has passed.
            float filled = down ? 1f
                    : 1f - next.secondsToTouchdown() / Math.max(1f, next.turnaroundSeconds());
            float left = x - width / 2, right = x + width / 2, bottom = top - 38;
            out.addSolidRect(layer(), left, bottom, right, bottom + BAR_HEIGHT, .1f, .12f, .12f, .7f * alpha);
            out.addSolidRect(layer(), left, bottom, left + (right - left) * Math.max(0f, Math.min(1f, filled)),
                    bottom + BAR_HEIGHT, r, g, b, alpha);
        }
    }

    /** Whole seconds as m:ss, rounded up so the timer reads 0:00 only once the lift is down. */
    static String clock(float seconds) {
        int whole = (int) Math.ceil(Math.max(0f, seconds));
        return whole / 60 + ":" + (whole % 60 < 10 ? "0" : "") + whole % 60;
    }
}

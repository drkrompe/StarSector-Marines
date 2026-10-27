package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.function.Supplier;

/**
 * A marine's kit mount drawn as Mech Lab hardware ({@link KitBrick}): the piece's bricks in
 * its colour with its armoury art laid along them, or an empty recess when the mount carries
 * nothing. Long art turns to stand upright, as long guns do on the Mech Lab's boards.
 */
public final class KitBrickCanvas implements CanvasProducer {
    /** The rim an empty mount shows: no hardware, so no hardware's colour. */
    private static final Color EMPTY_RIM = HardpointBricks.OMNI;

    private final Supplier<KitBrick> piece;

    public KitBrickCanvas(Supplier<KitBrick> piece) {
        if (piece == null) throw new IllegalArgumentException("piece is required");
        this.piece = piece;
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth(), height = context.metrics().surfaceHeight();
        HardpointBricks.Layout layout = HardpointBricks.fit(0f, 0f, width, height,
                KitBrick.COLUMNS, KitBrick.ROWS, HardpointBricks.BRICK_GAP);
        KitBrick shown = piece.get();
        if (shown == null) {
            HardpointBricks.drawEmpty(context, layout, EMPTY_RIM);
            return;
        }
        HardpointBricks.drawFilled(context, layout, 0, 0, layout.columns(), layout.rows(),
                HardpointBricks.tone(shown.ordnance()));
        if (!shown.icon().isEmpty()) drawArt(context, shown, layout);
    }

    private static void drawArt(CanvasContext context, KitBrick shown, HardpointBricks.Layout layout) {
        float[] size = context.imageSize(shown.icon());
        if (size == null || size[0] <= 0f || size[1] <= 0f) return;
        float boxWidth = layout.width() * .92f, boxHeight = layout.height() * .94f;
        float scale, angle;
        if (shown.lie() == null) {
            scale = Math.min(boxWidth / size[0], boxHeight / size[1]);
            angle = 0f;
        } else {
            // Long art turns about its centre until its line stands vertical, muzzle up. The
            // line crosses its picture from edge to edge, so its length is the picture's run
            // over the cosine of its lie (or rise over the sine, past 45 degrees); its slim
            // girth fits the brick's width without a measure of its own.
            double lie = Math.toRadians(Math.max(0f, Math.min(90f, shown.lie())));
            float length = (float) (lie <= Math.PI / 4 ? size[0] / Math.cos(lie) : size[1] / Math.sin(lie));
            scale = boxHeight / length;
            angle = 90f - (float) Math.toDegrees(lie);
        }
        context.sprite(shown.icon(), null, layout.centerX(), layout.centerY(),
                size[0] * scale, size[1] * scale, angle, Color.WHITE);
    }
}

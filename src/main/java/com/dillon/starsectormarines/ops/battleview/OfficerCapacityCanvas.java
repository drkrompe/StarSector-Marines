package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.Objects;
import java.util.function.Supplier;

/** Discrete whole-squad command slots, including the result of an organization draft. */
public final class OfficerCapacityCanvas implements CanvasProducer {

    public static final int SURFACE_WIDTH = 216;
    public static final int SURFACE_HEIGHT = 34;
    private static final int MAX_VISIBLE_SLOTS = 36;

    /** Plain immutable inputs; neither the canvas nor a projected slot writes command authority. */
    public record Projection(int capacity, int current, int projected) {
        public Projection {
            if (capacity < 0 || current < 0 || projected < 0) {
                throw new IllegalArgumentException("command counts cannot be negative");
            }
        }
    }

    private static final Color EMPTY = new Color(0x2D, 0x48, 0x5A);
    private static final Color CURRENT = new Color(0x77, 0xC5, 0xDF);
    private static final Color INCOMING = new Color(0xB8, 0xE3, 0xEF);
    private static final Color OUTGOING = new Color(0x78, 0x90, 0xA2);
    private static final Color OVERFLOW = new Color(0xF1, 0x84, 0x7D);
    private static final Color BACKGROUND = new Color(0x0B, 0x1B, 0x26);

    private final Supplier<Projection> projection;

    public OfficerCapacityCanvas(Supplier<Projection> projection) {
        this.projection = Objects.requireNonNull(projection, "command capacity supplier");
    }

    @Override
    public void draw(CanvasContext context) {
        Projection value = projection.get();
        if (value == null) return;
        int total = Math.max(value.capacity(), Math.max(value.current(), value.projected()));
        // Legacy commands can exceed rank limits. Keep the visual bounded while
        // the surrounding retained copy reports the complete exact count.
        int count = Math.min(MAX_VISIBLE_SLOTS, total);
        if (count == 0) return;
        int columns = Math.min(12, count);
        int rows = (count + columns - 1) / columns;
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        float gap = Math.min(3f, Math.min(width / columns, height / rows) * 0.18f);
        float cellWidth = Math.max(0f, (width - gap * (columns - 1)) / columns);
        float cellHeight = Math.max(0f, Math.min(13f,
                (height - gap * (rows - 1)) / rows));
        float top = (height - rows * cellHeight - gap * (rows - 1)) * 0.5f;
        for (int index = 0; index < count; index++) {
            float x = (index % columns) * (cellWidth + gap);
            float y = top + (index / columns) * (cellHeight + gap);
            boolean overflow = index >= value.capacity();
            boolean remaining = index < value.current() && index < value.projected();
            boolean incoming = index >= value.current() && index < value.projected();
            boolean outgoing = index >= value.projected() && index < value.current();
            Color accent = overflow ? OVERFLOW
                    : incoming ? INCOMING : outgoing ? OUTGOING : remaining ? CURRENT : EMPTY;
            context.fillRect(x, y, cellWidth, cellHeight, BACKGROUND);
            if (remaining && !overflow) {
                context.fillRect(x + 1f, y + 1f, Math.max(0f, cellWidth - 2f),
                        Math.max(0f, cellHeight - 2f), accent);
            }
            context.strokeRect(x + 0.5f, y + 0.5f, Math.max(0f, cellWidth - 1f),
                    Math.max(0f, cellHeight - 1f), accent, 1f);
            if (overflow) {
                hatch(context, x, y, cellWidth, cellHeight, accent, true);
            } else if (incoming) {
                hatch(context, x, y, cellWidth, cellHeight, accent, false);
                float centerX = x + cellWidth * 0.5f;
                float centerY = y + cellHeight * 0.5f;
                float arm = Math.min(3f, Math.min(cellWidth, cellHeight) * 0.3f);
                float stroke = Math.min(2f, arm * 0.7f);
                context.fillRect(centerX - arm, centerY - stroke * 0.5f,
                        arm * 2f, stroke, accent);
                context.fillRect(centerX - stroke * 0.5f, centerY - arm,
                        stroke, arm * 2f, accent);
            } else if (outgoing) {
                context.line(x + 3f, y + cellHeight * 0.5f,
                        x + cellWidth - 3f, y + cellHeight * 0.5f, accent, 1.4f);
            }
            if (total > count && index == count - 1) {
                float centerX = x + cellWidth * 0.5f;
                float centerY = y + cellHeight * 0.5f;
                context.fillRect(centerX - 5f, centerY - 2f, 10f, 4f, BACKGROUND);
                for (int dot = 0; dot < 3; dot++) {
                    context.fillRect(centerX - 5f + dot * 4f, centerY - 1f, 2f, 2f, accent);
                }
            }
        }
    }

    private static void hatch(CanvasContext context, float x, float y, float width,
                              float height, Color color, boolean cross) {
        float inset = 2f;
        float innerWidth = Math.max(0f, width - inset * 2f);
        float innerHeight = Math.max(0f, height - inset * 2f);
        // Short diagonal segments remain bounded inside every slot.
        for (float offset = -innerHeight; offset <= innerWidth; offset += 5f) {
            float from = Math.max(0f, -offset);
            float to = Math.min(innerHeight, innerWidth - offset);
            if (to <= from) continue;
            context.line(x + inset + offset + from, y + inset + innerHeight - from,
                    x + inset + offset + to, y + inset + innerHeight - to, color, 0.8f);
            if (cross) {
                context.line(x + inset + offset + from, y + inset + from,
                        x + inset + offset + to, y + inset + to, color, 0.8f);
            }
        }
    }
}

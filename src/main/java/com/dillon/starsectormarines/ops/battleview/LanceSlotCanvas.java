package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.function.Supplier;

/**
 * One pad of a lance as a small picture: the chassis or vehicle standing on it, or an empty
 * pad, with the pad's letter in the corner. The lance picker shows four of these per lance so
 * what a lance holds, and what it lacks, reads before any text.
 */
public final class LanceSlotCanvas implements CanvasProducer {

    private static final Color EMPTY_FLOOR = new Color(0x0b, 0x10, 0x12);
    private static final Color EMPTY_EDGE = new Color(0x8e, 0x9a, 0x8c, 110);
    private static final Color LETTER_PLATE = new Color(0xe2, 0xb3, 0x4a, 235);
    private static final Color LETTER_PLATE_EMPTY = new Color(0x5c, 0x62, 0x5a, 220);
    private static final Color LETTER_INK = new Color(0x14, 0x11, 0x0a, 255);

    private final String letter;
    private final CanvasProducer machine;

    public LanceSlotCanvas(String letter, MechVariant variant, VehicleType vehicle,
                           Supplier<LayeredMechAssets> assets) {
        if (letter == null) throw new IllegalArgumentException("pad letter required");
        this.letter = letter;
        this.machine = variant != null ? new MechChassisPreviewCanvas(variant, assets)
                : vehicle != null ? new VehiclePreviewCanvas(vehicle) : null;
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        if (machine != null) {
            machine.draw(context);
        } else {
            context.fillRect(0f, 0f, width, height, EMPTY_FLOOR);
            float inset = Math.max(4f, Math.min(width, height) * 0.14f);
            MechLabDollCanvas.drawCornerBrackets(context, inset, inset, width - inset * 2f, height - inset * 2f,
                    EMPTY_EDGE, 1.5f, Math.min(width, height) * 0.2f, 3f);
        }
        // Thumbnails are drawn at twice their laid-out size, so the tag is sized against the surface.
        float plate = Math.max(22f, Math.min(width, height) * 0.27f);
        BitmapFont font = plate >= 26f ? Fonts.ORBITRON_24_BOLD : Fonts.ORBITRON_12_BOLD;
        context.fillRect(2f, 2f, plate, plate, machine != null ? LETTER_PLATE : LETTER_PLATE_EMPTY);
        context.text(font, letter, 2f + (plate - font.measureWidth(letter)) * 0.5f,
                2f + (plate - font.getLineHeight()) * 0.5f, LETTER_INK);
    }
}

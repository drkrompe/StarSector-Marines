package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;

/**
 * Middle column: planet's equirectangular unwrap as a flat "tactical map."
 * Direct {@link SpriteAPI#render} draw inside the column body — no FBO,
 * no scene graph, no shader. The flat is the canvas that future mission node
 * sprites will be overlaid on.
 */
public class TacticalMapPanel extends OpsPanel {

    private static final Logger LOG = Global.getLogger(TacticalMapPanel.class);

    private static final float PAD_INNER = 8f;

    @Override
    public String getHeaderKey() {
        return "colTacticalMap";
    }

    @Override
    protected void onLayout(WidgetRoot widgets) {
        // Map is non-widget content; nothing to register in the widget tree (yet).
    }

    @Override
    public void onRender(float alphaMult) {
        if (ctx.planetTexture == null) return;
        try {
            SpriteAPI flat = Global.getSettings().getSprite(ctx.planetTexture);
            if (flat == null) return;
            float drawW = rect.w - 2 * PAD_INNER;
            float drawH = rect.h - 2 * PAD_INNER;
            flat.setSize(drawW, drawH);
            flat.setAlphaMult(alphaMult);
            flat.setNormalBlend();
            flat.render(rect.x + PAD_INNER, rect.y + PAD_INNER);
        } catch (Exception e) {
            LOG.error("TacticalMapPanel: flat unwrap draw failed", e);
        }
    }
}

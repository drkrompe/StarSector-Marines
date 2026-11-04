package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiTag;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.fs.starfarer.api.campaign.PlanetAPI;

/**
 * The space behind the company's menus, as the base game's own screens hang over
 * the world the fleet is at (owner). It is a document of its own, one full-page
 * canvas drawn before the page, so no page has to know about it; a page only has
 * to leave its root translucent.
 */
public final class MenuBackdrop {

    /** Close and large, filling the right of the page, as a market screen hangs over its world. */
    private static final float PLANET_X = 0.72f, PLANET_Y = 0.5f, PLANET_RADIUS = 0.72f;
    /** Lit from near the viewer, so the whole face shows, as on a market screen; a night side would be a black page. */
    private static final BackdropPlanetPass.Light DAYLIGHT = new BackdropPlanetPass.Light(0.45f, 0.35f, 0.82f, 1f);

    private final ShipBackdrop sky;
    private final UiViewport viewport;
    private final UiDocument document;

    public MenuBackdrop(PlanetAPI parkedAt, UiViewport viewport) {
        this.sky = ShipBackdrop.at(parkedAt);
        sky.hangPlanet(PLANET_X, PLANET_Y, PLANET_RADIUS, DAYLIGHT);
        this.viewport = viewport;
        int width = Math.round(viewport.documentWidth()), height = Math.round(viewport.documentHeight());
        UiElement canvas = new UiElement("menu-backdrop")
                .tag(UiTag.CANVAS)
                .canvasSize(width, height)
                .preferredSize(width, height);
        document = new UiDocument(canvas);
        document.canvases().set(canvas, context -> sky.draw(context, context.hostViewport(), 0f, 0f));
        document.layout(viewport.documentWidth(), viewport.documentHeight());
    }

    public void advance(float dt) {
        sky.advance(dt);
    }

    public void render(float alphaMult) {
        document.render(viewport, alphaMult);
    }
}

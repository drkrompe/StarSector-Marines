package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.texture.TextureLedger;
import com.dillon.starsectormarines.ui.retained.CanvasBlend;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.CanvasSpriteRegion;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.PlanetSpecAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import java.awt.Color;
import java.util.List;

/**
 * What is outside the hull: the sky of wherever the fleet is, and the world she
 * is parked at, if any.
 *
 * <p>The art is the base game's own, read off the campaign, so a ship seen in
 * Engineering is in the same place the player left on the map. Parked, the stars
 * barely move and the planet turns beyond her; away from a world, or anywhere
 * the company view is opened from the map, they stream past. Hyperspace has its
 * own clouds and no stars.
 */
public final class ShipBackdrop {

    /** A location that names no background of its own. */
    private static final String DEFAULT_SKY = "graphics/backgrounds/background4.jpg";
    private static final List<String> STAR_ART = List.of(
            "graphics/backgrounds/star0.png", "graphics/backgrounds/star1.png", "graphics/backgrounds/star2.png");
    private static final int STARS = 320;
    private static final long STAR_SEED = 4711L;
    /** A star's unit size on screen, in host pixels. */
    private static final float STAR_PX = 2.2f;
    /** Host pixels a second for the nearest stars: the hull's bow is to the left, so they run right. */
    private static final float UNDERWAY_DRIFT = 16f, PARKED_DRIFT = 2.5f;
    private static final Color SKY_TINT = new Color(150, 150, 150);
    /** How much larger than the page the sky is drawn, so panning never finds its edge. */
    private static final float SKY_OVERSCAN = 1.15f;
    /** Host pixels each layer shifts across the camera's whole pan; much less than the deck, so it is far away. */
    private static final float SKY_TRAVEL = 60f, STAR_TRAVEL = 220f, PLANET_TRAVEL = 90f;
    /** Where the planet hangs, as fractions of the page from its lower left, and its radius in page heights. */
    private static final float PLANET_X = 0.84f, PLANET_Y = 0.38f, PLANET_RADIUS = 0.5f;

    /**
     * A spec's spin suits a world the size of a map marker. Hung this large, the same
     * rate has its surface sliding by, so behind the hull it is a tenth of that (owner).
     */
    private static final float SPIN_SCALE = 0.1f;

    /** How a world looks, in the terms of its planet spec. */
    record Planet(String texture, String clouds, float tilt, float pitch, float spin, float cloudSpin,
                  Color tint, Color cloudTint, Color atmosphere, float atmosphereReach) {
    }

    private final String sky;
    private final Planet planet;
    private final boolean stars;
    private final StarField field = new StarField(STAR_SEED, STARS, STAR_ART.size());
    private float time;

    private ShipBackdrop(String sky, Planet planet, boolean stars) {
        this.sky = sky;
        this.planet = planet;
        this.stars = stars;
    }

    /** The backdrop for a fleet in its current location, parked at {@code parkedAt} or nowhere. */
    public static ShipBackdrop at(PlanetAPI parkedAt) {
        SectorAPI sector = Global.getSector();
        LocationAPI location = sector == null ? null : sector.getCurrentLocation();
        String sky = location == null ? null : location.getBackgroundTextureFilename();
        boolean hyperspace = location != null && location.isHyperspace();
        ShipBackdrop backdrop = new ShipBackdrop(sky == null || sky.isBlank() ? DEFAULT_SKY : sky, look(parkedAt), !hyperspace);
        backdrop.leaveArtWithTheGame();
        return backdrop;
    }

    /** All of this is the map's art too, and closing the page must not take it from the map. */
    private void leaveArtWithTheGame() {
        TextureLedger ledger = TextureLedger.shared();
        ledger.gameOwned(sky);
        STAR_ART.forEach(ledger::gameOwned);
        if (planet != null) {
            ledger.gameOwned(planet.texture());
            ledger.gameOwned(planet.clouds());
        }
    }

    private static Planet look(PlanetAPI world) {
        if (world == null || world.isStar() || world.getSpec() == null) return null;
        PlanetSpecAPI spec = world.getSpec();
        String texture = spec.getTexture();
        if (texture == null || texture.isBlank()) return null;
        String clouds = spec.getCloudTexture();
        return new Planet(texture, clouds == null || clouds.isBlank() ? null : clouds,
                spec.getTilt(), spec.getPitch(), spec.getRotation() * SPIN_SCALE, spec.getCloudRotation() * SPIN_SCALE,
                spec.getPlanetColor(), spec.getCloudColor(), spec.getAtmosphereColor(),
                spec.getAtmosphereThickness() * 0.25f);
    }

    public void advance(float dt) {
        time += dt;
    }

    /**
     * Paints behind everything else on the canvas.
     *
     * @param panX where the camera looks across the ship, -0.5 to 0.5
     * @param panY likewise along Y, which runs up
     */
    void draw(CanvasContext context, CanvasHostViewport rect, float panX, float panY) {
        float width = rect.width(), height = rect.height();
        float scaleX = rect.scaleX(), scaleY = rect.scaleY();

        // Vanilla's backgrounds are square; the sky is sized as one to keep them from stretching.
        float side = Math.max(width, height) * SKY_OVERSCAN;
        context.sprite(sky, null,
                (width * 0.5f - panX * SKY_TRAVEL) / scaleX, (height * 0.5f + panY * SKY_TRAVEL) / scaleY,
                side / scaleX, side / scaleY, 0f, SKY_TINT);

        if (stars) drawStars(context, width, height, scaleX, scaleY, panX, panY);

        if (planet != null) {
            context.hostPass(new BackdropPlanetPass(planet,
                    width * PLANET_X - panX * PLANET_TRAVEL, height * PLANET_Y - panY * PLANET_TRAVEL,
                    height * PLANET_RADIUS, time));
        }
    }

    private void drawStars(CanvasContext context, float width, float height, float scaleX, float scaleY,
                           float panX, float panY) {
        float drift = time * (planet != null ? PARKED_DRIFT : UNDERWAY_DRIFT);
        float travelX = drift - panX * STAR_TRAVEL, travelY = panY * STAR_TRAVEL;
        for (int star = 0; star < field.count(); star++) {
            float px = field.size(star) * STAR_PX;
            int level = Math.round(field.brightness(star) * 255f);
            context.sprite(STAR_ART.get(field.art(star)), null,
                    field.x(star, travelX, width) / scaleX, field.y(star, travelY, height) / scaleY,
                    px / scaleX, px / scaleY, 0f, new Color(level, level, level),
                    CanvasSpriteRegion.FULL, CanvasBlend.ADDITIVE);
        }
    }
}

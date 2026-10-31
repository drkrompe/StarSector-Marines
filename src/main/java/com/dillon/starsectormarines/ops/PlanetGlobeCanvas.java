package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.CanvasBlend;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.retained.CanvasSpriteRegion;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import java.awt.Color;
import java.util.Map;

/**
 * A small lit globe wrapped in a vanilla planet's equirectangular skin. It is drawn as vertical
 * strips of the texture, each as tall as the disc is at that column and shaded by its angle to
 * the light, so it reads as a sphere through the plain sprite draw both canvas backends share.
 * A location with no fixed planet gets a dark unknown world (its markup lays a "?" over it).
 */
final class PlanetGlobeCanvas implements CanvasProducer {

    /** Vanilla planet entity and that planet's own skin, by the location name the catalog shows. */
    private record Planet(String entityId, String texture) { }

    private static final Map<String, Planet> PLACED = Map.of(
            "Jangala", new Planet("jangala", "graphics/planets/jungle.jpg"),
            "Asharu", new Planet("asharu", "graphics/planets/desert.jpg"),
            "Warion", new Planet("corvus_IIIb", "graphics/planets/barren00.jpg"),
            "Garnir", new Planet("corvus_IIIa", "graphics/planets/cryovolcanic01.jpg"),
            "Agreus", new Planet("agreus", "graphics/planets/barren02.jpg"));
    private static final String UNKNOWN_SKIN = "graphics/planets/barren00.jpg";
    private static final Color UNKNOWN_TINT = new Color(78, 92, 100);
    private static final int STRIPS = 32;
    /** Light from the upper left, as the campaign map's star usually sits off-screen. */
    private static final float LIGHT_X = -0.6f, LIGHT_Z = 0.8f;

    private final String texture;
    private final Color tint;

    private PlanetGlobeCanvas(String texture, Color tint) {
        this.texture = texture;
        this.tint = tint;
    }

    /** True when {@code location} names a vanilla planet with a skin to show. */
    static boolean placed(String location) {
        return PLACED.containsKey(location);
    }

    static PlanetGlobeCanvas forLocation(String location) {
        Planet planet = PLACED.get(location);
        if (planet == null) return new PlanetGlobeCanvas(UNKNOWN_SKIN, UNKNOWN_TINT);
        return new PlanetGlobeCanvas(liveSkin(planet), Color.WHITE);
    }

    /** The skin the running sector gives the planet, which a mod may have changed; else vanilla's. */
    private static String liveSkin(Planet planet) {
        SectorAPI sector = Global.getSector();
        if (sector != null && sector.getEntityById(planet.entityId()) instanceof PlanetAPI live
                && live.getSpec() != null && live.getSpec().getTexture() != null) {
            return live.getSpec().getTexture();
        }
        return planet.texture();
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth(), height = context.metrics().surfaceHeight();
        float radius = Math.min(width, height) / 2f - 1f;
        float cx = width / 2f, cy = height / 2f;
        float step = 2f * radius / STRIPS;
        for (int i = 0; i < STRIPS; i++) {
            float left = -radius + i * step, right = left + step;
            float middle = (left + right) / 2f / radius;
            float halfHeight = radius * (float) Math.sqrt(Math.max(0f, 1f - middle * middle));
            // The visible hemisphere spans half the texture's longitude, centred on its middle.
            float u0 = 0.5f + (float) (Math.asin(left / radius) / (2 * Math.PI));
            float u1 = 0.5f + (float) (Math.asin(right / radius) / (2 * Math.PI));
            float normalZ = (float) Math.sqrt(Math.max(0f, 1f - middle * middle));
            float light = 0.16f + 0.84f * Math.max(0f, middle * LIGHT_X + normalZ * LIGHT_Z);
            Color shade = new Color(Math.round(tint.getRed() * light), Math.round(tint.getGreen() * light),
                    Math.round(tint.getBlue() * light));
            // A hair of overlap keeps the strips from showing seams once scaled to device pixels.
            context.sprite(texture, null, cx + (left + right) / 2f, cy, step + 0.35f, 2f * halfHeight, 0f, shade,
                    new CanvasSpriteRegion(u0, 0f, Math.max(0.001f, u1 - u0), 1f), CanvasBlend.NORMAL);
        }
    }
}

package com.dillon.starsectormarines.battle.world.model;

import java.util.List;

/**
 * What a map's void cells show: the structure the playing surface is the roof of, stepping down in tiers to
 * the ground far below, and that ground (the city drop void style, `voids.md`). Presentation only, written
 * once at generation; nothing in the battle reads it.
 *
 * @param cityTexture      a seamless, square mod image of the ground below, seen straight down
 * @param depthMeters      how far below the playing surface that ground lies; the structure stands on it
 * @param cityTileMeters   how many metres of the ground below one copy of its image covers
 * @param hazeR            the colour everything fades into with depth, matched to the ground image's haze
 * @param facadeTexture    a seamless image of the structure's walls seen straight on, or null for plain walls
 * @param facadeTileMeters how many metres of wall, across and down, one copy of the facade covers
 * @param roofTexture      a seamless image of a terrace's roof seen straight down, or null for plain roofs
 * @param roofTileMeters   how many metres of roof one copy of the roof image covers
 * @param tiers            the terraces below the surface, shallowest first: each stands at its depth and
 *                         reaches its outset beyond the surface's own edge
 * @param eyeHeight        how high the camera looks down from, as a multiple of the view's width: higher
 *                         flattens the drop (a long lens), lower deepens it (a wide one)
 * @param craft            sprite paths of the hulls that fly in and out of the structure's bays, cross the
 *                         skyways below and stand parked on its terraces; empty for none
 * @param craftMetersPerPx how many metres one pixel of a hull's sprite spans below the surface: the battle's
 *                         own craft are drawn small to sit beside infantry, and the structure's traffic is
 *                         sized to the structure instead
 */
public record VoidBackdrop(String cityTexture, float depthMeters, float cityTileMeters,
                           float hazeR, float hazeG, float hazeB,
                           String facadeTexture, float facadeTileMeters,
                           String roofTexture, float roofTileMeters,
                           List<Tier> tiers, float eyeHeight,
                           List<String> craft, float craftMetersPerPx) {

    /** A terrace: its roof stands {@code depthMeters} below the surface, {@code outsetMeters} out past its edge. */
    public record Tier(float depthMeters, float outsetMeters) { }

    public VoidBackdrop {
        tiers = List.copyOf(tiers);
        craft = List.copyOf(craft);
    }
}

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
 * @param eyeMeters        how high above the surface the camera looks down from, in metres, at every zoom: higher
 *                         flattens the drop (a long lens), lower deepens it (a wide one)
 * @param craft            sprite paths of the hulls that fly in and out of the structure's bays, cross the
 *                         skyways below and stand parked on its terraces; empty for none
 * @param craftMetersPerPx how many metres one pixel of a hull's sprite spans below the surface: the battle's
 *                         own craft are drawn small to sit beside infantry, and the structure's traffic is
 *                         sized to the structure instead
 * @param neighbours       towers standing in the drop around the playing surface, their roofs below it: no one
 *                         stands on them, they fill the space between the surface and the ground below
 * @param skyways          straight lanes of traffic through the drop, each flown both ways; empty to let the
 *                         renderer cross the drop beside the structures instead
 */
public record VoidBackdrop(String cityTexture, float depthMeters, float cityTileMeters,
                           float hazeR, float hazeG, float hazeB,
                           String facadeTexture, float facadeTileMeters,
                           String roofTexture, float roofTileMeters,
                           List<Tier> tiers, float eyeMeters,
                           List<String> craft, float craftMetersPerPx, List<Tower> neighbours,
                           List<Skyway> skyways) {

    /** A terrace: its roof stands {@code depthMeters} below the surface, {@code outsetMeters} out past its edge. */
    public record Tier(float depthMeters, float outsetMeters) { }

    /**
     * A tower standing in the drop: its roof covers cells {@code x0..x1-1} by {@code y0..y1-1}, {@code
     * roofDepthMeters} below the playing surface, and it steps down in the backdrop's tiers deeper than that.
     */
    public record Tower(int x0, int y0, int x1, int y1, float roofDepthMeters) { }

    /** A lane of traffic from {@code (x0, y0)} to {@code (x1, y1)} in cells, {@code depthMeters} below the surface. */
    public record Skyway(float x0, float y0, float x1, float y1, float depthMeters) { }

    public VoidBackdrop {
        skyways = List.copyOf(skyways);
        tiers = List.copyOf(tiers);
        craft = List.copyOf(craft);
        neighbours = List.copyOf(neighbours);
    }
}

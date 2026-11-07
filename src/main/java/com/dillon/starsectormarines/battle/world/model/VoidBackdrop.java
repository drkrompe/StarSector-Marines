package com.dillon.starsectormarines.battle.world.model;

/**
 * What a map's void cells show: the ground far below the playing surface, drawn as a tiled image that
 * shifts against the surface with depth as the camera moves (the city drop void style, `voids.md`).
 * Presentation only, written once at generation; nothing in the battle reads it.
 *
 * @param texturePath  a seamless, square mod image of the ground below, seen straight down
 * @param depthMeters  how far below the playing surface that ground lies; the surface's edge walls reach it
 * @param tileMeters   how many metres (cells) of the ground below one copy of the image covers
 * @param hazeR        the colour the edge walls fade into with depth, matched to the image's haze
 * @param lit          whether the walls' windows are lit (dusk, night) or dark glass (day)
 */
public record VoidBackdrop(String texturePath, float depthMeters, float tileMeters,
                           float hazeR, float hazeG, float hazeB, boolean lit) {
}

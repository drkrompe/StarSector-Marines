package com.dillon.starsectormarines.battle.world.gen;

/**
 * The private service lane through a factory row ({@code set-piece-blocks.md}): a walled corridor with a
 * gate at each end onto the street, the row's loading doors opening onto it. The two gate cells sit in the
 * row's end walls on the lane's centre, and the centre line runs between them in world cells; {@code width}
 * is the lane's clear width across. A fight down the lane can come from either gate.
 */
public record ServiceLane(int gateAX, int gateAY, int gateBX, int gateBY,
                          float startX, float startY, float endX, float endY, int width) {
}

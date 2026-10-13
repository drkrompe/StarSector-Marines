package com.dillon.starsectormarines.battle.combat.fx;

/** A crash fireball travelling in ground coordinates, independent of the hull after emission. */
public record RollingBlast(float x, float y, float radius, float vx, float vy) {}

package com.dillon.starsectormarines.battle.air.engine;

/**
 * One scraped weapon slot from a vanilla {@code .ship} hull, already converted
 * into the shuttle-local frame (cells, {@code +Y} nose / {@code +X} starboard) at
 * the global pixel density. {@code type}/{@code angleDeg} carry the raw vanilla
 * metadata so callers can filter (which slots a turret may mount on) and a
 * preview can label them.
 */
public record WeaponSlot(float localX, float localY, String type, float angleDeg) {}

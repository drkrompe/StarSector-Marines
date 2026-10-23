package com.dillon.starsectormarines.battle.turret;

/**
 * One {@link EmplacementStyle}'s art for a structure: the painted base under it and its mount's head and recoil
 * layers. A null path keeps the structure's own default ({@code art.base}, the mount's {@code sprite} and
 * {@code recoilSprite}), so a style may restyle the base alone.
 */
public record StructureLook(String basePath, String spritePath, String recoilSpritePath) {
}

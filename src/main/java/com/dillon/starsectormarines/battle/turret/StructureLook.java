package com.dillon.starsectormarines.battle.turret;

/**
 * One {@link EmplacementStyle}'s art for a structure: the painted base under it and its mount's head and recoil
 * layers. A null path keeps the structure's own default ({@code art.base}, the mount's {@code sprite} and
 * {@code recoilSprite}), so a style may restyle the base alone.
 *
 * <p>{@code shieldCells}, when positive, draws a light flux shield over the gun, a bubble of that radius in cells that
 * flares where hostile rounds land and is down while the structure is badly hurt (the high-tech styles). It is drawn,
 * not simulated: rounds pass it and do their full damage, as for every other look.
 */
public record StructureLook(String basePath, String spritePath, String recoilSpritePath, float shieldCells) {

    /** A look with no shield. */
    public StructureLook(String basePath, String spritePath, String recoilSpritePath) {
        this(basePath, spritePath, recoilSpritePath, 0f);
    }
}

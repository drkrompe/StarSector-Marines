package com.dillon.starsectormarines.battle.turret;

/**
 * One {@link EmplacementStyle}'s art for a structure: the painted base under it and its mount's head and recoil
 * layers. A null path keeps the structure's own default ({@code art.base}, the mount's {@code sprite} and
 * {@code recoilSprite}), so a style may restyle the base alone.
 *
 * <p>{@code shield}, when not null, fits the structure's directional flux shield ({@code StructureDef.fluxShield}),
 * drawn in that tint: the field-emitter styles' builders fit the generator, the others do not. It is the one thing a
 * look decides that a battle reads; the shield's strength is the structure's.
 */
public record StructureLook(String basePath, String spritePath, String recoilSpritePath, ShieldTint shield) {

    /** A look with no shield. */
    public StructureLook(String basePath, String spritePath, String recoilSpritePath) {
        this(basePath, spritePath, recoilSpritePath, null);
    }
}

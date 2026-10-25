package com.dillon.starsectormarines.battle.turret;

/**
 * One {@link EmplacementStyle}'s art for a structure: the painted base under it and its mount's head and recoil
 * layers. A null path keeps the structure's own default ({@code art.base}, the mount's {@code sprite} and
 * {@code recoilSprite}), so a style may restyle the base alone.
 *
 * <p>{@code shield}, when not null, fits the structure's directional flux shield ({@code StructureDef.fluxShield}),
 * drawn in that tint: the field-emitter styles' builders fit the generator, the others do not. It is the one thing a
 * look decides that a battle reads; the shield's strength is the structure's.
 *
 * <p>{@code fluxDevicePath}, when not null, is the style's flux hardware for a shielded structure: the capacitor banks
 * and radiators bolted to its plinth, painted cold, with the same hardware at full charge beside it as
 * {@code <name>-hot.png}. It is drawn only while a shield is fitted, heating with its flux.
 */
public record StructureLook(String basePath, String spritePath, String recoilSpritePath, ShieldTint shield,
                            String fluxDevicePath) {

    /** A look with no shield. */
    public StructureLook(String basePath, String spritePath, String recoilSpritePath) {
        this(basePath, spritePath, recoilSpritePath, null, null);
    }

    /** The flux hardware at full charge: {@link #fluxDevicePath} with {@code -hot} before its extension. */
    public String fluxDeviceHotPath() {
        if (fluxDevicePath == null) return null;
        int dot = fluxDevicePath.lastIndexOf('.');
        return dot < 0 ? fluxDevicePath + "-hot" : fluxDevicePath.substring(0, dot) + "-hot" + fluxDevicePath.substring(dot);
    }
}

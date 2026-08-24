package com.dillon.starsectormarines.marine;

import java.io.Serializable;

/** Stable loadout identity for one special item, separate from any weapon it activates. */
public record SpecialEquipmentDef(
        String id,
        String displayName,
        SpecialActivation activation,
        String weaponId,
        int startingAmmo,
        String aimSpritePath,
        String armoryIconPath,
        SmokeGrenadeSpec smokeGrenadeSpec,
        SatchelChargeSpec satchelChargeSpec) implements Serializable {

    public SpecialEquipmentDef(String id, String displayName,
                               SpecialActivation activation, String weaponId,
                               int startingAmmo, String aimSpritePath,
                               String armoryIconPath) {
        this(id, displayName, activation, weaponId, startingAmmo,
                aimSpritePath, armoryIconPath, null, null);
    }

    public SpecialEquipmentDef(String id, String displayName,
                               SpecialActivation activation, String weaponId,
                               int startingAmmo, String aimSpritePath,
                               String armoryIconPath,
                               SmokeGrenadeSpec smokeGrenadeSpec) {
        this(id, displayName, activation, weaponId, startingAmmo,
                aimSpritePath, armoryIconPath, smokeGrenadeSpec, null);
    }
}

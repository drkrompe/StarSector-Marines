package com.dillon.starsectormarines.battle.air.engine;

/**
 * One scraped weapon slot from a vanilla {@code .ship} hull, already converted
 * into the shuttle-local frame (cells, {@code +Y} nose / {@code +X} starboard) at
 * the global pixel density. {@code type}/{@code angleDeg} carry the raw vanilla
 * metadata so callers can filter (which slots a turret may mount on) and a
 * preview can label them.
 *
 * @param angleDeg vanilla's facing, degrees off the nose with port positive
 * @param mount vanilla's mount kind: {@code TURRET}, {@code HARDPOINT} or {@code HIDDEN}
 * @param arcDeg how far the mount traverses, centred on {@code angleDeg}
 */
public record WeaponSlot(float localX, float localY, String type, float angleDeg,
                         String mount, float arcDeg) {

    public WeaponSlot(float localX, float localY, String type, float angleDeg) {
        this(localX, localY, type, angleDeg, "", 0f);
    }

    /** Whether this is a traversing turret rather than a fixed hardpoint. */
    public boolean turret() {
        return "TURRET".equals(mount);
    }
}
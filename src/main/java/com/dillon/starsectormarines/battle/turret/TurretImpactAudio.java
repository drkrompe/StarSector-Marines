package com.dillon.starsectormarines.battle.turret;

/** Shared authored-sound selection for turret projectile arrivals. */
public final class TurretImpactAudio {

    private TurretImpactAudio() {}

    /** Authored audio wins; explosive turrets without it retain the generic fallback. */
    public static Cue resolve(StructureDef turret, String explosiveFallback) {
        if (turret == null) return null;
        var weapon = turret.mount.weapon;
        String soundId = weapon.impactSoundId;
        if (soundId == null) {
            if (!weapon.fx.hasExplosiveImpact()) return null;
            soundId = explosiveFallback;
        }
        float volume = weapon.fx.hasHeavyImpact() ? 0.82f : 0.55f;
        return new Cue(soundId, volume);
    }

    public record Cue(String soundId, float volume) {
        public Cue {
            if (soundId == null || soundId.isBlank()) {
                throw new IllegalArgumentException("impact sound id is required");
            }
        }
    }
}

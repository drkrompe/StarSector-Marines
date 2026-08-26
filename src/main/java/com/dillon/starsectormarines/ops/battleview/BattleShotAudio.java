package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.fs.starfarer.api.Global;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Shared authored weapon-fire audio dispatch for battle and embedded scenes. */
public final class BattleShotAudio {

    public static final float WORLD_UNITS_PER_CELL = 30f;
    private static final String FALLBACK_RIFLE = "marines_smallarms_rifle";
    private static final float PITCH_JITTER = 0.10f;

    private BattleShotAudio() { }

    /** Plays ordinary positional battle fire at each simulation muzzle. */
    public static void playPositional(List<ShotEvent> shots) {
        Vector2f zeroVelocity = new Vector2f(0f, 0f);
        for (ShotEvent shot : shots) {
            Cue cue = cue(shot);
            Vector2f location = new Vector2f(
                    shot.fromX * WORLD_UNITS_PER_CELL,
                    shot.fromY * WORLD_UNITS_PER_CELL);
            Global.getSoundPlayer().playSound(
                    cue.soundId(), pitch(), cue.volume(), location, zeroVelocity);
        }
    }

    /**
     * Plays the same authored weapon clips at the campaign listener anchor for
     * a bounded embedded scene. Vanilla weapon clips are positional combat
     * sounds rather than UI cues, so routing them through {@code playUISound}
     * can silently drop them even though the simulation emitted the shot.
     */
    public static void playCampaignLocal(List<ShotEvent> shots, float volumeScale) {
        float scale = Math.max(0f, volumeScale);
        Vector2f anchor = campaignAnchor();
        if (anchor == null) return;
        Vector2f zeroVelocity = new Vector2f(0f, 0f);
        for (ShotEvent shot : shots) {
            Cue cue = cue(shot);
            Global.getSoundPlayer().playSound(
                    cue.soundId(), pitch(), cue.volume() * scale,
                    anchor, zeroVelocity);
        }
    }

    private static Vector2f campaignAnchor() {
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null
                || Global.getSector().getPlayerFleet().getLocation() == null) {
            return null;
        }
        return new Vector2f(Global.getSector().getPlayerFleet().getLocation());
    }

    static Cue cue(ShotEvent shot) {
        if (shot.turretKind != null) {
            return new Cue(shot.turretKind.fireSoundId(), 1f);
        }
        if (shot.specialEquipmentDef != null) {
            return new Cue(shot.specialEquipmentDef.fireSoundId(), 1f);
        }
        if (shot.primaryWeaponDef != null) {
            return new Cue(shot.primaryWeaponDef.fireSoundId, 0.85f);
        }
        if (shot.mechWeapon != null) {
            return new Cue(shot.mechWeapon.fireSoundId(), 1f);
        }
        return new Cue(FALLBACK_RIFLE, 0.5f);
    }

    private static float pitch() {
        return 1f + ThreadLocalRandom.current().nextFloat(
                -PITCH_JITTER, PITCH_JITTER);
    }

    record Cue(String soundId, float volume) { }
}

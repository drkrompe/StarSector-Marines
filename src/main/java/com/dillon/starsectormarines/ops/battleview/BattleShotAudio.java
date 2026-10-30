package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.fs.starfarer.api.Global;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Shared authored weapon-fire audio dispatch for battle and embedded scenes. */
public final class BattleShotAudio {

    public static final float WORLD_UNITS_PER_CELL = 30f;
    private static final String FALLBACK_RIFLE = "marines_smallarms_rifle";
    /** How far a fire clip's pitch is rolled either side of 1; an offline soundtrack rolls the same spread. */
    static final float PITCH_JITTER = 0.10f;

    private BattleShotAudio() { }

    /**
     * Plays ordinary positional battle fire at each simulation muzzle. A
     * weapon with an authored loop is heard through {@code loops} instead: its
     * fire clip once as a lance starts, then its loop at the muzzle each
     * rendered frame until its pulses stop. {@code dt} is real seconds.
     */
    public static void playPositional(List<ShotEvent> shots, HeldFireLoops loops, float dt) {
        Vector2f zeroVelocity = new Vector2f(0f, 0f);
        loops.advance(dt);
        for (ShotEvent shot : shots) {
            Cue cue = cue(shot);
            if (!startsSound(shot, cue, loops)) continue;
            Vector2f location = new Vector2f(
                    shot.fromX * WORLD_UNITS_PER_CELL,
                    shot.fromY * WORLD_UNITS_PER_CELL);
            Global.getSoundPlayer().playSound(
                    cue.soundId(), pitch(), cue.volume(), location, zeroVelocity);
        }
        for (HeldFireLoops.Voice voice : loops.held()) {
            Global.getSoundPlayer().playLoop(voice.loopSoundId(), voice, 1f, voice.volume(),
                    new Vector2f(voice.x() * WORLD_UNITS_PER_CELL, voice.y() * WORLD_UNITS_PER_CELL),
                    zeroVelocity);
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
            if (cue.soundId() == null) continue;
            Global.getSoundPlayer().playSound(
                    cue.soundId(), pitch(), cue.volume() * scale,
                    anchor, zeroVelocity);
        }
    }

    /**
     * Arms a loop weapon's held voice and says whether this shot's own clip
     * should play: always for a one-clip weapon, only on a lance's first pulse
     * for a looped one. Package-private so an offline soundtrack ({@code BattleSoundLog}) decides as the screen does.
     */
    static boolean startsSound(ShotEvent shot, Cue cue, HeldFireLoops loops) {
        WeaponDef weapon = shot.weaponDef();
        String loopId = weapon != null ? weapon.loopSoundId : null;
        if (loopId != null
                && !loops.arm(shot.shooterId, loopId, cue.volume(), shot.fromX, shot.fromY)) {
            return false;
        }
        return cue.soundId() != null;
    }

    private static Vector2f campaignAnchor() {
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null
                || Global.getSector().getPlayerFleet().getLocation() == null) {
            return null;
        }
        return new Vector2f(Global.getSector().getPlayerFleet().getLocation());
    }

    /**
     * The fire clip a shot is heard by. A turret weapon may name none: the ground
     * colony's spines travel unseen under the ground and are heard only where they
     * break out, through the weapon's impact sound.
     */
    static Cue cue(ShotEvent shot) {
        if (shot.turretStructureDef != null) {
            return new Cue(shot.turretStructureDef.mount.weapon.fireSoundId, 1f);
        }
        if (shot.specialEquipmentDef != null) {
            return new Cue(shot.specialEquipmentDef.fireSoundId(), 1f);
        }
        if (shot.primaryWeaponDef != null) {
            return new Cue(shot.primaryWeaponDef.fireSoundId, 0.85f);
        }
        if (shot.mechWeaponDef != null) {
            return new Cue(shot.mechWeaponDef.fireSoundId, 1f);
        }
        return new Cue(FALLBACK_RIFLE, 0.5f);
    }

    private static float pitch() {
        return 1f + ThreadLocalRandom.current().nextFloat(
                -PITCH_JITTER, PITCH_JITTER);
    }

    record Cue(String soundId, float volume) { }
}

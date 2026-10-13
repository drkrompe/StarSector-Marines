package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.warship.WarshipSoundCue;
import com.fs.starfarer.api.Global;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Plays the sounds a warship asked for this frame, with the game's own sound
 * ids and positionally, as ground weapons are: the sound player attenuates and
 * pans by distance from the listener, so nothing here computes a volume from
 * the camera. Shared by the battle screen and the embedded ground presentation.
 */
public final class WarshipAudio {

    private static final float PITCH_JITTER = 0.06f;

    /** Identity the sound player keys a loop by; one is enough, as beams rarely overlap. */
    private static final Object BEAM_LOOP_OWNER = new Object();

    private WarshipAudio() { }

    /**
     * The pitch a kind of cue is played at. The atmosphere sounds are the game's own
     * clips used out of their register: the boom slowed to a rumble, the heavy hit
     * to a thud, the engine boost low at lift-off and higher as the ship climbs.
     */
    static float basePitch(WarshipSoundCue.Kind kind) {
        return switch (kind) {
            case ARRIVAL_REPORT -> 0.62f;
            case ARRIVAL_SETTLE -> 0.55f;
            case DEPARTURE_ROAR -> 0.6f;
            case DEPARTURE_CLIMB -> 0.95f;
            default -> 1f;
        };
    }

    public static void play(List<WarshipSoundCue> cues) {
        if (cues.isEmpty()) return;
        Vector2f zeroVelocity = new Vector2f(0f, 0f);
        for (WarshipSoundCue cue : cues) {
            Vector2f where = new Vector2f(
                    cue.x() * BattleShotAudio.WORLD_UNITS_PER_CELL,
                    cue.y() * BattleShotAudio.WORLD_UNITS_PER_CELL);
            if (cue.kind() == WarshipSoundCue.Kind.BEAM_BURNING) {
                // A loop lasts only the frames it is asked for again; no jitter, or it warbles.
                Global.getSoundPlayer().playLoop(cue.soundId(), BEAM_LOOP_OWNER, 1f, cue.volume(), where, zeroVelocity);
                continue;
            }
            float pitch = basePitch(cue.kind())
                    + ThreadLocalRandom.current().nextFloat(-PITCH_JITTER, PITCH_JITTER);
            Global.getSoundPlayer().playSound(cue.soundId(), pitch, cue.volume(), where, zeroVelocity);
        }
    }
}

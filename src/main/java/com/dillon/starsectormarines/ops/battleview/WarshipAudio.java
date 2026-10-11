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

    private WarshipAudio() { }

    public static void play(List<WarshipSoundCue> cues) {
        if (cues.isEmpty()) return;
        Vector2f zeroVelocity = new Vector2f(0f, 0f);
        for (WarshipSoundCue cue : cues) {
            Vector2f where = new Vector2f(
                    cue.x() * BattleShotAudio.WORLD_UNITS_PER_CELL,
                    cue.y() * BattleShotAudio.WORLD_UNITS_PER_CELL);
            float pitch = 1f + ThreadLocalRandom.current().nextFloat(-PITCH_JITTER, PITCH_JITTER);
            Global.getSoundPlayer().playSound(cue.soundId(), pitch, cue.volume(), where, zeroVelocity);
        }
    }
}

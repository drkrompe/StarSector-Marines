package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.fx.OrdnanceDelivery;
import com.dillon.starsectormarines.battle.combat.fx.OrdnanceRelease;
import com.fs.starfarer.api.Global;
import org.lwjgl.util.vector.Vector2f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Plays the weapon a delivery is coming out of, and the round arriving.
 *
 * <p>Positional throughout: every clip named by {@link OrdnanceFx} is one of
 * the base game's own mono weapon files, registered under our own id against
 * the read-only install rather than copied, the same trick the vehicle engine
 * loops and the flyby weapon block already use. A stereo clip here would be
 * silently non-positional.
 *
 * <p><b>A beam is held, not struck.</b> A gun and a bomb rack are events —
 * a clip per release, thinned by {@link OrdnanceCueGate} so a fourteen-round
 * second reads as a burst instead of as static. A beam being dragged across
 * the ground is one continuous sound for as long as it is on, so it is a loop
 * re-armed while the aircraft keeps firing and left to fade on its own when it
 * stops. The loop is keyed per releasing entity, so two aircraft painting at
 * once stay on two voices rather than folding into one.
 */
public final class OrdnanceAudio {

    /**
     * Seconds a beam loop stays armed after the last round of it.
     *
     * <p>Longer than the gap between two of its own rounds and shorter than a
     * pass, so the tone is continuous while the trigger is down and gone
     * shortly after it comes up. Re-arming strictly per released round would
     * flutter: the simulation steps at a coarser rate than the screen draws.
     */
    private static final float BEAM_HOLD_SECONDS = 0.30f;

    private static final float PITCH_JITTER = 0.08f;

    /** One held beam voice: its loop key, how long it stays armed, and where it is. */
    private static final class BeamVoice {
        final Object key = new Object();
        float hold;
        float x;
        float y;
    }

    private final OrdnanceCueGate gate = new OrdnanceCueGate();
    private final Map<Long, BeamVoice> beams = new HashMap<>();

    /**
     * Plays this frame's cues. {@code releases} are rounds that just left a
     * carrier and {@code arrivals} are rounds that just reached the ground —
     * two lists because they happen at different moments and a bomb's fall is
     * long enough to hear the difference.
     */
    public void update(List<OrdnanceRelease> releases, List<OrdnanceRelease> arrivals, float dt) {
        gate.advance(dt);
        decayBeams(dt);
        Vector2f zeroVelocity = new Vector2f(0f, 0f);

        for (int i = 0, n = releases.size(); i < n; i++) {
            OrdnanceRelease release = releases.get(i);
            OrdnanceFx fx = OrdnanceFx.of(release.delivery());
            if (fx.fireLoops()) {
                armBeam(release);
                continue;
            }
            if (!gate.allowFire(release.delivery())) continue;
            play(fx.fireSoundId(), fx.fireVolume(), release.fromX(), release.fromY(), zeroVelocity);
        }

        for (int i = 0, n = arrivals.size(); i < n; i++) {
            OrdnanceRelease arrival = arrivals.get(i);
            if (!gate.allowImpact(arrival.delivery())) continue;
            OrdnanceFx fx = OrdnanceFx.of(arrival.delivery());
            play(fx.impactSoundId(), fx.impactVolume(), arrival.toX(), arrival.toY(), zeroVelocity);
        }

        driveBeamLoops(zeroVelocity);
    }

    /** Drops every held voice — for a host tearing a battle down. */
    public void clear() {
        gate.clear();
        beams.clear();
    }

    private void armBeam(OrdnanceRelease release) {
        BeamVoice voice = beams.computeIfAbsent(release.sourceId(), id -> new BeamVoice());
        voice.hold = BEAM_HOLD_SECONDS;
        voice.x = release.fromX();
        voice.y = release.fromY();
    }

    private void decayBeams(float dt) {
        if (beams.isEmpty() || dt <= 0f) return;
        Iterator<Map.Entry<Long, BeamVoice>> it = beams.entrySet().iterator();
        while (it.hasNext()) {
            BeamVoice voice = it.next().getValue();
            voice.hold -= dt;
            // The held tone is left to fade the way the engine loops do: an
            // un-rearmed loop lapses on its own, so nothing has to know that an
            // aircraft stopped existing mid-pass.
            if (voice.hold <= 0f) it.remove();
        }
    }

    private void driveBeamLoops(Vector2f zeroVelocity) {
        if (beams.isEmpty()) return;
        OrdnanceFx fx = OrdnanceFx.of(OrdnanceDelivery.BEAM);
        for (BeamVoice voice : beams.values()) {
            Global.getSoundPlayer().playLoop(fx.fireSoundId(), voice.key, 1f, fx.fireVolume(),
                    world(voice.x, voice.y), zeroVelocity);
        }
    }

    private static void play(String soundId, float volume, float x, float y, Vector2f velocity) {
        if (soundId == null) return;
        Global.getSoundPlayer().playSound(soundId, pitch(), volume, world(x, y), velocity);
    }

    private static Vector2f world(float cellX, float cellY) {
        return new Vector2f(cellX * BattleShotAudio.WORLD_UNITS_PER_CELL,
                cellY * BattleShotAudio.WORLD_UNITS_PER_CELL);
    }

    private static float pitch() {
        return 1f + ThreadLocalRandom.current().nextFloat(-PITCH_JITTER, PITCH_JITTER);
    }
}

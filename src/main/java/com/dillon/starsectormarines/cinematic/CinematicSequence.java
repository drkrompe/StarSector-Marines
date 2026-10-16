package com.dillon.starsectormarines.cinematic;

import com.dillon.starsectormarines.cinematic.CinematicShot.Cue;

import java.util.ArrayList;
import java.util.List;

/**
 * The shots that actually loaded, laid end to end on one clock. A shot with no video never reaches
 * here, so the sequence closes over it: the arrival plays what exists, and shots can land one at
 * a time.
 */
public final class CinematicSequence {
    /** A cue on the sequence clock; {@code until} is NaN for a one-shot. */
    public record Scheduled(Cue cue, double start, double until) {
        /** Volume of a looped cue at {@code now}, ramped in and out; 0 outside its span. */
        public float loopVolume(double now) {
            if (now < start || now >= until) return 0f;
            double in = cue.fadeIn() <= 0 ? 1 : Math.min(1, (now - start) / cue.fadeIn());
            double out = cue.fadeOut() <= 0 ? 1 : Math.min(1, (until - now) / cue.fadeOut());
            return (float) (cue.volume() * in * out);
        }
    }

    private final List<CinematicShot> shots;
    private final double[] offsets;
    private final double total;
    private final List<Scheduled> cues = new ArrayList<>();

    public CinematicSequence(List<CinematicShot> shots) {
        this.shots = List.copyOf(shots);
        offsets = new double[this.shots.size()];
        double at = 0;
        for (int i = 0; i < offsets.length; i++) {
            offsets[i] = at;
            CinematicShot shot = this.shots.get(i);
            for (Cue c : shot.cues())
                cues.add(new Scheduled(c, at + c.t(), c.looped() ? at + c.until() : Double.NaN));
            at += shot.duration();
        }
        total = at;
        cues.sort((a, b) -> Double.compare(a.start(), b.start()));
    }

    public boolean isEmpty() { return shots.isEmpty(); }
    public double duration() { return total; }
    public int size() { return shots.size(); }
    public CinematicShot shot(int index) { return shots.get(index); }
    public double offset(int index) { return offsets[index]; }
    public List<Scheduled> cues() { return cues; }

    /** Index of the shot playing at sequence time {@code t}; the last shot once the clock runs out. */
    public int shotAt(double t) {
        int index = 0;
        for (int i = 0; i < offsets.length; i++) if (t >= offsets[i]) index = i;
        return index;
    }

    /** The frame of shot {@code index} to show at sequence time {@code t}, within its range. */
    public int frameAt(int index, double t) {
        CinematicShot shot = shots.get(index);
        int frame = (int) Math.floor((t - offsets[index]) * shot.fps());
        return Math.max(0, Math.min(shot.frames() - 1, frame));
    }
}

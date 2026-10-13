package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a tender's builders ask the presentation to show and sound: a weld crackle, a stun
 * burst when a builder is hit, an alert when a site is stood down under fire, a flourish
 * when a turret stands.
 *
 * <p>The serial tender tick only records events here; it never reads them back, so a headless
 * run stays silent and nothing presented can reach the simulation. Sound requests last one
 * frame ({@link #beginFrame()}); visual events are kept for {@link #EFFECT_TICKS} ticks so a
 * renderer can draw a burst as a pure function of the event and the current tick.
 */
public final class TenderCues {

    /** What happened. */
    public enum Kind {
        /** A torch is striking at a site (rate-limited per site). */
        WELD,
        /** A builder was hit and shocked. */
        STUN,
        /** A site was stood down because its builders are being shot. */
        ALERT,
        /** The structure stands. */
        COMPLETE
    }

    /**
     * One event at a place, in cells. {@code id} is the builder for a stun, else 0.
     * {@code tick} is the simulation tick it happened on.
     */
    public record Cue(Kind kind, Faction faction, float x, float y, long id, int tick) { }

    /** Ticks a visual event stays available to draw. */
    public static final int EFFECT_TICKS = 18;
    /** Fewest ticks between two weld sounds for one site, so a crew of three is one crackle. */
    public static final int WELD_GAP_TICKS = 9;

    private final List<Cue> frame = new ArrayList<>();
    private final List<Cue> frameView = Collections.unmodifiableList(frame);
    private final List<Cue> recent = new ArrayList<>();
    private final List<Cue> recentView = Collections.unmodifiableList(recent);
    private final Map<ConstructionOrder, Integer> lastWeld = new IdentityHashMap<>();

    /** Sound requests since the last {@link #beginFrame()}. */
    public List<Cue> cuesThisFrame() {
        return frameView;
    }

    /** Visual events from the last {@link #EFFECT_TICKS} ticks, oldest first. */
    public List<Cue> recent() {
        return recentView;
    }

    /** Forgets last frame's sound requests; called once per simulation advance. */
    public void beginFrame() {
        frame.clear();
    }

    /** Drops visual events older than {@link #EFFECT_TICKS} as of {@code tick}, and finished sites' weld clocks. */
    void prune(int tick) {
        recent.removeIf(c -> tick - c.tick() >= EFFECT_TICKS);
        lastWeld.keySet().removeIf(o -> o.phase().terminal());
    }

    void stun(Faction faction, float x, float y, long builder, int tick) {
        Cue c = new Cue(Kind.STUN, faction, x, y, builder, tick);
        frame.add(c);
        recent.add(c);
    }

    void alert(Faction faction, float x, float y, int tick) {
        frame.add(new Cue(Kind.ALERT, faction, x, y, 0L, tick));
    }

    void complete(Faction faction, float x, float y, int tick) {
        Cue c = new Cue(Kind.COMPLETE, faction, x, y, 0L, tick);
        frame.add(c);
        recent.add(c);
    }

    /** Asks for a weld crackle at {@code order}'s site if one is due; returns whether it was recorded. */
    boolean weld(ConstructionOrder order, Faction faction, int tick) {
        Integer last = lastWeld.get(order);
        if (!weldDue(last, tick)) return false;
        lastWeld.put(order, tick);
        frame.add(new Cue(Kind.WELD, faction, order.siteX(), order.siteY(), 0L, tick));
        return true;
    }

    /** Whether a weld sound is due on {@code tick} given the tick of the last one (null for none). */
    static boolean weldDue(Integer lastTick, int tick) {
        return lastTick == null || tick - lastTick >= WELD_GAP_TICKS || tick < lastTick;
    }
}

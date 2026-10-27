package com.dillon.starsectormarines.battle.story;

import com.dillon.starsectormarines.battle.audio.MusicState;
import com.dillon.starsectormarines.battle.audio.QuietBed;
import java.util.function.Predicate;

/**
 * Blind Watch's rules, free of the simulation ({@code blind-watch-sounding.md}, Mission flow): the quiet before the
 * alarm and what raised it, the uplink's warning to Garnir, and which of the three installations still stand. The
 * riggers and the false schedule are side-objective toolkit jobs ({@code mission-command-nouns.md}); the schedule's
 * payoff is {@link #falseSchedule()}.
 *
 * <p>Each advance takes what the battle saw this tick ({@link Facts}). A charge completed on a tick is applied before
 * the warning moves, so an uplink blown on the tick the bar would fill stops it.
 */
public final class BlindWatchProgress {
    /** How long the uplink takes to send the whole warning once the alarm sounds. UNTUNED: the doc's "about three minutes". */
    public static final float WARNING_SECONDS = 180f;
    /** Share sent at which the half-way call is made. */
    public static final float WARNING_HALF = .5f;
    /** The campaign mark the warning leaves once it reaches Garnir, for A Cold Account to read. */
    public static final String GARNIR_WARNED = "corvus.sable_wake.garnir_warned";
    /** The Dust Ledger's mark for the broker delivered: without it, Teague got away and warned the post. */
    public static final String TEAGUE_DELIVERED = "corvus.teague.delivered";
    /**
     * Whether an escaped Teague stands the post to (the owner's pending pick, built default on). Off, the post is never
     * stood-to whatever the campaign holds.
     */
    public static final boolean STOOD_TO_ENABLED = true;
    /** How much farther a stood-to post's sentries and mast see for the alarm. UNTUNED first pass. */
    public static final float STOOD_TO_SIGHT_SCALE = 1.25f;

    /** What raised the alarm, or {@link #QUIET} while nothing has. */
    public enum Alarm { QUIET, SHOTS, SIGHTED, MAST }

    /**
     * The warning's course. {@link #SENT}, {@link #STOPPED} and {@link #FALSE_SCHEDULE} are final: once the uplink is
     * down, or Garnir has taken the false schedule, nothing more goes out.
     */
    public enum Warning { WAITING, SENDING, SENT, STOPPED, FALSE_SCHEDULE;
        public boolean terminal() { return this == SENT || this == STOPPED || this == FALSE_SCHEDULE; }
    }

    /**
     * What the battle saw this tick.
     *
     * @param shot        a shot or thrown round by or at the company (anyone's but a civilian's)
     * @param sighted     a Sable sentry sees a company unit
     * @param mastSighted the ranging mast, still standing, sees a company unit
     * @param mastDown    the mast's charge has blown
     * @param uplinkDown  the uplink's charge has blown
     * @param dishesDown  the dish field's charge has blown
     */
    public record Facts(boolean shot, boolean sighted, boolean mastSighted, boolean mastDown, boolean uplinkDown,
                        boolean dishesDown) {
        public static Facts quiet() { return new Facts(false, false, false, false, false, false); }
        public Facts withShot() { return new Facts(true, sighted, mastSighted, mastDown, uplinkDown, dishesDown); }
        public Facts withSighted() { return new Facts(shot, true, mastSighted, mastDown, uplinkDown, dishesDown); }
        public Facts withMastSighted() { return new Facts(shot, sighted, true, mastDown, uplinkDown, dishesDown); }
        public Facts withMastDown() { return new Facts(shot, sighted, mastSighted, true, uplinkDown, dishesDown); }
        public Facts withUplinkDown() { return new Facts(shot, sighted, mastSighted, mastDown, true, dishesDown); }
        public Facts withDishesDown() { return new Facts(shot, sighted, mastSighted, mastDown, uplinkDown, true); }
    }

    private final float warningSeconds;
    private final boolean stoodTo;
    private float elapsed;
    private float sent;
    private Alarm alarm = Alarm.QUIET;
    private float alarmAt = -1f;
    private Warning warning = Warning.WAITING;
    private boolean mastStanding = true, uplinkStanding = true, dishesStanding = true;

    public BlindWatchProgress() { this(WARNING_SECONDS, false); }

    public BlindWatchProgress(float warningSeconds, boolean stoodTo) {
        if (!(warningSeconds > 0f)) throw new IllegalArgumentException("the warning must take some time");
        this.warningSeconds = warningSeconds;
        this.stoodTo = stoodTo;
    }

    /**
     * Whether the post starts stood-to, from the campaign's marks: Teague was not delivered, so he warned it. Read at
     * the fixture boundary; the battle is only told the answer.
     */
    public static boolean stoodTo(Predicate<String> campaignHas) {
        return STOOD_TO_ENABLED && !campaignHas.test(TEAGUE_DELIVERED);
    }

    /** The sentries the post posts: its usual watch, doubled when stood-to. */
    public static int sentries(int usual, boolean stoodTo) {
        if (usual < 0) throw new IllegalArgumentException("no negative watch");
        return stoodTo ? usual * 2 : usual;
    }

    public void advance(float dt, Facts facts) {
        elapsed += dt;
        if (alarm == Alarm.QUIET) {
            Alarm cause = facts.shot() ? Alarm.SHOTS : facts.sighted() ? Alarm.SIGHTED
                    : facts.mastSighted() && mastStanding ? Alarm.MAST : Alarm.QUIET;
            if (cause != Alarm.QUIET) { alarm = cause; alarmAt = elapsed; }
        }
        if (facts.mastDown()) mastStanding = false;
        if (facts.dishesDown()) dishesStanding = false;
        if (facts.uplinkDown() && uplinkStanding) {
            uplinkStanding = false;
            if (!warning.terminal()) warning = Warning.STOPPED;
        }
        if (warning == Warning.WAITING && alarm != Alarm.QUIET) warning = Warning.SENDING;
        if (warning == Warning.SENDING) {
            sent = Math.min(warningSeconds, sent + dt);
            if (sent >= warningSeconds) warning = Warning.SENT;
        }
    }

    /**
     * Garnir takes the false schedule instead of the warning: the warning can no longer complete. Only while the uplink
     * stands and the warning has not gone; otherwise nothing changes.
     */
    public void falseSchedule() {
        if (uplinkStanding && !warning.terminal()) warning = Warning.FALSE_SCHEDULE;
    }

    public boolean quiet() { return alarm == Alarm.QUIET; }
    /** The mission's music: quiet until the alarm, combat from it on. */
    public MusicState musicState() { return quiet() ? MusicState.QUIET : MusicState.COMBAT; }
    /** The walk-in's bed: cold and mechanical, a signals post. */
    public QuietBed quietBed() { return QuietBed.SEQUENCER_PULSE; }
    public Alarm alarm() { return alarm; }
    /** Seconds into the battle when the alarm sounded, or -1 while quiet. */
    public float alarmAt() { return alarmAt; }
    public boolean stoodTo() { return stoodTo; }
    /** How far the post's eyes reach for the alarm, as a share of their usual sight. */
    public float sightScale() { return stoodTo ? STOOD_TO_SIGHT_SCALE : 1f; }

    public Warning warning() { return warning; }
    /** Share of the warning sent so far, 0..1; it stays where it stood when the warning stopped. */
    public float sentShare() { return sent / warningSeconds; }
    public float warningSeconds() { return warningSeconds; }
    /** Seconds until the warning completes; zero unless it is being sent. */
    public float warningRemaining() { return warning == Warning.SENDING ? warningSeconds - sent : 0f; }
    /** Whether the warning reached Garnir: the campaign keeps {@link #GARNIR_WARNED}. */
    public boolean garnirWarned() { return warning == Warning.SENT; }

    /** The ranging mast stands: it watches for Sable. */
    public boolean mastStanding() { return mastStanding; }
    /** The uplink stands: it can send. */
    public boolean uplinkStanding() { return uplinkStanding; }
    /** The dish field stands: the riggers' window is open. */
    public boolean dishesStanding() { return dishesStanding; }
}

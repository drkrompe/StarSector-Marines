package com.dillon.starsectormarines.battle.story;

import com.dillon.starsectormarines.battle.audio.MusicState;

/**
 * The Dust Ledger's rules, free of the simulation ({@code dust-ledger-crossing.md}, Mission flow): the quiet phase and
 * its lift-off clock, the one alarm and what raised it, the ledger burn and its grade, and the broker's flight,
 * surrender and escape. The side objectives, the broker's delivery among them, are side-objective toolkit jobs
 * ({@code mission-command-nouns.md}) whose window is this quiet phase.
 *
 * <p>Each advance takes what the battle saw this tick ({@link Facts}) and moves the state on.
 */
public final class DustLedgerProgress {
    /** The broker's scheduled lift-off, counted from the first tick. UNTUNED: the doc's "about five minutes". */
    public static final float CLOCK_SECONDS = 300f;
    /** How long the clerks take to burn the whole ledger once the alarm sounds. UNTUNED: the doc's "about 75 seconds". */
    public static final float BURN_SECONDS = 75f;
    /** Unburned share at or above which the ledger grades {@link Grade#WHOLE}. */
    public static final float WHOLE_SHARE = .75f;

    /** What raised the alarm, or {@link #QUIET} while nothing has. */
    public enum Alarm { QUIET, SHOTS, SIGHTED, CLOCK }

    /**
     * Where the broker is in his flight. {@link #ESCAPED} and {@link #DEAD} are final; a {@link #HELD} broker brought
     * to the apron is the broker side job's to judge.
     */
    public enum Broker { IN_OFFICE, FLEEING, HELD, ESCAPED, DEAD;
        public boolean terminal() { return this == ESCAPED || this == DEAD; }
    }

    /** The unburned share at seizure, banded for the debrief. */
    public enum Grade { WHOLE, PARTIAL, ASHES }

    /**
     * What the battle saw this tick.
     *
     * @param shot          a shot or thrown round by the company or Sable Wake
     * @param sighted       a Sable Wake combatant sees a company unit inside the exclusion ring
     * @param ledgerSeized  the ledger room is seized (the Raid seizure)
     * @param brokerAlive   the broker is on the field and alive (false once he has left it)
     * @param brokerContact a company combatant is within reach of the broker
     * @param brokerAtPad   the broker stands at the skimmer pad
     */
    public record Facts(boolean shot, boolean sighted, boolean ledgerSeized, boolean brokerAlive,
                        boolean brokerContact, boolean brokerAtPad) {
        public static Facts quiet() { return new Facts(false, false, false, true, false, false); }
        public Facts withShot() { return new Facts(true, sighted, ledgerSeized, brokerAlive, brokerContact, brokerAtPad); }
        public Facts withSighted() { return new Facts(shot, true, ledgerSeized, brokerAlive, brokerContact, brokerAtPad); }
        public Facts withSeized() { return new Facts(shot, sighted, true, brokerAlive, brokerContact, brokerAtPad); }
        public Facts withBrokerDead() { return new Facts(shot, sighted, ledgerSeized, false, brokerContact, brokerAtPad); }
        public Facts withContact() { return new Facts(shot, sighted, ledgerSeized, brokerAlive, true, brokerAtPad); }
        public Facts withAtPad() { return new Facts(shot, sighted, ledgerSeized, brokerAlive, brokerContact, true); }
    }

    private final float clockSeconds;
    private final float burnSeconds;
    private float elapsed;
    private float burned;
    private Alarm alarm = Alarm.QUIET;
    private float alarmAt = -1f;
    private Broker broker = Broker.IN_OFFICE;
    private float unburnedAtSeizure = -1f;

    public DustLedgerProgress() { this(CLOCK_SECONDS, BURN_SECONDS); }

    public DustLedgerProgress(float clockSeconds, float burnSeconds) {
        if (clockSeconds <= 0f || burnSeconds <= 0f) throw new IllegalArgumentException("clock and burn must be positive");
        this.clockSeconds = clockSeconds;
        this.burnSeconds = burnSeconds;
    }

    public void advance(float dt, Facts facts) {
        elapsed += dt;
        if (alarm == Alarm.QUIET) {
            Alarm cause = facts.shot() ? Alarm.SHOTS : facts.sighted() ? Alarm.SIGHTED
                    : elapsed >= clockSeconds ? Alarm.CLOCK : Alarm.QUIET;
            if (cause != Alarm.QUIET) raise(cause);
        }
        if (unburnedAtSeizure < 0f) {
            if (facts.ledgerSeized()) unburnedAtSeizure = 1f - burned / burnSeconds;
            else if (alarm != Alarm.QUIET) burned = Math.min(burnSeconds, burned + dt);
        }
        advanceBroker(facts);
    }

    private void raise(Alarm cause) {
        alarm = cause;
        alarmAt = elapsed;
        // The skimmer left on schedule with him aboard; he radios the alarm on his way out.
        if (cause == Alarm.CLOCK && broker == Broker.IN_OFFICE) broker = Broker.ESCAPED;
        else if (broker == Broker.IN_OFFICE) broker = Broker.FLEEING;
    }

    private void advanceBroker(Facts facts) {
        if (broker.terminal()) return;
        if (!facts.brokerAlive()) { broker = Broker.DEAD; return; }
        if ((broker == Broker.IN_OFFICE || broker == Broker.FLEEING) && facts.brokerContact()) broker = Broker.HELD;
        if (broker == Broker.FLEEING && facts.brokerAtPad()) broker = Broker.ESCAPED;
    }

    public boolean quiet() { return alarm == Alarm.QUIET; }
    /** The mission's music: quiet while the company walks in as freight, combat from the alarm on. */
    public MusicState musicState() { return quiet() ? MusicState.QUIET : MusicState.COMBAT; }
    public Alarm alarm() { return alarm; }
    /** Seconds into the battle when the alarm sounded, or -1 while quiet. */
    public float alarmAt() { return alarmAt; }
    /** Seconds left on the lift-off clock; zero once the alarm has sounded. */
    public float clockRemaining() { return quiet() ? Math.max(0f, clockSeconds - elapsed) : 0f; }
    public float clockSeconds() { return clockSeconds; }
    /** Share of the ledger burned so far, 0..1. */
    public float burnedShare() { return burned / burnSeconds; }
    public boolean burning() { return !quiet() && unburnedAtSeizure < 0f && burned < burnSeconds; }
    public boolean seized() { return unburnedAtSeizure >= 0f; }
    /** The unburned share when the ledger room was seized, or -1 before seizure. */
    public float unburnedShare() { return unburnedAtSeizure; }
    /** The ledger grade, or {@code null} before seizure. A fully burned ledger is {@link Grade#ASHES}, not a defeat. */
    public Grade grade() {
        if (unburnedAtSeizure < 0f) return null;
        return unburnedAtSeizure >= WHOLE_SHARE ? Grade.WHOLE : unburnedAtSeizure > 0f ? Grade.PARTIAL : Grade.ASHES;
    }
    public Broker broker() { return broker; }
}

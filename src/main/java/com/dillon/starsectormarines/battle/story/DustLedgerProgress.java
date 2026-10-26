package com.dillon.starsectormarines.battle.story;

import com.dillon.starsectormarines.battle.audio.MusicState;

/**
 * The Dust Ledger's rules, free of the simulation ({@code dust-ledger-crossing.md}, Mission flow and Side
 * objectives): the quiet phase and its lift-off clock, the one alarm and what raised it, the ledger burn and its
 * grade, the broker's flight, surrender, escort and escape, and the two quiet-phase side objectives.
 *
 * <p>Each advance takes what the battle saw this tick ({@link Facts}) and moves the state on. Side objectives are
 * judged before the alarm in the same advance, so a crate set down on the tick the alarm sounds still counts; one
 * finished after the alarm pays nothing.
 */
public final class DustLedgerProgress {
    /** The broker's scheduled lift-off, counted from the first tick. UNTUNED: the doc's "about five minutes". */
    public static final float CLOCK_SECONDS = 300f;
    /** How long the clerks take to burn the whole ledger once the alarm sounds. UNTUNED: the doc's "about 75 seconds". */
    public static final float BURN_SECONDS = 75f;
    /** How long a squad leader stays at the well house for the warden's nod. UNTUNED first pass. */
    public static final float NOD_SECONDS = 8f;
    /** How long a company combatant works the feed junction, unbroken, to cut the nests' power. UNTUNED first pass. */
    public static final float FEED_SECONDS = 10f;
    /** Unburned share at or above which the ledger grades {@link Grade#WHOLE}. */
    public static final float WHOLE_SHARE = .75f;

    /** What raised the alarm, or {@link #QUIET} while nothing has. */
    public enum Alarm { QUIET, SHOTS, SIGHTED, CLOCK }

    /** The broker's fate so far. {@link #DELIVERED}, {@link #ESCAPED} and {@link #DEAD} are final. */
    public enum Broker { IN_OFFICE, FLEEING, HELD, DELIVERED, ESCAPED, DEAD;
        public boolean terminal() { return this == DELIVERED || this == ESCAPED || this == DEAD; }
    }

    /** The unburned share at seizure, banded for the debrief. */
    public enum Grade { WHOLE, PARTIAL, ASHES }

    /** One side objective: not begun or under way, done before the alarm (pays), or not done in time (pays nothing). */
    public enum Side { OPEN, PAID, LAPSED }

    /** The three quiet-phase side objectives, by name. */
    public enum SideObjective { COUPLING, WARDEN, FEED }

    /**
     * What the battle saw this tick.
     *
     * @param shot          a shot or thrown round by the company or Sable Wake
     * @param sighted       a Sable Wake combatant sees a company unit inside the exclusion ring
     * @param ledgerSeized  the ledger room is seized (the Raid seizure)
     * @param brokerAlive   the broker is on the field and alive (false once he has left it)
     * @param brokerContact a company combatant is within reach of the broker
     * @param brokerAtPad   the broker stands at the skimmer pad
     * @param brokerAtApron the broker stands on the freight apron
     * @param crateInYard   the coupling crate has been carried into the Harrow yard
     * @param leaderAtWell  a company squad leader stands in the warden's well house
     * @param atFeed        a company combatant is working the feed junction
     */
    public record Facts(boolean shot, boolean sighted, boolean ledgerSeized, boolean brokerAlive,
                        boolean brokerContact, boolean brokerAtPad, boolean brokerAtApron,
                        boolean crateInYard, boolean leaderAtWell, boolean atFeed) {
        public static Facts quiet() { return new Facts(false, false, false, true, false, false, false, false, false, false); }
        public Facts withShot() { return new Facts(true, sighted, ledgerSeized, brokerAlive, brokerContact, brokerAtPad, brokerAtApron, crateInYard, leaderAtWell, atFeed); }
        public Facts withSighted() { return new Facts(shot, true, ledgerSeized, brokerAlive, brokerContact, brokerAtPad, brokerAtApron, crateInYard, leaderAtWell, atFeed); }
        public Facts withSeized() { return new Facts(shot, sighted, true, brokerAlive, brokerContact, brokerAtPad, brokerAtApron, crateInYard, leaderAtWell, atFeed); }
        public Facts withBrokerDead() { return new Facts(shot, sighted, ledgerSeized, false, brokerContact, brokerAtPad, brokerAtApron, crateInYard, leaderAtWell, atFeed); }
        public Facts withContact() { return new Facts(shot, sighted, ledgerSeized, brokerAlive, true, brokerAtPad, brokerAtApron, crateInYard, leaderAtWell, atFeed); }
        public Facts withAtPad() { return new Facts(shot, sighted, ledgerSeized, brokerAlive, brokerContact, true, brokerAtApron, crateInYard, leaderAtWell, atFeed); }
        public Facts withAtApron() { return new Facts(shot, sighted, ledgerSeized, brokerAlive, brokerContact, brokerAtPad, true, crateInYard, leaderAtWell, atFeed); }
        public Facts withCrateInYard() { return new Facts(shot, sighted, ledgerSeized, brokerAlive, brokerContact, brokerAtPad, brokerAtApron, true, leaderAtWell, atFeed); }
        public Facts withAtFeed() { return new Facts(shot, sighted, ledgerSeized, brokerAlive, brokerContact, brokerAtPad, brokerAtApron, crateInYard, leaderAtWell, true); }
        public Facts withLeaderAtWell() { return new Facts(shot, sighted, ledgerSeized, brokerAlive, brokerContact, brokerAtPad, brokerAtApron, crateInYard, true, atFeed); }
    }

    private final float clockSeconds;
    private final float burnSeconds;
    private float elapsed;
    private float burned;
    private float nod;
    private Alarm alarm = Alarm.QUIET;
    private float alarmAt = -1f;
    private Broker broker = Broker.IN_OFFICE;
    private Side coupling = Side.OPEN, warden = Side.OPEN, feed = Side.OPEN;
    private float feedWork;
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
            if (coupling == Side.OPEN && facts.crateInYard()) coupling = Side.PAID;
            nod = facts.leaderAtWell() ? nod + dt : 0f;
            if (warden == Side.OPEN && nod >= NOD_SECONDS) warden = Side.PAID;
            feedWork = facts.atFeed() ? feedWork + dt : 0f;
            if (feed == Side.OPEN && feedWork >= FEED_SECONDS) feed = Side.PAID;
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
        if (coupling == Side.OPEN) coupling = Side.LAPSED;
        if (warden == Side.OPEN) warden = Side.LAPSED;
        if (feed == Side.OPEN) feed = Side.LAPSED;
        // The skimmer left on schedule with him aboard; he radios the alarm on his way out.
        if (cause == Alarm.CLOCK && broker == Broker.IN_OFFICE) broker = Broker.ESCAPED;
        else if (broker == Broker.IN_OFFICE) broker = Broker.FLEEING;
    }

    private void advanceBroker(Facts facts) {
        if (broker.terminal()) return;
        if (!facts.brokerAlive()) { broker = Broker.DEAD; return; }
        if ((broker == Broker.IN_OFFICE || broker == Broker.FLEEING) && facts.brokerContact()) broker = Broker.HELD;
        if (broker == Broker.FLEEING && facts.brokerAtPad()) broker = Broker.ESCAPED;
        else if (broker == Broker.HELD && facts.brokerAtApron()) broker = Broker.DELIVERED;
    }

    /**
     * Pays a side objective outright, as if it had been done: for evidence that plays one approach on demand
     * ({@code DustLedgerSequence.SIDE_OBJECTIVES_PROPERTY}). Only while quiet, as the law requires.
     */
    public void grant(SideObjective which) {
        if (!quiet()) throw new IllegalStateException("side objectives pay only before the alarm");
        switch (which) {
            case COUPLING -> coupling = Side.PAID;
            case WARDEN -> warden = Side.PAID;
            case FEED -> feed = Side.PAID;
        }
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
    public Side coupling() { return coupling; }
    public Side warden() { return warden; }
    public Side feed() { return feed; }
    /** Seconds a company combatant has worked the feed junction without a break. */
    public float feedProgress() { return feedWork; }
    /** The gun nests fight when the alarm comes unless the feed was cut first. */
    public boolean nestsPowered() { return feed != Side.PAID; }
    /** Seconds a squad leader has stood at the well house without a break. */
    public float nodProgress() { return nod; }
    /** The salvage team answers the alarm only if the coupling reached them first. */
    public boolean salvageTeamJoins() { return coupling == Side.PAID; }
    /** The cantina drinkers arm and join unless the town watch holds the cantina. */
    public boolean drinkersJoin() { return warden != Side.PAID; }
    /** The watch's rooftop spotters reveal the compound yard from the nod until the alarm. */
    public boolean spottersUp() { return warden == Side.PAID && quiet(); }
}

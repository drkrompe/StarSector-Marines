package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.HullAirspace;
import com.dillon.starsectormarines.battle.warship.HullFireHold;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipDuelLedger;
import com.dillon.starsectormarines.battle.world.gen.BlackSluice;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The duel over Black Sluice ({@code warship-behaviours.md}, ship against ship). The <i>Greyhook</i>, Voss's Venture,
 * sits landed on the berths south of the ridge with her two Hound (P) escorts from the start, quiet: they fire at
 * nothing on the ground, and the battle's hull fire is held until Voss's withdrawal beat ({@link HullFireHold#untilBeat}).
 * When Voss orders the site abandoned, the <i>Greyhook</i> lifts and climbs out west over the southern ground,
 * answering what comes into reach as she goes; the hold lets the Hounds loose to screen and brawl; and two of Rusk's
 * Wolf (H) arrive through the cloud deck on a visit nobody can command. The hull fight is kept over the southern
 * ground ({@link HullAirspace}), in sight of the ground battle and off it. It stays gun against gun: the Venture's
 * fitting carries no wing, and a warship over a ground battle never launches one from its bay. Every hull fights
 * hulls by the ordinary default; no hull of the duel ever fires at the ground. Nothing is scripted after the order:
 * who escapes, breaks off or falls is the ships' own doing, and the debrief reads it from the
 * {@link WarshipDuelLedger}.
 *
 * <p>The ground referee reads nothing here. Stray rounds and falling wreckage are real on the ground, which is the
 * one way the duel can touch the fight below.
 */
public final class BlackSluiceDuel implements SmallWarDuel {

    private static final Logger LOG = Global.getLogger(BlackSluiceDuel.class);

    /** The Venture and her fitting: guns only, no wing. */
    static final String GREYHOOK_HULL = "venture", GREYHOOK_VARIANT = "venture_Greyhook";
    /** Rusk's Wolfs and their fitting. */
    static final String WOLF_HULL = "wolf_hegemony", WOLF_VARIANT = "wolf_hegemony_Rusk";
    /**
     * Where the Greyhook and her escorts sit: landed on the southern pad field at true scale
     * ({@code landed-ships-true-scale.md}), the Greyhook in the cradle and a Hound in each berth beside it, noses
     * west. They lift when Voss orders the withdrawal and fly their lift runs, their bodies easing to flight scale as
     * they go. The whole duel is kept over the southern ground below {@link BlackSluice#AIRSPACE_TOP}, off the ground
     * fight.
     */
    static final float RIG_X = BlackSluice.VENTURE_CRADLE.x(), RIG_Y = BlackSluice.VENTURE_CRADLE.y();
    static final List<BlackSluice.Berth> HOUND_BERTHS = BlackSluice.HOUND_BERTHS;
    /**
     * Where the Wolfs drop through the cloud deck: at the far west of the southern ground, so the chase runs east
     * toward the pads. At flight presence a pulse laser reaches about eighty cells, so nowhere on a three-hundred-cell
     * map is out of reach for long: from here the Wolfs fly about half a minute before the western Hound is in their
     * reach. Dropped seventy-five cells east of the pads they were on the eastern Hound as it lifted, and both Hounds
     * fell within fifteen seconds of the order having fired almost nothing; dropped eighty cells west of the western
     * berth, the same in twelve.
     */
    static final float[][] WOLF_ENTRIES = {{RIG_X - 176f, RIG_Y + 26f}, {RIG_X - 176f, RIG_Y - 14f}};
    /**
     * The Greyhook's way out: off her cradle nose first, round to the south and over the south edge. A Venture in
     * atmosphere makes under a cell a second, so the west edge, two hundred cells off, was most of five minutes; the
     * south edge is two and a half, under the Wolfs' guns for most of it.
     */
    static final float[][] CLIMB_ROUTE = {{RIG_X - 24f, RIG_Y - 18f}, {RIG_X - 40f, RIG_Y - 70f}};
    /** The southern ground the hull fight is kept over: the whole width, below the ridge that shuts off the north. */
    static final HullAirspace AIRSPACE = new HullAirspace(0f, 0f, BlackSluice.WIDTH, BlackSluice.AIRSPACE_TOP);
    /** Seconds without a hostile hull over the battle before the Wolfs leave. */
    static final float WOLF_DWELL_SECONDS = 10f;

    private Warship rig;
    private final List<Warship> hounds = new ArrayList<>();
    private final List<Warship> wolves = new ArrayList<>();
    private WarshipDuelLedger ledger;
    private boolean ordered;

    /** Puts the rig and its escorts on their pads, quiet, before the first tick. */
    public void stage(BattleSimulation sim) {
        if (rig != null) throw new IllegalStateException("The duel is staged once");
        try {
            rig = Warship.fit(GREYHOOK_HULL, GREYHOOK_VARIANT, Faction.DEFENDER, RIG_X, RIG_Y,
                    BlackSluice.VENTURE_CRADLE.facing());
            rig.berth(RIG_X, RIG_Y);
            rig.limitFlux();
            rig.groundFire(Warship.GroundFire.HOLD);
            for (BlackSluice.Berth berth : HOUND_BERTHS) {
                Warship hound = Warship.fit("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER,
                        berth.x(), berth.y(), berth.facing());
                hound.berth(berth.x(), berth.y());
                hound.limitFlux();
                hound.groundFire(Warship.GroundFire.HOLD);
                hounds.add(hound);
            }
        } catch (Exception e) {
            // A hull table that cannot be read leaves the chapter without a duel, never without its ground fight.
            LOG.warn("Black Sluice duel could not fit Greyhook's hulls; no duel overhead", e);
            rig = null;
            hounds.clear();
            return;
        }
        sim.addWarship(rig);
        for (Warship hound : hounds) sim.addWarship(hound);
        sim.warships().holdHullFire(HullFireHold.untilBeat(SmallWarSequence.VOSS_ABANDON_BEAT));
        sim.warships().keepHullFightOver(AIRSPACE);
        ledger = sim.warships().duelLedger();
    }

    private static Warship quiet(Warship ship) {
        ship.stationTo(ship.x, ship.y);
        ship.limitFlux();
        ship.useDoctrine();
        ship.groundFire(Warship.GroundFire.HOLD);
        return ship;
    }

    @Override
    public void withdrawalOrdered(BattleSimulation sim) {
        if (ordered || rig == null) return;
        ordered = true;
        rig.climbOut(CLIMB_ROUTE, BlackSluice.WIDTH, BlackSluice.HEIGHT);
        // The escorts lift out of their berths and take up their screen.
        for (Warship hound : hounds) {
            hound.lift(CLIMB_ROUTE[0][0], CLIMB_ROUTE[0][1]);
            quiet(hound).escort(rig);
        }
        try {
            for (float[] entry : WOLF_ENTRIES) {
                Warship wolf = quiet(Warship.fit(WOLF_HULL, WOLF_VARIANT, Faction.ALLY,
                        entry[0], entry[1], 0f));
                // Out of the deck already pointed at the Greyhook's group, closing under its own handling.
                wolf.arriveFromAbove(RIG_X, RIG_Y);
                wolf.visit(WOLF_DWELL_SECONDS);
                wolves.add(wolf);
            }
        } catch (Exception e) {
            LOG.warn("Black Sluice duel could not fit the Wolfs; the Greyhook leaves unopposed", e);
            wolves.clear();
        }
        for (Warship wolf : wolves) sim.addWarship(wolf);
    }

    @Override
    public void advance(BattleSimulation sim) {
        if (!ordered || rig == null) return;
        // An escort with no lead left and nobody to fight goes home after it: one finite visit for Greyhook too.
        boolean leadGone = rig.destroyed() || rig.departed();
        boolean wolfOver = false;
        for (Warship wolf : wolves) wolfOver |= !wolf.destroyed() && !wolf.departed();
        if (!leadGone || wolfOver) return;
        for (Warship hound : hounds) {
            if (!hound.destroyed() && !hound.departing() && !hound.departed()) {
                hound.climbOut(CLIMB_ROUTE, BlackSluice.WIDTH, BlackSluice.HEIGHT);
            }
        }
    }

    /** The Greyhook, Voss's Venture, or {@code null} when none could be fitted. */
    public Warship greyhook() { return rig; }
    public List<Warship> hounds() { return List.copyOf(hounds); }
    public List<Warship> wolves() { return List.copyOf(wolves); }
    /** Every hull of the duel: the Greyhook, her escorts, and the Wolfs once they have come. */
    public List<Warship> hulls() {
        List<Warship> out = new ArrayList<>();
        if (rig != null) out.add(rig);
        out.addAll(hounds);
        out.addAll(wolves);
        return out;
    }
    public boolean ordered() { return ordered; }

    @Override
    public String outcomeSummary() {
        if (!ordered || ledger == null) return null;
        StringBuilder s = new StringBuilder("Overhead: ");
        s.append(rigLine()).append(' ');
        s.append(group("Greyhook's two Hounds", hounds)).append(' ');
        s.append(wolves.isEmpty() ? "The Wolfs never came." : group("The two Wolfs Rusk lent", wolves));
        String ground = groundLine();
        if (ground != null) s.append(' ').append(ground);
        return s.toString();
    }

    private String rigLine() {
        WarshipDuelLedger.Entry e = ledger.entry(rig);
        WarshipDuelLedger.Outcome o = e == null ? WarshipDuelLedger.Outcome.OVER_THE_BATTLE : e.outcome();
        return switch (o) {
            case ESCAPED -> "the Greyhook got away, " + state(rig) + ".";
            case DESTROYED -> "the Greyhook was shot down onto the basin.";
            case BROKE_OFF, DEPARTED -> "the Greyhook left the field, " + state(rig) + ".";
            case OVER_THE_BATTLE -> "the Greyhook was still over the berths when we finished.";
        };
    }

    /** How a hull that got away looked going: untouched, scratched, hurt or barely flying. */
    static String state(Warship ship) {
        float f = ship.hullFraction();
        if (f >= 0.95f) return "untouched";
        if (f >= 0.7f) return "scarred";
        if (f >= 0.35f) return "badly hurt";
        return "barely flying";
    }

    private String group(String who, List<Warship> ships) {
        int[] counts = new int[WarshipDuelLedger.Outcome.values().length];
        for (Warship ship : ships) {
            WarshipDuelLedger.Entry e = ledger.entry(ship);
            counts[(e == null ? WarshipDuelLedger.Outcome.OVER_THE_BATTLE : e.outcome()).ordinal()]++;
        }
        List<String> parts = new ArrayList<>();
        add(parts, counts, WarshipDuelLedger.Outcome.DESTROYED, "shot down");
        add(parts, counts, WarshipDuelLedger.Outcome.ESCAPED, "got away");
        add(parts, counts, WarshipDuelLedger.Outcome.BROKE_OFF, "broke off hurt");
        add(parts, counts, WarshipDuelLedger.Outcome.DEPARTED, "left when the fight was done");
        add(parts, counts, WarshipDuelLedger.Outcome.OVER_THE_BATTLE, "still overhead");
        return who + ": " + String.join(", ", parts) + ".";
    }

    private static void add(List<String> parts, int[] counts, WarshipDuelLedger.Outcome o, String words) {
        int n = counts[o.ordinal()];
        if (n == 0) return;
        parts.add((n == 1 ? "one " : n == 2 ? "both " : n + " ") + words);
    }

    private String groundLine() {
        int ours = 0, allies = 0, theirs = 0;
        for (WarshipDuelLedger.Cause c : WarshipDuelLedger.Cause.values()) {
            ours += ledger.killed(c, Faction.MARINE);
            allies += ledger.killed(c, Faction.ALLY);
            theirs += ledger.killed(c, Faction.DEFENDER);
        }
        if (ours + allies + theirs == 0) return "Nothing that fell out of that fight killed anyone on the ground.";
        List<String> parts = new ArrayList<>();
        if (ours > 0) parts.add(ours + " of ours");
        if (allies > 0) parts.add(allies + " of Rusk's people");
        if (theirs > 0) parts.add(theirs + " of Greyhook's");
        return String.format(Locale.ROOT, "Stray fire and falling wreckage killed %s.", String.join(", ", parts));
    }

    /** The ledger's line for one hull of the duel, for evidence. */
    public WarshipDuelLedger.Entry entry(Warship ship) {
        return ledger == null ? null : ledger.entry(ship);
    }
}

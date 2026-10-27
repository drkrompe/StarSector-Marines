package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.HullFireHold;
import com.dillon.starsectormarines.battle.warship.HullRefit;
import com.dillon.starsectormarines.battle.warship.MapEdge;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipDuelHelm;
import com.dillon.starsectormarines.battle.warship.WarshipDuelLedger;
import com.dillon.starsectormarines.battle.warship.WarshipSystem;
import com.dillon.starsectormarines.battle.world.gen.BlackSluice;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * The duel over Black Sluice ({@code warship-behaviours.md}, ship against ship). The <i>Greyhook</i>, Voss's Venture,
 * sits landed alone in the cradle south of the ridge from the start, quiet: she fires at nothing on the ground, and the
 * battle's hull fire is held until Voss's withdrawal beat ({@link HullFireHold#untilBeat}). When Voss orders the site
 * abandoned, the <i>Greyhook</i> lifts and turns to fight: Dormer's ships are faster than she is, so she does not run at
 * once. She fights under the ordinary warship doctrine and tactics, and the pair the Dormer family has lent
 * ({@link #RUSK_PAIR}: a Hegemony Enforcer and Dormer's own Wolf) fly in across the west edge, already under way on a
 * heading east, on a visit nobody can command. Two bow-fighting hulls on one target fight it as a pack relay
 * ({@code warship-tactics.md}, slice 7): the sturdier Enforcer holds her eye and the Wolf strikes from her quarter.
 * Each lent hull carries its own refit ({@link RuskHull#refit}), the one thing this mission tunes on them.
 *
 * <p>The fight has an ending, and it is Voss's call ({@link BreakOff}): {@link #breakOffSeconds} after the first hull
 * fire, or sooner if her hull falls under {@link #BREAK_OFF_HULL} or nothing of Dormer's is left to stop her, Voss orders
 * the run, and the <i>Greyhook</i> flies out across her own edge ({@link #GREYHOOK_EDGE}, south) with Dormer's ships on her,
 * answering what is in reach as she goes. She gets off the map or she is shot down onto the basin, a falling wreck
 * that strikes and skids to rest. That is the only way any hull of the duel leaves: every hull holds the field
 * ({@link Warship#holdTheField}), so no doctrine withdraws one and no visit ends, and Dormer's ships stay over the battle
 * once she is gone. The battle is held open until her part has concluded ({@link WarshipSystem#concludeBeforeBattleEnds}):
 * a ground fight that finishes first waits for her escape or her wreck. Nothing warps in or out, and no area is
 * declared for the fight, which goes where the fighting takes it, kept off the map's edges only by the standing edge
 * pressure every warship flies with. It stays gun against gun: the Venture's fitting carries no wing, and a warship
 * over a ground battle never launches one from its bay. No hull of the duel ever fires at the ground. Only the order to
 * run is scripted: who falls is the ships' own doing, and the debrief reads it from the {@link WarshipDuelLedger}.
 *
 * <p>The ground referee reads nothing here. Stray rounds and falling wreckage are real on the ground, which is the
 * one way the duel can touch the fight below.
 */
public final class BlackSluiceDuel implements SmallWarDuel {

    private static final Logger LOG = Global.getLogger(BlackSluiceDuel.class);

    /** The Venture and her fitting: her guns, a Harpoon and a Sabot rack in her two small hardpoints, no wing. */
    static final String GREYHOOK_HULL = "venture", GREYHOOK_VARIANT = "venture_Greyhook";
    /**
     * Dormer's refit: capacity and dissipation raised together (the guns and the shield draw on the one pool) by a
     * quarter, the shield's upkeep cut by a quarter and each absorbed point costing a fifth less flux, and the arc
     * widened by twenty degrees. No gun, plate or hull figure changes. Written for his hunting Wolfs, it now rides on
     * his Enforcer ({@link #ENFORCER}).
     */
    public static final HullRefit RUSK_REFIT = new HullRefit("Dormer's hunter refit", 1.25f, 1.25f, 20f, 0.75f, 0.8f);
    /**
     * One of the hulls Dormer lends: what the radio and the captions call it, its hull and fitting, and the refit it
     * flies in.
     */
    public record RuskHull(String name, String hullId, String variantId, HullRefit refit) {
        public RuskHull {
            if (name == null || hullId == null || variantId == null || refit == null) {
                throw new IllegalArgumentException("A lent hull needs a name, a hull, a fitting and a refit");
            }
        }

        /** The same hull in another fitting or refit, for a tuning diagnostic. */
        public RuskHull with(String hull, String variant, HullRefit other) {
            return new RuskHull(name, hull, variant, other);
        }
    }
    /**
     * Dormer's Enforcer: the base game's own Balanced Enforcer, one of the Hegemony's stock medium-combat fits
     * (Arbalests and Heavy Mortars on the turrets, Harpoons and Reapers in the racks), fitted as Hegemony line ships
     * are, in Dormer's refit. Nine hundred of armour that her autocannons glance off makes it the pair's anvil: the
     * relay's tank, holding her eye from nearest. The refit is what lets it finish her: measured over the five duel
     * seeds, without it the Enforcer has to break off to vent, she kills the Wolf on four of them, and the lone Enforcer
     * cannot finish her inside the twenty-five-minute bound.
     */
    public static final RuskHull ENFORCER = new RuskHull("Enforcer", "enforcer", "enforcer_Balanced", RUSK_REFIT);
    /**
     * Dormer's Wolf: his own hunter fitting ({@code wolf_hegemony_Rusk}: an Antimatter Blaster, a pulse laser, Harpoons)
     * as fitted, with no refit: the frigate that darts in on her quarter while the Enforcer holds her eye, and the one
     * hull of the pair her autocannons bite. In the hunter refit it ended at 68 to 97 percent of its hull over the five
     * duel seeds; as fitted it is the hull at risk.
     */
    public static final RuskHull WOLF = new RuskHull("Wolf", "wolf_hegemony", "wolf_hegemony_Rusk", HullRefit.NONE);
    /**
     * The pair Dormer's family lends (owner, 2026-10-19: "We don't need this to be an even fight. We could have an
     * enforcer and a wolf even"): one Enforcer and one Wolf, flying in at {@link #RUSK_ENTRY_ALONG} in this order.
     */
    public static final List<RuskHull> RUSK_PAIR = List.of(ENFORCER, WOLF);
    /**
     * The Greyhook's refit for this fight: none, she flies as fitted. Her Heavy Autocannons' kinetic rounds glance off
     * an Enforcer's nine hundred of armour and only her pulse laser bites it, so her way through the pair is the Wolf.
     * The open-vents refit she carried while the duel ran to the death (dissipation up a third, a little more pool, a
     * shield taking a tenth less flux) let her outlast the pair for seven to ten minutes; in a fight that ends with her
     * run for the edge it made the run safe on every seed (19 to 40 percent of her hull left at the edge); as fitted
     * the run is a gamble, decided by whether she goes before the Enforcer's first real volley
     * ({@link #breakOffSeconds}).
     */
    public static final HullRefit GREYHOOK_REFIT = HullRefit.NONE;
    /**
     * Where the Greyhook sits: landed in the cradle on the southern pad field at true scale
     * ({@code landed-ships-true-scale.md}), nose west, the frigate pads either side of her empty. She lifts when Voss
     * orders the withdrawal and flies her lift run, her body easing to flight scale as she goes, turning toward
     * {@link #LIFT_TOWARD_X}, {@link #LIFT_TOWARD_Y}.
     */
    static final float RIG_X = BlackSluice.VENTURE_CRADLE.x(), RIG_Y = BlackSluice.VENTURE_CRADLE.y();
    /**
     * Dormer's edge: west, the side the company landed on and the far side of the basin from the pads, so their
     * whole approach is in sight before a gun bears.
     */
    public static final MapEdge RUSK_EDGE = MapEdge.WEST;
    /**
     * Where along the west edge Dormer's hulls fly in, in {@link #RUSK_PAIR} order: either side of a line a little north of the pads, clear of the
     * south edge's margin, so they come straight on across the basin rather than turning off the edge.
     */
    static final float[] RUSK_ENTRY_ALONG = {84f + 22f, 84f - 22f};
    /**
     * The Greyhook's edge: south, over her own pads and the salt flats beyond them, away from the ground fight and
     * square to Dormer's line of approach, so the run shows her flank to his guns the whole way. She leaves across it
     * and no other way. East (her side's ground: dispatch, the berth yard, the berth road) was measured first: the
     * fight sits about three hundred cells in, where the Enforcer's reach meets hers, a hundred and sixty cells and
     * nearly four minutes of flying from the east edge, so the duel ran five to six minutes past the first hit. The
     * south run is about a hundred and twenty cells, nearly three minutes, U-turn included.
     */
    public static final MapEdge GREYHOOK_EDGE = MapEdge.SOUTH;
    /** Why, and whether, Voss ordered the run for her edge. */
    public enum BreakOff {
        /** Still fighting. */
        NONE,
        /** {@link #breakOffSeconds} after the first hull fire: she has fought them as long as she means to. */
        ORDERED,
        /** Her hull fell under {@link #BREAK_OFF_HULL}. */
        CRIPPLED,
        /** Nothing of Dormer's is left over the battle to stop her, or no hull fire came at all. */
        UNOPPOSED
    }
    /**
     * The earliest and latest Voss orders the run, in seconds after the first hull fire ({@link #breakOffSeconds}).
     * The first fire is a long-range exchange on shields; the Enforcer first hurts her about seventy-five seconds
     * later, in a burst of missiles that takes a third of her hull. Ordered before it she reaches the edge with a few
     * percent of her hull; ordered after it she is mostly shot down. Where in this window Voss calls it is what makes
     * the run a gamble rather than a foregone conclusion.
     */
    public static final float BREAK_OFF_EARLIEST_SECONDS = 68f, BREAK_OFF_LATEST_SECONDS = 84f;
    /** Below this share of her hull Voss orders the run at once, whatever the clock says. */
    public static final float BREAK_OFF_HULL = 0.5f;
    /** Seconds after the order with no hull fire at all before she leaves anyway: nobody came to stop her. */
    static final float NO_CONTACT_SECONDS = 150f;
    /** Dormer's entry heading: due east, in the hull-facing convention. */
    static final float RUSK_HEADING = 270f;
    /**
     * Where the Greyhook's lift run turns toward: over the middle of the station, north-west of the
     * cradle and toward Dormer's side, clear of the south edge's margin. Where they go from there is their doctrine's.
     */
    static final float LIFT_TOWARD_X = BlackSluice.APPROACH + 150f, LIFT_TOWARD_Y = 98f;
    /**
     * One of Dormer's hulls this many times its own reach from a Greyhook hull is closing: the radio says so. An
     * Enforcer's reach is long enough that a wider share would hear it closing the moment it crosses the edge, with
     * the sighting.
     */
    static final float CLOSING_REACH_SHARE = 1.1f;

    /** The story beats the duel publishes for the radio. */
    public static final String RUSK_SIGHTED_BEAT = "small_war.rusk_sighted",
            RUSK_CLOSING_BEAT = "small_war.rusk_closing",
            FIRST_HULL_FIRE_BEAT = "small_war.first_hull_fire",
            RUSK_FIRST_DOWN_BEAT = "small_war.rusk_first_down",
            GREYHOOK_BREAKS_BEAT = "small_war.greyhook_breaks",
            GREYHOOK_ESCAPED_BEAT = "small_war.greyhook_escaped",
            GREYHOOK_DOWN_BEAT = "small_war.greyhook_down",
            RUSK_DOWN_BEAT = "small_war.rusk_down";

    private final String greyhookVariant;
    private final List<RuskHull> pair;
    private final HullRefit greyhookRefit;
    private Warship rig;
    private final List<Warship> rusk = new ArrayList<>();
    private WarshipDuelLedger ledger;
    private boolean ordered;
    private int decidedTick = -1;
    private int orderTick = -1, firstFireTick = -1, breakTick = -1;
    private BreakOff breakOff = BreakOff.NONE;
    private boolean sighted, closing, fired, firstDown, greyhookDown, greyhookEscaped, ruskDown;

    /** The chapter's duel: Dormer's Enforcer and Wolf, each in its own refit, the Greyhook in hers. */
    public BlackSluiceDuel() {
        this(GREYHOOK_VARIANT, RUSK_PAIR, GREYHOOK_REFIT);
    }

    /**
     * The duel with other hulls, fittings or refits on either side, for a tuning diagnostic; the chapter plays the
     * {@linkplain #BlackSluiceDuel() default}. Dormer's hulls fly in at {@link #RUSK_ENTRY_ALONG} in list order, at most
     * one per entry.
     */
    BlackSluiceDuel(String greyhookVariant, List<RuskHull> pair, HullRefit greyhookRefit) {
        if (pair.isEmpty() || pair.size() > RUSK_ENTRY_ALONG.length) {
            throw new IllegalArgumentException("Dormer lends one to " + RUSK_ENTRY_ALONG.length + " hulls, not " + pair.size());
        }
        this.greyhookVariant = greyhookVariant;
        this.pair = List.copyOf(pair);
        this.greyhookRefit = greyhookRefit;
    }

    /** Puts the rig in its cradle, quiet, before the first tick. */
    public void stage(BattleSimulation sim) {
        if (rig != null) throw new IllegalStateException("The duel is staged once");
        try {
            rig = Warship.fit(GREYHOOK_HULL, greyhookVariant, Faction.DEFENDER, RIG_X, RIG_Y,
                    BlackSluice.VENTURE_CRADLE.facing());
            rig.refit(greyhookRefit);
            rig.berth(RIG_X, RIG_Y);
            rig.limitFlux();
            rig.groundFire(Warship.GroundFire.HOLD);
        } catch (Exception e) {
            // A hull table that cannot be read leaves the chapter without a duel, never without its ground fight.
            LOG.warn("Black Sluice duel could not fit the Greyhook; no duel overhead", e);
            rig = null;
            return;
        }
        sim.addWarship(rig);
        sim.warships().holdHullFire(HullFireHold.untilBeat(SmallWarSequence.VOSS_ABANDON_BEAT));
        sim.warships().assignEdge(Faction.ALLY, RUSK_EDGE);
        sim.warships().assignEdge(Faction.DEFENDER, GREYHOOK_EDGE);
        ledger = sim.warships().duelLedger();
    }

    /** A hull of the duel: under doctrine, holding the field, firing at nothing on the ground. */
    private static Warship quiet(Warship ship) {
        ship.stationTo(ship.x, ship.y);
        ship.limitFlux();
        ship.useDoctrine();
        ship.holdTheField();
        ship.groundFire(Warship.GroundFire.HOLD);
        return ship;
    }

    @Override
    public void withdrawalOrdered(BattleSimulation sim) {
        if (ordered || rig == null) return;
        ordered = true;
        orderTick = sim.getSimTickIndex();
        // She lifts and fights under her doctrine; the battle does not end until she is off the map or her wreck is down.
        quiet(rig).lift(LIFT_TOWARD_X, LIFT_TOWARD_Y);
        sim.warships().concludeBeforeBattleEnds(rig);
        try {
            for (int i = 0; i < pair.size(); i++) {
                RuskHull lent = pair.get(i);
                float along = RUSK_ENTRY_ALONG[i];
                Warship ship = quiet(Warship.fit(lent.hullId(), lent.variantId(), Faction.ALLY, 0f, along, RUSK_HEADING));
                ship.refit(lent.refit());
                // In across the west edge already under way on its heading over the basin, closing on its own handling.
                sim.warships().enterAcrossEdge(sim, ship, along, RUSK_HEADING);
                // The mission's, not the player's. Holding the field, its visit never ends.
                ship.visit(0f);
                rusk.add(ship);
            }
        } catch (Exception e) {
            LOG.warn("Black Sluice duel could not fit Dormer's hulls; the Greyhook holds the sky unopposed", e);
            rusk.clear();
        }
        for (Warship ship : rusk) sim.addWarship(ship);
    }

    @Override
    public void advance(BattleSimulation sim) {
        if (!ordered || rig == null) return;
        radio(sim);
        breakOff(sim);
        if (!greyhookEscaped && rig.departed() && rig.physicallyDeparted()) {
            greyhookEscaped = true;
            sim.emitStoryBeat(GREYHOOK_ESCAPED_BEAT);
        }
        if (decidedTick < 0 && (rig.destroyed() || rig.departed())) decidedTick = sim.getSimTickIndex();
    }

    /**
     * When Voss orders the run, in seconds after the first hull fire, for an order to abandon Black Sluice given on
     * {@code orderTick}: somewhere in {@link #BREAK_OFF_EARLIEST_SECONDS} to {@link #BREAK_OFF_LATEST_SECONDS}, fixed by
     * a hash of that tick. Her nerve is read off the moment the ground battle forced her hand, so the same battle
     * always plays the same way, a different one may not, and nothing is drawn from the battle's random stream.
     */
    static float breakOffSeconds(int orderTick) {
        long h = (orderTick + 0x5EEDL) * 0x9E3779B97F4A7C15L;
        h ^= h >>> 32;
        h *= 0xD6E8FEB86659FD93L;
        h ^= h >>> 32;
        float u = (h >>> 40) / (float) (1L << 24);
        return BREAK_OFF_EARLIEST_SECONDS + u * (BREAK_OFF_LATEST_SECONDS - BREAK_OFF_EARLIEST_SECONDS);
    }

    /**
     * Voss's call: once she has fought {@link #breakOffSeconds} from the first hull fire, or her hull is under
     * {@link #BREAK_OFF_HULL}, or nothing of Dormer's is left to stop her, the Greyhook runs for her edge, fighting as
     * she goes. Called once a tick; it orders the run at most once.
     */
    private void breakOff(BattleSimulation sim) {
        if (breakOff != BreakOff.NONE || !over(rig) || rig.berthed()) return;
        int tick = sim.getSimTickIndex();
        if (fired && firstFireTick < 0) firstFireTick = tick;
        BreakOff why = rusk.isEmpty() || allDown(rusk) ? BreakOff.UNOPPOSED
                : rig.hullFraction() < BREAK_OFF_HULL ? BreakOff.CRIPPLED
                : firstFireTick >= 0 && tick - firstFireTick >= breakOffSeconds(orderTick) / BattleSimulation.TICK_DT ? BreakOff.ORDERED
                : firstFireTick < 0 && tick - orderTick >= NO_CONTACT_SECONDS / BattleSimulation.TICK_DT ? BreakOff.UNOPPOSED
                : BreakOff.NONE;
        if (why == BreakOff.NONE) return;
        breakOff = why;
        breakTick = tick;
        sim.emitStoryBeat(GREYHOOK_BREAKS_BEAT);
        sim.warships().leaveAcrossEdge(sim, rig);
    }

    private static boolean over(Warship ship) {
        return !ship.destroyed() && !ship.departed();
    }

    private static boolean allDown(List<Warship> ships) {
        for (Warship ship : ships) if (!ship.destroyed()) return false;
        return true;
    }

    /** Whether the hull's centre is over the map: a hull flying in is sighted once it has crossed the edge. */
    private static boolean onMap(Warship ship) {
        float x = ship.centerX(), y = ship.centerY();
        return x >= 0f && y >= 0f && x <= BlackSluice.WIDTH && y <= BlackSluice.HEIGHT;
    }

    /**
     * The fight on the radio: Dormer's hulls sighted crossing the edge, closing on the Greyhook, the first hull fire, the
     * first of Dormer's hulls going down while the Greyhook and another of his still fly, the Greyhook going down, and
     * the last of Dormer's going down.
     */
    private void radio(BattleSimulation sim) {
        if (!sighted) {
            for (Warship ship : rusk) sighted |= over(ship) && onMap(ship);
            if (sighted) sim.emitStoryBeat(RUSK_SIGHTED_BEAT);
        }
        if (!closing) {
            for (Warship ship : rusk) {
                if (!over(ship) || !onMap(ship)) continue;
                float near = WarshipDuelHelm.reach(ship) * CLOSING_REACH_SHARE;
                closing |= over(rig) && rig.distanceTo(ship.centerX(), ship.centerY()) <= near;
            }
            if (closing) sim.emitStoryBeat(RUSK_CLOSING_BEAT);
        }
        if (!fired) {
            for (Warship hull : hulls()) {
                for (Warship.Mount m : hull.mounts()) fired |= !m.pointDefence() && m.shotsFired > 0;
            }
            if (fired && !closing) {
                // A rack outreaching the guns (the Greyhook's Sabots) can open the fight before either side's guns
                // bear; a hull under fire is being closed on, so the radio hears that first.
                closing = true;
                sim.emitStoryBeat(RUSK_CLOSING_BEAT);
            }
            if (fired) sim.emitStoryBeat(FIRST_HULL_FIRE_BEAT);
        }
        if (!firstDown && rusk.size() > 1 && !allDown(rusk) && !rig.destroyed()) {
            for (Warship ship : rusk) firstDown |= ship.destroyed();
            if (firstDown) sim.emitStoryBeat(RUSK_FIRST_DOWN_BEAT);
        }
        if (!greyhookDown && rig.destroyed()) {
            greyhookDown = true;
            sim.emitStoryBeat(GREYHOOK_DOWN_BEAT);
        }
        if (!ruskDown && !rusk.isEmpty() && allDown(rusk)) {
            ruskDown = true;
            sim.emitStoryBeat(RUSK_DOWN_BEAT);
        }
    }

    /** The tick the Greyhook was destroyed or gone off the map, or -1 while she is still over the battle. */
    public int decidedTick() { return decidedTick; }
    /** Why Voss ordered the run, or {@link BreakOff#NONE} while the Greyhook still fights. */
    public BreakOff breakOff() { return breakOff; }
    /** The tick Voss ordered the run, or -1. */
    public int breakTick() { return breakTick; }
    /** The tick of the first hull fire, as the break-off clock read it, or -1. */
    public int firstFireTick() { return firstFireTick; }

    /** The Greyhook, Voss's Venture, or {@code null} when none could be fitted. */
    public Warship greyhook() { return rig; }
    /** Dormer's hulls once they have come, in {@link #RUSK_PAIR} order. */
    public List<Warship> rusk() { return List.copyOf(rusk); }
    /** What the radio calls each of Dormer's hulls, in {@link #rusk()} order. */
    public List<RuskHull> pair() { return pair; }
    /** Every hull of the duel: the Greyhook, and Dormer's once they have come. */
    public List<Warship> hulls() {
        List<Warship> out = new ArrayList<>();
        if (rig != null) out.add(rig);
        out.addAll(rusk);
        return out;
    }
    public boolean ordered() { return ordered; }

    @Override
    public String outcomeSummary() {
        if (!ordered || ledger == null) return null;
        return Strings.format("blackSluiceDuelSummary", Strings.join("blackSluiceDuelSentenceJoin", rigLine(),
                rusk.isEmpty() ? Strings.get("blackSluiceDuelRuskNeverCame") : ruskLine(), groundLine()));
    }

    private String rigLine() {
        WarshipDuelLedger.Entry e = ledger.entry(rig);
        WarshipDuelLedger.Outcome o = e == null ? WarshipDuelLedger.Outcome.OVER_THE_BATTLE : e.outcome();
        boolean ran = breakOff != BreakOff.NONE;
        return switch (o) {
            case DESTROYED -> ran
                    ? Strings.get("blackSluiceDuelRigDestroyedRan")
                    : Strings.get("blackSluiceDuelRigDestroyed");
            case ESCAPED -> rusk.isEmpty() || allDown(rusk)
                    ? Strings.format("blackSluiceDuelRigEscapedClear", state(rig))
                    : Strings.format("blackSluiceDuelRigEscapedPursued", state(rig));
            // The battle waits for her ending; only a withdrawal of our own leaves her still up there.
            case OVER_THE_BATTLE -> ran ? Strings.format("blackSluiceDuelRigRunning", state(rig))
                    : rusk.isEmpty() ? Strings.get("blackSluiceDuelRigOverhead")
                    : Strings.format("blackSluiceDuelRigFighting", state(rig));
            case BROKE_OFF, DEPARTED -> Strings.format("blackSluiceDuelRigLeft", state(rig));
        };
    }

    /** How a hull looked at the end: untouched, scarred, badly hurt or barely flying. */
    static String state(Warship ship) {
        float f = ship.hullFraction();
        if (f >= 0.95f) return Strings.get("blackSluiceDuelStateUntouched");
        if (f >= 0.7f) return Strings.get("blackSluiceDuelStateScarred");
        if (f >= 0.35f) return Strings.get("blackSluiceDuelStateBadlyHurt");
        return Strings.get("blackSluiceDuelStateBarelyFlying");
    }

    /** One clause per lent hull, by name: "Dormer's Enforcer was still overhead, scarred; his Wolf was shot down." */
    private String ruskLine() {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < rusk.size(); i++) {
            Warship ship = rusk.get(i);
            WarshipDuelLedger.Entry e = ledger.entry(ship);
            WarshipDuelLedger.Outcome o = e == null ? WarshipDuelLedger.Outcome.OVER_THE_BATTLE : e.outcome();
            String owned = Strings.format(i == 0 ? "blackSluiceDuelRuskFirst" : "blackSluiceDuelRuskNext", pair.get(i).name());
            parts.add(switch (o) {
                case DESTROYED -> Strings.format("blackSluiceDuelRuskDown", owned);
                case OVER_THE_BATTLE -> Strings.format("blackSluiceDuelRuskOverhead", owned, state(ship));
                case ESCAPED, BROKE_OFF, DEPARTED -> Strings.format("blackSluiceDuelRuskLeft", owned, state(ship));
            });
        }
        return Strings.format("blackSluiceDuelRuskLine", Strings.join("separatorList", parts.toArray(new String[0])));
    }

    private String groundLine() {
        int ours = 0, allies = 0, theirs = 0;
        for (WarshipDuelLedger.Cause c : WarshipDuelLedger.Cause.values()) {
            ours += ledger.killed(c, Faction.MARINE);
            allies += ledger.killed(c, Faction.ALLY);
            theirs += ledger.killed(c, Faction.DEFENDER);
        }
        if (ours + allies + theirs == 0) return Strings.get("blackSluiceDuelGroundNone");
        List<String> parts = new ArrayList<>();
        if (ours > 0) parts.add(Strings.format("blackSluiceDuelGroundOurs", ours));
        if (allies > 0) parts.add(Strings.format("blackSluiceDuelGroundAllies", allies));
        if (theirs > 0) parts.add(Strings.format("blackSluiceDuelGroundTheirs", theirs));
        return Strings.format("blackSluiceDuelGroundKilled", Strings.join("blackSluiceDuelGroundJoin", parts.toArray(new String[0])));
    }

    /** The ledger's line for one hull of the duel, for evidence. */
    public WarshipDuelLedger.Entry entry(Warship ship) {
        return ledger == null ? null : ledger.entry(ship);
    }
}

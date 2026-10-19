package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How much a warship spends into one opening: {@link MissileWindow} says <em>when</em> a missile may go, this says
 * <em>how many</em>, so a rack is spent across a fight's openings rather than emptied into the first.
 *
 * <p>An <b>opening</b> is one stretch of a target's vulnerability, shared by every launcher of a side: the first launch
 * into an opening window ({@link MissileWindow.Kind#opening()}) opens it, and every launch of that side into that
 * target while it lasts, packmates' salvo launches included, spends into it. It is <b>forced</b> when the target
 * cannot defend itself (an overload, a shield locked out by a full pool or held down by hand) and lasts exactly as long as that does; it is
 * <b>standing</b> when the window is a condition that persists (the flank, a pool running high, a worn hull, a
 * striker's moment, a salvo) and lasts {@link #STANDING_SECONDS} of the hull fight's clock, after which a launch opens
 * the next one. It is <b>decisive</b> when the target is overloaded or worn: the moment a kill is on.
 *
 * <p>Into one opening a ship spends at most its <b>budget</b> of volleys: a pair, or a quarter of the volleys left in
 * its gated racks if that is more ({@link #budget}). A torpedo or a finisher spends into a lesser opening only while
 * the ship keeps its {@link #RESERVE} of such volleys back for a decisive one. What is not spent keeps for the next
 * opening. General-purpose launchers, and launches into a standing condition that is not an opening (a raised shield
 * for a Sabot, a hull with no shield for a torpedo), are paced by the ship's tube stagger alone.
 * {@code -Dbattle.warship.missileRestraint=false} is the control: no budget, and a shield lowered at rest reads as
 * an opening.
 */
public final class MissileOpenings {

    /** {@code -Dbattle.warship.missileRestraint=false} is the control: no budget per opening. */
    public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("battle.warship.missileRestraint"));
    /** The least a ship may spend into one opening, in volleys. */
    public static final int PAIR = 2;
    /** The share of a ship's remaining gated volleys it may spend into one opening, when that is more than a pair. */
    static final float SHARE = 0.25f;
    /** How long a standing opening lasts, in hull-fight seconds. */
    static final float STANDING_SECONDS = 8f;
    /** Torpedo and finisher volleys a ship keeps back for a decisive opening. */
    public static final int RESERVE = 2;

    /** One opening on one target for one side, and what each ship has spent into it. */
    public static final class Opening {
        final int id;
        final Warship target;
        final Faction side;
        final MissileWindow.Kind kind;
        final boolean forced;
        final boolean decisive;
        final float startedAt;
        float age;
        boolean closed;
        final Map<Warship, int[]> spend = new LinkedHashMap<>();

        Opening(int id, Warship target, Faction side, MissileWindow.Kind kind, float startedAt) {
            this.id = id;
            this.target = target;
            this.side = side;
            this.kind = kind;
            this.forced = forced(kind);
            this.decisive = decisive(kind);
            this.startedAt = startedAt;
        }

        /** The window whose launch opened it. */
        public MissileWindow.Kind kind() {
            return kind;
        }
    }

    /**
     * What one ship spent into one opening: the volleys it launched (paid) and its budget there. {@code startedAt} is
     * the opening's start on the hull fight's seconds since the battle began.
     */
    public record Spend(int openingId, Warship ship, Warship target, MissileWindow.Kind kind, int launches, int budget,
                        float startedAt) {}

    private final Map<Warship, Opening[]> current = new IdentityHashMap<>();
    private final List<Opening> all = new ArrayList<>();
    private float clock;

    /** The budget of a ship with {@code remainingVolleys} left in its gated racks; a negative count is bottomless. */
    public static int budget(int remainingVolleys) {
        if (remainingVolleys < 0) return PAIR;
        return Math.max(PAIR, (int) Math.ceil(remainingVolleys * SHARE));
    }

    /** Whether {@code k} is a window the target cannot shut: an overload, or its shield down and unable to rise. */
    public static boolean forced(MissileWindow.Kind k) {
        return k == MissileWindow.Kind.OVERLOAD || k == MissileWindow.Kind.SHIELD_DOWN;
    }

    /** Whether {@code k} is the moment a kill is on: the target overloaded, or its hull worn. */
    public static boolean decisive(MissileWindow.Kind k) {
        return k == MissileWindow.Kind.OVERLOAD || k == MissileWindow.Kind.WORN;
    }

    /** Whether {@code role} spends its reserve: a torpedo or a finisher, the volleys kept back for a decisive opening. */
    public static boolean heavy(MissileWindow.Role role) {
        return role == MissileWindow.Role.TORPEDO || role == MissileWindow.Role.FINISHER;
    }

    /**
     * Whether a launch into {@code window} joins an opening of kind {@code openKind} ({@code forcedOpen} and
     * {@code decisiveOpen} its grade) rather than opening the next. A forced or decisive window arriving during a
     * lesser or standing opening is a new opening; anything else, a salvo included, joins.
     */
    public static boolean joins(boolean forcedOpen, boolean decisiveOpen, MissileWindow.Kind window) {
        if (decisive(window) && !decisiveOpen) return false;
        return !forced(window) || forcedOpen;
    }

    /**
     * Whether an opening is still open: a forced one while the target still cannot defend itself, a standing one for
     * {@link #STANDING_SECONDS} from its start.
     */
    public static boolean lasts(boolean forced, float ageSeconds, boolean targetForcedOpen) {
        return forced ? targetForcedOpen : ageSeconds < STANDING_SECONDS;
    }

    /**
     * Whether a ship with {@code spent} volleys already in this opening, its {@code budget} there and
     * {@code heavyLeft} torpedo or finisher volleys left (negative: bottomless) may launch one more of {@code role}.
     */
    public static boolean mayLaunch(MissileWindow.Role role, boolean decisiveOpening, int spent, int budget,
                                    int heavyLeft) {
        if (spent >= budget) return false;
        return decisiveOpening || !heavy(role) || heavyLeft < 0 || heavyLeft > RESERVE;
    }

    /**
     * Whether {@code target} cannot defend itself against a missile now: overloaded, or its shield down and unable or
     * unwilling to rise for one (locked out by a full pool, or held down by hand). An automatic shield lowered at rest
     * is not: it rises for the first hostile round on its way.
     */
    static boolean forcedOpen(Warship target) {
        return target.overloaded() || target.hasShield() && !target.shieldAbsorbing()
                && (target.shieldLockedOut() || !target.shieldAuto());
    }

    /** Volleys left in {@code ship}'s gated racks, or {@code -1} when one is bottomless; heavy racks only if asked. */
    static int volleysLeft(Warship ship, boolean heavyOnly) {
        int n = 0;
        for (Warship.Mount m : ship.mounts()) {
            if (!m.weapon.missile() || m.knockedOut || m.lost) continue;
            MissileWindow.Role role = MissileWindow.role(m.weapon);
            if (role == MissileWindow.Role.GENERAL || heavyOnly && !heavy(role)) continue;
            if (m.ammoRemaining < 0) return -1;
            n += (m.ammoRemaining + Math.max(1, m.weapon.burstSize()) - 1) / Math.max(1, m.weapon.burstSize());
        }
        return n;
    }

    /**
     * Commits one volley of {@code role} by {@code ship} into the opening {@code window} belongs to on {@code target},
     * opening it if need be; {@code null} when the budget or the reserve refuses it (nothing is then committed).
     */
    Opening commit(Warship ship, Warship target, MissileWindow.Role role, MissileWindow.Kind window) {
        Opening[] bySide = current.computeIfAbsent(target, t -> new Opening[Faction.values().length]);
        int side = ship.faction.ordinal();
        Opening o = bySide[side];
        if (o != null && (o.closed || !joins(o.forced, o.decisive, window))) o = null;
        Opening fresh = null;
        if (o == null) {
            // A salvo with no opening of its own (the opening it was called into has shut) stands as a lesser one.
            fresh = new Opening(all.size() + 1, target, ship.faction, window, clock);
            o = fresh;
        }
        int[] s = o.spend.get(ship);
        int committed = s == null ? 0 : s[0];
        int budget = s == null ? budget(volleysLeft(ship, false)) : s[2];
        if (!mayLaunch(role, o.decisive, committed, budget, volleysLeft(ship, true))) return null;
        if (fresh != null) {
            if (bySide[side] != null) bySide[side].closed = true;
            bySide[side] = fresh;
            all.add(fresh);
        }
        if (s == null) {
            s = new int[] {0, 0, budget};
            o.spend.put(ship, s);
        }
        s[0]++;
        return o;
    }

    /** A committed volley has left its tube. */
    static void paid(Opening o, Warship ship) {
        int[] s = o.spend.get(ship);
        if (s != null) s[1]++;
    }

    /** Ages the openings on the hull fight's clock, shutting those that have ended and forgetting gone targets. */
    void advance(float hullDt) {
        clock += hullDt;
        for (Iterator<Map.Entry<Warship, Opening[]>> it = current.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Warship, Opening[]> e = it.next();
            Warship target = e.getKey();
            boolean gone = target.destroyed() || target.departed();
            boolean forcedNow = !gone && forcedOpen(target);
            Opening[] bySide = e.getValue();
            for (int i = 0; i < bySide.length; i++) {
                Opening o = bySide[i];
                if (o == null) continue;
                o.age += hullDt;
                if (gone || !lasts(o.forced, o.age, forcedNow)) {
                    o.closed = true;
                    bySide[i] = null;
                }
            }
            if (gone) it.remove();
        }
    }

    /** Openings opened over the battle. */
    public int opened() {
        return all.size();
    }

    /** Every ship's spend in every opening it launched into, in the order the openings opened. */
    public List<Spend> spends() {
        List<Spend> out = new ArrayList<>();
        for (Opening o : all) {
            for (Map.Entry<Warship, int[]> e : o.spend.entrySet()) {
                if (e.getValue()[1] == 0) continue;
                out.add(new Spend(o.id, e.getKey(), o.target, o.kind, e.getValue()[1], e.getValue()[2], o.startedAt));
            }
        }
        return Collections.unmodifiableList(out);
    }
}

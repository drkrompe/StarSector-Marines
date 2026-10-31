package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.AirCorridor;
import com.dillon.starsectormarines.battle.air.AirOrdnance;
import com.dillon.starsectormarines.battle.air.AirfieldService;
import com.dillon.starsectormarines.battle.air.CoverDuty;
import com.dillon.starsectormarines.battle.air.GunshipOrbit;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.world.gen.Runway;
import com.dillon.starsectormarines.battle.airsupport.CommsOperators.Operator;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.fire.FireReach;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;

/**
 * Each side's pool of airframes, and the ledger that tasks them.
 *
 * <p><b>A pool, not a call count.</b> A side holds bombers and fighters in stock. It regenerates at the rate the
 * mission declared up to the declared establishment. A comms operator on the ground tasks one airframe onto a point;
 * it is then <em>out</em> for the length of its sortie. It comes back to the pool when it returns or is driven off,
 * and it is lost to the pool when it is shot down: it returns only by regeneration. A strike over a strong defence
 * therefore visibly drains the pool, which is the cost the owner asked anti-air to carry.
 *
 * <p><b>One pool for player and AI.</b> The player's air-strike power and the side's own {@link AirStrikeDirector}
 * both go through {@link #request}; neither pays command points or a charge, both need a living operator with fair
 * knowledge of the ground and an airframe in stock. Symmetric: the ledger is keyed by faction and only the declaration
 * decides who holds one.
 *
 * <p>The aircraft are ordinary corridor sorties ({@code spawnSortie}): they fly in across the side's map edge, make
 * their pass with the same ordnance and flight code every aircraft uses, and leave. Nothing here forks flight or
 * damage. The call surface is serial ({@link #request}, {@link #advance}); the reads only read.
 *
 * <p>A side with an airbase on the map ({@link WingAirbase}) flies a call off it instead: the airframe rolls off its
 * berth down the strip and lands back on it, and only a call with no berth of its kind ready crosses the edge.
 */
public final class AirPoolService {

    /** A service with no pool for anybody: requests are refused and nothing is mutated. */
    public static final AirPoolService NONE = new AirPoolService(AirPoolDeclaration.NONE);

    /** How a request ended. */
    public enum Outcome {
        /** An operator is on the net and an airframe is reserved; the sortie launches when the call goes out. */
        CALLING,
        /** The side has no airframe of that kind in stock (or never had a pool); nothing was reserved. */
        NO_AIRFRAME,
        /** Nobody on the side can direct a strike at that point; nothing was reserved. */
        NO_OPERATOR,
        /** Cover was asked for over ground a fighter already covers, or is on its way to; nothing was reserved. */
        ALREADY_COVERED,
        /** A commander asked for a strike and the director's own estimate of the anti-air over it says no; nothing was reserved. */
        TOO_DEFENDED
    }

    /** What happened to a sortie or a call, for the ledger. */
    public enum EventType { CALLED, LAUNCHED, RETURNED, ABORTED, LOST, CALL_DROPPED, REPLACED }

    /** One ledger line. */
    public record Event(int tick, Faction side, AirPoolKind kind, EventType type, float x, float y) {}

    /** What a side holds of one kind right now. */
    public record Readout(AirPoolKind kind, int available, int outFlying, int cap, int lost, int returned,
                          int aborted, int launched) {}

    private static final class Ledger {
        int available;
        int outFlying;
        int reserved;
        int lost;
        int returned;
        int aborted;
        int launched;
        float regenProgress;
    }

    /** What the pool has done for a side under one mission, for evidence and readouts. */
    public record MissionTally(int called, int launched, int returned, int aborted, int lost) {}

    private static final class Tally {
        int called;
        int launched;
        int returned;
        int aborted;
        int lost;
    }

    /** A call on the net; {@code ordnance} overrides the kind's load, or is null for the kind's own. */
    private record PendingCall(Faction side, AirPoolKind kind, AirMission job, long operator, float x, float y, int readyTick,
                               AirOrdnance ordnance) {}

    /** A sortie in the air; holds its mission so a craft reaped on the tick it lands can still be read. */
    private record Sortie(long craft, ShuttleMission mission, Faction side, AirPoolKind kind, AirMission job, float x, float y) {}

    private final AirPoolDeclaration declaration;
    /** Whether cover fighters launched from here fly the old head-on lead pursuit instead of the tail chase: a scene's control, never a mission's choice. */
    private boolean headOnCover;
    private final Map<Faction, Map<AirPoolKind, Ledger>> ledgers = new EnumMap<>(Faction.class);
    private final List<PendingCall> pending = new ArrayList<>();
    private final List<Sortie> sorties = new ArrayList<>();
    private final List<Event> events = new ArrayList<>();
    private final Map<Faction, AirStrikeDirector> directors = new EnumMap<>(Faction.class);
    private final Map<Faction, Map<AirMission, Tally>> tallies = new EnumMap<>(Faction.class);

    public AirPoolService(AirPoolDeclaration declaration) {
        this.declaration = declaration != null ? declaration : AirPoolDeclaration.NONE;
        for (Map.Entry<Faction, AirPoolAllowance> side : this.declaration.allowances().entrySet()) {
            Map<AirPoolKind, Ledger> perKind = new EnumMap<>(AirPoolKind.class);
            for (Map.Entry<AirPoolKind, AirPoolStock> stock : side.getValue().stocks().entrySet()) {
                Ledger ledger = new Ledger();
                ledger.available = stock.getValue().initial();
                perKind.put(stock.getKey(), ledger);
            }
            ledgers.put(side.getKey(), perKind);
        }
    }

    /**
     * Makes cover fighters launched from here after this call fly the old head-on lead pursuit rather than the tail chase
     * ({@code AirIntercept}). Per-instance state for a scene's control loop; the production service never sets it.
     */
    public void useHeadOnCoverPursuit(boolean headOn) {
        this.headOnCover = headOn;
    }

    // ---- reads ----

    /** Every pool the mission declared. */
    public AirPoolDeclaration declaration() {
        return declaration;
    }

    /** What the mission declared for {@code side}. */
    public AirPoolAllowance allowance(Faction side) {
        return declaration.forSide(side);
    }

    /** Whether {@code side} holds a pool at all. */
    public boolean hasPool(Faction side) {
        return !allowance(side).isNone();
    }

    /** Airframes of {@code kind} {@code side} could task right now. */
    public int available(Faction side, AirPoolKind kind) {
        Ledger l = ledger(side, kind);
        return l == null ? 0 : l.available;
    }

    /** Airframes of {@code kind} currently out on a sortie or reserved for a call in progress. */
    public int outFlying(Faction side, AirPoolKind kind) {
        Ledger l = ledger(side, kind);
        return l == null ? 0 : l.outFlying + l.reserved;
    }

    /** Airframes of {@code kind} {@code side} has lost to anti-air. */
    public int lost(Faction side, AirPoolKind kind) {
        Ledger l = ledger(side, kind);
        return l == null ? 0 : l.lost;
    }

    /** The side's whole stock, one row per declared kind. */
    public List<Readout> readout(Faction side) {
        List<Readout> out = new ArrayList<>();
        for (Map.Entry<AirPoolKind, AirPoolStock> stock : allowance(side).stocks().entrySet()) {
            Ledger l = ledger(side, stock.getKey());
            out.add(new Readout(stock.getKey(), l.available, l.outFlying + l.reserved, stock.getValue().cap(),
                    l.lost, l.returned, l.aborted, l.launched));
        }
        return out;
    }

    /** What the pool has done for {@code side} under {@code job}. */
    public MissionTally tally(Faction side, AirMission job) {
        Tally t = tallies.getOrDefault(side, Map.of()).get(job);
        return t == null ? new MissionTally(0, 0, 0, 0, 0) : new MissionTally(t.called, t.launched, t.returned, t.aborted, t.lost);
    }

    private Tally tallyOf(Faction side, AirMission job) {
        return tallies.computeIfAbsent(side, s -> new EnumMap<>(AirMission.class)).computeIfAbsent(job, j -> new Tally());
    }

    /** Cover fighters of {@code side} in the air, or called and not yet launched. */
    public int coversOut(Faction side) {
        int n = 0;
        for (Sortie s : sorties) {
            if (s.side == side && s.job == AirMission.COVER) n++;
        }
        for (PendingCall c : pending) {
            if (c.side == side && c.job == AirMission.COVER) n++;
        }
        return n;
    }

    /** Whether a cover fighter of {@code side} is, or is about to be, on station within {@code radius} cells of the point. */
    public boolean covers(Faction side, float x, float y, float radius) {
        for (Sortie s : sorties) {
            if (s.side == side && s.job == AirMission.COVER && Math.hypot(s.x - x, s.y - y) <= radius) return true;
        }
        for (PendingCall c : pending) {
            if (c.side == side && c.job == AirMission.COVER && Math.hypot(c.x - x, c.y - y) <= radius) return true;
        }
        return false;
    }

    /**
     * An immutable display snapshot of {@code side}'s pool at sim tick {@code nowTick}; {@link AirPoolView#NONE}
     * when the side holds none. Reads only: it reserves, launches and regenerates nothing.
     */
    public AirPoolView view(Faction side, int nowTick) {
        if (!hasPool(side)) return AirPoolView.NONE;
        List<AirPoolView.KindRow> rows = new ArrayList<>();
        int calls = 0;
        for (Map.Entry<AirPoolKind, AirPoolStock> stock : allowance(side).stocks().entrySet()) {
            Ledger l = ledger(side, stock.getKey());
            AirPoolStock declared = stock.getValue();
            int held = l.available + l.outFlying + l.reserved;
            float next = declared.regenSeconds() > 0f && held < declared.cap()
                    ? Math.max(0f, declared.regenSeconds() - l.regenProgress) : -1f;
            rows.add(new AirPoolView.KindRow(stock.getKey(), l.available, l.outFlying + l.reserved,
                    declared.cap(), l.lost, next));
            calls += l.reserved;
        }
        List<AirPoolView.SortieRow> flying = new ArrayList<>();
        for (Sortie s : sorties) {
            if (s.side == side) flying.add(new AirPoolView.SortieRow(s.kind, missionOf(s)));
        }
        int windowTicks = Math.round(AirPoolView.RECENT_LOSS_SECONDS / BattleSimulation.TICK_DT);
        int recent = 0;
        for (Event e : events) {
            if (e.side() == side && e.type() == EventType.LOST && nowTick - e.tick() <= windowTicks) recent++;
        }
        return new AirPoolView(rows, flying, calls, recent);
    }

    /** What a sortie was sent to do, as the display names it. */
    private static AirPoolView.Mission missionOf(Sortie sortie) {
        return switch (sortie.job) {
            case BOMB -> AirPoolView.Mission.STRIKE;
            case STRAFE -> AirPoolView.Mission.STRAFE;
            case COVER -> AirPoolView.Mission.COVER;
        };
    }

    /** Every ledger line, oldest first. */
    public List<Event> events() {
        return Collections.unmodifiableList(events);
    }

    /** Sorties currently in the air for {@code side}. */
    public int sortiesInFlight(Faction side) {
        int n = 0;
        for (Sortie s : sorties) {
            if (s.side == side) n++;
        }
        return n;
    }

    /**
     * Whether a request at this point would be taken: some kind is in stock and an operator can direct it.
     * Pure: asking reserves nothing.
     */
    public boolean canRequest(BattleSimulation sim, Faction side, float x, float y, boolean believes) {
        if (!hasPool(side) || sim == null) return false;
        boolean stocked = false;
        for (AirPoolKind kind : AirPoolKind.values()) stocked |= available(side, kind) > 0;
        if (!stocked) return false;
        Operator operator = CommsOperators.best(sim, side, x, y, believes);
        return operator != null && !busy(operator.unit());
    }

    /** The kind a request at no stated kind would use: a bomber when one is in stock, else a fighter. */
    public AirPoolKind defaultKind(Faction side) {
        if (available(side, AirPoolKind.BOMBER) > 0) return AirPoolKind.BOMBER;
        if (available(side, AirPoolKind.FIGHTER) > 0) return AirPoolKind.FIGHTER;
        return null;
    }

    private final List<ForwardObservers> observers = new ArrayList<>();
    private final Map<Faction, float[]> homes = new EnumMap<>(Faction.class);

    /**
     * Names {@code side}'s home ground, the edge its sorties come in over, for a side with neither a landing zone nor a
     * keep: an allied wing's airfield lies behind its own line, never over the enemy.
     */
    public AirPoolService homeAt(Faction side, float x, float y) {
        homes.put(side, new float[]{x, y});
        return this;
    }

    /**
     * Posts {@code posted}'s observers with the pool: their radios are issued as their formations come on the map, before
     * the side's officer looks for ground to strike each tick.
     */
    public AirPoolService observedFrom(ForwardObservers posted) {
        observers.add(posted);
        return this;
    }

    private final List<RovingObservers> roving = new ArrayList<>();
    private final Map<Faction, WingAirbase> bases = new EnumMap<>(Faction.class);
    private final List<StandingPatrol> patrols = new ArrayList<>();
    private final Map<Faction, Integer> basedLaunches = new EnumMap<>(Faction.class);

    /** Bases {@code base}'s side's sorties on it: a call whose kind has a berth ready there rolls off its strip. */
    public AirPoolService basedAt(WingAirbase base) {
        bases.put(base.side(), base);
        return this;
    }

    /** The airbase {@code side}'s pool flies from, or null when it flies in over the edge. */
    public WingAirbase base(Faction side) {
        return bases.get(side);
    }

    /** Sorties of {@code side}'s that rolled off its airbase rather than crossing the edge, for evidence. */
    public int basedLaunches(Faction side) {
        return basedLaunches.getOrDefault(side, 0);
    }

    /** Gives a side its standing patrol, looked at each tick after the observers. */
    public AirPoolService patrolledBy(StandingPatrol patrol) {
        patrols.add(patrol);
        return this;
    }

    /** The standing patrols the mission gave {@code side}, for evidence. */
    public List<StandingPatrol> patrols(Faction side) {
        List<StandingPatrol> out = new ArrayList<>();
        for (StandingPatrol p : patrols) if (p.side() == side) out.add(p);
        return out;
    }

    /** Sends {@code teams}' roving observers up as the battle goes, before the side's officer looks for ground each tick. */
    public AirPoolService rovingFrom(RovingObservers teams) {
        roving.add(teams);
        return this;
    }

    /** The roving observer teams the mission gave {@code side}, for readouts and evidence. */
    public List<RovingObservers> roving(Faction side) {
        List<RovingObservers> out = new ArrayList<>();
        for (RovingObservers teams : roving) if (teams.side() == side) out.add(teams);
        return out;
    }

    /** The observer posts the mission gave {@code side}, for readouts and evidence. */
    public List<ForwardObservers> observers(Faction side) {
        List<ForwardObservers> out = new ArrayList<>();
        for (ForwardObservers posted : observers) if (posted.side() == side) out.add(posted);
        return out;
    }

    /** This side's fire-support officer, created on first ask; its sightings are the side's anti-air knowledge. */
    public AirStrikeDirector director(Faction side) {
        return directors.computeIfAbsent(side, AirStrikeDirector::new);
    }

    // ---- the call ----

    /**
     * Has an operator of {@code side} direct one airframe of {@code kind} onto {@code (x, y)}. The airframe is
     * reserved now and launches when the operator's call has gone out, provided they are still alive and unsuppressed.
     *
     * @param believes whether the caller vouches for a belief in the point when no operator has sight of it
     */
    public Outcome request(BattleSimulation sim, Faction side, AirPoolKind kind, float x, float y, boolean believes) {
        return request(sim, side, kind, x, y, believes, unit -> true);
    }

    /** As above, through an operator {@code operators} accepts. */
    public Outcome request(BattleSimulation sim, Faction side, AirPoolKind kind, float x, float y, boolean believes,
                           LongPredicate operators) {
        Ledger ledger = ledger(side, kind);
        if (ledger == null || ledger.available <= 0) return Outcome.NO_AIRFRAME;
        Operator operator = CommsOperators.best(sim, side, x, y, believes, operators);
        if (operator == null || busy(operator.unit())) return Outcome.NO_OPERATOR;
        int ready = sim.getSimTickIndex() + Math.round(operator.spec().callSeconds() / BattleSimulation.TICK_DT);
        return call(sim, side, kind, AirMission.strikeOf(kind), operator, x, y, ready, null);
    }

    /**
     * As {@link #request(BattleSimulation, Faction, AirPoolKind, float, float, boolean, LongPredicate)} for a bomber
     * carrying firebombs ({@link AirOrdnance#INCENDIARY_BOMBS}): from the same stock as any bomber, and loaded with the
     * ordinary stick instead when a fire lit at the point could come to the side's own ({@link FireReach}). A stick
     * runs along the bomber's pass, so the ground it may light is taken to be the director's danger-close ring round
     * the point ({@link AirStrikeDirector#DANGER_CLOSE_CELLS}).
     */
    public Outcome requestIncendiary(BattleSimulation sim, Faction side, float x, float y, boolean believes,
                                     LongPredicate operators) {
        Ledger ledger = ledger(side, AirPoolKind.BOMBER);
        if (ledger == null || ledger.available <= 0) return Outcome.NO_AIRFRAME;
        Operator operator = CommsOperators.best(sim, side, x, y, believes, operators);
        if (operator == null || busy(operator.unit())) return Outcome.NO_OPERATOR;
        int ready = sim.getSimTickIndex() + Math.round(operator.spec().callSeconds() / BattleSimulation.TICK_DT);
        boolean safe = !FireReach.enabled()
                || !FireReach.reachesSide(sim.fireField(), (int) Math.floor(x), (int) Math.floor(y),
                        (int) AirStrikeDirector.DANGER_CLOSE_CELLS, side, sim);
        return call(sim, side, AirPoolKind.BOMBER, AirMission.strikeOf(AirPoolKind.BOMBER), operator, x, y, ready,
                safe ? AirOrdnance.INCENDIARY_BOMBS : null);
    }

    /**
     * As {@link #request(BattleSimulation, Faction, AirPoolKind, float, float, boolean, LongPredicate)}, but the kind is
     * the one the ground at the point calls for ({@link AirTasking}): a fighter for soft ground, a bomber for works and
     * armour, the other when the preferred one is out of stock.
     */
    public Outcome requestAt(BattleSimulation sim, Faction side, float x, float y, boolean believes, LongPredicate operators) {
        AirPoolKind kind = AirTasking.kindAt(sim, this, side, x, y);
        if (kind == null) return Outcome.NO_AIRFRAME;
        return request(sim, side, kind, x, y, believes, operators);
    }

    /**
     * Has an operator of {@code side} send one fighter to cover the friendly ground at {@code (x, y)}: it flies in, holds a
     * ring over the ground for a bounded time engaging hostile aircraft and, secondarily, ground threats the side has
     * sighted near it, and goes home. The fighter is reserved at the call like any other and is the same airframe from
     * the same stock a strafe uses.
     *
     * <p>The ground is the caller's own, so it is believed: an operator within radio range may call it without sight.
     */
    public Outcome requestCover(BattleSimulation sim, Faction side, float x, float y, LongPredicate operators) {
        Ledger ledger = ledger(side, AirPoolKind.FIGHTER);
        if (ledger == null || ledger.available <= 0) return Outcome.NO_AIRFRAME;
        Operator operator = CommsOperators.best(sim, side, x, y, true, operators);
        if (operator == null || busy(operator.unit())) return Outcome.NO_OPERATOR;
        int ready = sim.getSimTickIndex() + Math.round(operator.spec().callSeconds() / BattleSimulation.TICK_DT);
        return call(sim, side, AirPoolKind.FIGHTER, AirMission.COVER, operator, x, y, ready, null);
    }

    /** Seconds from a standing patrol's tasking to its launch: the wing's own operations room, no radio call. */
    static final float PATROL_READY_SECONDS = 2f;

    /**
     * The wing's own tasking of one fighter onto a standing patrol over its own ground at {@code (x, y)}
     * ({@link StandingPatrol}): a cover sortie, reserved from the same stock, with no operator on the ground, so it is
     * never dropped for one. Refused with nothing reserved when no fighter is in stock.
     */
    public Outcome patrol(BattleSimulation sim, Faction side, float x, float y) {
        Ledger ledger = ledger(side, AirPoolKind.FIGHTER);
        if (ledger == null || ledger.available <= 0) return Outcome.NO_AIRFRAME;
        ledger.available--;
        ledger.reserved++;
        int ready = sim.getSimTickIndex() + Math.round(PATROL_READY_SECONDS / BattleSimulation.TICK_DT);
        pending.add(new PendingCall(side, AirPoolKind.FIGHTER, AirMission.COVER, 0L, x, y, ready, null));
        events.add(new Event(sim.getSimTickIndex(), side, AirPoolKind.FIGHTER, EventType.CALLED, x, y));
        tallyOf(side, AirMission.COVER).called++;
        return Outcome.CALLING;
    }

    private Outcome call(BattleSimulation sim, Faction side, AirPoolKind kind, AirMission job, Operator operator,
                         float x, float y, int ready, AirOrdnance ordnance) {
        Ledger ledger = ledger(side, kind);
        ledger.available--;
        ledger.reserved++;
        pending.add(new PendingCall(side, kind, job, operator.unit(), x, y, ready, ordnance));
        if (operator.atFort()) {
            fortCalls.merge(side, 1, Integer::sum);
            operator.fort().holdSensorNetUntil(sim.getSimTickIndex() + Math.round(operator.spec().cooldownSeconds() / BattleSimulation.TICK_DT));
        } else {
            sim.world().setSecondaryCooldownTimer(operator.unit(), operator.spec().cooldownSeconds());
        }
        events.add(new Event(sim.getSimTickIndex(), side, kind, EventType.CALLED, x, y));
        tallyOf(side, job).called++;
        return Outcome.CALLING;
    }

    private final Map<Faction, Integer> fortCalls = new EnumMap<>(Faction.class);

    /** Calls {@code side} has placed through a fort's radar rather than a marine's radio, for evidence. */
    public int callsFromForts(Faction side) {
        return fortCalls.getOrDefault(side, 0);
    }

    private boolean busy(long operator) {
        for (PendingCall call : pending) {
            if (call.operator == operator) return true;
        }
        return false;
    }

    // ---- the tick ----

    /** Regenerates the pools, completes or drops calls, resolves sorties and lets AI-directed sides place strikes. */
    public void advance(BattleSimulation sim) {
        if (declaration.isNone()) return;
        regenerate(sim);
        completeCalls(sim);
        resolveSorties(sim);
        writeOffBurned(sim);
        for (Sortie sortie : sorties) {
            if (sortie.job == AirMission.COVER) CoverTasking.task(sim, this, sortie.craft, sortie.mission, sortie.side);
        }
        for (ForwardObservers posted : observers) posted.tick(sim);
        for (RovingObservers teams : roving) teams.tick(sim);
        for (StandingPatrol patrol : patrols) patrol.tick(sim, this);
        for (Map.Entry<Faction, AirPoolAllowance> side : declaration.allowances().entrySet()) {
            if (!side.getValue().aiDirected()) continue;
            director(side.getKey()).tick(sim, this);
        }
    }

    private void regenerate(BattleSimulation sim) {
        float dt = BattleSimulation.TICK_DT;
        for (Map.Entry<Faction, Map<AirPoolKind, Ledger>> side : ledgers.entrySet()) {
            AirPoolAllowance allowance = allowance(side.getKey());
            for (Map.Entry<AirPoolKind, Ledger> e : side.getValue().entrySet()) {
                AirPoolStock stock = allowance.stock(e.getKey());
                Ledger l = e.getValue();
                int held = l.available + l.outFlying + l.reserved;
                if (stock.regenSeconds() <= 0f || held >= stock.cap()) {
                    l.regenProgress = 0f;
                    continue;
                }
                l.regenProgress += dt;
                if (l.regenProgress >= stock.regenSeconds()) {
                    l.regenProgress -= stock.regenSeconds();
                    l.available++;
                    events.add(new Event(sim.getSimTickIndex(), side.getKey(), e.getKey(), EventType.REPLACED, 0f, 0f));
                    // A replacement ferried onto a stand the base lost in the air, when it has one standing empty.
                    WingAirbase base = bases.get(side.getKey());
                    if (base != null) base.restock(e.getKey());
                }
            }
        }
    }

    private void completeCalls(BattleSimulation sim) {
        for (int i = pending.size() - 1; i >= 0; i--) {
            PendingCall call = pending.get(i);
            // A standing patrol has no operator to lose: it is the wing's own tasking.
            boolean dropped = call.operator != 0L && !CommsOperators.stillAble(sim, call.side, call.operator);
            if (!dropped && sim.getSimTickIndex() < call.readyTick) continue;
            pending.remove(i);
            Ledger ledger = ledger(call.side, call.kind);
            ledger.reserved--;
            if (dropped || !launch(sim, call, ledger)) {
                // The call never went out: the airframe never left the pool.
                ledger.available++;
                events.add(new Event(sim.getSimTickIndex(), call.side, call.kind, EventType.CALL_DROPPED,
                        call.x, call.y));
            }
        }
    }

    private boolean launch(BattleSimulation sim, PendingCall call, Ledger ledger) {
        World world = sim.world();
        FighterProfile airframe = call.job == AirMission.COVER ? call.kind.coverProfile() : call.kind.profile();
        WingAirbase base = bases.get(call.side);
        AirfieldService.Berth berth = base == null ? null : base.berthFor(call.kind, airframe, call.x, call.y);
        if (berth != null && base.overrun(sim)) berth = null;
        long craft;
        if (berth != null) {
            airframe = (FighterProfile) berth.airframe;
            craft = rollOff(sim, base, berth, airframe, call.side, call.x, call.y);
            basedLaunches.merge(call.side, 1, Integer::sum);
        } else {
            float[] home = homes.get(call.side);
            if (home == null) home = homeGround(sim, call.side);
            if (home == null) home = friendlyCentroid(sim, call.side);
            if (home == null && call.operator != 0L) home = new float[]{world.x(call.operator), world.y(call.operator)};
            if (home == null) return false;
            AirCorridor corridor = AirCorridor.acrossNearestEdge(call.kind.name(), sim.getGrid().getWidth(),
                    sim.getGrid().getHeight(), home[0], home[1], call.x, call.y);
            if (corridor == null) return false;
            craft = sim.spawnSortie(airframe, call.side, call.x, call.y,
                    corridor.entryX, corridor.entryY, corridor.exitX, corridor.exitY, 0f);
        }
        ShuttleMission mission = world.mission(craft);
        mission.strikeSortie = true;
        mission.poolSortie = true;
        mission.passesLeft = call.kind.passes();
        mission.ordnance = call.job == AirMission.COVER ? airframe.ordnance()
                : call.ordnance != null ? call.ordnance : call.kind.ordnance();
        mission.aaExposure = call.kind.aaExposure();
        if (call.job == AirMission.COVER) beginCover(world, craft, mission, call, airframe, headOnCover);
        ledger.outFlying++;
        ledger.launched++;
        tallyOf(call.side, call.job).launched++;
        sorties.add(new Sortie(craft, mission, call.side, call.kind, call.job, call.x, call.y));
        events.add(new Event(sim.getSimTickIndex(), call.side, call.kind, EventType.LAUNCHED, call.x, call.y));
        return true;
    }

    /**
     * Puts {@code berth}'s aircraft in the air for a call at {@code (x, y)}: the hull that was on the stand, damage and
     * all, taxiing out to the strip and owed back to that stand, as a garrison's shed sortie is ({@code AirStrikeSystem}).
     */
    private static long rollOff(BattleSimulation sim, WingAirbase base, AirfieldService.Berth berth,
                                FighterProfile airframe, Faction side, float x, float y) {
        AirfieldService field = base.field();
        Runway strip = field.runway();
        float standX = berth.centerX + 0.5f;
        float standY = berth.centerY + 0.5f;
        long craft = sim.spawnSortie(airframe, side, x, y, standX, standY, standX, standY, 0f);
        ShuttleMission mission = sim.world().mission(craft);
        sim.world().setHp(craft, field.launch(berth));
        mission.homeBerth = berth;
        sim.world().kinematics(craft).teleport(standX, standY, berth.facingDegrees);
        mission.departFromRunway(strip, standX, standY, x, y);
        return craft;
    }

    /**
     * Writes off the pool what burned on the side's base: an aircraft standing there was one of the side's available
     * airframes, so it is lost as surely as one shot down, and comes back only by replacement.
     */
    private void writeOffBurned(BattleSimulation sim) {
        for (WingAirbase base : bases.values()) {
            for (AirPoolKind kind : base.newlyBurned()) {
                Ledger ledger = ledger(base.side(), kind);
                if (ledger == null || ledger.available <= 0) continue;
                ledger.available--;
                ledger.lost++;
                events.add(new Event(sim.getSimTickIndex(), base.side(), kind, EventType.LOST,
                        base.bounds()[0], base.bounds()[1]));
            }
        }
    }

    /** Turns a fresh fighter sortie into a patrol: the ring it holds about the ground, sized to what the airframe can fly. */
    private static void beginCover(World world, long craft, ShuttleMission mission, PendingCall call, FighterProfile airframe, boolean headOn) {
        AirBody body = world.kinematics(craft);
        mission.cover = new CoverDuty(call.x, call.y);
        mission.cover.headOnPursuit = headOn;
        mission.orbitCentreX = call.x;
        mission.orbitCentreY = call.y;
        mission.orbitRadius = CoverDuty.ringRadius(airframe.flight().minTurnRadiusCells());
        mission.orbitClockwise = GunshipOrbit.clockwiseFrom(body.x, body.y, body.facingDegrees, call.x, call.y,
                mission.orbitRadius);
    }

    private void resolveSorties(BattleSimulation sim) {
        if (sorties.isEmpty()) return;
        long[] airborne = sim.getAirEntityIds();
        for (int i = sorties.size() - 1; i >= 0; i--) {
            Sortie sortie = sorties.get(i);
            ShuttleMission mission = sortie.mission;
            Ledger ledger = ledger(sortie.side, sortie.kind);
            if (mission.destroyed) {
                // Shot down: gone from the pool until regeneration replaces it.
                ledger.outFlying--;
                ledger.lost++;
                tallyOf(sortie.side, sortie.job).lost++;
                sorties.remove(i);
                events.add(new Event(sim.getSimTickIndex(), sortie.side, sortie.kind, EventType.LOST,
                        sortie.x, sortie.y));
                director(sortie.side).noteLoss(sortie.x, sortie.y, sim.getSimTickIndex());
                continue;
            }
            boolean listed = false;
            for (long id : airborne) {
                if (id == sortie.craft) {
                    listed = true;
                    break;
                }
            }
            if (listed && mission.state != ShuttleState.GONE) continue;
            ledger.outFlying--;
            ledger.available++;
            ledger.returned++;
            sorties.remove(i);
            tallyOf(sortie.side, sortie.job).returned++;
            if (mission.aborted) {
                ledger.aborted++;
                tallyOf(sortie.side, sortie.job).aborted++;
                events.add(new Event(sim.getSimTickIndex(), sortie.side, sortie.kind, EventType.ABORTED,
                        sortie.x, sortie.y));
            }
            events.add(new Event(sim.getSimTickIndex(), sortie.side, sortie.kind, EventType.RETURNED,
                    sortie.x, sortie.y));
        }
    }

    private Ledger ledger(Faction side, AirPoolKind kind) {
        Map<AirPoolKind, Ledger> perKind = ledgers.get(side);
        return perKind == null ? null : perKind.get(kind);
    }

    /**
     * Where the side's aircraft come from: its landing zone for the side that landed, its keep for the side that
     * holds one, or {@code null} for a side with neither. A sortie crosses the map edge nearest this place, so it comes
     * in over its own side's ground and its bomb line runs on toward the enemy rather than back over its own troops.
     *
     * <p>It used to be the centre of the side's live force, which moves with the advance: on the trial ground, with
     * the marines gathered short of the keep near the north edge, a marine bomber came in from the enemy's rear,
     * released over the keep and carried its line of bombs south through the marines waiting for it, killing
     * seventeen of them in one pass.
     */
    private static float[] homeGround(BattleSimulation sim, Faction side) {
        TacticalMap map = sim.getTacticalMap();
        if (map == null) return null;
        TacticalNode keep = null;
        for (TacticalNode node : map.forFaction(side)) {
            if (node.kind == TacticalNode.Kind.BEACHHEAD) return new float[]{node.anchorX + 0.5f, node.anchorY + 0.5f};
            if (node.kind == TacticalNode.Kind.COMMAND_POST && keep == null) keep = node;
        }
        return keep == null ? null : new float[]{keep.anchorX + 0.5f, keep.anchorY + 0.5f};
    }

    /** The centre of mass of the side's live ground force, or {@code null} when it has nobody on the map. */
    private static float[] friendlyCentroid(BattleSimulation sim, Faction side) {
        UnitRosterService roster = sim.getRoster();
        long[] ours = roster.factionDenseArray(side);
        int count = roster.factionLiveCount(side);
        if (count <= 0) return null;
        World world = sim.world();
        float sumX = 0f;
        float sumY = 0f;
        for (int i = 0; i < count; i++) {
            sumX += world.x(ours[i]);
            sumY += world.y(ours[i]);
        }
        return new float[]{sumX / count, sumY / count};
    }
}

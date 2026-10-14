package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.air.AirCorridor;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.airsupport.CommsOperators.Operator;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.marine.CommsKitSpec;

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

    private record PendingCall(Faction side, AirPoolKind kind, long operator, float x, float y, int readyTick) {}

    /** A sortie in the air; holds its mission so a craft reaped on the tick it lands can still be read. */
    private record Sortie(long craft, ShuttleMission mission, Faction side, AirPoolKind kind, float x, float y) {}

    private final AirPoolDeclaration declaration;
    private final Map<Faction, Map<AirPoolKind, Ledger>> ledgers = new EnumMap<>(Faction.class);
    private final List<PendingCall> pending = new ArrayList<>();
    private final List<Sortie> sorties = new ArrayList<>();
    private final List<Event> events = new ArrayList<>();
    private final Map<Faction, AirStrikeDirector> directors = new EnumMap<>(Faction.class);

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

    // ---- reads ----

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
        ledger.available--;
        ledger.reserved++;
        int ready = sim.getSimTickIndex() + Math.round(operator.spec().callSeconds() / BattleSimulation.TICK_DT);
        pending.add(new PendingCall(side, kind, operator.unit(), x, y, ready));
        sim.world().setSecondaryCooldownTimer(operator.unit(), operator.spec().cooldownSeconds());
        events.add(new Event(sim.getSimTickIndex(), side, kind, EventType.CALLED, x, y));
        return Outcome.CALLING;
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
                }
            }
        }
    }

    private void completeCalls(BattleSimulation sim) {
        for (int i = pending.size() - 1; i >= 0; i--) {
            PendingCall call = pending.get(i);
            CommsKitSpec spec = sim.world().isAlive(call.operator) ? CommsKit.specOf(sim.world(), call.operator) : null;
            boolean dropped = spec == null || CommsOperators.suppressed(sim, call.operator, spec);
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
        float[] home = homeGround(sim, call.side);
        if (home == null) home = friendlyCentroid(sim, call.side);
        if (home == null) home = new float[]{world.x(call.operator), world.y(call.operator)};
        AirCorridor corridor = AirCorridor.acrossNearestEdge(call.kind.name(), sim.getGrid().getWidth(),
                sim.getGrid().getHeight(), home[0], home[1], call.x, call.y);
        if (corridor == null) return false;
        long craft = sim.spawnSortie(call.kind.profile(), call.side, call.x, call.y,
                corridor.entryX, corridor.entryY, corridor.exitX, corridor.exitY, 0f);
        ShuttleMission mission = world.mission(craft);
        mission.strikeSortie = true;
        mission.poolSortie = true;
        mission.passesLeft = call.kind.passes();
        mission.ordnance = call.kind.ordnance();
        ledger.outFlying++;
        ledger.launched++;
        sorties.add(new Sortie(craft, mission, call.side, call.kind, call.x, call.y));
        events.add(new Event(sim.getSimTickIndex(), call.side, call.kind, EventType.LAUNCHED, call.x, call.y));
        return true;
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
                sorties.remove(i);
                events.add(new Event(sim.getSimTickIndex(), sortie.side, sortie.kind, EventType.LOST,
                        sortie.x, sortie.y));
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
            if (mission.aborted) {
                ledger.aborted++;
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

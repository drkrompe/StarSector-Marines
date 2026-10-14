package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.air.EnemyConcentration;
import com.dillon.starsectormarines.battle.airsupport.AirPoolService.Outcome;
import com.dillon.starsectormarines.battle.airsupport.CommsOperators.Operator;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An AI side's fire-support officer: decides what ground is worth an aircraft and asks the side's comms operators to
 * direct one, through the same {@link AirPoolService#request} the player's power uses.
 *
 * <p><b>Fair knowledge.</b> The director never reads an enemy it could not have seen. It keeps a memory of hostile
 * emplacements any of its side's units has had a clear line to, and it designates only those, or a hostile
 * concentration an operator has in sight right now. Its estimate of the anti-air over a point is built from that same
 * memory ({@link AaRisk}), so a gun nobody has sighted is a gun the strike is surprised by.
 *
 * <p><b>Less useful against strong defences.</b> A target whose believed anti-air would cost more than
 * {@link #RISK_CEILING} of an airframe is skipped; a nest or a dug-in gun is struck while it stands alone or in a
 * thin defence, and a fort's massed guns are left to the ground force. A commander that wants a particular nest killed
 * calls {@link #strike} for it; the same risk judgement applies unless the caller overrides it.
 *
 * <p>UNTUNED: cadence, ceilings and values are first-pass figures.
 */
public final class AirStrikeDirector {

    /** Ticks between looks at the field. */
    static final int PERIOD_TICKS = 30;
    /** Range within which a unit of the side sights a hostile emplacement, in cells. UNTUNED. */
    static final float SIGHT_CELLS = 30f;
    /** Largest expected airframe share the director will risk on a strike. UNTUNED. */
    static final float RISK_CEILING = 1.0f;
    /** Cells within which a point counts as one already struck. */
    static final float REPEAT_CELLS = 9f;
    /** Ticks before a struck point is a candidate again. UNTUNED. */
    static final int REPEAT_TICKS = 1200;
    /** Most units sampled per look when updating sightings. */
    private static final int SIGHT_SAMPLE = 48;

    private record Struck(float x, float y, int tick) {}

    private record Candidate(float x, float y, boolean believed, float score, AirPoolKind kind, long gun) {}

    private final Faction side;
    private final Set<Long> seenGuns = new HashSet<>();
    private final List<Struck> struck = new ArrayList<>();
    private boolean debugDone;

    public AirStrikeDirector(Faction side) {
        this.side = side;
    }

    /** Hostile emplacements this side has sighted and believes it knows of (alive or not; callers check). */
    public Set<Long> knownGuns() {
        return seenGuns;
    }

    /** Whether the side has sighted {@code gun}. */
    public boolean knows(long gun) {
        return seenGuns.contains(gun);
    }

    /** One look at the field: update sightings and, if worthwhile ground is in reach of an operator, call a strike. */
    void tick(BattleSimulation sim, AirPoolService service) {
        int tick = sim.getSimTickIndex();
        if (tick % PERIOD_TICKS != side.ordinal() % PERIOD_TICKS) return;
        updateSightings(sim);
        if (service.defaultKind(side) == null) return;
        if (CommsOperators.available(sim, side).isEmpty()) return;
        AirPoolAllowance allowance = service.allowance(side);
        boolean debugDue = allowance.debugStrikeTick() >= 0 && tick >= allowance.debugStrikeTick() && !debugDone;
        List<Candidate> candidates = candidates(sim, service, debugDue);
        candidates.sort(Comparator.comparingDouble((Candidate c) -> c.score).reversed());
        for (Candidate c : candidates) {
            if (service.request(sim, side, c.kind, c.x, c.y, c.believed) == Outcome.CALLING) {
                struck.add(new Struck(c.x, c.y, tick));
                if (debugDue) debugDone = true;
                return;
            }
        }
    }

    /**
     * A commander's own request: direct the best airframe in stock onto {@code (x, y)}, which the commander says it
     * believes in. Refused (and nothing reserved) when no operator can, no airframe is in stock, or, unless
     * {@code ignoreRisk}, the believed anti-air over the point would cost more than the ceiling allows
     * ({@link Outcome#TOO_DEFENDED}).
     */
    public Outcome strike(BattleSimulation sim, AirPoolService service, float x, float y, boolean ignoreRisk) {
        AirPoolKind kind = service.defaultKind(side);
        if (kind == null) return Outcome.NO_AIRFRAME;
        updateSightings(sim);
        if (!ignoreRisk && risk(sim, x, y) > RISK_CEILING) return Outcome.TOO_DEFENDED;
        Outcome outcome = service.request(sim, side, kind, x, y, true);
        if (outcome == Outcome.CALLING) struck.add(new Struck(x, y, sim.getSimTickIndex()));
        return outcome;
    }

    /** The share of an airframe a pass over the point is believed to cost, from the guns this side has sighted. */
    public float risk(BattleSimulation sim, float x, float y) {
        return AaRisk.at(sim, side, x, y, seenGuns::contains).expectedLossShare();
    }

    private List<Candidate> candidates(BattleSimulation sim, AirPoolService service, boolean ignoreRisk) {
        List<Candidate> out = new ArrayList<>();
        World world = sim.world();
        int tick = sim.getSimTickIndex();
        struck.removeIf(s -> tick - s.tick > REPEAT_TICKS);
        for (long gun : seenGuns) {
            if (!world.isAlive(gun) || !side.hostileTo(sim.identity().faction(gun))) continue;
            float x = world.x(gun);
            float y = world.y(gun);
            if (recentlyStruck(x, y)) continue;
            float risk = risk(sim, x, y);
            if (!ignoreRisk && risk > RISK_CEILING) continue;
            int near = sim.getUnitIndex().countHostileCombatants(x, y, 5f, side, 0L);
            out.add(new Candidate(x, y, true, 3f + 0.5f * near - 2f * risk, AirPoolKind.BOMBER, gun));
        }
        long densest = EnemyConcentration.densest(sim, side);
        if (densest != 0L) {
            float x = world.x(densest);
            float y = world.y(densest);
            if (!recentlyStruck(x, y)) {
                float risk = risk(sim, x, y);
                int near = sim.getUnitIndex().countHostileCombatants(x, y, EnemyConcentration.CLUSTER_RADIUS, side, 0L);
                if (ignoreRisk || risk <= RISK_CEILING) {
                    AirPoolKind kind = service.available(side, AirPoolKind.BOMBER) > 0
                            ? AirPoolKind.BOMBER : AirPoolKind.FIGHTER;
                    // Believed false: only a concentration an operator has in sight is designated.
                    out.add(new Candidate(x, y, false, near - 2f * risk, kind, 0L));
                }
            }
        }
        return out;
    }

    private boolean recentlyStruck(float x, float y) {
        for (Struck s : struck) {
            if (Math.hypot(s.x - x, s.y - y) < REPEAT_CELLS) return true;
        }
        return false;
    }

    /** Adds every hostile emplacement a sampled unit of this side has a clear line to. */
    private void updateSightings(BattleSimulation sim) {
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        long[] ours = roster.factionDenseArray(side);
        int ourCount = roster.factionLiveCount(side);
        if (ourCount <= 0) return;
        int stride = Math.max(1, ourCount / SIGHT_SAMPLE);
        for (Faction enemy : Faction.values()) {
            if (!side.hostileTo(enemy)) continue;
            long[] theirs = roster.factionDenseArray(enemy);
            int theirCount = roster.factionLiveCount(enemy);
            for (int i = 0; i < theirCount; i++) {
                long gun = theirs[i];
                if (seenGuns.contains(gun) || !sim.identity().type(gun).isTurret()) continue;
                for (int j = 0; j < ourCount; j += stride) {
                    long watcher = ours[j];
                    if (Math.hypot(world.x(gun) - world.x(watcher), world.y(gun) - world.y(watcher)) > SIGHT_CELLS) continue;
                    if (CommsOperators.lineClear(sim.getGrid(), world.cellX(watcher), world.cellY(watcher),
                            world.cellX(gun), world.cellY(gun), SIGHT_CELLS)) {
                        seenGuns.add(gun);
                        break;
                    }
                }
            }
        }
    }

    /** Operators the side could use right now, for readouts. */
    public List<Operator> operators(BattleSimulation sim) {
        return CommsOperators.available(sim, side);
    }
}

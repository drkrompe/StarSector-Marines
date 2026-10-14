package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.List;

/**
 * Aircraft shooting aircraft: the nose guns of a cover fighter that {@link AirSystem} has put on a prey's tail.
 *
 * <p>The sibling of {@code GroundAntiAirSystem}, and it works the same way for the same reason: a round is rolled
 * against the geometry when it is fired ({@link AirToAirGun#hitChance}), a hit arrives after a short flight and lands
 * through {@code BattleSimulation.applyAimedDamage}, the armour and structure law every body takes, and a round that
 * misses is still drawn flying wide. There is no second damage route. The aircraft that dies is shot down the one way an
 * aircraft is shot down: the crash, the wreck, the berth written off, the pool's loss.
 *
 * <p>This system fires and does not fly. The chase is {@link AirSystem}'s ({@code INTERCEPT}), the choice of prey is
 * tasking's, and the only thing decided here is whether the prey is inside the window ahead of the nose this tick and
 * whether the gun is ready. Serial, after the air tick, in the same phase as the ground anti-air.
 */
public final class AirToAirFire {

    /** Nose offset the round is drawn leaving from, in cells; purely where the flash is painted. */
    private static final float MUZZLE_OFFSET_CELLS = AirOrdnanceDelivery.MUZZLE_OFFSET_CELLS;
    /** A miss passes the prey by at least this many cells and at most this many more. */
    private static final float MISS_MIN_CELLS = 1.2f;
    private static final float MISS_SPREAD_CELLS = 2.5f;

    /** What the loss ledger calls a craft a fighter's guns destroyed. */
    public static final String KILL_LABEL = "air-to-air";

    private record Round(long shooter, long target, float damage, float penetration, int arrival) {}

    private final List<Round> rounds = new ArrayList<>();
    private int roundsFired;
    private int roundsHit;

    /** Rounds fired at aircraft this battle, both sides, for evidence. */
    public int roundsFired() {
        return roundsFired;
    }

    /** Rounds that struck an aircraft this battle, for evidence. */
    public int roundsHit() {
        return roundsHit;
    }

    /** Lands the rounds that have arrived and fires the guns of every fighter with its prey in the window. */
    public void tick(BattleSimulation sim) {
        World world = sim.world();
        int tick = sim.getSimTickIndex();
        for (int i = 0; i < rounds.size(); ) {
            Round round = rounds.get(i);
            if (tick < round.arrival) {
                i++;
                continue;
            }
            rounds.remove(i);
            if (world.isAlive(round.target) && sim.getRoster().airTargets().isPresent(round.target)) {
                ShuttleMission prey = world.mission(round.target);
                if (prey != null) prey.killedByLabel = KILL_LABEL;
                sim.applyAimedDamage(round.target, round.shooter, round.damage, round.penetration);
                if (prey != null) prey.killedByLabel = null;
            }
        }
        long[] aircraft = sim.getAirEntityIds();
        for (long id : aircraft) {
            ShuttleMission mission = world.mission(id);
            if (mission == null || mission.cover == null || mission.destroyed || mission.state != ShuttleState.INTERCEPT) continue;
            fire(sim, id, mission, mission.cover);
        }
    }

    private void fire(BattleSimulation sim, long id, ShuttleMission mission, CoverDuty duty) {
        World world = sim.world();
        float dt = BattleSimulation.TICK_DT;
        duty.fireCooldown = Math.max(0f, duty.fireCooldown - dt);
        long preyId = duty.preyId;
        if (preyId == 0L || !duty.hasAirRounds()) return;
        AirBody body = world.kinematics(id);
        AirBody prey = world.kinematics(preyId);
        if (body == null || prey == null) return;
        Faction mine = world.airFaction(id);
        if (!mine.hostileTo(world.airFaction(preyId))) return;
        float range = (float) Math.hypot(prey.x - body.x, prey.y - body.y);
        float off = AirToAirGun.offNoseDegrees(body, prey.x, prey.y);
        if (!AirToAirGun.inWindow(range, off) || duty.fireCooldown > 0f) return;
        duty.fireCooldown = AirToAirGun.fireInterval();
        duty.airRoundsLeft--;
        duty.airRoundsFired++;
        roundsFired++;
        boolean hit = sim.random().nextFloat() < AirToAirGun.hitChance(range, off);
        double nose = Math.toRadians(body.facingDegrees + 90f);
        float fromX = body.x + (float) Math.cos(nose) * MUZZLE_OFFSET_CELLS;
        float fromY = body.y + (float) Math.sin(nose) * MUZZLE_OFFSET_CELLS;
        float toX = prey.x;
        float toY = prey.y;
        if (!hit) {
            float side = sim.random().nextBoolean() ? 1f : -1f;
            float miss = MISS_MIN_CELLS + MISS_SPREAD_CELLS * sim.random().nextFloat();
            toX += side * miss * (float) -Math.sin(nose);
            toY += side * miss * (float) Math.cos(nose);
        }
        if (hit) {
            duty.airRoundsHit++;
            roundsHit++;
            rounds.add(new Round(id, preyId, AirToAirGun.DAMAGE, AirToAirGun.PENETRATION,
                    sim.getSimTickIndex() + Math.max(1, Math.round(AirToAirGun.FLIGHT_SECONDS / dt))));
        }
        sim.postShot(new ShotEvent(fromX, fromY, AirAppearance.visualAltitudeOffsetCells(world.altitudeT(id)),
                toX, toY, AirAppearance.visualAltitudeOffsetCells(world.altitudeT(preyId)), hit, mine,
                AirToAirGun.FLIGHT_SECONDS, null, null, null, null, 0f, hit, null, 0L));
    }
}

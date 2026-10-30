package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.StaggerService.Cause;
import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.evacuation.BlastImpulseRules.Response;
import com.dillon.starsectormarines.battle.evacuation.BruteChargeRules.Kind;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.HullImpact;
import com.dillon.starsectormarines.battle.vehicle.HullSlide;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.weapon.BlastImpulse;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.Arrays;

/**
 * A blast's shove ({@code mechs-nouns.md}, Blast impulse): an explosive round whose weapon carries a
 * {@link BlastImpulse} pushes every body in its reach away from the burst, after its damage has landed. It is the
 * Brute charge's machinery driven by a blast instead of a body: a body on foot slides as a thrown one does
 * ({@link BruteChargeSystem#shove}) and is staggered, or knocked down for the trample's hold when what the salvo
 * has given it adds up; a hull takes the impulse through its footprint ({@link HullImpact#blast}) and slides and
 * turns on its tires or tracks ({@link HullSlide}); a mech slides against its feet. The heavy classes and
 * emplacements stand ({@link BlastImpulseRules#moves}). A blast pushes friend and foe alike, as its splash hurts
 * them. Walls stop a slide as they stop a drive.
 *
 * <p>Serial: landed from the detonation pass in its order, the bodies of one blast walked in id order, so a replay
 * pushes the same bodies the same way. Draws no random numbers.
 */
public final class BlastImpulseSystem {

    /** What a body on foot has taken from recent blasts (cells a second, as float bits) and the tick it last took one. */
    private final Long2IntOpenHashMap takenBits = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap takenTick = new Long2IntOpenHashMap();
    private final LongBucket nearby = new LongBucket();
    private long[] order = new long[64];
    /** The direction of the shove being landed, set by {@link #direction}. */
    private float dirX, dirY;

    /** Bodies knocked down and staggered by blasts this battle, and hulls slid: evidence. */
    private int tumbles, staggers, shovedHulls;

    public int tumbles() { return tumbles; }
    public int staggers() { return staggers; }
    public int shovedHulls() { return shovedHulls; }

    /** Serial: lands {@code det}'s shove on what stands in its reach. */
    public void land(PendingDetonation det, BattleSimulation sim) {
        BlastImpulse blast = det.blast();
        if (blast == null) return;
        UnitRosterService roster = sim.getRoster();
        NavigationGrid grid = sim.getGrid();
        float bx = det.endpointX, by = det.endpointY;
        int bcx = (int) Math.floor(bx), bcy = (int) Math.floor(by);
        nearby.clear();
        sim.getUnitIndex().gatherOverlapping(bx, by, blast.radius(), nearby);
        int n = nearby.size;
        if (order.length < n) order = new long[Math.max(n, order.length * 2)];
        System.arraycopy(nearby.ids, 0, order, 0, n);
        Arrays.sort(order, 0, n);
        int now = sim.getSimTickIndex();
        for (int i = 0; i < n; i++) {
            long id = order[i];
            if (!roster.isAliveById(id) || roster.isRiding(id) || sim.swarmHarvest().isCarried(id)) continue;
            if (det.friendlyFireImmune && det.shooterFaction.friendlyTo(sim.identity().faction(id))) continue;
            Kind kind = BruteChargeSystem.kind(id, sim);
            if (kind == null || !BlastImpulseRules.moves(kind)) continue;
            float ux = sim.world().x(id), uy = sim.world().y(id);
            int ucx = (int) Math.floor(ux), ucy = (int) Math.floor(uy);
            // A wall between the burst and the body takes the shove, as it takes the splash.
            if (!grid.hasLineOfSight(bcx, bcy, ucx, ucy)) continue;
            if (det.aerialDelivery && roofed(sim.getTopology(), ucx, ucy)) continue;
            if (sim.convoy().isVehicle(id)) {
                hull(id, det, blast, sim);
                continue;
            }
            float distance = Math.max(0f, (float) Math.hypot(ux - bx, uy - by) - roster.radius(id));
            float impulse = blast.at(distance);
            if (impulse <= 0f) continue;
            direction(ux - bx, uy - by, det, id, sim);
            if (kind == Kind.INFANTRY) {
                body(id, impulse / BlastImpulseRules.infantryTonnes(sim.identity().armorClass(id)), now, sim);
            } else {
                // A mech is a heavy body on its feet: what a blast gives it is a trace at most, and one too small
                // to see leaves its walk (and its path) alone.
                var loadout = sim.world().mechLoadout(id);
                float dv = impulse / BlastImpulseRules.mechTonnes(loadout == null ? 1f : loadout.variant.relativeMass);
                if (dv >= BlastImpulseRules.MIN_DV) {
                    sim.bruteCharges().shove(id, dirX * dv, dirY * dv, BruteChargeRules.MECH_SLIDE,
                            BlastImpulseRules.HULL_MAX_SPEED, sim);
                }
            }
        }
    }

    /** A body on foot: a step, a stagger, or, when the salvo has given it enough, a knockdown. */
    private void body(long id, float dv, int now, BattleSimulation sim) {
        float before = 0f;
        if (takenBits.containsKey(id)) {
            before = BlastImpulseRules.faded(Float.intBitsToFloat(takenBits.get(id)),
                    (now - takenTick.get(id)) * BattleSimulation.TICK_DT);
        }
        Response response = BlastImpulseRules.respond(dv, before);
        if (response == Response.NONE) return;
        takenBits.put(id, Float.floatToIntBits(before + dv));
        takenTick.put(id, now);
        sim.bruteCharges().shove(id, dirX * dv, dirY * dv, BruteChargeRules.BODY_SLIDE,
                BlastImpulseRules.BODY_MAX_SPEED, sim);
        switch (response) {
            case TUMBLE -> {
                boolean down = sim.stagger().isStaggered(id) && sim.stagger().cause(id) == Cause.KNOCKED_DOWN;
                sim.stagger().stagger(id, BruteChargeRules.ticks(BruteChargeRules.TRAMPLE_HOLD), Cause.KNOCKED_DOWN);
                takenBits.put(id, Float.floatToIntBits(0f));
                if (!down) {
                    tumbles++;
                    sim.swarmCues().post(SwarmCues.Kind.THROWN, id, sim);
                }
            }
            case STAGGER -> {
                if (!sim.stagger().isStaggered(id)) staggers++;
                sim.stagger().stagger(id, BlastImpulseRules.ticks(BlastImpulseRules.STAGGER_HOLD), Cause.STAGGERED);
            }
            default -> { }
        }
    }

    /** A hull: the impulse through its footprint, capped, and the dust its tires throw scrubbing. */
    private void hull(long id, PendingDetonation det, BlastImpulse blast, BattleSimulation sim) {
        GroundBody body = sim.convoy().body(id);
        if (body == null) return;
        VehicleType type = sim.convoy().vehicleType(id);
        float halfLength = type.visualLengthCells / 2f, halfWidth = type.visualWidthCells / 2f;
        float distance = HullImpact.surfaceDistance(body.x, body.y, body.facingDegrees, halfLength, halfWidth,
                det.endpointX, det.endpointY);
        float impulse = blast.at(distance);
        if (impulse <= 0f) return;
        direction(0f, 0f, det, id, sim);
        HullImpact.Result hit = HullImpact.blast(body.x, body.y, body.facingDegrees, halfLength, halfWidth,
                type.massTonnes(), det.endpointX, det.endpointY, impulse, dirX, dirY);
        if (hit.impulse() <= 0f) return;
        HullSlide.apply(body, hit);
        float speed = (float) Math.hypot(body.knockVx, body.knockVy);
        if (speed > BlastImpulseRules.HULL_MAX_SPEED) {
            body.knockVx *= BlastImpulseRules.HULL_MAX_SPEED / speed;
            body.knockVy *= BlastImpulseRules.HULL_MAX_SPEED / speed;
        }
        float dv = (float) Math.hypot(hit.hullVx(), hit.hullVy());
        if (dv >= BlastImpulseRules.DUST_DV) {
            shovedHulls++;
            sim.emitGroundDust(body.x, body.y, hit.hullVx() * .5f, hit.hullVy() * .5f,
                    Math.min(.6f, .2f + dv * .2f), EffectsService.DUST_WHEEL);
        }
    }

    /**
     * Sets {@link #dirX}, {@link #dirY} to the unit vector from the burst to a body {@code (rx, ry)} off it; for a
     * body the round burst on, the round's own line from its shooter, or a heading fixed by the body's id when the
     * shooter is gone. Never random.
     */
    private void direction(float rx, float ry, PendingDetonation det, long id, BattleSimulation sim) {
        float d = (float) Math.hypot(rx, ry);
        if (d > 1e-3f) {
            dirX = rx / d;
            dirY = ry / d;
            return;
        }
        if (det.shooterId != 0L && sim.getRoster().isAliveById(det.shooterId)) {
            float sx = det.endpointX - sim.world().x(det.shooterId), sy = det.endpointY - sim.world().y(det.shooterId);
            float sd = (float) Math.hypot(sx, sy);
            if (sd > 1e-3f) {
                dirX = sx / sd;
                dirY = sy / sd;
                return;
            }
        }
        double angle = (id * 0x9E3779B97F4A7C15L >>> 40) / (double) (1L << 24) * Math.PI * 2;
        dirX = (float) Math.cos(angle);
        dirY = (float) Math.sin(angle);
    }

    private static boolean roofed(CellTopology topology, int x, int y) {
        return topology.getBuildingId(x, y) != 0 && !topology.isRoofDestroyed(x, y);
    }
}

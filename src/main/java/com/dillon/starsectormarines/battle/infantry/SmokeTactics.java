package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.smoke.SmokeFieldService;
import com.dillon.starsectormarines.battle.squad.BelievedContact;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.marine.SpecialActivation;
import com.dillon.starsectormarines.marine.SpecialAiPolicy;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SmokeGrenadeSpec;

/** Squad-level reservation and utility scoring for smoke-assisted maneuvers. */
public final class SmokeTactics {

    private SmokeTactics() {}

    /** Equipment availability only; tactical usefulness belongs to the requesting owner. */
    public static boolean canThrow(long carrier, BattleView sim) {
        World world = sim.world();
        if (!world.isAlive(carrier) || !world.hasSecondaryWeapon(carrier)) return false;
        SpecialEquipmentDef special = world.specialEquipment(carrier);
        return special != null && special.activation() == SpecialActivation.UTILITY_SMOKE
                && world.secondaryAmmo(carrier) > 0 && world.secondaryActionTimer(carrier) <= 0f
                && world.secondaryCooldownTimer(carrier) <= 0f;
    }

    /** Commits the throw point and ordinary channel. Ammunition is spent only by the release procedure. */
    public static boolean beginThrow(long carrier, PointFireAim target, boolean manual, BattleControl sim) {
        if (target == null || !Float.isFinite(target.x()) || !Float.isFinite(target.y())
                || !canThrow(carrier, sim)) return false;
        World world = sim.world();
        SmokeGrenadeSpec spec = world.specialEquipment(carrier).smokeGrenadeSpec();
        float fromX = world.renderX(carrier);
        float fromY = world.renderY(carrier);
        float dx = target.x() - fromX;
        float dy = target.y() - fromY;
        float distance = (float) Math.hypot(dx, dy);
        if (!Float.isFinite(distance)) return false;
        float x = target.x();
        float y = target.y();
        if (distance > spec.throwRange()) {
            x = fromX + dx / distance * spec.throwRange();
            y = fromY + dy / distance * spec.throwRange();
        }
        x = Math.max(.5f, Math.min(sim.getGrid().getWidth() - .5f, x));
        y = Math.max(.5f, Math.min(sim.getGrid().getHeight() - .5f, y));
        world.setSmokeThrowCommit(carrier, new SmokeThrowCommit(new PointFireAim(x, y), manual));
        world.setSecondaryActionTimer(carrier, spec.throwDuration());
        world.setSecondaryFired(carrier, false);
        world.setSecondaryAimTargetId(carrier, 0L);
        sim.combat().clearPrimaryFire(carrier);
        return true;
    }

    /** Shared release authority: one grenade spends one use and enters the ordinary flight service. */
    public static void release(long carrier, float targetX, float targetY, UnitRosterService roster,
                               SmokeFieldService fields, NavigationGrid grid) {
        World world = roster.world();
        if (!world.isAlive(carrier) || !world.hasSecondaryWeapon(carrier)) return;
        SpecialEquipmentDef secondary = world.specialEquipment(carrier);
        if (secondary == null || secondary.activation() != SpecialActivation.UTILITY_SMOKE
                || !Float.isFinite(targetX) || !Float.isFinite(targetY)) return;
        int ammo = world.secondaryAmmo(carrier);
        if (ammo <= 0) return;
        SmokeGrenadeSpec spec = secondary.smokeGrenadeSpec();
        float fromX = world.renderX(carrier);
        float fromY = world.renderY(carrier);
        float dx = targetX - fromX;
        float dy = targetY - fromY;
        float distance = (float) Math.hypot(dx, dy);
        if (!Float.isFinite(distance)) return;
        if (distance > spec.throwRange()) {
            targetX = fromX + dx / distance * spec.throwRange();
            targetY = fromY + dy / distance * spec.throwRange();
        }
        targetX = Math.max(.5f, Math.min(grid.getWidth() - .5f, targetX));
        targetY = Math.max(.5f, Math.min(grid.getHeight() - .5f, targetY));
        world.setSecondaryAmmo(carrier, ammo - 1);
        roster.telemetry().recordSecondaryUsed(carrier);
        fields.launch(carrier, roster.identity().faction(carrier), fromX, fromY,
                targetX, targetY, spec);
    }

    /** True while an exposed advance waits for its reserved cloud to become opaque. */
    public static boolean holdForAdvanceSmoke(Squad squad, long threat,
                                              int destX, int destY,
                                              BattleControl sim) {
        if (threat == 0L || sim.resolveUnit(threat) == 0L) return false;
        synchronized (squad.lock) {
            if (reservationResolvedOrPending(squad, sim)) return squad.smokeCarrierId != 0L;
            int sx = clamp(Math.round(squad.centroidX * 0.55f
                    + sim.world().x(threat) * 0.45f), 0, sim.getGrid().getWidth() - 1);
            int sy = clamp(Math.round(squad.centroidY * 0.55f
                    + sim.world().y(threat) * 0.45f), 0, sim.getGrid().getHeight() - 1);
            boolean laneExposed = sim.getGrid().hasLineOfSight(
                    sim.world().cellX(threat), sim.world().cellY(threat),
                    (int) Math.floor(squad.centroidX), (int) Math.floor(squad.centroidY))
                    || sim.getGrid().hasLineOfSight(sim.world().cellX(threat),
                    sim.world().cellY(threat), destX, destY);
            if (!laneExposed || sim.smokeFields().hasSmokeNear(sx + 0.5f, sy + 0.5f, 0.75f)) return false;
            long carrier = chooseCarrier(squad, sx, sy, sim);
            if (carrier == 0L) return false;
            return reserveAndAim(squad, carrier, threat, sx, sy, sim);
        }
    }

    /** Starts at most one useful smoke throw while displacers continue withdrawing. */
    public static void coverWithdrawal(Squad squad, BattleControl sim) {
        synchronized (squad.lock) {
            if (reservationResolvedOrPending(squad, sim)) return;
            BelievedContact threat = freshestContact(squad);
            if (threat == null) return;
            int sx = clamp(Math.round((squad.centroidX + threat.lastSeenCellX()) * 0.5f),
                    0, sim.getGrid().getWidth() - 1);
            int sy = clamp(Math.round((squad.centroidY + threat.lastSeenCellY()) * 0.5f),
                    0, sim.getGrid().getHeight() - 1);
            if (sim.smokeFields().hasSmokeNear(sx + 0.5f, sy + 0.5f, 0.75f)) return;
            long carrier = chooseCarrier(squad, sx, sy, sim);
            if (carrier != 0L) reserveAndAim(squad, carrier, threat.unitId(), sx, sy, sim);
        }
    }

    private static boolean reservationResolvedOrPending(Squad squad, BattleControl sim) {
        if (squad.smokeCarrierId == 0L) return false;
        if (sim.smokeFields().hasSmokeNear(squad.smokeTargetX + 0.5f,
                squad.smokeTargetY + 0.5f, 0.75f)) {
            squad.clearSmokeReservation();
            return false;
        }
        long carrier = sim.resolveUnit(squad.smokeCarrierId);
        if (carrier == 0L || !sim.world().hasSecondaryWeapon(carrier)
                || sim.world().secondaryAmmo(carrier) <= 0
                && sim.world().secondaryActionTimer(carrier) <= 0f
                && !sim.smokeFields().hasThrowFrom(carrier)) {
            squad.clearSmokeReservation();
            return false;
        }
        return true;
    }

    private static long chooseCarrier(Squad squad, int tx, int ty, BattleControl sim) {
        long best = 0L;
        float bestDistanceSq = Float.MAX_VALUE;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long candidate = sim.liveUnitAt(i);
            if (!sim.squad().hasSquad(candidate) || sim.squad().squadId(candidate) != squad.id) continue;
            if (!squad.participatesInPlan(candidate)) continue;
            if (!sim.world().hasSecondaryWeapon(candidate)) continue;
            SpecialEquipmentDef special = sim.world().specialEquipment(candidate);
            if (special.aiPolicy() != SpecialAiPolicy.SQUAD_SMOKE_SCREEN
                    || sim.world().secondaryAmmo(candidate) <= 0
                    || sim.world().secondaryActionTimer(candidate) > 0f
                    || sim.world().secondaryCooldownTimer(candidate) > 0f) continue;
            SmokeGrenadeSpec spec = special.smokeGrenadeSpec();
            float dx = tx + 0.5f - sim.world().x(candidate);
            float dy = ty + 0.5f - sim.world().y(candidate);
            float d2 = dx * dx + dy * dy;
            if (d2 <= spec.throwRange() * spec.throwRange() && d2 < bestDistanceSq) {
                best = candidate;
                bestDistanceSq = d2;
            }
        }
        return best;
    }

    private static boolean reserveAndAim(Squad squad, long carrier, long threat,
                                      int tx, int ty, BattleControl sim) {
        if (!beginThrow(carrier, new PointFireAim(tx + .5f, ty + .5f), false, sim)) return false;
        squad.smokeCarrierId = carrier;
        squad.smokeThreatId = threat;
        squad.smokeTargetX = tx;
        squad.smokeTargetY = ty;
        return true;
    }

    private static BelievedContact freshestContact(Squad squad) {
        BelievedContact freshest = null;
        for (BelievedContact contact : squad.believedContacts()) {
            if (freshest == null || contact.lastSeenTick() > freshest.lastSeenTick()
                    || contact.lastSeenTick() == freshest.lastSeenTick()
                    && contact.confidence() > freshest.confidence()) freshest = contact;
        }
        return freshest;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

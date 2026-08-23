package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.squad.BelievedContact;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.marine.SpecialActivation;
import com.dillon.starsectormarines.marine.SmokeGrenadeSpec;

/** Squad-level reservation and utility scoring for smoke-assisted maneuvers. */
public final class SmokeTactics {

    private SmokeTactics() {}

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
            reserveAndAim(squad, carrier, threat, sx, sy, sim);
            return true;
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
            if (!sim.world().hasSecondaryWeapon(candidate)) continue;
            MarineSecondary special = sim.world().secondaryWeapon(candidate);
            if (special.activation() != SpecialActivation.UTILITY_SMOKE
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

    private static void reserveAndAim(Squad squad, long carrier, long threat,
                                      int tx, int ty, BattleControl sim) {
        squad.smokeCarrierId = carrier;
        squad.smokeThreatId = threat;
        squad.smokeTargetX = tx;
        squad.smokeTargetY = ty;
        MarineSecondary special = sim.world().secondaryWeapon(carrier);
        sim.world().setSecondaryActionTimer(carrier, special.smokeGrenadeSpec().throwDuration());
        sim.world().setSecondaryFired(carrier, false);
        sim.world().setSecondaryAimTargetId(carrier, 0L);
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

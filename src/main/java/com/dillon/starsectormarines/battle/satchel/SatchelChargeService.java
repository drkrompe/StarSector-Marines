package com.dillon.starsectormarines.battle.satchel;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.marine.SatchelChargeSpec;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Owns target reservations and fuse-tracked demolition packs attached to hardened units. */
public final class SatchelChargeService {

    private final Map<Long, Long> carrierByReservedTarget = new LinkedHashMap<>();
    private final ArrayList<ArmedCharge> active = new ArrayList<>();
    private volatile List<ChargeView> snapshot = List.of();
    private long nextId = 1L;

    /** Atomically reserves one target for one planter. */
    public synchronized boolean tryReserve(long carrierId, long targetId) {
        if (carrierId == 0L || targetId == 0L || hasChargeForTargetInternal(targetId)) return false;
        Long reserved = carrierByReservedTarget.get(targetId);
        if (reserved != null && reserved != carrierId) return false;
        carrierByReservedTarget.put(targetId, carrierId);
        return true;
    }

    public synchronized void releaseReservation(long carrierId) {
        carrierByReservedTarget.entrySet().removeIf(entry -> entry.getValue() == carrierId);
    }

    public synchronized boolean isReservedBy(long carrierId, long targetId) {
        Long reserved = carrierByReservedTarget.get(targetId);
        return reserved != null && reserved == carrierId;
    }

    /** Converts a completed reservation into an armed charge. */
    public synchronized boolean plant(long carrierId, long targetId,
                                      Faction faction, float x, float y,
                                      SatchelChargeSpec spec) {
        if (!isReservedBy(carrierId, targetId) || hasChargeForTargetInternal(targetId)) return false;
        carrierByReservedTarget.remove(targetId);
        active.add(new ArmedCharge(nextId++, carrierId, targetId, faction, x, y,
                spec.fuseSeconds(), spec.fuseSeconds(), spec.blastRadius(),
                spec.damage(), spec.penetration()));
        publishSnapshot();
        return true;
    }

    /** Advances attachment positions and resolves expired fuses through the shared blast pipeline. */
    public synchronized void tick(float dt, BattleControl sim) {
        Iterator<Map.Entry<Long, Long>> reservations = carrierByReservedTarget.entrySet().iterator();
        while (reservations.hasNext()) {
            Map.Entry<Long, Long> reservation = reservations.next();
            if (sim.resolveUnit(reservation.getKey()) == 0L
                    || sim.resolveUnit(reservation.getValue()) == 0L) {
                reservations.remove();
            }
        }
        for (int i = active.size() - 1; i >= 0; i--) {
            ArmedCharge charge = active.get(i);
            long liveTarget = sim.resolveUnit(charge.targetId);
            if (liveTarget != 0L) {
                charge.x = sim.world().x(liveTarget);
                charge.y = sim.world().y(liveTarget);
            }
            charge.remaining -= dt;
            if (charge.remaining > 0f) continue;
            sim.spawnHeavyImpact(charge.x, charge.y, charge.blastRadius);
            sim.detonateNow(new PendingDetonation(charge.carrierId,
                    charge.x, charge.y, 0f, charge.blastRadius,
                    charge.damage, charge.penetration, 0,
                    charge.faction, false));
            active.remove(i);
        }
        publishSnapshot();
    }

    public List<ChargeView> activeCharges() { return snapshot; }

    public boolean hasChargeForTarget(long targetId) {
        for (ChargeView charge : snapshot) {
            if (charge.targetId() == targetId) return true;
        }
        return false;
    }

    public ChargeView nearestFriendlyHazard(Faction faction, float x, float y,
                                            float margin) {
        ChargeView nearest = null;
        float nearestDistanceSq = Float.MAX_VALUE;
        for (ChargeView charge : snapshot) {
            if (charge.sourceFaction() != faction) continue;
            float dx = charge.x() - x;
            float dy = charge.y() - y;
            float reach = charge.blastRadius() + Math.max(0f, margin);
            float distanceSq = dx * dx + dy * dy;
            if (distanceSq <= reach * reach && distanceSq < nearestDistanceSq) {
                nearest = charge;
                nearestDistanceSq = distanceSq;
            }
        }
        return nearest;
    }

    private boolean hasChargeForTargetInternal(long targetId) {
        for (ArmedCharge charge : active) {
            if (charge.targetId == targetId) return true;
        }
        return false;
    }

    private void publishSnapshot() {
        ArrayList<ChargeView> views = new ArrayList<>(active.size());
        for (ArmedCharge charge : active) {
            views.add(new ChargeView(charge.id, charge.carrierId, charge.targetId,
                    charge.faction, charge.x, charge.y, charge.remaining,
                    charge.totalFuse, charge.blastRadius));
        }
        snapshot = List.copyOf(views);
    }

    public record ChargeView(long id, long carrierId, long targetId,
                             Faction sourceFaction, float x, float y,
                             float remaining, float totalFuse,
                             float blastRadius) {
    }

    private static final class ArmedCharge {
        final long id;
        final long carrierId;
        final long targetId;
        final Faction faction;
        final float totalFuse;
        final float blastRadius;
        final float damage;
        final float penetration;
        float x;
        float y;
        float remaining;

        ArmedCharge(long id, long carrierId, long targetId, Faction faction,
                    float x, float y, float remaining, float totalFuse,
                    float blastRadius, float damage, float penetration) {
            this.id = id;
            this.carrierId = carrierId;
            this.targetId = targetId;
            this.faction = faction;
            this.x = x;
            this.y = y;
            this.remaining = remaining;
            this.totalFuse = totalFuse;
            this.blastRadius = blastRadius;
            this.damage = damage;
            this.penetration = penetration;
        }
    }
}

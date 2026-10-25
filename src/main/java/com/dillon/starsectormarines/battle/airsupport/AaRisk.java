package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.fort.Fort;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretRole;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;

import java.util.function.LongPredicate;

/**
 * What an aircraft sent at a point can expect to meet. Counts the hostile emplacements that can put fire into the
 * air within reach of the point, and turns that into the share of an airframe a pass over it is likely to cost.
 *
 * <p>Honest about knowledge: the caller supplies which guns it {@code knows} of, so a side's estimate is built from
 * what its observers have sighted and never from a roster it has no way to read. A gun nobody has seen is a gun the
 * estimate does not include, which is exactly how a strike gets surprised by one.
 *
 * <p>Every turret reaches the air by the area model (the air system's anti-air bubble); a dedicated anti-air mount
 * also fires real rounds or guided missiles, so it counts for more, and counts wherever its own weapon reaches: a
 * maw-grade spore colony or a flak gun twenty cells off threatens the point though it stands outside the bubble. A fort's anti-air yard is counted by its mounts' real
 * reach instead of the bubble (a guided battery covers far more than fourteen cells), and a yard whose console is
 * wrecked covers nothing. UNTUNED: the figures are first-pass and were not
 * set to hit a target.
 */
public final class AaRisk {

    /** Cells within which a turret threatens an aircraft over the point; mirrors the air system's anti-air bubble. */
    public static final float THREAT_RADIUS_CELLS = 14f;
    /**
     * The farthest a dedicated anti-air emplacement outside a fort is looked for: past a maw-grade spore colony's and a
     * flak gun's own reach, which are counted by that reach rather than the bubble.
     */
    static final float AA_REACH_CELLS = 30f;
    /** Share of an airframe one ordinary emplacement costs a pass over its bubble. UNTUNED. */
    static final float LOSS_PER_GUN = 0.30f;
    /** Further share a dedicated anti-air mount adds over an ordinary one. UNTUNED. */
    static final float LOSS_PER_AA_MOUNT = 0.35f;

    /** What a point holds: ordinary emplacements, dedicated anti-air mounts, and the airframe share they would cost. */
    public record Assessment(int guns, int antiAirMounts, float expectedLossShare) {
        /** Whether the defence is strong enough that a strike over it is likely to be lost whole. */
        public boolean lethal() {
            return expectedLossShare >= 1f;
        }
    }

    public static final Assessment NONE = new Assessment(0, 0, 0f);

    private AaRisk() {}

    /**
     * The risk to an aircraft of {@code side} over {@code (x, y)}, counting only the hostile guns {@code known} names.
     *
     * @param known which emplacements the caller has fair knowledge of; {@code null} counts none
     */
    public static Assessment at(BattleSimulation sim, Faction side, float x, float y, LongPredicate known) {
        if (known == null) return NONE;
        World world = sim.world();
        LongBucket scratch = new LongBucket();
        sim.getUnitIndex().gather(x, y, Math.max(THREAT_RADIUS_CELLS, AA_REACH_CELLS), scratch);
        int guns = 0;
        int aa = 0;
        for (int i = 0; i < scratch.size; i++) {
            long e = scratch.ids[i];
            if (!world.isAlive(e) || !side.hostileTo(sim.identity().faction(e))) continue;
            if (sim.forts().airMountFort(e) != null) continue; // a fort's anti-air is counted by its real reach below
            if (!sim.identity().type(e).isTurret() || !known.test(e)) continue;
            StructureDef structure = sim.turretState().isTurret(e) ? sim.turretState().structure(e) : null;
            boolean antiAir = structure != null && structure.targetRole == TurretRole.AA && structure.mount != null;
            // A dedicated anti-air mount threatens out to its own weapon's reach; every other turret, the bubble.
            float reach = antiAir ? structure.mount.weapon.range : THREAT_RADIUS_CELLS;
            float dx = world.x(e) - x, dy = world.y(e) - y;
            if (dx * dx + dy * dy > reach * reach) continue;
            guns++;
            if (antiAir) aa++;
        }
        for (Fort fort : sim.forts().forts()) {
            if (fort.airMountIds().isEmpty() || fort.airDefenceWrecked()) continue;
            for (long mount : fort.airMountIds()) {
                if (!world.isAlive(mount) || !side.hostileTo(sim.identity().faction(mount)) || !known.test(mount)) continue;
                StructureDef structure = sim.turretState().structure(mount);
                if (structure == null) continue;
                float dx = world.x(mount) - x;
                float dy = world.y(mount) - y;
                float reach = structure.mount.weapon.range;
                if (dx * dx + dy * dy > reach * reach) continue;
                guns++;
                aa++;
            }
        }
        return new Assessment(guns, aa,Math.min(2f, guns * LOSS_PER_GUN + aa * LOSS_PER_AA_MOUNT));
    }
}

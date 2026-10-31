package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.ArmorArcs;
import com.dillon.starsectormarines.battle.combat.DurabilityModel;

/**
 * The turret of an armed hull as a section of it: a share of the hull's armor capacity and structure, a share of
 * the hits it takes, and the gun it loses when that share is gone. The vehicle twin of a mech's mount section
 * ({@code mechs-nouns.md}, Section pools), on the same law: the hull's HEALTH/ARMOR columns keep carrying the
 * totals, so whole-body readers still see one hull, and what is not the turret's is the hull's own.
 *
 * <p>A hit rolled onto the turret meets the turret's armor, then its structure; what it cannot hold goes on into the
 * hull ({@link HullLocations}), which meets it with the plate on the side it arrives from before its structure. A
 * hit on a turret already at zero goes through the hole to that plate. Only the hull's own structure decides death; the turret at zero <b>destroys the gun</b>
 * and nothing else: the hull drives on unarmed, its turret still drawn where it last pointed. The armor rating is
 * the hull's, one plate per machine, as a mech's sections share their chassis's rating; what that plate is worth
 * depends on the arc a hit arrives on ({@link ArmorArcs}), read for a turret hit against the turret's own facing.
 *
 * <p>Pools live here; the hull's own structure is derived from the columns on every hit, so there is no second
 * copy of the hull to drift. Written only on the serialized damage path. See {@code convoy-nouns.md}.
 */
public final class HullTurretSection {

    /** {@code -Dbattle.vehicle.turretDamage=false} is the control: a hull is one body and its gun never goes. */
    public static final String PROPERTY = "battle.vehicle.turretDamage";

    public static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    /** Evidence overrides for an authored share; absent, each hull's own shares stand. */
    static final float HIT_SHARE_OVERRIDE = share("battle.vehicle.turretHitShare");
    static final float POOL_SHARE_OVERRIDE = share("battle.vehicle.turretPoolShare");

    private final float hitShare;
    private final float maxArmor;
    private final float maxStructure;
    private float armor;
    private float structure;
    private boolean destroyed;

    /**
     * @param hitShare  the share of the hull's hits that land on the turret
     * @param poolShare the share of the hull's armor capacity and structure the turret holds
     */
    public HullTurretSection(float hitShare, float poolShare, float hullStructure, float hullArmor) {
        if (!(hitShare >= 0f && hitShare <= 1f) || !(poolShare > 0f && poolShare < 1f)) {
            throw new IllegalArgumentException("hit share in [0, 1] and pool share in (0, 1) required");
        }
        this.hitShare = hitShare;
        this.maxStructure = hullStructure * poolShare;
        this.maxArmor = hullArmor * poolShare;
        this.structure = maxStructure;
        this.armor = maxArmor;
    }

    /** The section a hull of {@code type} deploys with, or null when it authors none or the control is off. */
    public static HullTurretSection forHull(VehicleType type) {
        return forHull(type, HullFitEffect.NONE);
    }

    /** As above, its share taken of the plate and structure the hull's fit gives it ({@link HullFitEffect}). */
    public static HullTurretSection forHull(VehicleType type, HullFitEffect fit) {
        if (!ENABLED || !type.hasTurretWeapon() || type.turretPoolShare() <= 0f) return null;
        float hit = Float.isNaN(HIT_SHARE_OVERRIDE) ? type.turretHitShare() : HIT_SHARE_OVERRIDE;
        float pool = Float.isNaN(POOL_SHARE_OVERRIDE) ? type.turretPoolShare() : POOL_SHARE_OVERRIDE;
        return new HullTurretSection(hit, pool, type.maxStructure * fit.structureFactor(),
                type.maxArmor * fit.armorFactor());
    }

    /** Whether a hit whose roll is {@code roll} in [0, 1) lands on the turret. */
    public boolean struck(float roll) {
        return roll < hitShare;
    }

    /** Whether the gun is gone. Once true it stays true for the battle. */
    public boolean destroyed() {
        return destroyed;
    }

    public float armor() {
        return armor;
    }

    public float structure() {
        return structure;
    }

    public float maxArmor() {
        return maxArmor;
    }

    public float maxStructure() {
        return maxStructure;
    }

    /** Sets the armor to {@code share} of its maximum outside the damage path: for staging a look, never for play. */
    void stageArmorShare(float share) {
        armor = maxArmor * Math.max(0f, Math.min(1f, share));
    }

    /**
     * Shoots the gun away outside the damage path (the caller lowers the structure column by {@link #maxStructure}):
     * for staging a look, never for play.
     */
    void stageDestroyed() {
        armor = 0f;
        structure = 0f;
        destroyed = true;
    }

    /**
     * Takes a hit on the turret alone: its armor, then its structure. Writes the turret's own armor and structure
     * loss and whether the gun went into {@code out} and returns the damage that goes on through
     * the turret into the hull. A turret already at zero passes the whole hit through.
     */
    public float strike(float damage, float penetration, float rating, float plate,
                        DurabilityModel.Resolution scratch, Hit out) {
        if (structure <= 0f) {
            out.set(0f, 0f, false);
            return damage;
        }
        float armorBefore = armor;
        float structureBefore = structure;
        DurabilityModel.resolvePlatedInto(damage, penetration, armorBefore, rating, plate, structureBefore, scratch);
        float armorDamage = scratch.armorDamage();
        float onSection = scratch.structureDamage();
        float through = 0f;
        if (onSection >= structureBefore) {
            float toBreak = armorBefore > 0f
                    ? armorBefore * plate / DurabilityModel.armorEfficiency(penetration, rating) : 0f;
            through = Math.max(0f, damage - toBreak - structureBefore);
        }
        armor = Math.max(0f, armorBefore - armorDamage);
        structure = Math.max(0f, structureBefore - onSection);
        boolean gunNowDestroyed = !destroyed && onSection > 0f && structure <= 0f;
        if (gunNowDestroyed) destroyed = true;
        out.set(armorDamage, onSection, gunNowDestroyed);
        return through;
    }

    private static float share(String property) {
        String value = System.getProperty(property);
        return value == null ? Float.NaN : Float.parseFloat(value);
    }

    /** Mutable result of one {@link #strike}; reused by the damage path. */
    public static final class Hit {
        private float armorDamage;
        private float structureDamage;
        private boolean gunDestroyed;

        void set(float armorDamage, float structureDamage, boolean gunDestroyed) {
            this.armorDamage = armorDamage;
            this.structureDamage = structureDamage;
            this.gunDestroyed = gunDestroyed;
        }

        /** Armor removed from the turret. */
        public float armorDamage() {
            return armorDamage;
        }

        /** Structure removed from the turret. */
        public float structureDamage() {
            return structureDamage;
        }

        /** Whether this hit took the turret's structure to zero. */
        public boolean gunDestroyed() {
            return gunDestroyed;
        }

    }
}

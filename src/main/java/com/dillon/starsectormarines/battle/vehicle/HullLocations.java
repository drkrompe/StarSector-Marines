package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.ArmorArcs;
import com.dillon.starsectormarines.battle.combat.DurabilityModel;

/**
 * Where a ground hull holds its damage: four armor <b>plates</b> (front, left, right, rear), the <b>drive</b> (tracks,
 * wheels or lift fans) and, for an armed hull, its turret ({@link HullTurretSection}). Behind them is the hull's own
 * structure, the only pool whose zero is death. The vehicle twin of a mech's sections ({@code mechs-nouns.md}).
 *
 * <p>A hit on the hull meets the plate on the side it arrives from ({@link #plateFor}): front and rear by
 * {@link ArmorArcs#classify}, a side hit split left or right by which side of the hull its origin stands. Damage the
 * plate cannot hold reaches the hull's structure, so a stripped side lets every later hit from that side straight
 * through while the other plates stay whole. That is what makes flanking a tactic and what the damage doll draws.
 *
 * <p>Some hits land on the drive instead of the hull behind the plate: an authored share by arc, larger from the
 * sides and rear, where the running gear shows. Such a hit still meets that side's plate first; what gets through
 * spends the drive's structure, and what the drive cannot hold goes on into the hull. At zero the hull is
 * <b>immobilised</b>: it can no longer move, still turns its turret and fires, and is not dead.
 *
 * <p>The front plate holds the whole of the armor a hull of one pool had, so a hull shot head-on lasts as long as
 * it did; the side and rear plates and the drive are held on top of that and only matter to a hull caught from
 * there. The world's HEALTH and ARMOR columns carry the totals (plates and turret armor; hull, turret and drive
 * structure), so whole-body readers still see one machine; what is not a plate, the turret or the drive is the
 * hull's own, derived on every hit. Written only on the serialized damage path. See {@code convoy-nouns.md}.
 *
 * <p>Every ground hull carries them. A hull that turns its face to its attackers spends the plates they stripped
 * meanwhile on top of the front's whole pool, so a fight the hull un-flanks runs longer than a hull of one pool did;
 * a flank held pays at once ({@code heavy-equipment-progression.md}, located plates).
 */
public final class HullLocations {

    /** The four plates, in the order the doll and the arrays use. */
    public enum Plate { FRONT, LEFT, RIGHT, REAR }

    private static final Plate[] PLATES = Plate.values();

    private final float[] maxArmor = new float[PLATES.length];
    private final float[] armor = new float[PLATES.length];
    private final float maxDrive;
    private float drive;
    private boolean immobilised;
    private final float driveFront;
    private final float driveSide;
    private final float driveRear;
    private final HullTurretSection turret;

    /**
     * @param hullArmor      the hull's armor outside the turret
     * @param plateArmor     each plate as a multiple of {@code hullArmor}: front, left, right, rear
     * @param driveStructure the drive's structure, held on top of the hull's
     * @param driveHits      the share of hull hits that land on the drive from the front, a side, and the rear
     */
    public HullLocations(float hullArmor, float[] plateArmor, float driveStructure, float[] driveHits,
                         HullTurretSection turret) {
        if (plateArmor.length != PLATES.length || driveHits.length != 3) {
            throw new IllegalArgumentException("four plate multiples and three drive hit shares required");
        }
        for (int i = 0; i < PLATES.length; i++) {
            if (!(plateArmor[i] >= 0f)) throw new IllegalArgumentException("plate multiples must be non-negative");
            maxArmor[i] = Math.max(0f, hullArmor) * plateArmor[i];
            armor[i] = maxArmor[i];
        }
        maxDrive = Math.max(0f, driveStructure);
        drive = maxDrive;
        driveFront = driveHits[0];
        driveSide = driveHits[1];
        driveRear = driveHits[2];
        this.turret = turret;
    }

    /** The locations a hull of {@code type} deploys with, around its turret section (null for none). */
    public static HullLocations of(VehicleType type, HullTurretSection turret) {
        return of(type, turret, VehicleKit.Effect.NONE);
    }

    /** As above, with the plate and structure its kits add ({@link VehicleKit}). */
    public static HullLocations of(VehicleType type, HullTurretSection turret, VehicleKit.Effect kits) {
        float turretArmor = turret != null ? turret.maxArmor() : 0f;
        return new HullLocations(type.maxArmor * kits.armorFactor() - turretArmor, type.plateArmor(),
                type.maxStructure * kits.structureFactor() * type.driveStructureShare(), type.driveHitShares(),
                turret);
    }

    /** Armor across the plates and the turret: the hull's ARMOR column at full. */
    public float maxTotalArmor() {
        float sum = turret != null ? turret.maxArmor() : 0f;
        for (float plate : maxArmor) sum += plate;
        return sum;
    }

    /**
     * The plate a hit from {@code (dx, dy)} away (origin minus hull) meets, for a hull facing {@code facingDegrees}:
     * the front or rear arc, or a side by which side of the hull the origin stands. The hull's right is
     * {@code (cos, sin)} of its facing ({@link ArmorArcs}). A hit with no bearing (no origin to read, or one standing
     * on the hull) meets the front plate, which holds what the hull's one pool held: the nominal plate.
     */
    public Plate plateFor(ArmorArcs.Arc arc, float facingDegrees, float dx, float dy) {
        if (dx == 0f && dy == 0f) return Plate.FRONT;
        return switch (arc) {
            case FRONT -> Plate.FRONT;
            case REAR -> Plate.REAR;
            case SIDE -> {
                double radians = Math.toRadians(facingDegrees);
                yield dx * Math.cos(radians) + dy * Math.sin(radians) > 0 ? Plate.RIGHT : Plate.LEFT;
            }
        };
    }

    /** The share of hull hits arriving on {@code arc} that land on the drive. */
    public float driveHitShare(ArmorArcs.Arc arc) {
        return switch (arc) {
            case FRONT -> driveFront;
            case SIDE -> driveSide;
            case REAR -> driveRear;
        };
    }

    public float armor(Plate plate) {
        return armor[plate.ordinal()];
    }

    public float maxArmor(Plate plate) {
        return maxArmor[plate.ordinal()];
    }

    /**
     * Sets {@code plate}'s armor to {@code share} of its maximum, outside the damage path and without touching the
     * world's columns: for staging a look at a worn hull, never for play.
     */
    void stageArmorShare(Plate plate, float share) {
        armor[plate.ordinal()] = maxArmor[plate.ordinal()] * Math.max(0f, Math.min(1f, share));
    }

    /** {@link #stageArmorShare(Plate, float)} for every plate and the turret. */
    void stageArmorShare(float share) {
        for (Plate plate : PLATES) stageArmorShare(plate, share);
        if (turret != null) turret.stageArmorShare(share);
    }

    /**
     * Spends the drive outright, outside the damage path and without touching the world's columns (the caller lowers
     * the structure column by {@link #maxDrive}): for staging a look at an immobilised hull, never for play.
     */
    void stageDriveLost() {
        drive = 0f;
        immobilised = true;
    }

    public float drive() {
        return drive;
    }

    public float maxDrive() {
        return maxDrive;
    }

    /** Whether the drive is gone: the hull cannot move. Once true it stays true for the battle. */
    public boolean immobilised() {
        return immobilised;
    }

    /**
     * The hull's own structure out of a HEALTH column standing at {@code totalStructure}: what is neither the
     * turret's nor the drive's. Only its zero is death.
     */
    public float hullStructure(float totalStructure) {
        float turretStructure = turret != null ? turret.structure() : 0f;
        return Math.max(0f, totalStructure - turretStructure - drive);
    }

    /** The turret section, or null for a hull whose gun is not one. */
    public HullTurretSection turret() {
        return turret;
    }

    /**
     * Resolves one hit on the hull whose structure column stands at {@code totalStructure} (turret and drive
     * included). {@code onTurret} and {@code onDrive} are the rolls' answers (a turret hit never lands on the drive);
     * {@code plate} is where the hull is struck, and where what gets through the turret meets the hull.
     * Allocation-free; {@code scratch} and {@code out} are the caller's, who writes the column deltas.
     */
    public void resolve(boolean onTurret, boolean onDrive, Plate plate, float damage, float penetration,
                        float turretRating, float turretPlate, float hullRating, float hullPlate,
                        float totalStructure, DurabilityModel.Resolution scratch, Hit out) {
        boolean turretStruck = onTurret && turret != null;
        float turretArmorDamage = 0f;
        float turretStructureDamage = 0f;
        boolean gunNowDestroyed = false;
        float toHull = damage;
        if (turretStruck) {
            toHull = turret.strike(damage, penetration, turretRating, turretPlate, scratch, out.turret);
            turretArmorDamage = out.turret.armorDamage();
            turretStructureDamage = out.turret.structureDamage();
            gunNowDestroyed = out.turret.gunDestroyed();
        }
        float turretStructure = turret != null ? turret.structure() : 0f;
        float hullStructure = Math.max(0f, totalStructure - turretStructureDamage - turretStructure - drive);
        int p = plate.ordinal();
        float plateArmorDamage = 0f;
        float driveDamage = 0f;
        float hullDamage = 0f;
        boolean driveNowDestroyed = false;
        boolean struckDrive = !turretStruck && onDrive && drive > 0f;
        if (toHull > 0f) {
            float armorBefore = armor[p];
            float target = struckDrive ? drive : hullStructure;
            DurabilityModel.resolvePlatedInto(toHull, penetration, armorBefore, hullRating, hullPlate, target, scratch);
            plateArmorDamage = scratch.armorDamage();
            armor[p] = Math.max(0f, armorBefore - plateArmorDamage);
            if (struckDrive) {
                float driveBefore = drive;
                driveDamage = scratch.structureDamage();
                drive = Math.max(0f, driveBefore - driveDamage);
                if (driveDamage >= driveBefore) {
                    driveNowDestroyed = !immobilised;
                    immobilised = true;
                    float toBreak = armorBefore > 0f
                            ? armorBefore * hullPlate / DurabilityModel.armorEfficiency(penetration, hullRating) : 0f;
                    hullDamage = Math.min(hullStructure, Math.max(0f, toHull - toBreak - driveBefore));
                }
            } else {
                hullDamage = scratch.structureDamage();
            }
        }
        boolean hullNowDestroyed = hullStructure > 0f && hullDamage >= hullStructure;
        out.set(turretArmorDamage + plateArmorDamage, turretStructureDamage + driveDamage + hullDamage,
                gunNowDestroyed, driveNowDestroyed, hullNowDestroyed);
    }

    /** Mutable result of one {@link #resolve}; reused by the damage path. */
    public static final class Hit {
        final HullTurretSection.Hit turret = new HullTurretSection.Hit();
        private float armorDamage;
        private float structureDamage;
        private boolean gunDestroyed;
        private boolean driveDestroyed;
        private boolean hullDestroyed;

        void set(float armorDamage, float structureDamage, boolean gunDestroyed, boolean driveDestroyed,
                 boolean hullDestroyed) {
            this.armorDamage = armorDamage;
            this.structureDamage = structureDamage;
            this.gunDestroyed = gunDestroyed;
            this.driveDestroyed = driveDestroyed;
            this.hullDestroyed = hullDestroyed;
        }

        /** Armor removed from the turret and the struck plate together. */
        public float armorDamage() {
            return armorDamage;
        }

        /** Structure removed from the turret, the drive and the hull together. */
        public float structureDamage() {
            return structureDamage;
        }

        public boolean gunDestroyed() {
            return gunDestroyed;
        }

        /** Whether this hit took the drive to zero: the hull is now immobilised. */
        public boolean driveDestroyed() {
            return driveDestroyed;
        }

        /** Whether this hit took the hull's own structure to zero: the hull dies. */
        public boolean hullDestroyed() {
            return hullDestroyed;
        }
    }
}

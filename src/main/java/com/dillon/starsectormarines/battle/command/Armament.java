package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.marine.CommsKitSpec;
import com.dillon.starsectormarines.marine.SatchelChargeSpec;
import com.dillon.starsectormarines.marine.SmokeGrenadeSpec;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;

/**
 * One thing a unit carries that can hurt a defended position, reduced to the numbers an assault plan needs and nothing
 * about what the unit or the item is called. {@link CapabilityProfile} sums these into what a squad can do; the plan
 * never sees the weapon.
 *
 * <p>Every field is read off catalog data (the weapon definition, the mount's rack, the special-equipment item) and the
 * unit's live resource (rounds or uses left), so a new weapon, a re-rolled loadout or a half-empty rack changes what the
 * squad is believed able to do without any code naming it.
 *
 * @param delivery        how it reaches its target
 * @param range           reach, in cells
 * @param roundsPerTrigger rounds (separate detonations) one trigger pull emits
 * @param cycleSeconds    seconds from one trigger pull to the next, loader or cooldown included
 * @param damagePerRound  damage one round does where it lands
 * @param penetration     armour penetration of one round
 * @param wallDamage      structural damage one round does to a wall, zero for a weapon that does none
 * @param triggersLeft    trigger pulls left, or {@link #UNLIMITED}
 * @param coverSeconds    for a {@link Delivery#SCREEN}, how long the cloud it stands holds its full radius; zero for anything else
 * @param cloudRadius     for a {@link Delivery#SCREEN}, the radius that cloud blocks to while it is whole; zero for anything else
 */
public record Armament(Delivery delivery, float range, int roundsPerTrigger, float cycleSeconds,
                       float damagePerRound, float penetration, int wallDamage, int triggersLeft,
                       float coverSeconds, float cloudRadius) {

    /** An armament that stands no cloud. */
    public Armament(Delivery delivery, float range, int roundsPerTrigger, float cycleSeconds,
                    float damagePerRound, float penetration, int wallDamage, int triggersLeft) {
        this(delivery, range, roundsPerTrigger, cycleSeconds, damagePerRound, penetration, wallDamage, triggersLeft, 0f, 0f);
    }

    /** {@code triggersLeft} of a weapon with no magazine to run dry. */
    public static final int UNLIMITED = -1;

    /** How an armament reaches what it hits. */
    public enum Delivery {
        /** A flat-trajectory round: it stops at the first wall in its path, and so can be aimed at a wall. */
        DIRECT,
        /** An arcing round: it flies over what is in the way and can fall on a wall from far off. */
        INDIRECT,
        /** A hand-thrown arc: anti-personnel, not used against structures by a plan. */
        THROWN,
        /**
         * An arcing tube set down and served (a field mortar): it fires on bodies, from the section's belief or an assigned
         * fire mission, and cannot be aimed at a wall cell, so it is anti-structure fire and suppression but never a breacher.
         */
        EMPLACED,
        /** A grenade thrown to stand a cloud of smoke on ground: no damage, an obstruction to sight. */
        SCREEN,
        /** A radio that directs the side's air onto ground: not a weapon, the means to ask for one. */
        CALLED,
        /** A charge placed at the target and left to go off. */
        CHARGE
    }

    public Armament {
        if (!(cycleSeconds > 0f)) throw new IllegalArgumentException("cycleSeconds must be positive");
        if (roundsPerTrigger < 1) throw new IllegalArgumentException("roundsPerTrigger must be at least one");
    }

    /** Whether it can still be used at all: a magazine that has run dry contributes nothing. */
    public boolean usable() {
        return triggersLeft != 0;
    }

    /** Trigger pulls in total from now, infinite when unlimited. */
    public float triggers() {
        return triggersLeft < 0 ? Float.POSITIVE_INFINITY : triggersLeft;
    }

    /**
     * A catalog weapon fired by something that is not a mech mount: a marine's primary, with no stock to
     * count. A magazine reloads without end, so it costs cadence rather than triggers: the cycle is the
     * service-grade average over a load and its reload.
     */
    public static Armament ofPrimary(WeaponDef weapon) {
        if (weapon == null) return null;
        int rounds = Math.max(1, weapon.burstCount) * Math.max(1, weapon.projectilesPerShot);
        float cycle = weapon.magazine() == null ? weapon.cooldown
                : weapon.magazine().sustainedTriggerInterval(EquipmentGrade.SERVICE, weapon.cooldown,
                        Math.max(1, weapon.burstCount), weapon.magazine().reloadSeconds());
        return new Armament(weapon.indirectFire || weapon.arcHeight > 0f ? Delivery.INDIRECT : Delivery.DIRECT,
                weapon.range, rounds, Math.max(0.1f, cycle), weapon.damage, weapon.penetration,
                weapon.wallDamage, UNLIMITED);
    }

    /** A mech hardpoint, with the pulls its rack holds now. */
    public static Armament ofMount(MechWeaponMount mount) {
        if (mount == null) return null;
        WeaponDef weapon = mount.weaponDef();
        int triggers = mount.component.ammoCapacity < 0 ? UNLIMITED : Math.max(0, mount.ammo);
        return new Armament(weapon.indirectFire || weapon.arcHeight > 0f ? Delivery.INDIRECT : Delivery.DIRECT,
                weapon.range, Math.max(1, mount.component.projectilesPerTrigger),
                Math.max(0.1f, mount.component.cycleSeconds()), weapon.damage, weapon.penetration,
                weapon.wallDamage, triggers);
    }

    /**
     * A carried special item with the uses it has left, or null when it is not something a plan can use against a
     * position (smoke, a screen, a dressing).
     */
    public static Armament ofSpecial(SpecialEquipmentDef item, int ammo) {
        if (item == null) return null;
        int triggers = item.usesAmmunition() ? Math.max(0, ammo) : UNLIMITED;
        switch (item.activation()) {
            case DIRECT_EXPLOSIVE, DIRECT_PRECISION -> {
                WeaponDef weapon = item.weaponDef();
                return new Armament(Delivery.DIRECT, weapon.range, 1,
                        Math.max(0.1f, Math.max(weapon.cooldown, item.aimDuration())), weapon.damage,
                        weapon.penetration, weapon.wallDamage, triggers);
            }
            case ARC_EXPLOSIVE -> {
                WeaponDef weapon = item.weaponDef();
                return new Armament(Delivery.THROWN, weapon.range, 1, Math.max(0.1f, weapon.cooldown),
                        weapon.damage, weapon.penetration, weapon.wallDamage, triggers);
            }
            case UTILITY_SATCHEL -> {
                SatchelChargeSpec spec = item.satchelChargeSpec();
                return new Armament(Delivery.CHARGE, spec.contactRange(), 1,
                        spec.cooldownSeconds() + spec.plantDuration() + spec.fuseSeconds(), spec.damage(),
                        spec.penetration(), spec.wallBreachDamage(), triggers);
            }
            case UTILITY_DEPLOYABLE -> {
                if (!item.isCrewServed()) return null;
                WeaponDef weapon = item.deployedWeaponDef();
                StructureDef structure = TurretCatalogRegistry.requireStructure(
                        item.deployableEmplacementSpec().structureId());
                // The item's own ammunition counts tubes; the rounds are the tube's magazine, carried between deployments.
                int rounds = ammo > 0 || !item.usesAmmunition() ? Math.max(1, structure.mount.ammoCapacity) : 0;
                return new Armament(Delivery.EMPLACED, weapon.range, 1, Math.max(0.1f, weapon.cooldown), weapon.damage,
                        weapon.penetration, weapon.wallDamage, rounds);
            }
            case UTILITY_SMOKE -> {
                SmokeGrenadeSpec spec = item.smokeGrenadeSpec();
                return new Armament(Delivery.SCREEN, spec.throwRange(), 1, Math.max(0.1f, spec.throwDuration()), 0f, 0f, 0, triggers,
                        spec.coverSeconds(), spec.cloudRadius());
            }
            case UTILITY_COMMS -> {
                CommsKitSpec spec = item.commsKitSpec();
                return new Armament(Delivery.CALLED, spec.radioRangeCells(), 1,
                        Math.max(0.1f, spec.callSeconds() + spec.cooldownSeconds()), 0f, 0f, 0, UNLIMITED);
            }
            default -> {
                return null;
            }
        }
    }
}

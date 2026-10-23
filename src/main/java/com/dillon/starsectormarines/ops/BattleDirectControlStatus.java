package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.combat.HeavyWeapons;
import com.dillon.starsectormarines.battle.combat.ManualPointFire;
import com.dillon.starsectormarines.battle.combat.MechFireGroups;
import com.dillon.starsectormarines.battle.combat.MechTriggerPicks;
import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.combat.MitigationService;
import com.dillon.starsectormarines.battle.control.DirectControlAbility;
import com.dillon.starsectormarines.battle.control.MarineRightTrigger;
import com.dillon.starsectormarines.battle.control.MarineSprint;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.IntegralSystemService;
import com.dillon.starsectormarines.battle.infantry.SmokeThrowCommit;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.mech.RotaryFeed;
import com.dillon.starsectormarines.battle.mech.MechFlux;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.turret.TurretMountDef;
import com.dillon.starsectormarines.battle.turret.TurretMountGeometry;
import com.dillon.starsectormarines.battle.ui.panel.Ordnance;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.vehicle.GroundVehicleTurretSystem;
import com.dillon.starsectormarines.battle.weapon.MagazineDef;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Read-only values for the exact controlled body; labels and selection belong to the HUD adapter. */
final class BattleDirectControlStatus {
    private BattleDirectControlStatus() { }

    enum Carrier { NONE, MARINE, MECH, VEHICLE, WARSHIP, TENDER }
    /** {@code ROTARY} fires while held, off a belt, at the rate its spin has reached. */
    enum FireKind { SINGLE, BURST, PACKET, INDIRECT, ROTARY }
    /** {@code SPINNING}: a rotary gun's barrels turning, its cycle fields carrying the spin. */
    enum WeaponState { BURST, RELOADING, CYCLING, LOADED, EMPTY, TOO_CLOSE, SPINNING }
    enum AmmoUnit { UNTRACKED, ROUNDS, TRIGGER_PACKS }

    record Snapshot(long entityId, String name, Carrier carrier, Durability durability,
                    List<WeaponStatus> weapons, List<AbilityStatus> abilities,
                    MarineSprint.Status sprint, MechFluxStatus flux, WarshipStatus warship) {
        static final Snapshot EMPTY = new Snapshot(0L, "", Carrier.NONE,
                new Durability(0f, 0f, 0f, 0f, 0f), List.of());

        Snapshot {
            name = name == null ? "" : name;
            weapons = List.copyOf(weapons);
            abilities = List.copyOf(abilities);
            sprint = sprint == null ? MarineSprint.EMPTY : sprint;
        }

        Snapshot(long entityId, String name, Carrier carrier, Durability durability,
                 List<WeaponStatus> weapons, List<AbilityStatus> abilities, MarineSprint.Status sprint,
                 MechFluxStatus flux) {
            this(entityId, name, carrier, durability, weapons, abilities, sprint, flux, null);
        }

        Snapshot(long entityId, String name, Carrier carrier, Durability durability,
                 List<WeaponStatus> weapons, List<AbilityStatus> abilities, MarineSprint.Status sprint) {
            this(entityId, name, carrier, durability, weapons, abilities, sprint, null, null);
        }

        Snapshot(long entityId, String name, Carrier carrier, Durability durability,
                 List<WeaponStatus> weapons, List<AbilityStatus> abilities) {
            this(entityId, name, carrier, durability, weapons, abilities, MarineSprint.EMPTY);
        }

        Snapshot(long entityId, String name, Carrier carrier, Durability durability,
                 List<WeaponStatus> weapons) {
            this(entityId, name, carrier, durability, weapons, List.of());
        }
    }

    record MechFluxStatus(float value, boolean venting, float capacity) {
        MechFluxStatus(float value, boolean venting) { this(value, venting, MechFlux.CAPACITY); }
    }

    /** How a status card reads at a glance: ready, busy, or something the pilot should look at. */
    enum Tone { READY, BUSY, WARN }

    /**
     * One card in a warship's equipment strip: a battery, the shield, or the ship's own
     * automation. {@code fill} is its readiness or level, 0 to 1.
     */
    record ShipCard(String key, String name, String detail, String resource, String state,
                    Tone tone, float fill) { }

    /** The flux gauge: absent when the ship is not on flux. */
    record FluxStatus(boolean limited, float fraction, String label, String state, Tone tone) { }

    /** What a pilot reads of the hull beyond its two durability lines. */
    record WarshipStatus(List<ShipCard> cards, FluxStatus flux) {
        WarshipStatus {
            cards = List.copyOf(cards);
        }
    }

    /**
     * A broken screen is a durability fact; the suit's remaining boost is not shield uptime. {@code picked} is
     * the Marine's right-button pick, the item the right button uses.
     */
    record AbilityStatus(DirectControlAbility ability, String name, boolean ready,
                         float activeSeconds, float durationSeconds,
                         float cooldownSeconds, float cooldownDurationSeconds,
                         int remaining, int capacity, float soakRemaining, float soakCapacity,
                         boolean broken, float facingDegrees, float arcDegrees, boolean picked) {
        AbilityStatus(DirectControlAbility ability, String name, boolean ready,
                      float activeSeconds, float durationSeconds,
                      float cooldownSeconds, float cooldownDurationSeconds,
                      int remaining, int capacity, float soakRemaining, float soakCapacity,
                      boolean broken, float facingDegrees, float arcDegrees) {
            this(ability, name, ready, activeSeconds, durationSeconds, cooldownSeconds, cooldownDurationSeconds,
                    remaining, capacity, soakRemaining, soakCapacity, broken, facingDegrees, arcDegrees, false);
        }

        AbilityStatus withPicked(boolean on) {
            return new AbilityStatus(ability, name, ready, activeSeconds, durationSeconds, cooldownSeconds,
                    cooldownDurationSeconds, remaining, capacity, soakRemaining, soakCapacity, broken,
                    facingDegrees, arcDegrees, on);
        }

        AbilityStatus(DirectControlAbility ability, String name, boolean ready,
                      float activeSeconds, float durationSeconds,
                      float cooldownSeconds, float cooldownDurationSeconds,
                      int remaining, int capacity, float soakRemaining, float soakCapacity) {
            this(ability, name, ready, activeSeconds, durationSeconds,
                    cooldownSeconds, cooldownDurationSeconds, remaining, capacity,
                    soakRemaining, soakCapacity, false, 0f, 0f);
        }
    }

    record Durability(float hp, float maxHp, float armor, float maxArmor, float armorRating) {
        float hpFraction() { return fraction(hp, maxHp); }
        float armorFraction() { return fraction(armor, maxArmor); }
        boolean hasArmor() { return maxArmor > 0f; }
    }

    /**
     * Scheduled releases and simultaneous packets are separate authored dimensions. {@code trigger} is a
     * magazine-fed primary's trigger mode, {@code null} for a weapon that only cycles. {@code ordnance}
     * is what it throws, which decides how its load is drawn.
     */
    record Behavior(FireKind kind, int projectilesPerTrigger, float burstSpacingSeconds,
                    int projectilesPerShot, MagazineDef.Trigger trigger, Ordnance ordnance) {
        Behavior(FireKind kind, int projectilesPerTrigger, float burstSpacingSeconds,
                 int projectilesPerShot, MagazineDef.Trigger trigger) {
            this(kind, projectilesPerTrigger, burstSpacingSeconds, projectilesPerShot, trigger, Ordnance.BALLISTIC);
        }
        Behavior(FireKind kind, int projectilesPerTrigger, float burstSpacingSeconds, int projectilesPerShot) {
            this(kind, projectilesPerTrigger, burstSpacingSeconds, projectilesPerShot, null);
        }
    }

    /** UNTRACKED is absence of an ammunition model, never an unlimited magazine. */
    record Ammo(int remaining, int capacity, AmmoUnit unit, boolean unlimited) { }

    /**
     * {@code trigger} names the mouse button that fires this weapon under its trigger's current pick:
     * {@code L}, {@code R}, or empty when none does. {@code group} is the button whose list it is in,
     * picked or not ({@code L}, {@code R}, or empty); empty for a status built without groups, which
     * reads its group off {@code trigger}. {@code tooClose} is a cursor inside the mount's manual minimum range, where
     * its trigger is refused. {@code reloading} is a magazine being changed, in which case the cooldown
     * and cycle fields carry the reload clock instead of the action's. {@code heldRounds} is what a held rack
     * trigger has fired so far in this hold ({@code SalvoChain}), zero when its button is up.
     */
    record WeaponStatus(int number, String slotKey, String name, Behavior behavior,
                        boolean directEligible, float cooldownSeconds, float cycleDurationSeconds,
                        int burstRemaining, float burstSeconds, Ammo ammo,
                        boolean fluxBlocked, String mechanism, String trigger, boolean tooClose,
                        boolean reloading, String group, int heldRounds) {
        WeaponStatus(int number, String slotKey, String name, Behavior behavior,
                     boolean directEligible, float cooldownSeconds, float cycleDurationSeconds,
                     int burstRemaining, float burstSeconds, Ammo ammo,
                     boolean fluxBlocked, String mechanism, String trigger, boolean tooClose,
                     boolean reloading, String group) {
            this(number, slotKey, name, behavior, directEligible, cooldownSeconds, cycleDurationSeconds,
                    burstRemaining, burstSeconds, ammo, fluxBlocked, mechanism, trigger, tooClose, reloading, group, 0);
        }
        WeaponStatus(int number, String slotKey, String name, Behavior behavior,
                     boolean directEligible, float cooldownSeconds, float cycleDurationSeconds,
                     int burstRemaining, float burstSeconds, Ammo ammo,
                     boolean fluxBlocked, String mechanism, String trigger, boolean tooClose,
                     boolean reloading) {
            this(number, slotKey, name, behavior, directEligible, cooldownSeconds, cycleDurationSeconds,
                    burstRemaining, burstSeconds, ammo, fluxBlocked, mechanism, trigger, tooClose, reloading, "");
        }
        WeaponStatus(int number, String slotKey, String name, Behavior behavior,
                     boolean directEligible, float cooldownSeconds, float cycleDurationSeconds,
                     int burstRemaining, float burstSeconds, Ammo ammo,
                     boolean fluxBlocked, String mechanism, String trigger, boolean tooClose) {
            this(number, slotKey, name, behavior, directEligible, cooldownSeconds, cycleDurationSeconds,
                    burstRemaining, burstSeconds, ammo, fluxBlocked, mechanism, trigger, tooClose, false);
        }
        WeaponStatus(int number, String slotKey, String name, Behavior behavior,
                     boolean directEligible, float cooldownSeconds, float cycleDurationSeconds,
                     int burstRemaining, float burstSeconds, Ammo ammo,
                     boolean fluxBlocked, String mechanism, String trigger) {
            this(number, slotKey, name, behavior, directEligible, cooldownSeconds,
                    cycleDurationSeconds, burstRemaining, burstSeconds, ammo, fluxBlocked, mechanism, trigger, false);
        }
        WeaponStatus(int number, String slotKey, String name, Behavior behavior,
                     boolean directEligible, float cooldownSeconds, float cycleDurationSeconds,
                     int burstRemaining, float burstSeconds, Ammo ammo) {
            this(number, slotKey, name, behavior, directEligible, cooldownSeconds,
                    cycleDurationSeconds, burstRemaining, burstSeconds, ammo, false, "", "");
        }
        WeaponStatus(int number, String slotKey, String name, Behavior behavior,
                     boolean directEligible, float cooldownSeconds, float cycleDurationSeconds,
                     int burstRemaining, float burstSeconds, Ammo ammo,
                     boolean fluxBlocked, String mechanism) {
            this(number, slotKey, name, behavior, directEligible, cooldownSeconds,
                    cycleDurationSeconds, burstRemaining, burstSeconds, ammo, fluxBlocked, mechanism, "");
        }
        WeaponState state() {
            if (burstRemaining > 0) return WeaponState.BURST;
            if (reloading) return WeaponState.RELOADING;
            if (ammo.unit() != AmmoUnit.UNTRACKED && !ammo.unlimited() && ammo.remaining() <= 0) {
                return WeaponState.EMPTY;
            }
            if (tooClose) return WeaponState.TOO_CLOSE;
            if (behavior.kind() == FireKind.ROTARY) {
                return cycleFraction() > 0f ? WeaponState.SPINNING : WeaponState.LOADED;
            }
            return cooldownSeconds > 0f ? WeaponState.CYCLING : WeaponState.LOADED;
        }

        /** The button whose list this weapon is in, read off its trigger when the status carries no group. */
        String listedOn() { return group.isEmpty() ? trigger : group; }

        /** Clock completion only: pose, alignment and the actual muzzle gate still decide a shot. */
        float cycleFraction() { return 1f - fraction(cooldownSeconds, cycleDurationSeconds); }
    }

    static Snapshot snapshot(BattleSimulation sim) {
        if (sim == null || !sim.directControl().active()) return Snapshot.EMPTY;
        if (sim.directControl().controlledTender() != null) {
            return BattleDirectTenderStatus.snapshot(sim.directControl());
        }
        if (sim.directControl().warshipControl()) {
            return BattleDirectWarshipStatus.snapshot(sim, sim.directControl().controlledWarship());
        }
        long id = sim.directControl().activeUnitId();
        World world = sim.world();
        if (!world.isAlive(id)) return Snapshot.EMPTY;
        Durability durability = new Durability(world.hp(id), world.maxHp(id),
                world.hasArmor(id) ? world.armor(id) : 0f,
                world.hasArmor(id) ? world.maxArmor(id) : 0f,
                world.hasArmor(id) ? world.armorRating(id) : 0f);
        String name = sim.identity().name(id);
        if (sim.directControl().controlledVehicleId() == id) {
            var turret = sim.convoy().turret(id);
            var type = sim.convoy().vehicleType(id);
            var structure = type.turretStructure();
            List<WeaponStatus> weapons = turret != null && structure != null && !turret.destroyed()
                    ? List.of(vehicleWeapon(turret, structure.mount,
                            vehicleAimDistance(sim, GroundVehicleTurretSystem.mountPoint(type, sim.convoy().body(id)))))
                    : List.of();
            return new Snapshot(id, name, Carrier.VEHICLE, durability, weapons);
        }
        if (world.hasMechLoadout(id)) {
            return new Snapshot(id, name, Carrier.MECH, durability,
                    mechWeapons(world.mechLoadout(id).mounts(), world.mechLoadout(id).flux,
                    sim.directControl().mechPicks(), aimDistance(sim, id)), List.of(),
                    MarineSprint.EMPTY, new MechFluxStatus(world.mechLoadout(id).flux.value(),
                    world.mechLoadout(id).flux.venting(),
                    world.mechLoadout(id).flux.capacity()));
        }
        var combat = sim.combat();
        WeaponDef primary = combat.has(id) ? combat.primaryWeaponDef(id) : null;
        List<WeaponStatus> weapons = primary == null ? List.of()
                : List.of(marineWeapon(primary, combat.equipmentGrade(id), combat.cooldownTimer(id),
                combat.attackCooldown(id), combat.burstRemaining(id), combat.burstTimer(id),
                combat.roundsLoaded(id), combat.reloadTimer(id), combat.reloadSeconds(id)));
        return new Snapshot(id, name, Carrier.MARINE, durability, weapons,
                marineAbilities(sim.getRoster(), id, sim.directControl()::canUseAbility,
                        sim.directControl().marineRightPick(), sim.directControl().manualThrowActive()),
                sim.directControl().sprintStatus());
    }

    static List<AbilityStatus> marineAbilities(UnitRosterService roster, long id,
                                              Predicate<DirectControlAbility> available) {
        return marineAbilities(roster, id, available, null, false);
    }

    /**
     * The Marine's right-button items in list order (smoke, a hand grenade, the shield), {@code pick} marked.
     * {@code manualThrow} is a grenade channel the player started, whose clock the grenade row shows.
     */
    static List<AbilityStatus> marineAbilities(UnitRosterService roster, long id,
                                              Predicate<DirectControlAbility> available,
                                              MarineRightTrigger.Item pick, boolean manualThrow) {
        World world = roster.world();
        if (!world.isAlive(id) || roster.identity().type(id) != UnitType.MARINE) return List.of();
        IntegralSystemDef system = roster.integralSystems().spec(id);
        SpecialEquipmentDef special = world.hasSecondaryWeapon(id) ? world.specialEquipment(id) : null;
        List<AbilityStatus> abilities = new ArrayList<>(3);
        for (MarineRightTrigger.Item item : MarineRightTrigger.members(special, system)) {
            AbilityStatus status = switch (item) {
                case SMOKE -> smokeAbility(special, world.secondaryAmmo(id), world.smokeThrowCommit(id),
                        world.secondaryActionTimer(id), available.test(DirectControlAbility.SMOKE));
                case GRENADE -> grenadeAbility(special, world.secondaryAmmo(id), manualThrow,
                        world.secondaryActionTimer(id), available.test(DirectControlAbility.GRENADE));
                case SHIELD -> shieldAbility(system, roster.integralSystems(), roster.mitigations(), id,
                        available.test(DirectControlAbility.SHIELD));
            };
            abilities.add(status.withPicked(item == pick));
        }
        return List.copyOf(abilities);
    }

    static AbilityStatus shieldAbility(IntegralSystemDef def, IntegralSystemService systems,
                                       MitigationService mitigations, long id, boolean ready) {
        return new AbilityStatus(DirectControlAbility.SHIELD, def.displayName(), ready,
                mitigations.isActive(id) ? mitigations.remaining(id) : 0f, def.durationSeconds(),
                systems.cooldownRemaining(id), def.cooldownSeconds(),
                def.usesAmmunition() ? systems.ammo(id) : -1,
                def.usesAmmunition() ? def.startingAmmo() : -1,
                mitigations.soakRemaining(id), mitigations.soakCapacity(id),
                mitigations.breakFlashRemaining(id) > 0f,
                mitigations.facingDegrees(id), mitigations.arcDegrees(id));
    }

    static AbilityStatus smokeAbility(SpecialEquipmentDef def, int ammo, SmokeThrowCommit commit,
                                      float actionRemaining, boolean ready) {
        float active = commit != null && commit.manual() ? Math.max(0f, actionRemaining) : 0f;
        return new AbilityStatus(DirectControlAbility.SMOKE, def.displayName(), ready,
                active, def.smokeGrenadeSpec().throwDuration(), 0f, 0f,
                ammo, def.startingAmmo(), 0f, 0f);
    }

    /**
     * A hand grenade, named by its model ("Shattercap", "Firebrand"): grenades left of those issued, and a throw
     * the player started on its clock.
     */
    static AbilityStatus grenadeAbility(SpecialEquipmentDef def, int ammo, boolean manualThrow,
                                        float actionRemaining, boolean ready) {
        float active = manualThrow ? Math.max(0f, actionRemaining) : 0f;
        return new AbilityStatus(DirectControlAbility.GRENADE, def.weaponDef().modelName(), ready,
                active, def.aimDuration(), 0f, 0f, ammo, def.startingAmmo(), 0f, 0f);
    }

    static WeaponStatus marineWeapon(WeaponDef weapon, EquipmentGrade grade,
                                     float cooldown, float cycleDuration, int burstRemaining,
                                     float burstTimer) {
        return marineWeapon(weapon, grade, cooldown, cycleDuration, burstRemaining, burstTimer,
                weapon.magazine() != null ? weapon.magazine().size(grade) : 0, 0f, 0f);
    }

    /** A magazine-fed primary counts its rounds and shows its reload on the cycle bar. */
    static WeaponStatus marineWeapon(WeaponDef weapon, EquipmentGrade grade,
                                     float cooldown, float cycleDuration, int burstRemaining,
                                     float burstTimer, int roundsLoaded, float reloadRemaining,
                                     float reloadDuration) {
        boolean direct = PointFireAim.supports(weapon) && Float.isFinite(weapon.range) && weapon.range > 0f;
        if (weapon.magazine() == null) {
            return new WeaponStatus(1, "PRIMARY", weapon.catalogName(grade),
                    behavior(weapon, weapon.burstCount), direct,
                    cooldown, cycleDuration, burstRemaining, burstTimer,
                    new Ammo(0, 0, AmmoUnit.UNTRACKED, false));
        }
        boolean reloading = reloadRemaining > 0f;
        Ammo ammo = new Ammo(roundsLoaded, weapon.magazine().size(grade), AmmoUnit.ROUNDS, false);
        return new WeaponStatus(1, "PRIMARY", weapon.catalogName(grade),
                behavior(weapon, weapon.burstCount), direct,
                reloading ? reloadRemaining : cooldown, reloading ? reloadDuration : cycleDuration,
                burstRemaining, burstTimer, ammo, false, "", "", false, reloading);
    }

    static List<WeaponStatus> mechWeapons(MechWeaponMount[] mounts) {
        return mechWeapons(mounts, new MechFlux());
    }

    static List<WeaponStatus> mechWeapons(MechWeaponMount[] mounts, MechFlux flux) {
        return mechWeapons(mounts, flux, MechTriggerPicks.LINKED);
    }

    static List<WeaponStatus> mechWeapons(MechWeaponMount[] mounts, MechFlux flux, MechTriggerPicks picks) {
        return mechWeapons(mounts, flux, picks, Float.NaN);
    }

    /** The cursor's distance from the chassis centre, or NaN without a finite aim. */
    private static float aimDistance(BattleSimulation sim, long id) {
        var intent = sim.directControl().intent();
        if (!Float.isFinite(intent.aimX()) || !Float.isFinite(intent.aimY())) return Float.NaN;
        return (float) Math.hypot(intent.aimX() - sim.world().renderX(id), intent.aimY() - sim.world().renderY(id));
    }

    /** {@code aimDistance} is the cursor's distance from the chassis centre; NaN reads as no cursor. */
    static List<WeaponStatus> mechWeapons(MechWeaponMount[] mounts, MechFlux flux, MechTriggerPicks picks,
                                          float aimDistance) {
        List<WeaponStatus> weapons = new ArrayList<>();
        for (MechWeaponMount mount : mounts) {
            if (mount == null) continue;
            WeaponDef weapon = mount.weaponDef();
            if (mount.isRotary()) {
                weapons.add(rotaryWeapon(mount, mounts, flux, picks, aimDistance));
                continue;
            }
            weapons.add(new WeaponStatus(mount.slot.ordinal() + 1, mount.slot.name(),
                    mount.component.displayName(), behavior(weapon, mount.component.projectilesPerTrigger,
                            Ordnance.of(mount.component.hardpointType)),
                    HeavyWeapons.supportsPointFire(mount), mount.cycleRemaining, mount.component.cycleSeconds(),
                    mount.burstRemaining, mount.burstTimer,
                    new Ammo(mount.ammo, mount.component.ammoCapacity, AmmoUnit.TRIGGER_PACKS,
                            mount.component.ammoCapacity < 0),
                    !flux.permits(mount.triggerFlux(), false), mount.component.cycleLabel(),
                    MechFireGroups.badge(mounts, picks, mount),
                    HeavyWeapons.tooClose(mount, aimDistance), false, MechFireGroups.group(mounts, mount),
                    mount.holdSalvos * mount.component.projectilesPerTrigger));
        }
        weapons.sort(Comparator.comparingInt(WeaponStatus::number));
        return List.copyOf(weapons);
    }

    /** Rounds per block of a rotary gun's belt strip: what the folded belt is read in. */
    static final int BELT_BLOCK_ROUNDS = 10;

    /**
     * A rotary mount: its belt as rounds, its spin as the cycle (the cooldown is the spin still to come, so
     * the row's bar fills as the barrels come up to speed), and the flux of one round as its hold.
     */
    private static WeaponStatus rotaryWeapon(MechWeaponMount mount, MechWeaponMount[] mounts, MechFlux flux,
                                             MechTriggerPicks picks, float aimDistance) {
        RotaryFeed feed = mount.rotary;
        WeaponDef weapon = mount.weaponDef();
        float spinUp = feed.def().spinUpSeconds();
        return new WeaponStatus(mount.slot.ordinal() + 1, mount.slot.name(), mount.component.displayName(),
                new Behavior(FireKind.ROTARY, BELT_BLOCK_ROUNDS, 0f, weapon.projectilesPerShot, null,
                        Ordnance.of(mount.component.hardpointType)),
                HeavyWeapons.supportsPointFire(mount), (1f - feed.spin()) * spinUp, spinUp, 0, 0f,
                new Ammo(feed.beltRounds(), feed.def().belt(), AmmoUnit.ROUNDS, false),
                !flux.permits(mount.component.fluxPerRound(), false), mount.component.cycleLabel(),
                MechFireGroups.badge(mounts, picks, mount),
                HeavyWeapons.tooClose(mount, aimDistance), false, MechFireGroups.group(mounts, mount));
    }

    static WeaponStatus vehicleWeapon(GroundTurret turret, TurretMountDef mount) {
        return vehicleWeapon(turret, mount, Float.NaN);
    }

    /** The cursor's distance from the turret ring, or NaN without a finite aim. */
    private static float vehicleAimDistance(BattleSimulation sim, TurretMountGeometry.Point ring) {
        var intent = sim.directControl().intent();
        if (!Float.isFinite(intent.aimX()) || !Float.isFinite(intent.aimY())) return Float.NaN;
        return (float) Math.hypot(intent.aimX() - ring.x(), intent.aimY() - ring.y());
    }

    /**
     * {@code aimDistance} is the cursor's distance from the turret ring, which is where the turret's point fire
     * measures its minimum from; NaN reads as no cursor. Eligibility and the refusal are {@link ManualPointFire}'s,
     * the same rules the turret fires by.
     */
    static WeaponStatus vehicleWeapon(GroundTurret turret, TurretMountDef mount, float aimDistance) {
        WeaponDef weapon = mount.weapon;
        return new WeaponStatus(1, "TURRET", weapon.displayName(), behavior(weapon, weapon.burstCount),
                ManualPointFire.supportsPointFire(weapon),
                turret.cooldownTimer, weapon.cooldown, turret.burstRemaining, turret.burstTimer,
                new Ammo(turret.ammo, mount.ammoCapacity, AmmoUnit.ROUNDS, mount.ammoCapacity == 0),
                false, "", "", ManualPointFire.tooClose(weapon, aimDistance), false, "", turret.holdRounds);
    }

    private static Behavior behavior(WeaponDef weapon, int releasesPerTrigger) {
        return behavior(weapon, releasesPerTrigger, Ordnance.of(weapon));
    }

    private static Behavior behavior(WeaponDef weapon, int releasesPerTrigger, Ordnance ordnance) {
        FireKind kind = weapon.indirectFire || weapon.arcHeight > 0f ? FireKind.INDIRECT
                : releasesPerTrigger > 1 ? FireKind.BURST
                : weapon.projectilesPerShot > 1 ? FireKind.PACKET : FireKind.SINGLE;
        return new Behavior(kind, releasesPerTrigger, weapon.burstSpacing, weapon.projectilesPerShot,
                weapon.magazine() != null ? weapon.magazine().trigger() : null, ordnance);
    }

    private static float fraction(float value, float maximum) {
        if (!Float.isFinite(value) || !Float.isFinite(maximum) || maximum <= 0f) return 0f;
        return Math.max(0f, Math.min(1f, value / maximum));
    }
}

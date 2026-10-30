package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleKit;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Atomic campaign authority for Mech Lab fabrication and fitting. Building anything is paid in
 * credits and needs a civilized port ({@link Status#NO_MARKET}); fitting, stripping, moving and
 * recycling what is already in the bay work anywhere.
 */
public final class MechWorkshop {

    private final MechBay bay;
    private final FabricationResources resources;
    private final IntSupplier workingBerths;
    private final Supplier<MechFieldRepair> fieldRepair;

    public MechWorkshop(MechBay bay, FabricationResources resources) {
        this(bay, resources, () -> CampaignMechSquad.CAPACITY);
    }

    /** The host supplies its current physical capacity; a command rechecks it before spending. */
    public MechWorkshop(MechBay bay, FabricationResources resources, IntSupplier workingBerths) {
        this(bay, resources, workingBerths, () -> MechFieldRepair.NEUTRAL);
    }

    /** {@code fieldRepair} is the ship's field-repair refit, read whenever an order is quoted. */
    public MechWorkshop(MechBay bay, FabricationResources resources, IntSupplier workingBerths,
                        Supplier<MechFieldRepair> fieldRepair) {
        if (bay == null || resources == null || workingBerths == null || fieldRepair == null) {
            throw new IllegalArgumentException("mech bay and fabrication resources are required");
        }
        this.bay = bay;
        this.resources = resources;
        this.workingBerths = workingBerths;
        this.fieldRepair = fieldRepair;
    }

    /** What fabricating one {@code component} costs now: the workshop's quote with the spares rack's share off. */
    public FabricationCost weaponQuote(MechWeaponComponent component) {
        MechFabricationCatalog.Recipe recipe = MechFabricationCatalog.weapon(component);
        return recipe == null ? null : resources.quote(fieldRepair.get().weaponBill(recipe.cost()));
    }

    public Result fitWeapon(String mechId, MechMountSlot slot,
                            MechWeaponComponent component) {
        CampaignMech mech = bay.mechById(mechId);
        if (mech == null || slot == null || component == null) return Result.of(Status.INVALID);
        if (mech.weaponAt(slot) == component) return Result.of(Status.ALREADY_INSTALLED);
        if (!bay.canInstallWeapon(mechId, slot, component.id)) {
            return Result.of(Status.INCOMPATIBLE);
        }
        if (!bay.withinBudget(mechId, mech.fit().withWeapon(slot, component))) {
            return Result.of(Status.OVER_BUDGET);
        }
        if (bay.availableWeapon(component.id) > 0) {
            return bay.installWeapon(mechId, slot, component.id)
                    ? Result.of(Status.INSTALLED_FROM_STORES) : Result.of(Status.INVALID);
        }
        if (!bay.canFabricate(component)) return Result.of(Status.PATTERN_LOCKED);
        if (!resources.atMarket()) return Result.of(Status.NO_MARKET);
        FabricationCost bill = weaponQuote(component);
        if (bill == null || !resources.spend(bill)) {
            return Result.of(Status.INSUFFICIENT_MATERIALS);
        }
        bay.addWeapon(component.id, 1);
        if (!bay.installWeapon(mechId, slot, component.id)) {
            throw new IllegalStateException("validated fabricated component could not be installed");
        }
        return Result.of(Status.FABRICATED_AND_INSTALLED);
    }

    /**
     * Fits one internal, from stores when a free assembly exists and otherwise fabricated
     * against its pattern. Socket, budget, pattern and cargo are all checked before anything
     * is spent; the outgoing part returns to stores.
     */
    public Result fitInternal(String mechId, MechInternal internal) {
        CampaignMech mech = bay.mechById(mechId);
        if (mech == null || internal == null) return Result.of(Status.INVALID);
        if (MechCompartment.limit(internal.role) == 1 && mech.internal(internal.role) == internal) {
            return Result.of(Status.ALREADY_INSTALLED);
        }
        if (!bay.canInstallInternal(mechId, internal)) return Result.of(Status.INCOMPATIBLE);
        if (!bay.withinBudget(mechId, mech.fit().withInternal(internal.role, internal))) {
            return Result.of(Status.OVER_BUDGET);
        }
        if (bay.availableInternal(internal.id) > 0) {
            return bay.installInternal(mechId, internal)
                    ? Result.of(Status.INSTALLED_FROM_STORES) : Result.of(Status.INVALID);
        }
        if (!bay.canFabricate(internal)) return Result.of(Status.PATTERN_LOCKED);
        if (!resources.atMarket()) return Result.of(Status.NO_MARKET);
        MechFabricationCatalog.Recipe recipe = MechFabricationCatalog.internal(internal);
        if (recipe == null || !resources.spend(resources.quote(recipe.cost()))) {
            return Result.of(Status.INSUFFICIENT_MATERIALS);
        }
        bay.addInternal(internal.id, 1);
        if (!bay.installInternal(mechId, internal)) {
            throw new IllegalStateException("validated fabricated internal could not be installed");
        }
        return Result.of(Status.FABRICATED_AND_INSTALLED);
    }

    /**
     * Builds one weapon assembly into the bay's stores against its known pattern. Nothing is
     * fitted: the player drags the new spare onto a mount.
     */
    public Result fabricateWeapon(MechWeaponComponent component) {
        if (component == null) return Result.of(Status.INVALID);
        if (!bay.canFabricate(component)) return Result.of(Status.PATTERN_LOCKED);
        if (!resources.atMarket()) return Result.of(Status.NO_MARKET);
        FabricationCost bill = weaponQuote(component);
        if (bill == null || !resources.spend(bill)) return Result.of(Status.INSUFFICIENT_MATERIALS);
        bay.addWeapon(component.id, 1);
        return Result.of(Status.FABRICATED);
    }

    /** Builds one internal into the bay's stores against its known pattern. */
    public Result fabricateInternal(MechInternal internal) {
        if (internal == null) return Result.of(Status.INVALID);
        if (!bay.canFabricate(internal)) return Result.of(Status.PATTERN_LOCKED);
        if (!resources.atMarket()) return Result.of(Status.NO_MARKET);
        MechFabricationCatalog.Recipe recipe = MechFabricationCatalog.internal(internal);
        if (recipe == null || !resources.spend(resources.quote(recipe.cost()))) {
            return Result.of(Status.INSUFFICIENT_MATERIALS);
        }
        bay.addInternal(internal.id, 1);
        return Result.of(Status.FABRICATED);
    }

    /** Builds one vehicle kit into the bay's stores against its known pattern. */
    public Result fabricateKit(VehicleKit kit) {
        if (kit == null) return Result.of(Status.INVALID);
        if (!bay.canFabricate(kit)) return Result.of(Status.PATTERN_LOCKED);
        if (!resources.atMarket()) return Result.of(Status.NO_MARKET);
        MechFabricationCatalog.Recipe recipe = MechFabricationCatalog.kit(kit);
        if (recipe == null || !resources.spend(resources.quote(recipe.cost()))) {
            return Result.of(Status.INSUFFICIENT_MATERIALS);
        }
        bay.addKit(kit.id, 1);
        return Result.of(Status.FABRICATED);
    }

    /** Puts a spare kit on a vehicle's rack. Kits fit from stores only; building one is its own step. */
    public Result fitKit(String vehicleId, VehicleKit kit) {
        CampaignVehicle vehicle = bay.vehicleById(vehicleId);
        if (vehicle == null || kit == null) return Result.of(Status.INVALID);
        if (vehicle.kits().contains(kit)) return Result.of(Status.ALREADY_INSTALLED);
        if (!vehicle.canFit(kit)) return Result.of(Status.INCOMPATIBLE);
        if (bay.availableKit(kit.id) <= 0) return Result.of(Status.INSUFFICIENT_MATERIALS);
        return bay.fitKit(vehicleId, kit) ? new Result(Status.INSTALLED_FROM_STORES, null, vehicle)
                : Result.of(Status.INVALID);
    }

    /** Takes a kit off a vehicle's rack back to stores. */
    public Result stripKit(String vehicleId, VehicleKit kit) {
        CampaignVehicle vehicle = bay.vehicleById(vehicleId);
        if (vehicle == null || kit == null) return Result.of(Status.INVALID);
        if (!vehicle.kits().contains(kit)) return Result.of(Status.ALREADY_STRIPPED);
        return bay.stripKit(vehicleId, kit) ? new Result(Status.STRIPPED, null, vehicle) : Result.of(Status.INVALID);
    }

    /**
     * Puts a capacitor bank or vent array on a vehicle's rack from stores, as on a mech's compartment: a bank goes in
     * place of the one fitted, which goes back to stores.
     */
    public Result fitRackPart(String vehicleId, MechInternal part) {
        CampaignVehicle vehicle = bay.vehicleById(vehicleId);
        if (vehicle == null || part == null) return Result.of(Status.INVALID);
        if (part.role == MechInternal.Role.CAPACITOR_BANK && vehicle.rackInternals().contains(part)) {
            return Result.of(Status.ALREADY_INSTALLED);
        }
        if (!vehicle.canFitRack(part)) return Result.of(Status.INCOMPATIBLE);
        if (bay.availableInternal(part.id) <= 0) return Result.of(Status.INSUFFICIENT_MATERIALS);
        return bay.fitRackPart(vehicleId, part) ? new Result(Status.INSTALLED_FROM_STORES, null, vehicle)
                : Result.of(Status.INVALID);
    }

    /** Takes one bank or vent array off a vehicle's rack back to stores; without a bank it cannot drop. */
    public Result stripRackPart(String vehicleId, MechInternal part) {
        CampaignVehicle vehicle = bay.vehicleById(vehicleId);
        if (vehicle == null || part == null) return Result.of(Status.INVALID);
        if (!vehicle.rackInternals().contains(part)) return Result.of(Status.ALREADY_STRIPPED);
        return bay.stripRackPart(vehicleId, part) ? new Result(Status.STRIPPED, null, vehicle)
                : Result.of(Status.INVALID);
    }

    /**
     * Puts a plating part at a vehicle's armor point from stores, as a kit is fitted: building one is its own
     * step. The plate it replaces goes back to stores.
     */
    public Result fitPlating(String vehicleId, MechInternal plating) {
        CampaignVehicle vehicle = bay.vehicleById(vehicleId);
        if (vehicle == null || plating == null) return Result.of(Status.INVALID);
        if (vehicle.plating() == plating) return Result.of(Status.ALREADY_INSTALLED);
        if (!bay.canFitPlating(vehicleId, plating)) return Result.of(Status.INCOMPATIBLE);
        if (bay.availableInternal(plating.id) <= 0) return Result.of(Status.INSUFFICIENT_MATERIALS);
        return bay.fitPlating(vehicleId, plating) ? new Result(Status.INSTALLED_FROM_STORES, null, vehicle)
                : Result.of(Status.INVALID);
    }

    /** Takes the plating off a vehicle's armor point back to stores. */
    public Result stripPlating(String vehicleId) {
        CampaignVehicle vehicle = bay.vehicleById(vehicleId);
        if (vehicle == null) return Result.of(Status.INVALID);
        if (vehicle.plating() == null) return Result.of(Status.ALREADY_STRIPPED);
        return bay.stripPlating(vehicleId) ? new Result(Status.STRIPPED, null, vehicle) : Result.of(Status.INVALID);
    }

    /**
     * Puts an engine at a vehicle's engine core from stores, as plating is fitted: building one is its own
     * step. The engine it replaces goes back to stores.
     */
    public Result fitEngine(String vehicleId, MechInternal engine) {
        CampaignVehicle vehicle = bay.vehicleById(vehicleId);
        if (vehicle == null || engine == null) return Result.of(Status.INVALID);
        if (vehicle.engine() == engine) return Result.of(Status.ALREADY_INSTALLED);
        if (!bay.canFitEngine(vehicleId, engine)) return Result.of(Status.INCOMPATIBLE);
        if (bay.availableInternal(engine.id) <= 0) return Result.of(Status.INSUFFICIENT_MATERIALS);
        return bay.fitEngine(vehicleId, engine) ? new Result(Status.INSTALLED_FROM_STORES, null, vehicle)
                : Result.of(Status.INVALID);
    }

    /** Takes the engine off a vehicle's engine core back to stores. */
    public Result stripEngine(String vehicleId) {
        CampaignVehicle vehicle = bay.vehicleById(vehicleId);
        if (vehicle == null) return Result.of(Status.INVALID);
        if (vehicle.engine() == null) return Result.of(Status.ALREADY_STRIPPED);
        return bay.stripEngine(vehicleId) ? new Result(Status.STRIPPED, null, vehicle) : Result.of(Status.INVALID);
    }

    /**
     * Puts a weapon in a vehicle's gun card from stores, as a mech's weapon is fitted: building one is its own step.
     * The weapon it replaces goes back to stores.
     */
    public Result fitGun(String vehicleId, MechWeaponComponent gun) {
        CampaignVehicle vehicle = bay.vehicleById(vehicleId);
        if (vehicle == null || gun == null) return Result.of(Status.INVALID);
        if (vehicle.gun() == gun) return Result.of(Status.ALREADY_INSTALLED);
        if (!bay.canFitGun(vehicleId, gun)) return Result.of(Status.INCOMPATIBLE);
        if (bay.availableWeapon(gun.id) <= 0) return Result.of(Status.INSUFFICIENT_MATERIALS);
        return bay.fitGun(vehicleId, gun) ? new Result(Status.INSTALLED_FROM_STORES, null, vehicle)
                : Result.of(Status.INVALID);
    }

    /** Takes the weapon out of a vehicle's gun card back to stores. */
    public Result stripGun(String vehicleId) {
        CampaignVehicle vehicle = bay.vehicleById(vehicleId);
        if (vehicle == null) return Result.of(Status.INVALID);
        if (vehicle.gun() == null) return Result.of(Status.ALREADY_STRIPPED);
        return bay.stripGun(vehicleId) ? new Result(Status.STRIPPED, null, vehicle) : Result.of(Status.INVALID);
    }

    /** Takes the first vent array out of the compartment. */
    public Result stripVentArray(String mechId) {
        return stripInternal(mechId, MechInternal.Role.VENT_ARRAY);
    }

    /**
     * Empties one socketed internal back to stores. Stripping an engine or capacitor bank is
     * allowed and leaves the mech unable to drop until one is fitted ({@link MechBay#stripInternal}).
     */
    public Result stripInternal(String mechId, MechInternal.Role role) {
        CampaignMech mech = bay.mechById(mechId);
        if (mech == null || role == null || role.replaceOnly()) return Result.of(Status.INVALID);
        if (mech.internal(role) == null) return Result.of(Status.ALREADY_STRIPPED);
        return bay.stripInternal(mechId, role) ? Result.of(Status.STRIPPED) : Result.of(Status.INVALID);
    }

    /** Takes one part out of the compartment back to stores. */
    public Result stripPart(String mechId, MechInternal part) {
        CampaignMech mech = bay.mechById(mechId);
        if (mech == null || part == null || !part.role.compartment()) return Result.of(Status.INVALID);
        if (!mech.compartment().contains(part)) return Result.of(Status.ALREADY_STRIPPED);
        return bay.stripPart(mechId, part) ? Result.of(Status.STRIPPED) : Result.of(Status.INVALID);
    }

    public Result stripWeapon(String mechId, MechMountSlot slot) {
        CampaignMech mech = bay.mechById(mechId);
        if (mech == null || slot == null) return Result.of(Status.INVALID);
        if (mech.weaponAt(slot) == null) return Result.of(Status.ALREADY_STRIPPED);
        return bay.stripWeapon(mechId, slot)
                ? Result.of(Status.STRIPPED) : Result.of(Status.INCOMPATIBLE);
    }

    public Result moveWeapon(String mechId, MechMountSlot source, MechMountSlot target) {
        CampaignMech mech = bay.mechById(mechId);
        if (mech == null || source == null || target == null || source == target
                || mech.weaponAt(source) == null) return Result.of(Status.INVALID);
        if (mech.weaponAt(target) != null) return Result.of(Status.TARGET_OCCUPIED);
        if (!bay.canInstallWeapon(mechId, target, mech.weaponAt(source).id)) {
            return Result.of(Status.INCOMPATIBLE);
        }
        return bay.moveWeapon(mechId, source, target)
                ? Result.of(Status.MOVED) : Result.of(Status.INVALID);
    }

    public Result fabricateChassis(String squadId, MechVariant variant) {
        if (variant == null || bay.squadById(squadId) == null) return Result.of(Status.INVALID);
        if (!bay.canFabricate(variant)) return Result.of(Status.PATTERN_LOCKED);
        if (!bay.canAddMech(squadId) || bay.squadById(squadId).occupied() >= workingBerths.getAsInt())
            return Result.of(Status.LANCE_FULL);
        if (!resources.atMarket()) return Result.of(Status.NO_MARKET);
        MechFabricationCatalog.Recipe recipe = MechFabricationCatalog.chassis(variant);
        if (recipe == null || !resources.spend(resources.quote(recipe.cost()))) {
            return Result.of(Status.INSUFFICIENT_MATERIALS);
        }
        CampaignMech mech = bay.fabricateFrame(squadId, variant);
        if (mech == null) {
            throw new IllegalStateException("validated chassis could not enter its vacant gantry");
        }
        return new Result(Status.CHASSIS_FABRICATED, mech);
    }

    /** Builds a vehicle onto a vacant gantry: the same berth and material rules as a chassis. */
    public Result fabricateVehicle(String squadId, VehicleType type) {
        if (type == null || bay.squadById(squadId) == null) return Result.of(Status.INVALID);
        if (!bay.canFabricate(type)) return Result.of(Status.PATTERN_LOCKED);
        if (!bay.canAddMech(squadId) || bay.squadById(squadId).occupied() >= workingBerths.getAsInt())
            return Result.of(Status.LANCE_FULL);
        if (!resources.atMarket()) return Result.of(Status.NO_MARKET);
        MechFabricationCatalog.Recipe recipe = MechFabricationCatalog.vehicle(type);
        if (recipe == null || !resources.spend(resources.quote(recipe.cost()))) {
            return Result.of(Status.INSUFFICIENT_MATERIALS);
        }
        CampaignVehicle vehicle = bay.fabricateVehicle(squadId, type);
        if (vehicle == null) {
            throw new IllegalStateException("validated vehicle could not enter its vacant gantry");
        }
        return new Result(Status.VEHICLE_FABRICATED, null, vehicle);
    }

    /** What recycling one spare {@code component} would put back now; null for none. */
    public FabricationCost weaponRecovery(MechWeaponComponent component) {
        return component == null ? null : resources.reclamation().recover(weaponQuote(component));
    }

    public FabricationCost internalRecovery(MechInternal internal) {
        MechFabricationCatalog.Recipe recipe = internal == null ? null : MechFabricationCatalog.internal(internal);
        return recipe == null ? null : resources.reclamation().recover(resources.quote(recipe.cost()));
    }

    public FabricationCost kitRecovery(VehicleKit kit) {
        MechFabricationCatalog.Recipe recipe = kit == null ? null : MechFabricationCatalog.kit(kit);
        return recipe == null ? null : resources.reclamation().recover(resources.quote(recipe.cost()));
    }

    /**
     * Breaks one spare weapon assembly in stores down for credits, at the machine
     * shop's share of what building one costs now. A fitted copy is never a spare.
     */
    public Result recycleWeapon(MechWeaponComponent component) {
        if (component == null) return Result.of(Status.INVALID);
        return recycle(bay.availableWeapon(component.id), weaponRecovery(component), () -> bay.takeWeapon(component.id));
    }

    public Result recycleInternal(MechInternal internal) {
        if (internal == null) return Result.of(Status.INVALID);
        return recycle(bay.availableInternal(internal.id), internalRecovery(internal), () -> bay.takeInternal(internal.id));
    }

    public Result recycleKit(VehicleKit kit) {
        if (kit == null) return Result.of(Status.INVALID);
        return recycle(bay.availableKit(kit.id), kitRecovery(kit), () -> bay.takeKit(kit.id));
    }

    /** The purse is paid before the spare leaves stores, so a fleet with no purse keeps it. */
    private Result recycle(int spares, FabricationCost back, BooleanSupplier take) {
        if (spares <= 0) return Result.of(Status.NO_SPARE);
        if (!resources.reclamation().works()) return Result.of(Status.WORKSHOP_IDLE);
        if (back == null) return Result.of(Status.NOTHING_TO_RECOVER);
        if (!resources.credit(back)) return Result.of(Status.INVALID);
        if (!take.getAsBoolean()) throw new IllegalStateException("counted spare could not leave stores");
        return Result.of(Status.RECYCLED);
    }

    /**
     * What scrapping {@code mechId} would return, or null when it returns nothing: the
     * machine shop's share of today's chassis quote, less the parts a bought frame comes with.
     * Those parts (its plating and any factory-locked gun) go back to stores as spares, so the
     * hull is not paid out for them a second time.
     */
    public FabricationCost mechSalvage(String mechId) {
        CampaignMech mech = bay.mechById(mechId);
        MechFabricationCatalog.Recipe recipe = mech == null ? null : MechFabricationCatalog.chassis(mech.variant());
        if (recipe == null) return null;
        List<FabricationCost> stock = new ArrayList<>();
        for (MechFabricationCatalog.Recipe part : frameParts(mech.variant())) stock.add(resources.quote(part.cost()));
        return resources.reclamation().recover(Reclamation.hull(resources.quote(recipe.cost()), stock));
    }

    /** What a bought frame of {@code variant} stands in its gantry with, as the recipes that build those parts. */
    private static List<MechFabricationCatalog.Recipe> frameParts(MechVariant variant) {
        // The same chassis MechBay.fabricateFrame builds, stripped the same way.
        CampaignMech frame = new CampaignMech("frame", "frame", variant, variant.defaultRole,
                MissileReplenisherComponent.STANDARD.id());
        frame.stripToFrame();
        List<MechFabricationCatalog.Recipe> parts = new ArrayList<>();
        for (MechMountSlot slot : MechMountSlot.values()) {
            MechFabricationCatalog.Recipe weapon = frame.weaponAt(slot) == null ? null
                    : MechFabricationCatalog.weapon(frame.weaponAt(slot));
            if (weapon != null) parts.add(weapon);
        }
        List<MechInternal> internals = new ArrayList<>(frame.compartment());
        internals.add(frame.internal(MechInternal.Role.ENGINE));
        internals.add(frame.internal(MechInternal.Role.PLATING));
        for (MechInternal internal : internals) {
            MechFabricationCatalog.Recipe recipe = internal == null ? null : MechFabricationCatalog.internal(internal);
            if (recipe != null) parts.add(recipe);
        }
        return parts;
    }

    /**
     * What scrapping {@code vehicleId} would return, or null when it returns nothing: the
     * machine shop's share of today's quote. A vehicle is bought whole with no kits, so its
     * whole price is hull.
     */
    public FabricationCost vehicleSalvage(String vehicleId) {
        CampaignVehicle vehicle = bay.vehicleById(vehicleId);
        MechFabricationCatalog.Recipe recipe = vehicle == null ? null : MechFabricationCatalog.vehicle(vehicle.type());
        return recipe == null ? null : resources.reclamation().recover(resources.quote(recipe.cost()));
    }

    /**
     * Scraps a chassis to free its gantry: fitted parts go back to stores and the machine
     * shop's share of the hull back to the purse. A shop that cannot recycle still frees the
     * gantry; it returns nothing.
     */
    public Result scrapMech(String mechId) {
        CampaignMech mech = bay.mechById(mechId);
        if (mech == null) return Result.of(Status.INVALID);
        FabricationCost salvage = mechSalvage(mechId);
        if (!bay.scrapMech(mechId)) return Result.of(Status.INVALID);
        if (salvage != null) resources.credit(salvage);
        return new Result(Status.SCRAPPED, mech);
    }

    /** Scraps a vehicle to free its gantry, the machine shop's share of its price back to the purse. */
    public Result scrapVehicle(String vehicleId) {
        CampaignVehicle vehicle = bay.vehicleById(vehicleId);
        if (vehicle == null) return Result.of(Status.INVALID);
        FabricationCost salvage = vehicleSalvage(vehicleId);
        if (!bay.scrapVehicle(vehicleId)) return Result.of(Status.INVALID);
        if (salvage != null) resources.credit(salvage);
        return new Result(Status.SCRAPPED, null, vehicle);
    }

    /** Starts the free repair clock on any damage still waiting for one (an older save's). */
    public void startPendingRepairs(float day) {
        bay.startPendingRepairs(day, fieldRepair.get());
    }

    public enum Status {
        ALREADY_INSTALLED,
        INSTALLED_FROM_STORES,
        FABRICATED_AND_INSTALLED,
        /** One part was built into stores; nothing was fitted. */
        FABRICATED,
        MOVED,
        TARGET_OCCUPIED,
        STRIPPED,
        ALREADY_STRIPPED,
        CHASSIS_FABRICATED,
        VEHICLE_FABRICATED,
        /** A machine was broken up; its gantry is vacant and its salvage is in stores. */
        SCRAPPED,
        /** One spare in stores was broken down for credits. */
        RECYCLED,
        NO_SPARE,
        /** The machine shop's machinery is not running, so nothing can be broken down. */
        WORKSHOP_IDLE,
        /** Too little of the price would come back to be worth a credit. */
        NOTHING_TO_RECOVER,
        INCOMPATIBLE,
        /** The fit would weigh more than the engine can carry. */
        OVER_BUDGET,
        /** The purse is short of the price. */
        INSUFFICIENT_MATERIALS,
        /** Buying needs the fleet docked at a civilized market, and it is not. */
        NO_MARKET,
        LANCE_FULL,
        PATTERN_LOCKED,
        INVALID
    }

    public record Result(Status status, CampaignMech mech, CampaignVehicle vehicle) {
        public Result(Status status, CampaignMech mech) { this(status, mech, null); }
        private static Result of(Status status) { return new Result(status, null, null); }
        public boolean succeeded() {
            return status == Status.ALREADY_INSTALLED
                    || status == Status.INSTALLED_FROM_STORES
                    || status == Status.FABRICATED_AND_INSTALLED
                    || status == Status.FABRICATED
                    || status == Status.MOVED
                    || status == Status.STRIPPED
                    || status == Status.ALREADY_STRIPPED
                    || status == Status.CHASSIS_FABRICATED
                    || status == Status.VEHICLE_FABRICATED
                    || status == Status.SCRAPPED
                    || status == Status.RECYCLED;
        }
    }
}

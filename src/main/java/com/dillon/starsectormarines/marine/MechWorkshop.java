package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Atomic campaign authority for Mech Lab fabrication and fitting. */
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
        if (mech.internal(internal.role) == internal) return Result.of(Status.ALREADY_INSTALLED);
        if (!bay.canInstallInternal(mechId, internal)) return Result.of(Status.INCOMPATIBLE);
        if (!bay.withinBudget(mechId, mech.fit().withInternal(internal.role, internal))) {
            return Result.of(Status.OVER_BUDGET);
        }
        if (bay.availableInternal(internal.id) > 0) {
            return bay.installInternal(mechId, internal)
                    ? Result.of(Status.INSTALLED_FROM_STORES) : Result.of(Status.INVALID);
        }
        if (!bay.canFabricate(internal)) return Result.of(Status.PATTERN_LOCKED);
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
        FabricationCost bill = weaponQuote(component);
        if (bill == null || !resources.spend(bill)) return Result.of(Status.INSUFFICIENT_MATERIALS);
        bay.addWeapon(component.id, 1);
        return Result.of(Status.FABRICATED);
    }

    /** Builds one internal into the bay's stores against its known pattern. */
    public Result fabricateInternal(MechInternal internal) {
        if (internal == null) return Result.of(Status.INVALID);
        if (!bay.canFabricate(internal)) return Result.of(Status.PATTERN_LOCKED);
        MechFabricationCatalog.Recipe recipe = MechFabricationCatalog.internal(internal);
        if (recipe == null || !resources.spend(resources.quote(recipe.cost()))) {
            return Result.of(Status.INSUFFICIENT_MATERIALS);
        }
        bay.addInternal(internal.id, 1);
        return Result.of(Status.FABRICATED);
    }

    /** Empties the optional utility socket. */
    public Result stripVentArray(String mechId) {
        return stripInternal(mechId, MechInternal.Role.VENT_ARRAY);
    }

    /**
     * Empties one socketed internal back to stores. Stripping an engine or capacitor bank is
     * allowed and leaves the mech unable to drop until one is fitted ({@link MechBay#stripInternal}).
     */
    public Result stripInternal(String mechId, MechInternal.Role role) {
        CampaignMech mech = bay.mechById(mechId);
        if (mech == null || role == null || role.socket() == null) return Result.of(Status.INVALID);
        if (mech.internal(role) == null) return Result.of(Status.ALREADY_STRIPPED);
        return bay.stripInternal(mechId, role) ? Result.of(Status.STRIPPED) : Result.of(Status.INVALID);
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

    /** Half of a pattern's base bill, rounded down: what breaking up one machine puts back in the hold. */
    public static FabricationCost salvage(FabricationCost bill) {
        if (bill == null) return null;
        List<FabricationCost.Line> lines = new ArrayList<>();
        for (FabricationCost.Line line : bill.lines()) {
            int back = line.quantity() / 2;
            if (back > 0) lines.add(new FabricationCost.Line(line.commodityId(), back));
        }
        return lines.isEmpty() ? null : new FabricationCost(lines);
    }

    /** What scrapping {@code mechId} would return, or null when it returns no materials. */
    public FabricationCost mechSalvage(String mechId) {
        CampaignMech mech = bay.mechById(mechId);
        MechFabricationCatalog.Recipe recipe = mech == null ? null : MechFabricationCatalog.chassis(mech.variant());
        return recipe == null ? null : salvage(recipe.cost());
    }

    /** What scrapping {@code vehicleId} would return, or null when it returns no materials. */
    public FabricationCost vehicleSalvage(String vehicleId) {
        CampaignVehicle vehicle = bay.vehicleById(vehicleId);
        MechFabricationCatalog.Recipe recipe = vehicle == null ? null : MechFabricationCatalog.vehicle(vehicle.type());
        return recipe == null ? null : salvage(recipe.cost());
    }

    /**
     * Scraps a chassis to free its gantry: fitted parts go back to stores and half the
     * chassis bill back to the hold.
     */
    public Result scrapMech(String mechId) {
        CampaignMech mech = bay.mechById(mechId);
        if (mech == null) return Result.of(Status.INVALID);
        FabricationCost salvage = mechSalvage(mechId);
        if (!bay.scrapMech(mechId)) return Result.of(Status.INVALID);
        if (salvage != null) resources.credit(salvage);
        return new Result(Status.SCRAPPED, mech);
    }

    /** Scraps a vehicle to free its gantry, half its bill back to the hold. */
    public Result scrapVehicle(String vehicleId) {
        CampaignVehicle vehicle = bay.vehicleById(vehicleId);
        if (vehicle == null) return Result.of(Status.INVALID);
        FabricationCost salvage = vehicleSalvage(vehicleId);
        if (!bay.scrapVehicle(vehicleId)) return Result.of(Status.INVALID);
        if (salvage != null) resources.credit(salvage);
        return new Result(Status.SCRAPPED, null, vehicle);
    }

    /** The quoted bill and clock for repairing {@code mechId}'s battle damage, or null if whole. */
    public RepairQuote repairQuote(String mechId) {
        CampaignMech mech = bay.mechById(mechId);
        MechRepairs.Estimate estimate = MechRepairs.estimate(mech);
        if (estimate == null) return null;
        return new RepairQuote(mechId, estimate.wear(), fieldRepair.get().repairDays(estimate.days()),
                resources.quote(estimate.bill()));
    }

    /**
     * Orders the repair of a damaged mech: the whole bill is spent, or nothing is and the
     * damage stands. A paid order clears the damage and starts the clock; the mech stays out of
     * drops until {@code day} plus the quoted days.
     */
    public Result repair(String mechId, float day) {
        CampaignMech mech = bay.mechById(mechId);
        if (mech == null || !Float.isFinite(day)) return Result.of(Status.INVALID);
        RepairQuote quote = repairQuote(mechId);
        if (quote == null) return Result.of(Status.NOT_DAMAGED);
        if (!resources.spend(quote.bill())) return new Result(Status.INSUFFICIENT_MATERIALS, mech);
        mech.beginRepair(day + quote.days());
        return new Result(Status.REPAIR_STARTED, mech);
    }

    /** A repair order as the player reviews it: wear, days in the gantry and the quoted bill. */
    public record RepairQuote(String mechId, float wear, float days, FabricationCost bill) {
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
        /** A repair order was paid; the mech is in its gantry until the clock runs out. */
        REPAIR_STARTED,
        /** There is no battle damage to repair. */
        NOT_DAMAGED,
        INCOMPATIBLE,
        /** The fit would weigh more than the engine can carry. */
        OVER_BUDGET,
        INSUFFICIENT_MATERIALS,
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
                    || status == Status.REPAIR_STARTED;
        }
    }
}

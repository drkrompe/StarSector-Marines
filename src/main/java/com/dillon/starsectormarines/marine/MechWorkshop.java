package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import java.util.function.IntSupplier;

/** Atomic campaign authority for Mech Lab fabrication and fitting. */
public final class MechWorkshop {

    private final MechBay bay;
    private final FabricationResources resources;
    private final IntSupplier workingBerths;

    public MechWorkshop(MechBay bay, FabricationResources resources) {
        this(bay, resources, () -> CampaignMechSquad.CAPACITY);
    }

    /** The host supplies its current physical capacity; a command rechecks it before spending. */
    public MechWorkshop(MechBay bay, FabricationResources resources, IntSupplier workingBerths) {
        if (bay == null || resources == null || workingBerths == null) {
            throw new IllegalArgumentException("mech bay and fabrication resources are required");
        }
        this.bay = bay;
        this.resources = resources;
        this.workingBerths = workingBerths;
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
        MechFabricationCatalog.Recipe recipe = MechFabricationCatalog.weapon(component);
        if (recipe == null || !resources.spend(resources.quote(recipe.cost()))) {
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

    /** Empties the optional utility socket; nothing else on a frame can be left empty. */
    public Result stripCooler(String mechId) {
        CampaignMech mech = bay.mechById(mechId);
        if (mech == null) return Result.of(Status.INVALID);
        if (mech.internal(MechInternal.Role.COOLER) == null) return Result.of(Status.ALREADY_STRIPPED);
        return bay.stripCooler(mechId) ? Result.of(Status.STRIPPED) : Result.of(Status.INVALID);
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
        CampaignMech mech = bay.fabricateChassis(squadId, variant);
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

    public enum Status {
        ALREADY_INSTALLED,
        INSTALLED_FROM_STORES,
        FABRICATED_AND_INSTALLED,
        MOVED,
        TARGET_OCCUPIED,
        STRIPPED,
        ALREADY_STRIPPED,
        CHASSIS_FABRICATED,
        VEHICLE_FABRICATED,
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
                    || status == Status.MOVED
                    || status == Status.STRIPPED
                    || status == Status.ALREADY_STRIPPED
                    || status == Status.CHASSIS_FABRICATED
                    || status == Status.VEHICLE_FABRICATED;
        }
    }
}

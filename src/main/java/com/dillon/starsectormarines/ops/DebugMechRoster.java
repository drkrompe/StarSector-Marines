package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.WaylineHound;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.power.SpawnInOnslaught;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.ops.detachment.Detachment;
import com.dillon.starsectormarines.ops.detachment.PowerCatalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Immutable briefing-selected mech family roster used only by debug missions. */
public final class DebugMechRoster {

    public static final int DEFAULT_COUNT = MechSupport.LANCE_SIZE;

    private static final MechVariant[] FAMILY = {
            MechVariant.BULWARK, MechVariant.HOUND, MechVariant.WARDEN
    };

    private final List<MechVariant> variants;
    private final List<VehicleType> vehicles;
    private final boolean recoveredHound;

    public static DebugMechRoster recoveredHound() {
        return new DebugMechRoster(List.of(MechVariant.HOUND), true);
    }

    /** The recovered Hound with {@code mechs} and {@code vehicles} beside it in the bay. */
    public static DebugMechRoster recoveredHoundWith(List<MechVariant> mechs, List<VehicleType> vehicles) {
        List<MechVariant> variants = new ArrayList<>();
        variants.add(MechVariant.HOUND);
        variants.addAll(mechs);
        return new DebugMechRoster(variants, vehicles, true);
    }

    /**
     * The bay's drop, flown as the campaign would fly it (owner: a player cannot just get a
     * Valkyrie): the committed company boat that lifts the most of it, with heavy lift behind
     * her only where the mission provides it ({@link PowerCatalog#providesHeavyLift}).
     */
    private MechSupport support(List<FittedBoat> boats, boolean heavyLift) {
        List<MechDeploymentSpec> mechs = new ArrayList<>();
        // A recovered Hound roster leads with the Wayline Hound; any other chassis is a standard fit.
        for (int i = 0; i < variants.size(); i++)
            mechs.add(recoveredHound && i == 0 ? WaylineHound.deployment() : MechDeploymentSpec.standard(variants.get(i)));
        List<VehicleDeploymentSpec> bay = new ArrayList<>();
        for (VehicleType type : vehicles) bay.add(VehicleDeploymentSpec.standard(type));
        return PowerCatalog.flownBy(MechSupport.configured(mechs, bay, null, heavyLift), boats, heavyLift);
    }

    private DebugMechRoster(List<MechVariant> variants) { this(variants, false); }

    private DebugMechRoster(List<MechVariant> variants, boolean recoveredHound) {
        this(variants, List.of(), recoveredHound);
    }

    private DebugMechRoster(List<MechVariant> variants, List<VehicleType> vehicles, boolean recoveredHound) {
        this.recoveredHound = recoveredHound;
        this.vehicles = List.copyOf(vehicles);
        this.variants = Collections.unmodifiableList(new ArrayList<>(variants));
    }

    public static DebugMechRoster randomized(int count, long seed) {
        int resolved = Math.max(0, count);
        Random random = new Random(seed);
        List<MechVariant> variants = new ArrayList<>(resolved);
        for (int i = 0; i < resolved; i++) {
            variants.add(FAMILY[random.nextInt(FAMILY.length)]);
        }
        return new DebugMechRoster(variants);
    }

    /** The roster the briefing's bay slots name, in slot order; empty slots add nothing. */
    public static DebugMechRoster ofSlots(List<DebugBaySlot> slots) {
        List<MechVariant> mechs = new ArrayList<>();
        List<VehicleType> bay = new ArrayList<>();
        for (DebugBaySlot slot : slots) {
            if (slot.mech != null) mechs.add(slot.mech);
            else if (slot.vehicle != null) bay.add(slot.vehicle);
        }
        return new DebugMechRoster(mechs, bay, false);
    }

    /** The slots a roster fills, for editing; at most {@link DebugBaySlot#SLOTS}. */
    public List<DebugBaySlot> slots() {
        List<DebugBaySlot> out = new ArrayList<>();
        for (MechVariant variant : variants) out.add(DebugBaySlot.of(variant));
        for (VehicleType type : vehicles) out.add(DebugBaySlot.of(type));
        return out.size() > DebugBaySlot.SLOTS ? out.subList(0, DebugBaySlot.SLOTS) : out;
    }

    /** Powers a debug mission's briefing offers whatever its roster, and every briefing while CHEAT is on ({@link Cheats}). */
    public static List<CommandPower> easterEggPowers() {
        return List.of(new SpawnInOnslaught());
    }

    public int count() {
        return variants.size() + vehicles.size();
    }

    public List<MechVariant> variants() {
        return variants;
    }

    /**
     * Replace a sourced/default Mech Support in place, or append the debug grant, flown by
     * {@code boats} and, where {@code heavyLift}, the mission's heavy lift.
     */
    public List<CommandPower> applyTo(List<CommandPower> powers, List<FittedBoat> boats, boolean heavyLift) {
        MechSupport support = count() > 0 ? support(boats, heavyLift) : null;
        List<CommandPower> configured = new ArrayList<>();
        boolean inserted = false;
        if (powers != null) {
            for (CommandPower power : powers) {
                if (MechSupport.ID.equals(power.id)) {
                    if (!inserted && support != null) {
                        configured.add(support);
                        inserted = true;
                    }
                } else {
                    configured.add(power);
                }
            }
        }
        if (!inserted && support != null) configured.add(support);
        return configured;
    }

    public Detachment applyTo(Detachment detachment, List<FittedBoat> boats, boolean heavyLift) {
        if (detachment == null) return Detachment.EMPTY;
        return new Detachment(detachment.shuttleManifest, detachment.marineWings,
                applyTo(detachment.powers, boats, heavyLift));
    }

    /** A debug detachment with the easter-egg powers added, whatever the bay holds. */
    public static Detachment withEasterEggs(Detachment detachment) {
        return new Detachment(detachment.shuttleManifest, detachment.marineWings, withEasterEggs(detachment.powers));
    }

    /** {@code powers} with any easter-egg power it lacks appended. */
    public static List<CommandPower> withEasterEggs(List<CommandPower> powers) {
        List<CommandPower> out = new ArrayList<>(powers);
        for (CommandPower egg : easterEggPowers())
            if (out.stream().noneMatch(p -> p.id.equals(egg.id))) out.add(egg);
        return out;
    }
}

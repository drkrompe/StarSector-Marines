package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;

import java.util.ArrayList;
import java.util.List;

/**
 * What a company's mech and a company's vehicle are alike (mechs-nouns.md, one machine, one part catalog): a machine on
 * a gantry, fitted from the one part catalog. Whatever reads a machine reads this, so a rule written here holds for
 * both; what stays on {@link CampaignMech} or {@link CampaignVehicle} alone is a stated difference between them.
 */
public interface CampaignMachine {

    String id();

    String displayName();

    /** What the machine, as fitted, offers the parts that go in it. */
    MachineCapabilities capabilities();

    /** The part fitted in {@code role}, or null when it is empty; vent arrays stack, and this is the first. */
    MechInternal internal(MechInternal.Role role);

    /** What the auxiliary compartment holds, in fitted order. */
    List<MechInternal> compartment();

    /** The compartment's bays, in packing order. */
    List<SocketDef> compartmentGrids();

    /** What the machine lacks to drop: the roles it {@linkplain MachineCapabilities#required requires} left empty. */
    default List<MechInternal.Role> missingInternals() {
        List<MechInternal.Role> missing = new ArrayList<>();
        for (MechInternal.Role role : capabilities().required()) {
            if (internal(role) == null) missing.add(role);
        }
        return List.copyOf(missing);
    }

    /** Whether the machine has every part it cannot go without. */
    default boolean complete() {
        return missingInternals().isEmpty();
    }
}

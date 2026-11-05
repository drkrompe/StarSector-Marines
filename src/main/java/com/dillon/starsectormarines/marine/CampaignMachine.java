package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;

import java.util.ArrayList;
import java.util.List;

/**
 * What a company's mech and a company's vehicle are alike (mechs-nouns.md, one machine, one part catalog): a machine on
 * a gantry, fitted from the one part catalog. Whatever reads a machine reads this, so a rule written here holds for
 * both; what stays on {@link CampaignMech} or {@link CampaignVehicle} alone is a stated difference between them.
 */
public interface CampaignMachine extends Equipped {

    String id();

    String displayName();

    /** Changes only the player-facing callsign; false for one that is empty or too long. */
    boolean rename(String value);

    /** What the machine, as fitted, offers the parts that go in it. */
    MachineCapabilities capabilities();

    /** The part fitted in {@code role}, or null when it is empty; vent arrays stack, and this is the first. */
    MechInternal internal(MechInternal.Role role);

    /** What the auxiliary compartment holds, in fitted order. */
    List<MechInternal> compartment();

    /** The compartment's bays, in packing order. */
    List<SocketDef> compartmentGrids();

    /** Where each compartment part sits, in fitted order: the place it was given, kept ({@link CompartmentFit}). */
    List<MechCompartment.Placement> compartmentPlacements();

    /** What the machine lacks to drop: the roles it {@linkplain MachineCapabilities#required requires} left empty. */
    default List<MechInternal.Role> missingInternals() {
        List<MechInternal.Role> missing = new ArrayList<>();
        for (MechInternal.Role role : capabilities().required()) {
            if (internal(role) == null) missing.add(role);
        }
        return List.copyOf(missing);
    }

    /**
     * Whether the machine can carry a weapon and has none anywhere: it can still go, so this is only recommended. A
     * hull with no gun card is not unarmed, it is built that way.
     */
    boolean unarmed();

    /**
     * What it is fitted without, by the one rule (mechs-nouns.md, readiness): each role it requires left empty, then
     * an empty armor point and, where it can carry one, no weapon anywhere.
     */
    @Override
    default List<FittingGap> gaps() {
        List<FittingGap> gaps = new ArrayList<>();
        for (MechInternal.Role role : missingInternals()) gaps.add(FittingGap.missing(role));
        if (internal(MechInternal.Role.PLATING) == null) gaps.add(FittingGap.without(FittingGap.Lack.ARMOR));
        if (unarmed()) gaps.add(FittingGap.without(FittingGap.Lack.WEAPON));
        return List.copyOf(gaps);
    }

    /** Whether the machine has every part it cannot go without. */
    @Override
    default boolean complete() {
        return missingInternals().isEmpty();
    }
}

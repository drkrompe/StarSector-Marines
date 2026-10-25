package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.gen.bsp.District;

import java.util.List;

/**
 * The cordoned facility district ({@code set-piece-districts.md}): several buildings on their own streets
 * inside one wall, the place a mission is about. Its bounds are the wall's, inclusive. Each gatehouse is a gate
 * where one of the town's streets crosses the wall; the courtyard is the open square the buildings stand round,
 * inclusive; each building has its role in the facility, its shell's bounds and its front door, or
 * {@code -1, -1} where its shell could not be carved.
 */
public record CordonedFacility(Character character, int left, int top, int right, int bottom,
                               List<Gatehouse> gatehouses, int courtyardLeft, int courtyardTop,
                               int courtyardRight, int courtyardBottom, List<Building> buildings) {

    /** What the facility is, read from the zoning it stands in. */
    public enum Character {
        /** Laboratories round an admin block: a research station. */
        RESEARCH_STATION,
        /** Workshops and vehicle sheds: a depot. */
        DEPOT,
        /** A clinic, stores and a shelter hall round a muster yard: a civil-defence centre. */
        CIVIL_DEFENCE
    }

    /**
     * A gate: the lane through the wall from {@code (x0, y0)} to {@code (x1, y1)} inclusive, the first street
     * cell outside it and the first inside it, both on the lane's middle.
     */
    public record Gatehouse(int x0, int y0, int x1, int y1, int outX, int outY, int inX, int inY) {
    }

    /** One of the facility's buildings, or its open ground for a yard. */
    public record Building(District.Role role, int left, int top, int right, int bottom, int doorX, int doorY) {
    }

    public CordonedFacility {
        gatehouses = List.copyOf(gatehouses);
        buildings = List.copyOf(buildings);
    }
}

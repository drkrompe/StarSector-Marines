package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;

import java.util.Collections;
import java.util.Map;

/**
 * A set piece over several adjacent leaves and the streets between them ({@code set-piece-districts.md}).
 * A program chose the leaves through a {@link DistrictClaim}, gave each one a {@link Role}, and may have
 * rewritten the streets inside the claim before the road graph was built from them. It is published as one
 * {@link Compound}, whose seed kind names the program's filler, so it fills the way a compound does.
 *
 * <p>The civilian spaceport campus is the first district: a {@link Shape#CLUSTER} of apron leaves from its
 * zoning pocket, with no streets of its own. It keeps the {@link Compound.Role}s its filler reads.
 */
public final class District {

    /** Which leaves a district takes. */
    public enum Shape {
        /** A connected cluster of leaves from one zoning pocket; the streets between them stay public. */
        CLUSTER,
        /** One straight street with leaves fronting it on both sides ({@link SpineClaim}). */
        SPINE,
        /** A 2x2 or 2x3 block of leaves round the cross streets between them ({@link QuarterClaim}). */
        QUARTER
    }

    /**
     * What a member leaf is in its district's program. Programs add the roles they place; a program reads
     * only its own.
     */
    public enum Role {
        /** A shop whose doors and windows face the district's street (main street). */
        SHOPFRONT,
        /** The open square at one end of a spine (main street). */
        PLAZA,
        /**
         * Ground under the market square set piece ({@code MarketSquare}), facing the district's street: the
         * members holding it and the street between them are the square's lot (main street's plaza end).
         */
        MARKET_SQUARE,
        /** The public building closing the other end of a spine: a civic hall (main street). */
        LANDMARK
    }

    /** The program that planned it, e.g. {@code "main-street"}; also how a mission names it. */
    public final String program;
    public final Shape shape;
    /** The claim it stands on; null for a {@link Shape#CLUSTER}, which owns no streets. */
    public final DistrictClaim claim;
    /** The members as one compound; its seed kind selects the program's filler. */
    public final Compound compound;
    /** Each member's role in the program; empty for a cluster, whose roles are the compound's. */
    public final Map<BlockLeaf, Role> roles;

    public District(String program, Shape shape, DistrictClaim claim, Compound compound,
                    Map<BlockLeaf, Role> roles) {
        this.program = program;
        this.shape = shape;
        this.claim = claim;
        this.compound = compound;
        this.roles = Collections.unmodifiableMap(roles);
    }

    /** The planned district whose compound this is, or null. */
    public static District of(Compound compound, Iterable<District> districts) {
        if (districts == null) return null;
        for (District district : districts) {
            if (district.compound == compound) return district;
        }
        return null;
    }
}

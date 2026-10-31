package com.dillon.starsectormarines.battle.world.gen.layout;

import com.dillon.starsectormarines.battle.world.gen.AirbaseLot;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;

/**
 * An airbase lot a mission lays at a place it names (`mapgen-nouns.md`, airbase lot): its reservation, fence and
 * clearance together, is held from before the places grow, as a {@link Site} stated up front is, and the lot is
 * authored onto it with the works. {@code (x0, y0)} is the reservation's low corner; the lot's {@code purpose} is what
 * its berths are published as, so an allied base publishes no garrison node for the attacker to take.
 */
public record Airfield(String name, int x0, int y0, AirbaseLot.Facing facing, AirbaseLot.Size size,
                       LandingPad.Purpose purpose, boolean required) implements Piece {

    public Airfield {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("an airfield needs a name");
        if (facing == null || size == null || purpose == null) {
            throw new IllegalArgumentException("an airfield needs a facing, a size and a purpose: " + name);
        }
    }

    /** The ground it holds: the lot and its clearance, as a site no place grows onto. */
    public Site reservation() {
        return new Site(name, x0, y0, x0 + AirbaseLot.reservedSpanX(size, facing) - 1,
                y0 + AirbaseLot.reservedSpanY(size, facing) - 1, SitePurpose.OPEN, required);
    }

    /** The lot inside its clearance, as the lot is authored: {@code {left, bottom, right, top}}. */
    public int[] lot() {
        Site held = reservation();
        int clear = size.clearance();
        return new int[]{held.x0() + clear, held.y0() + clear, held.x1() - clear, held.y1() - clear};
    }
}

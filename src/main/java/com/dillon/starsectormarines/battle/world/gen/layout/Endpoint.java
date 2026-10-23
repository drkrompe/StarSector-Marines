package com.dillon.starsectormarines.battle.world.gen.layout;

/**
 * Where a {@link Road} ends, stated against a named place: the middle of one
 * side of the ground it claimed, just outside it, moved by a world offset
 * {@code (dx, dy)}. With {@code gate}, the place's open gate nearest that side
 * instead of the middle, so a road arrives where the wall will let it in.
 */
public record Endpoint(String place, Side side, boolean gate, int dx, int dy) {

    public Endpoint {
        if (place == null || place.isEmpty()) throw new IllegalArgumentException("an endpoint names a place");
        if (side == null) throw new IllegalArgumentException("an endpoint names a side: " + place);
    }

    /** At the place's open gate nearest {@code side}. */
    public static Endpoint gate(String place, Side side) {
        return new Endpoint(place, side, true, 0, 0);
    }

    /** Off the middle of the place's {@code side}, moved {@code (dx, dy)}. */
    public static Endpoint beside(String place, Side side, int dx, int dy) {
        return new Endpoint(place, side, false, dx, dy);
    }
}

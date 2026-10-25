package com.dillon.starsectormarines.battle.world.gen.layout;

/**
 * A road the mission wants between two places ({@code mission-layout.md}):
 * carved into the road network as the places grow, {@code width} cells wide,
 * so the gates it reaches, its paving and the road graph come from the
 * ordinary stages. A road with a {@code via} passes through that point on its
 * way, each leg a dog-leg. It is laid only on open country: across another place's
 * ground or the surveyed country the road is that ground's own streets, and
 * the weld joins the pieces. A road a {@link Deferred} resolves with the works
 * is not carved: it is the way along the streets already standing between its
 * ends, the widest first, cleared of the props in its carriageway
 * ({@code LayoutStreets}; its {@code via} and {@code width} are not read).
 */
public record Road(String name, Endpoint from, Endpoint via, Endpoint to, int width, boolean required) implements Piece {

    public Road {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("a road needs a name");
        if (from == null || to == null) throw new IllegalArgumentException("a road runs between two endpoints: " + name);
        if (width < 1) throw new IllegalArgumentException("a road is at least a cell wide: " + name);
    }

    /** A road straight between two places, by a dog-leg. */
    public Road(String name, Endpoint from, Endpoint to, int width, boolean required) {
        this(name, from, null, to, width, required);
    }
}

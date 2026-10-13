package com.dillon.starsectormarines.battle.warship;

import java.util.ArrayList;
import java.util.List;

/**
 * How one called ship ended its battle: frozen, and nothing from the campaign. A committed
 * member that was never called has no result at all, which is how it pays nothing.
 *
 * @param memberId the fleet member it was frozen from (never null here)
 * @param shipName the member's name, for the readout
 * @param hullId the base hull
 * @param hullFraction the share of the hull left at the end, 0 when it was destroyed
 * @param destroyed whether the battle took it to nothing
 */
public record ShipSupportResult(String memberId, String shipName, String hullId,
                                float hullFraction, boolean destroyed) {

    public ShipSupportResult {
        if (memberId == null || memberId.isBlank()) throw new IllegalArgumentException("a result needs a member");
        hullFraction = destroyed ? 0f : Math.max(0f, Math.min(1f, hullFraction));
    }

    /** The result of a hull as it stands now, or null when it was not called from a fleet member. */
    public static ShipSupportResult of(Warship ship) {
        ShipSupportSource source = ship == null ? null : ship.supportSource();
        if (source == null || source.memberId() == null) return null;
        return new ShipSupportResult(source.memberId(), source.shipName(), ship.hullId,
                ship.hullFraction(), ship.destroyed());
    }

    /** The results of every member-called hull in {@code ships}, in the order they were called. */
    public static List<ShipSupportResult> allOf(List<Warship> ships) {
        List<ShipSupportResult> out = new ArrayList<>();
        for (Warship ship : ships) {
            ShipSupportResult result = of(ship);
            if (result != null) out.add(result);
        }
        return out;
    }
}

package com.dillon.starsectormarines.battle.warship;

import java.util.Locale;

/**
 * What kind of ship a hull is, read once at fit time from data the game already
 * carries: the {@code hullSize} of the {@code .ship}, and the {@code designation},
 * {@code fighter bays}, {@code hints} and {@code shield type} columns of the hull
 * table. It is derived, never authored per hull, and it is not a posture: it
 * biases the threat assessment ({@link AirThreatWeights}) and nothing else, so a
 * class never changes how hard anything hits.
 *
 * @param size   the hull's size, frigate to capital
 * @param role   what the hull is for
 * @param bays   fighter bays the table lists (0 for none)
 * @param shield the table's shield type, or {@code NONE}; carried for readouts, not yet weighted
 */
public record ShipClass(HullSize size, Role role, int bays, Shield shield) {

    /** Hull size, smallest first. A fighter-size hull is read as a frigate. */
    public enum HullSize { FRIGATE, DESTROYER, CRUISER, CAPITAL }

    /** What the hull is for. Escort needs a lead-ship relation that does not exist yet and is not a role here. */
    public enum Role { COMBAT, CARRIER, SUPPORT }

    /** The table's shield type; {@code PHASE} is kept apart from {@code NONE}. */
    public enum Shield { NONE, FRONT, OMNI, PHASE }

    /** Bays at which a hull is a carrier whatever its hints say. */
    static final int CARRIER_BAYS = 3;

    /**
     * The class every ship has until it is fitted from data: a combat capital,
     * which is what the threat weights were tuned against, so a ship built
     * without a hull row behaves exactly as it did before classes existed.
     */
    public static final ShipClass DEFAULT = new ShipClass(HullSize.CAPITAL, Role.COMBAT, 0, Shield.NONE);

    /**
     * Classifies one hull from its raw columns; any argument may be {@code null}.
     *
     * <ul>
     *   <li>{@code SUPPORT}: the hints say {@code CIVILIAN}, or the designation names a tender.
     *       A drone tender carries a {@code CARRIER} hint and one bay, but it is a builder, not a wing's home.</li>
     *   <li>{@code CARRIER}: the hints say {@code CARRIER}, or the hull has {@value #CARRIER_BAYS} or more bays.
     *       A gun ship with a bay or two (a Venture, an Odyssey) stays {@code COMBAT}.</li>
     *   <li>otherwise {@code COMBAT}. A {@code COMBAT} hint beside {@code CARRIER} does not change the role.</li>
     * </ul>
     * An unknown or absent size reads as a capital, the hull the rest of the fit defaults to.
     */
    public static ShipClass classify(String hullSize, String designation, String hints, String bays, String shieldType) {
        int bayCount = (int) Math.max(0, Math.round(number(bays)));
        String hintSet = "," + normal(hints).replace(" ", "") + ",";
        Role role;
        if (hintSet.contains(",CIVILIAN,") || normal(designation).contains("TENDER")) {
            role = Role.SUPPORT;
        } else if (hintSet.contains(",CARRIER,") || bayCount >= CARRIER_BAYS) {
            role = Role.CARRIER;
        } else {
            role = Role.COMBAT;
        }
        return new ShipClass(sizeOf(hullSize), role, bayCount, shieldOf(shieldType));
    }

    private static HullSize sizeOf(String raw) {
        return switch (normal(raw)) {
            case "FRIGATE", "FIGHTER" -> HullSize.FRIGATE;
            case "DESTROYER" -> HullSize.DESTROYER;
            case "CRUISER" -> HullSize.CRUISER;
            default -> HullSize.CAPITAL;
        };
    }

    private static Shield shieldOf(String raw) {
        return switch (normal(raw)) {
            case "FRONT" -> Shield.FRONT;
            case "OMNI" -> Shield.OMNI;
            case "PHASE" -> Shield.PHASE;
            default -> Shield.NONE;
        };
    }

    private static String normal(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }

    private static double number(String raw) {
        try {
            return raw == null ? 0.0 : Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /** One line for a readout, such as {@code capital combat}. */
    public String describe() {
        return size.name().toLowerCase(Locale.ROOT) + " " + role.name().toLowerCase(Locale.ROOT);
    }
}

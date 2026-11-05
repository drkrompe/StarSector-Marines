package com.dillon.starsectormarines.ops.fitting;

import com.dillon.starsectormarines.i18n.Strings;

import java.util.ArrayList;
import java.util.List;

/**
 * One reading of a machine, mech, vehicle or shuttle: what it would land with. Every specifications card draws its bars
 * from these, so a figure one machine shows, every machine shows, with the same words and the same hover preview.
 *
 * @param rating {@link #NO_RATING} on a machine whose armor has none, which has no row for it: a shuttle's
 */
public record MachineFigures(float armor, float rating, float structure, float speed, float flux, float dissipation,
                             float range, int missiles) {

    public static final float NO_RATING = -1f;

    /** What a machine carries: its bar's value line, how many seats and the most any such machine has. None: no bar. */
    public record Seats(String value, int count, int maximum) {
        public static final Seats NONE = new Seats("", 0, 0);

        public static Seats of(int count, int maximum) {
            return new Seats(Strings.format("openingMechLabSeats", count), count, maximum);
        }
    }

    /**
     * The bars of one machine: where it stands, where a hovered part would take it, and the stock fit's mark.
     *
     * @param idPrefix keeps one card's bars apart from another's
     * @param ceiling  the full scale of each bar
     */
    public static List<FittingMeter> meters(String idPrefix, MachineFigures now, MachineFigures ghost, MachineFigures stock,
                                            MachineFigures ceiling, Seats seats) {
        List<FittingMeter> meters = new ArrayList<>();
        meters.add(FittingMeter.of(idPrefix + "armor", Strings.get("openingMechLabArmor"),
                Strings.format("openingMechLabPlate", Math.round(now.armor())),
                now.armor(), ceiling.armor(), ghost.armor(), stock.armor(), FittingMeter.PLATE_TONE));
        if (stock.rating() != NO_RATING) {
            meters.add(FittingMeter.of(idPrefix + "rating", Strings.get("openingMechLabArmorRating"), number(now.rating()),
                    now.rating(), ceiling.rating(), ghost.rating(), stock.rating(), FittingMeter.PLATE_TONE));
        }
        meters.add(FittingMeter.of(idPrefix + "structure", Strings.get("openingMechLabStructure"), number(now.structure()),
                now.structure(), ceiling.structure(), ghost.structure(), stock.structure(), FittingMeter.PLATE_TONE));
        meters.add(FittingMeter.of(idPrefix + "mobility", Strings.get("openingMechLabMobility"),
                Strings.format("openingMechLabCellsS", number(now.speed())),
                now.speed(), ceiling.speed(), ghost.speed(), stock.speed(), FittingMeter.ENGINE_TONE));
        meters.add(FittingMeter.of(idPrefix + "flux", Strings.get("openingMechLabCapacitorBank"),
                Strings.format("openingMechLabFluxValue", number(now.flux())),
                now.flux(), ceiling.flux(), ghost.flux(), stock.flux(), FittingMeter.FLUX_TONE));
        meters.add(FittingMeter.of(idPrefix + "dissipation", Strings.get("openingMechLabDissipation"),
                Strings.format("openingMechLabDissipationValue", number(now.dissipation())),
                now.dissipation(), ceiling.dissipation(), ghost.dissipation(), stock.dissipation(), FittingMeter.FLUX_TONE));
        meters.add(FittingMeter.of(idPrefix + "range", Strings.get("openingMechLabMaxRange"),
                Strings.format("openingMechLabCells", number(now.range())),
                now.range(), ceiling.range(), ghost.range(), stock.range(), FittingMeter.WEAPON_TONE));
        meters.add(FittingMeter.of(idPrefix + "endurance", Strings.get("openingMechLabMissiles"),
                Strings.format("openingMechLabTriggers", now.missiles()),
                now.missiles(), ceiling.missiles(), ghost.missiles(), stock.missiles(), FittingMeter.MISSILE_TONE));
        if (seats.count() > 0) {
            meters.add(FittingMeter.of(idPrefix + "capacity", Strings.get("openingMechLabPassengers"), seats.value(),
                    seats.count(), seats.maximum()));
        }
        return List.copyOf(meters);
    }

    private static String number(float value) {
        if (Math.abs(value - Math.round(value)) < .01f) return String.format(Strings.locale(), "%d", Math.round(value));
        return String.format(Strings.locale(), "%.1f", value);
    }
}

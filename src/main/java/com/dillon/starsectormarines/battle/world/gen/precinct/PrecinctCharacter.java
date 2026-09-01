package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.gen.WeightedTable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * What a zoned precinct is on the inside.
 *
 * <p>A programmed precinct has a {@code FortressProgram} that says what stands
 * in it. A zoned one had nothing: its parcels were cut from its own claim and
 * then themed by a map-wide scatter that had never heard of it, so a town, a
 * hamlet and an outlying district were three samples of one distribution.
 * Measured, three places on one map came out 71-85% residential apiece.
 *
 * <p>A character is the missing statement. It is a <b>mix</b> of the district
 * themes the place is built from — the same {@link MapDistrictTheme} tables the
 * fills already read, weighted the way this kind of place weights them — and,
 * optionally, a <b>centre</b>: the theme forced onto the block its seed stands
 * on, so a town has a civic core of its own rather than borrowing the one nudge
 * the map used to have at its trunk crossing.
 *
 * <p>The mix is a statement, not a roll. "A depot" and "a dormitory suburb" are
 * different documents, in the way a garrison and a depot are different programs,
 * and two places with the same character are the same kind of place whatever
 * the seed did. It is stated per place and authored-wins, the same shape as
 * {@link Fortification}: a mission may say what a place is, and
 * {@link PrecinctPlan#derive} picks one for a place nobody described.
 *
 * <p>Hinterland has no character and must not acquire one. Open country is
 * dressed rather than built, and the moment it has a theme it is a fourth kind
 * of settlement.
 *
 * @param name   what to call it in evidence; never read by the game
 * @param mix    district themes and their shares. Integer shares, proportions
 *               only, the same convention as the theme tables themselves
 * @param centre the theme forced at the seed, or {@code null} for a place with
 *               no centre — a hamlet is houses along a road and has none
 */
public record PrecinctCharacter(String name, Map<MapDistrictTheme, Integer> mix,
                                MapDistrictTheme centre) {

    public PrecinctCharacter {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("a character is named");
        if (mix == null || mix.isEmpty()) {
            throw new IllegalArgumentException(name + " builds nothing, which is hinterland");
        }
        for (Map.Entry<MapDistrictTheme, Integer> share : mix.entrySet()) {
            if (share.getValue() == null || share.getValue() <= 0) {
                throw new IllegalArgumentException(name + " gives " + share.getKey()
                        + " a share of " + share.getValue());
            }
        }
        mix = Collections.unmodifiableMap(new EnumMap<>(mix));
    }

    /** How much a {@link #leaning} adds, against tables that total about a hundred. */
    private static final int LEAN = 60;

    /**
     * The main settlement: a civic core with everything else around it. The
     * one character with a centre, because a town is the place that has one.
     */
    public static final PrecinctCharacter TOWN = of("town", MapDistrictTheme.CIVIC,
            MapDistrictTheme.RESIDENTIAL, 30,
            MapDistrictTheme.MIXED, 25,
            MapDistrictTheme.CIVIC, 20,
            MapDistrictTheme.INDUSTRIAL, 15,
            MapDistrictTheme.OUTSKIRTS, 10);

    /** Houses along a road with open ground between them. No centre. */
    public static final PrecinctCharacter HAMLET = of("hamlet", null,
            MapDistrictTheme.OUTSKIRTS, 45,
            MapDistrictTheme.RESIDENTIAL, 40,
            MapDistrictTheme.MIXED, 15);

    /** A dormitory: housing and very little else. */
    public static final PrecinctCharacter SUBURB = of("suburb", null,
            MapDistrictTheme.RESIDENTIAL, 70,
            MapDistrictTheme.MIXED, 20,
            MapDistrictTheme.OUTSKIRTS, 10);

    /** Yards, sheds and the odd fortified post: an outlying place that works. */
    public static final PrecinctCharacter DEPOT = of("depot", null,
            MapDistrictTheme.INDUSTRIAL, 65,
            MapDistrictTheme.OUTSKIRTS, 20,
            MapDistrictTheme.MIXED, 15);

    /** One district of a city: dense and mixed, with no core of its own. */
    public static final PrecinctCharacter QUARTER = of("quarter", null,
            MapDistrictTheme.MIXED, 30,
            MapDistrictTheme.RESIDENTIAL, 30,
            MapDistrictTheme.CIVIC, 25,
            MapDistrictTheme.INDUSTRIAL, 15);

    /**
     * This character bent toward one theme — how a mining world's town comes
     * out industrial without being a depot.
     *
     * <p>{@code null} is "no signal" and returns this unchanged, so a caller
     * holding an economy that may be neutral need not branch.
     */
    public PrecinctCharacter leaning(MapDistrictTheme theme) {
        if (theme == null) return this;
        Map<MapDistrictTheme, Integer> bent = new EnumMap<>(mix);
        bent.merge(theme, LEAN, Integer::sum);
        return new PrecinctCharacter(name + " leaning " + theme.name().toLowerCase(), bent, centre);
    }

    /** Whether the seed's block is forced to something. */
    public boolean hasCentre() {
        return centre != null;
    }

    /** The mix as something a block can be rolled from. Built per call; roll from one. */
    public WeightedTable<MapDistrictTheme> table() {
        WeightedTable.Builder<MapDistrictTheme> table = WeightedTable.builder();
        for (Map.Entry<MapDistrictTheme, Integer> share : mix.entrySet()) {
            table.add(share.getKey(), share.getValue());
        }
        return table.build();
    }

    private static PrecinctCharacter of(String name, MapDistrictTheme centre, Object... shares) {
        Map<MapDistrictTheme, Integer> mix = new EnumMap<>(MapDistrictTheme.class);
        for (int i = 0; i < shares.length; i += 2) {
            mix.put((MapDistrictTheme) shares[i], (Integer) shares[i + 1]);
        }
        return new PrecinctCharacter(name, mix, centre);
    }
}

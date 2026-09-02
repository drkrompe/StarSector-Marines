package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.ops.MissionType;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.StringJoiner;

/**
 * What each mission needs its map to contain, and whether a given map has it.
 *
 * <p>A mission is a promise about the fight. Conquest promises a fortified
 * defender with somewhere to be taken from them, and part of that is an air arm
 * with a field to fly from — a field that can be burned. When generation
 * quietly fails to produce one, nothing is wrong anywhere in particular and the
 * mission is not the mission any more. That is not a bug a pass can catch,
 * because no pass knows what was promised.
 *
 * <p>The requirements are therefore stated here, next to the mission, and
 * checked against the finished map. A mission with no entry requires nothing
 * and is unaffected; adding a requirement is one line, and it takes effect for
 * every map that mission generates.
 *
 * <p><b>A failed requirement is a re-roll, then an error.</b> Map generation is
 * seeded, so the cheapest correct answer to "this map is not valid for this
 * mission" is a different map — the shape {@code createCivilianRescue} already
 * uses. Only when a run of seeds all fail is something actually wrong, and then
 * it is worth stopping for: silently shipping the invalid map is how the
 * missing airfield survived for as long as it did.
 */
public final class MissionMapRequirements {

    private static final Map<MissionType, EnumSet<MapFeature>> REQUIRED =
            new EnumMap<>(MissionType.class);

    static {
        // Conquest is the mission with real structural demands: a fortified
        // defender, a keep to take, an air arm to ground, a shore to land on,
        // and a way to reach every compound it is won by taking. Every other
        // mission type is deliberately absent — it requires nothing until
        // somebody can say what it actually needs, and an invented
        // requirement is worse than none.
        REQUIRED.put(MissionType.CONQUEST, EnumSet.of(
                MapFeature.DEFENDER_GARRISON,
                MapFeature.CENTRAL_KEEP,
                MapFeature.GARRISON_AIRFIELD,
                MapFeature.MARINE_LANDING_ZONE,
                MapFeature.WALKABLE_COMPOUNDS));
    }

    private MissionMapRequirements() { }

    /** What {@code type} needs a map to contain. Empty when it makes no demands. */
    public static EnumSet<MapFeature> requiredFor(MissionType type) {
        EnumSet<MapFeature> required = type == null ? null : REQUIRED.get(type);
        return required == null ? EnumSet.noneOf(MapFeature.class) : EnumSet.copyOf(required);
    }

    /** Which of {@code type}'s requirements this map fails to meet. */
    public static EnumSet<MapFeature> missingFrom(MissionType type, MapResult map) {
        EnumSet<MapFeature> missing = EnumSet.noneOf(MapFeature.class);
        for (MapFeature feature : requiredFor(type)) {
            if (!feature.presentIn(map)) missing.add(feature);
        }
        return missing;
    }

    /** Whether this map is a valid one for {@code type} to be played on. */
    public static boolean satisfied(MissionType type, MapResult map) {
        return missingFrom(type, map).isEmpty();
    }

    /** What to say when a run of seeds could not produce a valid map. */
    public static String describeFailure(MissionType type, long seed, int attempts,
                                         EnumSet<MapFeature> missing) {
        StringJoiner what = new StringJoiner(", ");
        for (MapFeature feature : missing) what.add(feature.description);
        return type + " map generation failed after " + attempts + " seeds from "
                + seed + ": the last map had no " + what;
    }
}

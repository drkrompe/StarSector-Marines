package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A story map's buildings published as places a squad can hold.
 *
 * <p>An authored map knows its buildings as points of interest and, until it says so, nothing tactical can see
 * them: no frontage is derived for them, no commander can man them and no player order can point at them. Each
 * building becomes an {@link TacticalNode.Kind#INNER_POSITION} on its own footprint, entered at its interior
 * anchor. That kind is a held position and nothing more: no capture record, no marker and no supply gate, so
 * publishing a building never adds an objective to the mission.
 *
 * <p>A held place asks for no garrison. Places are manned in answer to threat or by order, never filled because
 * they exist (see {@code garrisonables.md}), so whoever holds one is somebody sent there.
 */
public final class HeldPlaces {

    /** Ranks a held building below every authored objective. UNTUNED. */
    public static final int PRIORITY = 30;

    private HeldPlaces() {}

    /**
     * One held place per building point of interest. Landing sites are open ground, not buildings, and are left
     * out.
     *
     * @param guard who would naturally hold each building when the battle starts
     */
    public static List<TacticalNode> fromBuildings(List<PointOfInterest> pois,
                                                   Function<PointOfInterest, Faction> guard) {
        List<TacticalNode> places = new ArrayList<>();
        for (PointOfInterest poi : pois) {
            if (poi.kind == PointOfInterest.Kind.LANDING_SITE) continue;
            places.add(new TacticalNode(TacticalNode.Kind.INNER_POSITION, poi.interiorAnchorX, poi.interiorAnchorY,
                    poi.left, poi.top, poi.right, poi.bottom, guard.apply(poi), PRIORITY, 0));
        }
        return places;
    }

    /** Whether a node is a held place nobody is posted to: present on the map, but not a tactical centre. */
    public static boolean isUnposted(TacticalNode node) {
        return node.kind == TacticalNode.Kind.INNER_POSITION && node.garrisonSize == 0;
    }
}

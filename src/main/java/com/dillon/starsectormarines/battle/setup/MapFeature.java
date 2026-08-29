package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;

/**
 * Something a generated map either has or does not, that a mission may need in
 * order to be the battle it claims to be.
 *
 * <p>Generation is a long chain of passes that each decline politely. A ward
 * that cannot fit an airfield builds none; a claim that comes up short takes a
 * smaller lot; a stamper with nowhere to stand emits nothing. Every one of
 * those is the right local decision, and none of them knows what the mission
 * was promised. The garrison airfield was missing from a quarter of live
 * conquest battles for exactly that reason: the ward skipped it, and the only
 * symptom was an enemy that flew its reinforcements in from off map.
 *
 * <p>So the requirement is stated where it is owned — by the mission, not by
 * the pass that happens to satisfy it — and checked against the finished map.
 * A feature is a question about the <em>output</em>, deliberately: it does not
 * care which pass produced it, and it keeps working when one is replaced.
 *
 * @see MissionMapRequirements
 */
public enum MapFeature {

    /** Somewhere for the defence to live — the nodes garrison squads deploy to. */
    DEFENDER_GARRISON("a defender garrison") {
        @Override
        public boolean presentIn(MapResult map) {
            return map != null && map.tacticalMap != null
                    && map.tacticalMap.all().stream()
                        .anyMatch(node -> node.defaultGuard == Faction.DEFENDER);
        }
    },

    /** The compound an assault is won by taking. */
    CENTRAL_KEEP("a defender command post") {
        @Override
        public boolean presentIn(MapResult map) {
            return map != null && map.tacticalMap != null
                    && map.tacticalMap.all().stream()
                        .anyMatch(node -> node.kind == TacticalNode.Kind.COMMAND_POST
                                && node.defaultGuard == Faction.DEFENDER);
        }
    },

    /**
     * Berths the defender's air arm flies from, and that an attacker can burn
     * to stop it.
     */
    GARRISON_AIRFIELD("a garrison airfield") {
        @Override
        public boolean presentIn(MapResult map) {
            return map != null && map.landingPads.stream()
                    .anyMatch(pad -> pad.purpose == LandingPad.Purpose.GARRISON_AIRFIELD);
        }
    },

    /** Somewhere for the assault to arrive. */
    MARINE_LANDING_ZONE("a marine landing zone") {
        @Override
        public boolean presentIn(MapResult map) {
            return map != null && map.marineSpawnX >= 0 && map.marineSpawnY >= 0;
        }
    };

    /** How this reads in the message a failed requirement produces. */
    public final String description;

    MapFeature(String description) {
        this.description = description;
    }

    /** Whether the finished map has it. */
    public abstract boolean presentIn(MapResult map);
}

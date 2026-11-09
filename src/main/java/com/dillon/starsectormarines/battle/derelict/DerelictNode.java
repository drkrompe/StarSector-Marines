package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;

/**
 * A security zone's control node (`derelict-security-network.md`, slice 5): an armoured, unarmed structure at the
 * zone's node cell. While it stands the zone relays alarms both ways, its cradle rebuilds drones, its repair drones
 * work, and it answers the contingency's call; destroyed, the zone is on its own senses
 * ({@link SecurityZone#loseNode()}).
 *
 * <p>It is a structure as the drone hub is ({@link UnitType#DRONE_HUB_STRUCTURE}, role {@link UnitRole#STRUCTURE}), so
 * the company targets, damages and demolishes it as it does a hub, until the node's own look is picked.
 */
public final class DerelictNode {

    /** UNTUNED: heavier than a flock's cradle, a target a squad has to work at. */
    public static final float MAX_STRUCTURE = 360f;
    public static final float ARMOR_CAPACITY = 320f;
    public static final float ARMOR_RATING = 18f;

    private DerelictNode() {}

    /** The node's structure at {@code (cellX, cellY)}. */
    public static EntitySpec create(String id, Faction side, int cellX, int cellY) {
        return new EntitySpec(id, side, UnitType.DRONE_HUB_STRUCTURE, cellX, cellY)
                .health(MAX_STRUCTURE)
                .armor(ARMOR_CAPACITY, ARMOR_RATING)
                .attackDamage(0f)
                .attackRange(0f)
                .attackCooldown(1f)
                .accuracy(0f)
                .moveSpeed(0f)
                .role(UnitRole.STRUCTURE);
    }

    /**
     * Marks every zone whose node no longer stands as having lost it.
     *
     * @param nodes  each zone's node entity, by zone id
     * @param stands whether an entity still stands
     * @return the zones that lost their node on this call, in zone order
     */
    public static List<Integer> watch(SecurityNetwork network, Map<Integer, Long> nodes, LongPredicate stands) {
        List<Integer> lost = new ArrayList<>();
        for (SecurityZone zone : network.zones()) {
            Long node = nodes.get(zone.id);
            if (node == null || !zone.nodeAlive() || stands.test(node)) continue;
            zone.loseNode();
            lost.add(zone.id);
        }
        return lost;
    }
}

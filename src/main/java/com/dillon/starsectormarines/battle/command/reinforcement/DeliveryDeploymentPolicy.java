package com.dillon.starsectormarines.battle.command.reinforcement;

/**
 * Where a side considers it safe to put a delivery down, and who owns what it
 * delivers.
 *
 * <p>Asked by every means that has to arrive somewhere: a convoy for its entry
 * and its drop, a shuttle for its landing zone. It exists because a request's
 * rally says where force is <em>needed</em>, which on a losing track is exactly
 * where the enemy is — so the question of where a delivery can survive arriving
 * is a separate one, and only the command layer watching the front can answer
 * it.
 */
@FunctionalInterface
public interface DeliveryDeploymentPolicy {
    DeliveryDeployment deploymentFor(ReinforcementRequest request);
}

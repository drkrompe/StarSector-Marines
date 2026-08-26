package com.dillon.starsectormarines.battle.command.reinforcement;

/** Optional mission policy for convoy entry, drop placement, and delivered-squad ownership. */
@FunctionalInterface
public interface ConvoyDeploymentPolicy {
    ConvoyDeployment deploymentFor(ReinforcementRequest request);
}

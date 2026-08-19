package com.dillon.starsectormarines.campaign;

/** Resolves a persisted market-registry slot to player-facing location prose. */
@FunctionalInterface
public interface PatronTargetNameResolver {

    String displayName(int marketRegistryId);
}

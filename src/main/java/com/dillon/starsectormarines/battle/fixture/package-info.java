/**
 * Framework core (supporting) — versioned battle-construction metadata.
 *
 * <p>Category: framework core (headless construction seam; no feature owner).
 * <br>Charter: immutable plain-data inputs and codecs that rebuild battles
 *           through the production factories in {@code battle.setup}.
 * <br>Boundary: construction facts only. Mutable ECS state, campaign objects,
 *           test-only scene builders, command logs, and checkpoints do not
 *           belong here.
 *
 * <p>Design pointer: {@code battle-fixtures-nouns.md}.
 * See {@link com.dillon.starsectormarines.battle} for the full taxonomy.
 */
package com.dillon.starsectormarines.battle.fixture;

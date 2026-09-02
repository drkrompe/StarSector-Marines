/**
 * Screen copy written from the catalogs a Marine Operations screen shows.
 *
 * <p>Category: Marine Operations presentation. Charter: one place that turns a
 * catalog item — a weapon at a grade, an armour pattern, a special item, an
 * integral system, a mech chassis or one of its components, a collectible
 * template card — into the words and meters a player reads about it. The
 * value it produces is {@link com.dillon.starsectormarines.ui.spec.SpecSheet},
 * which lives in the generic toolkit because the runtime that paints it must
 * not know what a mech is.
 *
 * <p>Boundary: this package reads catalogs and writes strings. It holds no
 * screen state, builds no elements, and is never the authority for a number —
 * damage comes from {@code InfantryCombatStats}, a suit's capability from
 * {@link com.dillon.starsectormarines.ops.spec.IntegralSystemCopy}, a mech's
 * field note from {@code MechCatalog}. A screen never assembles a sheet by
 * hand from catalog fields; it asks
 * {@link com.dillon.starsectormarines.ops.spec.SpecSheets}.
 *
 * <p>Comparison meters are measured against
 * {@link com.dillon.starsectormarines.ops.spec.CatalogCeilings} rather than
 * against whatever else is on screen, so cycling an item produces a meaningful
 * change and a selection cannot rewrite the baseline
 * ({@code company-view-nouns.md}).
 *
 * <p>See {@code spec-sheet.md} and {@code equipment-lore-catalog.md}.
 */
package com.dillon.starsectormarines.ops.spec;

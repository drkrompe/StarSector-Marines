/**
 * Category: battle service.
 *
 * <p>Charter: state for close-contact special equipment — which contact a
 * committed carrier has claimed, and which cells were authored as breach
 * points a breaching tool may cut.
 *
 * <p>Boundary: this package holds no decision logic and applies no payload.
 * Target selection, legality, and channel lifecycle live in
 * {@code battle.infantry.CloseContactTactics}; damage, penetration, and wall
 * damage resolve through the shared durability and map-edit authorities from
 * the referenced weapon definition.
 *
 * <p>Pointer: {@code progression-nouns.md} — special equipment and its typed
 * activations.
 */
package com.dillon.starsectormarines.battle.contact;

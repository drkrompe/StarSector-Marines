/**
 * <b>Category:</b> asset store (data), not a simulation system.
 *
 * <p><b>Charter:</b> owns the data-backed portion of the weapon catalog — the
 * JSON schema, its parsed
 * {@link com.dillon.starsectormarines.battle.weapon.WeaponDef} shape, and the
 * id-addressed
 * {@link com.dillon.starsectormarines.battle.weapon.WeaponRegistry} that
 * serves it. Marine primaries are data-owned today; the shared schema is
 * intended to span secondaries, mech mounts, and turret mounts as their
 * stories ship.
 * {@link com.dillon.starsectormarines.battle.weapon.MountClass} distinguishes
 * them instead of a separate Java type per catalog.
 *
 * <p><b>Boundary:</b> this package stores and parses; it never fires, aims,
 * scores, or draws. Firing lives in {@code battle.infantry} /
 * {@code battle.combat}, presentation in {@code battle.combat.fx}, and AI
 * reasoning about weapons stays in {@code battle.decision}. Nothing here may
 * depend on a live simulation — a def is readable before a battle exists.
 *
 * <p><b>Pointer:</b> {@code moddable-weapons-nouns.md} for the
 * track, and {@code battle.world.tiles.TileRegistry} for the store pattern
 * this mirrors.
 */
package com.dillon.starsectormarines.battle.weapon;

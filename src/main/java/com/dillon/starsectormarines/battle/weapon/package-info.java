/**
 * <b>Category:</b> asset store (data), not a simulation system.
 *
 * <p><b>Charter:</b> owns the weapon catalog as data — the JSON schema, its
 * parsed {@link com.dillon.starsectormarines.battle.weapon.WeaponDef} shape,
 * and the id-addressed
 * {@link com.dillon.starsectormarines.battle.weapon.WeaponRegistry} that
 * serves it. One schema spans every catalog the mod ships (marine primaries
 * and secondaries, mech mounts, turret mounts);
 * {@link com.dillon.starsectormarines.battle.weapon.MountClass} distinguishes
 * them instead of a separate Java type per catalog.
 *
 * <p><b>Boundary:</b> this package stores and parses; it never fires, aims,
 * scores, or draws. Firing lives in {@code battle.infantry} /
 * {@code battle.combat}, presentation in {@code battle.combat.fx}, and AI
 * reasoning about weapons stays in {@code battle.decision}. Nothing here may
 * depend on a live simulation — a def is readable before a battle exists.
 *
 * <p><b>Pointer:</b> {@code roadmap/moddable-weapons/overview.md} for the
 * track, and {@code battle.world.tiles.TileRegistry} for the store pattern
 * this mirrors.
 */
package com.dillon.starsectormarines.battle.weapon;

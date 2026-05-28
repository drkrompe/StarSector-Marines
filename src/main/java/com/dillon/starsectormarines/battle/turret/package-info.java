/**
 * Actor domain — static emplacements.
 *
 * <p>Category: actor domain (static entity + behavior + lifecycle).
 * <br>Charter:  turrets and defense posts ({@code MapTurret},
 *           {@code DefensePost}, {@code TurretKind}, {@code TurretRole},
 *           {@code DefensePostKind}), their fire path
 *           ({@code TurretFireService}, {@code TurretFireSink},
 *           {@code TurretAim}), demolition
 *           ({@code TurretDemolitionSystem}), and the emplacement
 *           behaviors ({@code TurretBehavior}, {@code StructureBehavior}).
 * <br>Boundary: emplacement aiming/firing logic here; the shared
 *           fire&rarr;damage pipeline is {@code combat/}. Air-LoS aiming
 *           ({@code TurretAim#airLosVisible}) is intentionally uncached
 *           (per-caller air radii) — keep it off {@code nav/LosCache}.
 *
 * <p>See {@code roadmap/battle-reorg/overview.md} for the full taxonomy.
 */
package com.dillon.starsectormarines.battle.turret;

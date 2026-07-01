package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Authors the {@code SPRITE} component's facing/pose frame for every live
 * sheet-drawn unit ({@link BattleComponents#liveSprites}) — the presentation
 * system that replaces {@code ops.battleview.UnitRenderService}'s former
 * per-frame derivation with authored component data (
 * {@link com.dillon.starsectormarines.battle.unit.UnitType#drawnAsSheet()}
 * gates {@code SPRITE} membership at spawn; this system writes it every tick
 * thereafter). Pure column walk over {@link #liveSprites}; the facing/frame
 * math itself is the stateless {@link LiveAppearance} helper.
 *
 * <p><b>Tick placement is load-bearing.</b> This must run at the <em>tail</em>
 * of the sim tick — after every system that writes {@code COMBAT.targetId},
 * {@code MOVEMENT} path/idx, or decrements {@code COMBAT.cooldownTimer}, and
 * after the air/ground deboards — so it authors the <em>post-tick</em> facing
 * a render read this frame will see. Placed immediately before {@code
 * BattleSimulation}'s {@code entityWorld.flush()} call. The Phase-2 renderer
 * becomes a pure reader of what this system wrote last tick.
 *
 * <p>Nothing sim-side reads {@code SPRITE} — this is write-only presentation
 * data, same as {@code battle.air.AirAppearance}'s {@code APPEARANCE} columns.
 * A future cover-facing/LoS-cone feature promoting facing to sim-read state
 * would be a deliberate decision (the real {@code facingDegrees} steer-state
 * on air/vehicle bodies is the precedent for that shape), not an accident of
 * this system existing.
 */
public final class FacingSystem {

    private final EntityWorld world;
    private final BattleComponents components;
    private final UnitRosterService roster;

    public FacingSystem(EntityWorld world, BattleComponents components, UnitRosterService roster) {
        this.world = world;
        this.components = components;
        this.roster = roster;
    }

    /** Authors {@code SPRITE_INDEX}/{@code SPRITE_FLIP_V}/{@code SPRITE_SHEET} for every row in {@link BattleComponents#liveSprites}. */
    public void tick() {
        for (ArchetypeTable t : world.matched(components.liveSprites)) {
            boolean hasCombat = t.has(components.COMBAT);
            boolean hasMovement = t.has(components.MOVEMENT);
            boolean hasSecondary = t.has(components.SECONDARY_WEAPON);

            Object[] types = t.objects(components.IDENTITY, BattleComponents.IDENTITY_TYPE).array();
            float[] hp = t.floats(components.HEALTH, BattleComponents.HEALTH_HP).array();
            int[] cellX = t.ints(components.POSITION, BattleComponents.POSITION_CELL_X).array();
            int[] cellY = t.ints(components.POSITION, BattleComponents.POSITION_CELL_Y).array();
            int[] sheetSel = t.ints(components.SPRITE, BattleComponents.SPRITE_SHEET).array();
            int[] frameIdx = t.ints(components.SPRITE, BattleComponents.SPRITE_INDEX).array();
            int[] flipV = t.ints(components.SPRITE, BattleComponents.SPRITE_FLIP_V).array();

            float[] cooldownTimer = hasCombat
                    ? t.floats(components.COMBAT, BattleComponents.COMBAT_COOLDOWN_TIMER).array() : null;
            float[] attackCooldown = hasCombat
                    ? t.floats(components.COMBAT, BattleComponents.COMBAT_ATTACK_COOLDOWN).array() : null;
            long[] targetId = hasCombat
                    ? t.longs(components.COMBAT, BattleComponents.COMBAT_TARGET_ID).array() : null;

            Object[] paths = hasMovement
                    ? t.objects(components.MOVEMENT, BattleComponents.MOVEMENT_PATH).array() : null;
            int[] pathIdx = hasMovement
                    ? t.ints(components.MOVEMENT, BattleComponents.MOVEMENT_PATH_IDX).array() : null;

            float[] actionTimer = hasSecondary
                    ? t.floats(components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_ACTION_TIMER).array() : null;

            for (int r = 0, n = t.rowCount(); r < n; r++) {
                // A released-not-yet-transmuted row (killed this tick, death
                // drain hasn't transmuted it away yet): skip. The eventual
                // DeadBodySystem.onDeath write owns this row's SPRITE.
                if (hp[r] <= 0f) continue;

                UnitType type = (UnitType) types[r];
                boolean inAim = hasSecondary && actionTimer[r] > 0f;
                boolean up = LiveAppearance.weaponUp(inAim, type.combatant,
                        hasCombat ? cooldownTimer[r] : 0f, hasCombat ? attackCooldown[r] : 0f);

                // Facing source, exactly the renderer's fallback chain: aim at
                // a live target first, else the next path cell, else none
                // (defaults to SOUTH / S below). Non-combatants carry no
                // COMBAT — they have no target anyway, so this gates on both
                // hasCombat and type.combatant before any target read.
                int dx = 0;
                int dy = 0;
                boolean haveDelta = false;
                if (hasCombat && type.combatant) {
                    long tid = targetId[r];
                    if (tid != 0L && roster.isLive(tid)) {
                        int tcx = world.getInt(tid, components.POSITION, BattleComponents.POSITION_CELL_X);
                        int tcy = world.getInt(tid, components.POSITION, BattleComponents.POSITION_CELL_Y);
                        int tdx = tcx - cellX[r];
                        int tdy = tcy - cellY[r];
                        if (tdx != 0 || tdy != 0) {
                            dx = tdx;
                            dy = tdy;
                            haveDelta = true;
                        }
                    }
                }
                if (!haveDelta && hasMovement) {
                    int[] path = (int[]) paths[r];
                    int idx = pathIdx[r];
                    if (idx < Paths.cellCount(path)) {
                        int pdx = Paths.cellX(path, idx) - cellX[r];
                        int pdy = Paths.cellY(path, idx) - cellY[r];
                        if (pdx != 0 || pdy != 0) {
                            dx = pdx;
                            dy = pdy;
                            haveDelta = true;
                        }
                    }
                }

                if (type.frameLayout == UnitType.FrameLayout.EIGHT_WAY_NO_WEAPON_UP) {
                    LiveAppearance.EightWayFacing facing8 = haveDelta
                            ? LiveAppearance.eightWayFromDelta(dx, dy) : LiveAppearance.EightWayFacing.S;
                    frameIdx[r] = LiveAppearance.pickFrameEightWay(facing8);
                    flipV[r] = 0;
                } else {
                    LiveAppearance.Facing facing = haveDelta
                            ? LiveAppearance.facingFromDelta(dx, dy) : LiveAppearance.Facing.SOUTH;
                    frameIdx[r] = LiveAppearance.pickFrame(facing, up);
                    flipV[r] = LiveAppearance.flipV(facing, up) ? 1 : 0;
                }
                // Render-tier's frameIdx-out-of-range clamp (sheet-cache-dependent)
                // stays out of this system — it authors the unclamped logical frame.
                sheetSel[r] = inAim ? LiveAppearance.SHEET_SECONDARY_AIM : LiveAppearance.SHEET_BASE;
            }
        }
    }
}

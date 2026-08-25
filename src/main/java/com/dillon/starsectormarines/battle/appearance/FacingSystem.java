package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechWeapon;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.marine.SpecialActivation;
import com.dillon.starsectormarines.marine.SpecialUsePose;

/**
 * Authors the {@code SPRITE} component's facing/pose frame for every live
 * sheet-drawn unit ({@link BattleComponents#liveSprites}) — the presentation
 * system that replaces {@code ops.battleview.UnitRenderService}'s former
 * per-frame derivation with authored component data (
 * {@link com.dillon.starsectormarines.battle.unit.UnitType#drawnAsSheet()}
 * gates {@code SPRITE} membership at spawn; this system writes it every tick
 * thereafter). Pure column walk over {@link BattleComponents#liveSprites}; the facing/frame
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

    /**
     * Minimum applied speed, in cells/sec, for velocity to count as travel
     * when deriving facing/gait. Below it a unit reads as standing (no travel
     * delta — same rendering as a zeroed velocity). This is the deadband that
     * keeps residual {@code SeparationSystem} jostle on settled units from
     * vibrating body rotation and pulsing the walk pose: separation nudges on
     * a near-relaxed crowd are tiny and alternate direction tick to tick,
     * while the slowest genuine mover (mech, 1.15 cells/sec) and any real
     * shove (up to {@code SeparationSystem.MAX_PUSH_SPEED}, 1.5) sit well
     * above it. The only genuine motion below it is the final sub-step of an
     * arrival pin, where idling one tick early is invisible.
     */
    public static final float MIN_TRAVEL_SPEED = 0.5f;
    /** Civilian body/look rotation is visual state, smoothed across path changes. */
    public static final float CIVILIAN_TURN_RATE_DEGREES_PER_SECOND = 180f;
    /** Combatant torso rotation is visual only; firing never waits for alignment. */
    public static final float COMBATANT_TURN_RATE_DEGREES_PER_SECOND = 360f;
    private static final float MIN_TRAVEL_SPEED_SQ = MIN_TRAVEL_SPEED * MIN_TRAVEL_SPEED;
    private static final float CIVILIAN_TURN_STEP =
            CIVILIAN_TURN_RATE_DEGREES_PER_SECOND * BattleSimulation.TICK_DT;
    private static final float COMBATANT_TURN_STEP =
            COMBATANT_TURN_RATE_DEGREES_PER_SECOND * BattleSimulation.TICK_DT;

    private final EntityWorld world;
    private final BattleComponents components;
    private final UnitRosterService roster;
    /** Serial tick scratch for a target's continuous POSITION pair. */
    private final float[] targetPosition = new float[2];

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
            boolean hasLayeredAnimation = t.has(components.LAYERED_ANIMATION);
            boolean hasMechLayeredAnimation = t.has(components.MECH_LAYERED_ANIMATION);
            boolean hasMechLocomotion = t.has(components.MECH_LOCOMOTION);
            boolean hasMechLoadout = t.has(components.MECH_LOADOUT);

            Object[] types = t.objects(components.IDENTITY, BattleComponents.IDENTITY_TYPE).array();
            float[] hp = t.floats(components.HEALTH, BattleComponents.HEALTH_HP).array();
            // Continuous position columns; target-facing math floors per-row to
            // grid cells below, while travel facing uses the applied velocity.
            float[] posX = t.floats(components.POSITION, BattleComponents.POSITION_X).array();
            float[] posY = t.floats(components.POSITION, BattleComponents.POSITION_Y).array();
            int[] sheetSel = t.ints(components.SPRITE, BattleComponents.SPRITE_SHEET).array();
            int[] frameIdx = t.ints(components.SPRITE, BattleComponents.SPRITE_INDEX).array();
            int[] flipV = t.ints(components.SPRITE, BattleComponents.SPRITE_FLIP_V).array();

            float[] cooldownTimer = hasCombat
                    ? t.floats(components.COMBAT, BattleComponents.COMBAT_COOLDOWN_TIMER).array() : null;
            float[] attackCooldown = hasCombat
                    ? t.floats(components.COMBAT, BattleComponents.COMBAT_ATTACK_COOLDOWN).array() : null;
            long[] targetId = hasCombat
                    ? t.longs(components.COMBAT, BattleComponents.COMBAT_TARGET_ID).array() : null;
            long[] reflexTargetId = hasCombat
                    ? t.longs(components.COMBAT, BattleComponents.COMBAT_REFLEX_TARGET_ID).array() : null;

            float[] gaitPhase = hasMovement
                    ? t.floats(components.MOVEMENT, BattleComponents.MOVEMENT_GAIT_PHASE).array() : null;
            float[] velX = hasMovement
                    ? t.floats(components.MOVEMENT, BattleComponents.MOVEMENT_VEL_X).array() : null;
            float[] velY = hasMovement
                    ? t.floats(components.MOVEMENT, BattleComponents.MOVEMENT_VEL_Y).array() : null;

            float[] actionTimer = hasSecondary
                    ? t.floats(components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_ACTION_TIMER).array() : null;
            Object[] secondarySpec = hasSecondary
                    ? t.objects(components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_SPEC).array() : null;
            int[] secondaryFired = hasSecondary
                    ? t.ints(components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_FIRED).array() : null;
            long[] secondaryAimTarget = hasSecondary
                    ? t.longs(components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_AIM_TARGET_ID).array() : null;

            float[] layeredFacing = hasLayeredAnimation
                    ? t.floats(components.LAYERED_ANIMATION, BattleComponents.LAYERED_FACING_DEGREES).array() : null;
            float[] locomotionPhase = hasLayeredAnimation
                    ? t.floats(components.LAYERED_ANIMATION, BattleComponents.LAYERED_LOCOMOTION_PHASE).array() : null;
            float[] weaponPhase = hasLayeredAnimation
                    ? t.floats(components.LAYERED_ANIMATION, BattleComponents.LAYERED_WEAPON_PHASE).array() : null;
            float[] headLook = hasLayeredAnimation
                    ? t.floats(components.LAYERED_ANIMATION, BattleComponents.LAYERED_HEAD_LOOK_DEGREES).array() : null;
            int[] weaponPose = hasLayeredAnimation
                    ? t.ints(components.LAYERED_ANIMATION, BattleComponents.LAYERED_WEAPON_POSE).array() : null;
            int[] layeredFlags = hasLayeredAnimation
                    ? t.ints(components.LAYERED_ANIMATION, BattleComponents.LAYERED_FLAGS).array() : null;
            Object[] mechLoadout = hasMechLoadout
                    ? t.objects(components.MECH_LOADOUT, BattleComponents.MECH_LOADOUT_STATE).array() : null;
            float[] mechFacing = hasMechLayeredAnimation
                    ? t.floats(components.MECH_LAYERED_ANIMATION, BattleComponents.MECH_LAYERED_FACING_DEGREES).array() : null;
            float[] mechHipFacing = hasMechLayeredAnimation
                    ? t.floats(components.MECH_LAYERED_ANIMATION, BattleComponents.MECH_LAYERED_HIP_FACING_DEGREES).array() : null;
            float[] mechLocomotion = hasMechLayeredAnimation
                    ? t.floats(components.MECH_LAYERED_ANIMATION, BattleComponents.MECH_LAYERED_LOCOMOTION_PHASE).array() : null;
            float[] mechChaingunPhase = hasMechLayeredAnimation
                    ? t.floats(components.MECH_LAYERED_ANIMATION, BattleComponents.MECH_LAYERED_CHAINGUN_PHASE).array() : null;
            float[] mechSrmPhase = hasMechLayeredAnimation
                    ? t.floats(components.MECH_LAYERED_ANIMATION, BattleComponents.MECH_LAYERED_SRM_PHASE).array() : null;
            float[] mechLrmPhase = hasMechLayeredAnimation
                    ? t.floats(components.MECH_LAYERED_ANIMATION, BattleComponents.MECH_LAYERED_LRM_PHASE).array() : null;
            int[] mechFlags = hasMechLayeredAnimation
                    ? t.ints(components.MECH_LAYERED_ANIMATION, BattleComponents.MECH_LAYERED_FLAGS).array() : null;
            float[] mechSteeringFacing = hasMechLocomotion
                    ? t.floats(components.MECH_LOCOMOTION, BattleComponents.MECH_LOCOMOTION_FACING_DEGREES).array() : null;
            float[] mechAngularVelocity = hasMechLocomotion
                    ? t.floats(components.MECH_LOCOMOTION, BattleComponents.MECH_LOCOMOTION_ANGULAR_VELOCITY).array() : null;

            for (int r = 0, n = t.rowCount(); r < n; r++) {
                // A released-not-yet-transmuted row (killed this tick, death
                // drain hasn't transmuted it away yet): skip. The eventual
                // DeadBodySystem.onDeath write owns this row's SPRITE.
                if (hp[r] <= 0f) continue;

                UnitType type = (UnitType) types[r];
                boolean inAim = hasSecondary && actionTimer[r] > 0f;
                boolean up = LiveAppearance.weaponUp(inAim, type.combatant,
                        hasCombat ? cooldownTimer[r] : 0f, hasCombat ? attackCooldown[r] : 0f);

                // The grid cell this row occupies — floored locally because target
                // facing uses an integer cell delta while movement retains continuous
                // position.
                int rowCellX = (int) Math.floor(posX[r]);
                int rowCellY = (int) Math.floor(posY[r]);

                // Facing source: aim at a live target first, else use the applied
                // travel velocity below, else none (defaults to SOUTH / S below).
                // Non-combatants carry no
                // COMBAT — they have no target anyway, so this gates on both
                // hasCombat and type.combatant before any target read.
                int dx = 0;
                int dy = 0;
                boolean haveDelta = false;
                int targetDx = 0;
                int targetDy = 0;
                boolean haveTargetDelta = false;
                if (hasCombat && type.combatant) {
                    long tid = inAim && secondaryAimTarget != null && secondaryAimTarget[r] != 0L
                            ? secondaryAimTarget[r]
                            : (reflexTargetId[r] != 0L ? reflexTargetId[r] : targetId[r]);
                    if (tid != 0L && roster.isLive(tid)) {
                        world.readFloatPair(tid, components.POSITION,
                                BattleComponents.POSITION_X,
                                BattleComponents.POSITION_Y, targetPosition);
                        int tcx = (int) Math.floor(targetPosition[0]);
                        int tcy = (int) Math.floor(targetPosition[1]);
                        int tdx = tcx - rowCellX;
                        int tdy = tcy - rowCellY;
                        if (tdx != 0 || tdy != 0) {
                            targetDx = tdx;
                            targetDy = tdy;
                            haveTargetDelta = true;
                            dx = tdx;
                            dy = tdy;
                            haveDelta = true;
                        }
                    }
                }
                // Travel bearing from the velocity the mover actually applied
                // this tick, quantized to the same octants the old next-cell
                // delta produced. Cell deltas are unusable under the carrot
                // mover: nextPathCell − flooredCell is (0,0) for the second
                // half of every segment, which strobed the walk pose/facing.
                // Layered actors can walk while looking/aiming independently at
                // a target, so the travel delta is kept even when the legacy
                // sheet's target-first fallback already resolved.
                int travelDx = 0;
                int travelDy = 0;
                boolean haveTravelDelta = false;
                if (hasMovement
                        && velX[r] * velX[r] + velY[r] * velY[r] >= MIN_TRAVEL_SPEED_SQ) {
                    travelDx = octantComponent(velX[r], velY[r]);
                    travelDy = octantComponent(velY[r], velX[r]);
                    haveTravelDelta = travelDx != 0 || travelDy != 0;
                    if (!haveDelta && haveTravelDelta) {
                        dx = travelDx;
                        dy = travelDy;
                        haveDelta = true;
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
                MarineSecondary secondary = inAim && secondarySpec != null
                        ? (MarineSecondary) secondarySpec[r] : null;
                boolean utilityAction = secondary != null
                        && (secondary.activation() == SpecialActivation.UTILITY_SMOKE
                        || secondary.activation() == SpecialActivation.UTILITY_SATCHEL);
                sheetSel[r] = inAim && !utilityAction
                        ? LiveAppearance.SHEET_SECONDARY_AIM
                        : LiveAppearance.SHEET_BASE;

                if (hasLayeredAnimation) {
                    authorLayeredRow(r, type, hasCombat, hasMovement, inAim,
                            cooldownTimer, attackCooldown, actionTimer, secondarySpec,
                            secondaryFired, gaitPhase, haveTargetDelta, targetDx,
                            targetDy, haveTravelDelta, travelDx, travelDy, layeredFacing,
                            locomotionPhase, weaponPhase, headLook, weaponPose,
                            layeredFlags);
                }
                if (hasMechLayeredAnimation && hasMechLoadout && hasMechLocomotion) {
                    authorLayeredMechRow(r, hasMovement && haveTravelDelta, gaitPhase,
                            haveTargetDelta, targetDx, targetDy,
                            (MechLoadoutComponent) mechLoadout[r], mechFacing,
                            mechLocomotion, mechChaingunPhase, mechSrmPhase,
                            mechLrmPhase, mechFlags, mechSteeringFacing,
                            mechAngularVelocity, mechHipFacing);
                }
            }
        }
    }

    private static void authorLayeredMechRow(
            int row, boolean moving, float[] gaitPhase,
            boolean haveTargetDelta, int targetDx, int targetDy,
            MechLoadoutComponent loadout, float[] facing, float[] locomotion,
            float[] chaingunPhase, float[] srmPhase, float[] lrmPhase, int[] flags,
            float[] steeringFacing, float[] angularVelocity, float[] hipFacing) {
        // moving = translation actually applied this tick, so a pivot-gated
        // mech (path un-exhausted, translation held) correctly plays the
        // turn-step below instead of freezing mid-walk-stride.
        boolean turning = Math.abs(angularVelocity[row]) > 0.0001f;
        float hips = steeringFacing[row];
        hipFacing[row] = hips;
        // Unlike the infantry look-at, the mech torso is simulation state:
        // MechTurretSystem integrates it at a finite traverse rate and weapon
        // tracks require that same state to be on target before firing.
        facing[row] = loadout.torsoFacingDegrees;
        // Gait is gated on applied velocity (not on the raw phase): a stopped
        // unit keeps its stale phase value, and the gate is what returns it
        // to the idle pose.
        locomotion[row] = turning && !moving
                ? LayeredMechAppearance.turnStepPhase(steeringFacing[row])
                : LayeredAppearance.locomotionPhase(moving ? gaitPhase[row] : 0f);

        MechWeaponMount arms = loadout.mount(MechMountSlot.ARMS);
        MechWeaponMount srm = representativeMount(loadout, MechWeapon.SRM_POD);
        MechWeaponMount lrm = representativeMount(loadout, MechWeapon.LRM_ARTILLERY);
        boolean chaingunActive = arms != null && arms.burstRemaining > 0;
        boolean srmActive = activeMount(loadout, MechWeapon.SRM_POD) != null;
        boolean lrmActive = activeMount(loadout, MechWeapon.LRM_ARTILLERY) != null;
        chaingunPhase[row] = trackPhase(arms);
        srmPhase[row] = trackPhase(srm);
        lrmPhase[row] = trackPhase(lrm);

        int authoredFlags = moving ? LayeredMechAppearance.FLAG_MOVING : 0;
        if (turning) authoredFlags |= LayeredMechAppearance.FLAG_TURNING;
        if (chaingunActive) authoredFlags |= LayeredMechAppearance.FLAG_CHAINGUN_ACTIVE;
        if (srmActive) authoredFlags |= LayeredMechAppearance.FLAG_SRM_ACTIVE;
        if (lrmActive) authoredFlags |= LayeredMechAppearance.FLAG_LRM_ACTIVE;
        if (trackFlash(arms)) {
            authoredFlags |= LayeredMechAppearance.FLAG_CHAINGUN_FLASH;
        }
        if (anyTrackFlash(loadout, MechWeapon.SRM_POD)) {
            authoredFlags |= LayeredMechAppearance.FLAG_SRM_FLASH;
        }
        if (anyTrackFlash(loadout, MechWeapon.LRM_ARTILLERY)) {
            authoredFlags |= LayeredMechAppearance.FLAG_LRM_FLASH;
        }
        flags[row] = authoredFlags;
    }

    private static MechWeaponMount representativeMount(MechLoadoutComponent loadout,
                                                        MechWeapon weapon) {
        MechWeaponMount active = activeMount(loadout, weapon);
        if (active != null) return active;
        for (MechWeaponMount mount : loadout.mounts()) {
            if (mount != null && mount.weapon() == weapon) return mount;
        }
        return null;
    }

    private static MechWeaponMount activeMount(MechLoadoutComponent loadout,
                                                MechWeapon weapon) {
        for (MechWeaponMount mount : loadout.mounts()) {
            if (mount != null && mount.weapon() == weapon && mount.burstRemaining > 0) {
                return mount;
            }
        }
        return null;
    }

    private static float trackPhase(MechWeaponMount mount) {
        return mount != null
                ? LayeredMechAppearance.trackPhase(mount.burstTimer, mount.weapon().burstSpacing)
                : 0f;
    }

    private static boolean trackFlash(MechWeaponMount mount) {
        return mount != null && LayeredMechAppearance.trackFlash(
                mount.cooldown, mount.weapon().cooldown, mount.burstRemaining,
                mount.burstTimer, mount.weapon().burstSpacing);
    }

    private static boolean anyTrackFlash(MechLoadoutComponent loadout, MechWeapon weapon) {
        for (MechWeaponMount mount : loadout.mounts()) {
            if (mount != null && mount.weapon() == weapon && trackFlash(mount)) return true;
        }
        return false;
    }

    private static void authorLayeredRow(
            int row, UnitType type, boolean hasCombat, boolean hasMovement, boolean inAim,
            float[] cooldownTimer, float[] attackCooldown, float[] actionTimer,
            Object[] secondarySpec, int[] secondaryFired,
            float[] gaitPhase,
            boolean haveTargetDelta, int targetDx, int targetDy,
            boolean haveTravelDelta, int travelDx, int travelDy,
            float[] facing, float[] locomotion, float[] phase, float[] headLook,
            int[] pose, int[] flags) {

        int previousFlags = flags[row];
        // Nonzero applied velocity — true iff the mover translated this tick,
        // so held units (aim freeze, dwell with a retained path) idle cleanly.
        boolean moving = hasMovement && haveTravelDelta;
        boolean primaryUp = hasCombat && LiveAppearance.weaponUp(false, type.combatant,
                cooldownTimer[row], attackCooldown[row]);

        // While walking, shoulders follow travel unless the weapon is actively
        // shouldered. The helmet can continue tracking the target independently.
        int torsoDx = 0;
        int torsoDy = -1;
        boolean torsoTracksTarget = false;
        if ((inAim || primaryUp) && haveTargetDelta) {
            torsoDx = targetDx;
            torsoDy = targetDy;
            torsoTracksTarget = true;
        } else if (haveTravelDelta) {
            torsoDx = travelDx;
            torsoDy = travelDy;
        } else if (haveTargetDelta) {
            torsoDx = targetDx;
            torsoDy = targetDy;
            torsoTracksTarget = true;
        }
        float targetFacing = haveTargetDelta
                ? LayeredAppearance.facingDegrees(targetDx, targetDy) : 0f;
        float torsoFacing = torsoTracksTarget
                ? targetFacing : LayeredAppearance.facingDegrees(torsoDx, torsoDy);
        if (!type.combatant) {
            // A held civilian keeps the last authored look instead of snapping
            // to the generic south-idle fallback between movement substeps.
            // New travel bearings rotate on a shortest-arc turn rate so rapid
            // screen/path changes cannot flip the actor every simulation tick.
            float desiredFacing = haveTravelDelta ? torsoFacing : facing[row];
            torsoFacing = LayeredAppearance.approachFacing(
                    facing[row], desiredFacing, CIVILIAN_TURN_STEP);
        } else {
            torsoFacing = LayeredAppearance.approachFacing(
                    facing[row], torsoFacing, COMBATANT_TURN_STEP);
        }
        facing[row] = torsoFacing;
        // Retain the last gait sample while stationary. Idle clips ignore it,
        // but a newly-started action can settle out of the exact prior stride.
        locomotion[row] = LayeredAppearance.locomotionPhase(gaitPhase[row]);
        headLook[row] = haveTargetDelta
                ? LayeredAppearance.headLookDegrees(torsoFacing, targetFacing)
                : 0f;

        int authoredPose = LayeredAppearance.POSE_IDLE;
        float authoredPhase = 0f;
        int authoredFlags = moving ? LayeredAppearance.FLAG_MOVING : 0;

        if (inAim) {
            MarineSecondary secondary = (MarineSecondary) secondarySpec[row];
            float duration = secondary != null ? secondary.aimDuration() : 1f;
            float progress = clamp01((duration - actionTimer[row]) / duration);
            boolean fired = secondaryFired[row] != 0 || progress >= 0.5f;
            SpecialUsePose usePose = secondary.specialDef().presentation().usePose();
            boolean direct = secondary.activation() == SpecialActivation.DIRECT_EXPLOSIVE
                    || secondary.activation() == SpecialActivation.DIRECT_PRECISION;
            authoredPose = switch (usePose) {
                case THROW -> LayeredAppearance.POSE_SMOKE_THROW;
                case PLANT -> LayeredAppearance.POSE_SATCHEL_PLANT;
                case BRACED_RIFLE -> fired ? LayeredAppearance.POSE_AMR_FIRE
                        : LayeredAppearance.POSE_AMR_AIM;
                case SHOULDER_LAUNCHER -> fired ? LayeredAppearance.POSE_ROCKET_FIRE
                        : LayeredAppearance.POSE_ROCKET_AIM;
            };
            authoredPhase = direct
                    ? fired ? clamp01((progress - 0.5f) * 2f) : clamp01(progress * 2f)
                    : progress;
            if (!fired && authoredPhase < LayeredAppearance.ACTION_ENTRY_BLEND_PHASE
                    && (previousFlags & (LayeredAppearance.FLAG_MOVING
                    | LayeredAppearance.FLAG_ACTION_FROM_MOVING)) != 0) {
                authoredFlags |= LayeredAppearance.FLAG_ACTION_FROM_MOVING;
            }
            if (fired && usePose == SpecialUsePose.SHOULDER_LAUNCHER) {
                authoredFlags |= LayeredAppearance.FLAG_WEAPON_OVER_SHOULDER;
            }
            if (fired && direct) {
                float elapsedAfterFire = Math.max(0f, progress - 0.5f) * duration;
                if (elapsedAfterFire <= LayeredAppearance.ROCKET_FLASH_SECONDS) {
                    authoredFlags |= LayeredAppearance.FLAG_MUZZLE_FLASH;
                }
            }
        } else if (primaryUp) {
            float elapsed = Math.max(0f, attackCooldown[row] - cooldownTimer[row]);
            if (elapsed <= LayeredAppearance.PRIMARY_FLASH_SECONDS) {
                authoredPose = LayeredAppearance.POSE_FIRING;
                authoredPhase = clamp01(elapsed / LayeredAppearance.PRIMARY_FLASH_SECONDS);
                authoredFlags |= LayeredAppearance.FLAG_MUZZLE_FLASH;
            } else {
                authoredPose = LayeredAppearance.POSE_AIMED;
                authoredPhase = clamp01(elapsed / LiveAppearance.WEAPON_UP_TIME);
            }
        }

        pose[row] = authoredPose;
        phase[row] = authoredPhase;
        flags[row] = authoredFlags;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    /**
     * One axis of quantizing a velocity vector to the 8-way facing grid:
     * nonzero iff {@code a}'s magnitude puts the vector inside one of the
     * octant sectors that includes this axis (sector boundaries at 22.5°,
     * {@code tan(22.5°) ≈ 0.4142}). Call as {@code (octantComponent(vx, vy),
     * octantComponent(vy, vx))}; a zero vector yields (0, 0).
     */
    private static int octantComponent(float a, float b) {
        return Math.abs(a) > 0.41421357f * Math.abs(b) ? (a > 0f ? 1 : -1) : 0;
    }
}

package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.infantry.SmokeThrowCommit;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.mech.MechGaitState;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.marine.SpecialActivation;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialUsePose;

import java.util.function.LongUnaryOperator;

/**
 * Authors the {@code SPRITE} component's facing/pose frame for every live
 * posed unit ({@link BattleComponents#liveSprites}) — the presentation
 * system that replaces {@code ops.battleview.UnitRenderService}'s former
 * per-frame derivation with authored component data (
 * {@link com.dillon.starsectormarines.battle.unit.UnitType#carriesSprite()}
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
    /**
     * Point a mech that is not going anywhere, and leave it pointed.
     *
     * <p>For scenes that hold a still deck rather than run one: a berthed
     * machine has no velocity and no target, so the per-tick derivation below
     * has nothing to derive a heading from, and a scene that never ticks would
     * not run it anyway. Writing the steering heading alone is not enough
     * — the render reads the layered columns, and the tick that would normally
     * carry one to the other is exactly what is missing here.
     */
    public static void faceStanding(EntityWorld world, BattleComponents components,
                                    long id, float degrees) {
        float heading = LayeredAppearance.wrapDegrees(degrees);
        world.setFloat(id, components.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_FACING_DEGREES, heading);
        world.setFloat(id, components.MECH_LAYERED_ANIMATION,
                BattleComponents.MECH_LAYERED_FACING_DEGREES, heading);
        world.setFloat(id, components.MECH_LAYERED_ANIMATION,
                BattleComponents.MECH_LAYERED_HIP_FACING_DEGREES, heading);
        if (world.has(id, components.MECH_GAIT_STATE)) {
            MechGaitState gait = (MechGaitState) world.getObject(
                    id, components.MECH_GAIT_STATE,
                    BattleComponents.MECH_GAIT_STATE_STATE);
            if (gait != null) {
                gait.reset(world.getFloat(id, components.POSITION,
                                BattleComponents.POSITION_X),
                        world.getFloat(id, components.POSITION,
                                BattleComponents.POSITION_Y), heading);
            }
        }
    }

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
    /** The patient a medic is treating this tick, or zero; none by default. */
    private LongUnaryOperator treatmentPatient = unit -> 0L;
    public FacingSystem(EntityWorld world, BattleComponents components, UnitRosterService roster) {
        this.world = world;
        this.components = components;
        this.roster = roster;
    }

    /**
     * Where to read who is treating whom. A treating medic faces the patient,
     * lowers the weapon and, while standing, kneels with the treatment kit.
     */
    public void setTreatmentPatients(LongUnaryOperator treatmentPatient) {
        this.treatmentPatient = treatmentPatient != null ? treatmentPatient : unit -> 0L;
    }

    /** Authors the layered facing and pose columns for every row in {@link BattleComponents#liveSprites}. */
    public void tick() {
        tick(0L, Float.NaN, Float.NaN, false);
    }

    /** Manual infantry aim overrides target/travel facing without changing applied movement gait. */
    public void tick(long controlledUnitId, float aimX, float aimY) {
        tick(controlledUnitId, aimX, aimY, false);
    }

    /** Applied sprint motion keeps the cursor bearing while lowering the weapon pose. */
    public void tick(long controlledUnitId, float aimX, float aimY, boolean sprinting) {
        tick(controlledUnitId, aimX, aimY, sprinting, null);
    }

    /**
     * As above, with the controlled Marine holding the special its left button is on ({@code heldSpecial}),
     * or null while it is on the primary.
     */
    public void tick(long controlledUnitId, float aimX, float aimY, boolean sprinting,
                     HeldSpecialPose heldSpecial) {
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
            Object[] smokeCommit = hasSecondary
                    ? t.objects(components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_SMOKE_COMMIT).array() : null;

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
                long patient = hasCombat && type.combatant
                        ? treatmentPatient.applyAsLong(t.entityAt(r)) : 0L;
                boolean treating = patient != 0L && !inAim && roster.isAliveById(patient);
                SmokeThrowCommit throwCommit = inAim ? (SmokeThrowCommit) smokeCommit[r] : null;
                // A throw keeps facing its accepted point, including after control is handed back.
                float manualDx = (throwCommit != null ? throwCommit.aim().x() : aimX) - posX[r];
                float manualDy = (throwCommit != null ? throwCommit.aim().y() : aimY) - posY[r];
                boolean manualAim = (throwCommit != null || t.entityAt(r) == controlledUnitId)
                        && Float.isFinite(manualDx) && Float.isFinite(manualDy)
                        && (manualDx != 0f || manualDy != 0f);
                boolean sprintRow = sprinting && t.entityAt(r) == controlledUnitId
                        && type == UnitType.MARINE;
                HeldSpecialPose heldRow = hasSecondary && t.entityAt(r) == controlledUnitId
                        && type == UnitType.MARINE ? heldSpecial : null;
                float manualFacing = manualAim
                        ? AirBody.facingToward(manualDx, manualDy) : Float.NaN;
                // The grid cell this row occupies — floored locally because target
                // facing uses an integer cell delta while movement retains continuous
                // position.
                int rowCellX = (int) Math.floor(posX[r]);
                int rowCellY = (int) Math.floor(posY[r]);

                // Target bearing: the treated patient, else the secondary aim
                // target, else the reflex or current target. Non-combatants carry
                // no COMBAT, so this gates on both hasCombat and type.combatant
                // before any target read.
                int targetDx = 0;
                int targetDy = 0;
                boolean haveTargetDelta = false;
                if (!manualAim && hasCombat && type.combatant) {
                    long tid = treating ? patient
                            : inAim && secondaryAimTarget != null && secondaryAimTarget[r] != 0L
                            ? secondaryAimTarget[r]
                            : (reflexTargetId[r] != 0L ? reflexTargetId[r] : targetId[r]);
                    if (tid != 0L && roster.isAliveById(tid)) {
                        int tcx = (int) Math.floor(roster.world().x(tid));
                        int tcy = (int) Math.floor(roster.world().y(tid));
                        int tdx = tcx - rowCellX;
                        int tdy = tcy - rowCellY;
                        if (tdx != 0 || tdy != 0) {
                            targetDx = tdx;
                            targetDy = tdy;
                            haveTargetDelta = true;
                        }
                    }
                }
                // Travel bearing from the velocity the mover actually applied
                // this tick, quantized to octants. Cell deltas are unusable under
                // the carrot mover: nextPathCell - flooredCell is (0,0) for the
                // second half of every segment, which strobed the walk pose/facing.
                int travelDx = 0;
                int travelDy = 0;
                boolean haveTravelDelta = false;
                if (hasMovement
                        && velX[r] * velX[r] + velY[r] * velY[r] >= MIN_TRAVEL_SPEED_SQ) {
                    travelDx = octantComponent(velX[r], velY[r]);
                    travelDy = octantComponent(velY[r], velX[r]);
                    haveTravelDelta = travelDx != 0 || travelDy != 0;
                }

                if (hasLayeredAnimation) {
                    authorLayeredRow(r, type, hasCombat, hasMovement, inAim,
                            cooldownTimer, attackCooldown, actionTimer, secondarySpec,
                            secondaryFired, gaitPhase, haveTargetDelta, targetDx,
                            targetDy, haveTravelDelta, travelDx, travelDy, layeredFacing,
                            locomotionPhase, weaponPhase, headLook, weaponPose,
                            layeredFlags, manualFacing, sprintRow, treating, heldRow);
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

        MechWeaponMount arms = activeArm(loadout);
        MechWeaponMount srm = representativeMount(loadout, WeaponRegistry.MECH_SRM_POD_ID);
        if (srm == null) {
            srm = representativeMount(loadout, WeaponRegistry.MECH_PIONEER_ROCKET_ID);
        }
        MechWeaponMount lrm = representativeMount(loadout, WeaponRegistry.MECH_LRM_ARTILLERY_ID);
        boolean chaingunActive = arms != null && arms.firing();
        boolean srmActive = activeMount(loadout, WeaponRegistry.MECH_SRM_POD_ID) != null
                || activeMount(loadout, WeaponRegistry.MECH_PIONEER_ROCKET_ID) != null;
        boolean lrmActive = activeMount(loadout, WeaponRegistry.MECH_LRM_ARTILLERY_ID) != null;
        chaingunPhase[row] = trackPhase(arms);
        srmPhase[row] = trackPhase(srm);
        lrmPhase[row] = trackPhase(lrm);

        int authoredFlags = moving ? LayeredMechAppearance.FLAG_MOVING : 0;
        if (turning) authoredFlags |= LayeredMechAppearance.FLAG_TURNING;
        if (chaingunActive) authoredFlags |= LayeredMechAppearance.FLAG_CHAINGUN_ACTIVE;
        if (srmActive) authoredFlags |= LayeredMechAppearance.FLAG_SRM_ACTIVE;
        if (lrmActive) authoredFlags |= LayeredMechAppearance.FLAG_LRM_ACTIVE;
        if (anyArmFlash(loadout)) authoredFlags |= LayeredMechAppearance.FLAG_CHAINGUN_FLASH;
        if (anyTrackFlash(loadout, WeaponRegistry.MECH_SRM_POD_ID)
                || anyTrackFlash(loadout, WeaponRegistry.MECH_PIONEER_ROCKET_ID)) {
            authoredFlags |= LayeredMechAppearance.FLAG_SRM_FLASH;
        }
        if (anyTrackFlash(loadout, WeaponRegistry.MECH_LRM_ARTILLERY_ID)) {
            authoredFlags |= LayeredMechAppearance.FLAG_LRM_FLASH;
        }
        if (anyTrackFlash(loadout, WeaponRegistry.MECH_SHOULDER_LASER_ID)) {
            authoredFlags |= LayeredMechAppearance.FLAG_LASER_FLASH;
        }
        if (anyTrackFlash(loadout, WeaponRegistry.MECH_THERMAL_LANCE_ID)) {
            authoredFlags |= LayeredMechAppearance.FLAG_LASER_FLASH;
        }
        flags[row] = authoredFlags;
    }

    private static MechWeaponMount representativeMount(MechLoadoutComponent loadout,
                                                        String weaponId) {
        MechWeaponMount active = activeMount(loadout, weaponId);
        if (active != null) return active;
        for (MechWeaponMount mount : loadout.mounts()) {
            if (mount != null && sameRack(mount.weaponId(), weaponId)) return mount;
        }
        return null;
    }

    private static MechWeaponMount activeMount(MechLoadoutComponent loadout,
                                                String weaponId) {
        for (MechWeaponMount mount : loadout.mounts()) {
            if (mount != null && sameRack(mount.weaponId(), weaponId)
                    && mount.burstRemaining > 0) {
                return mount;
            }
        }
        return null;
    }

    /** An SRM or LRM family id stands for every warhead of that rack. */
    private static boolean sameRack(String mountWeaponId, String weaponId) {
        if (WeaponRegistry.MECH_SRM_POD_ID.equals(weaponId)) return WeaponRegistry.isMechSrm(mountWeaponId);
        if (WeaponRegistry.MECH_LRM_ARTILLERY_ID.equals(weaponId)) return WeaponRegistry.isMechLrm(mountWeaponId);
        return mountWeaponId.equals(weaponId);
    }

    private static float trackPhase(MechWeaponMount mount) {
        if (mount != null && mount.isRotary()) {
            // A rotary gun recoils round by round at the rate its spin has reached.
            float spacing = 1f / mount.rotary.rate();
            return LayeredMechAppearance.trackPhase(spacing - Math.min(spacing, mount.rotary.sinceRound()), spacing);
        }
        return mount != null
                ? LayeredMechAppearance.trackPhase(mount.burstTimer, mount.weaponDef().burstSpacing)
                : 0f;
    }

    private static boolean trackFlash(MechWeaponMount mount) {
        if (mount != null && mount.isRotary()) return mount.rotary.sinceRound() <= LayeredMechAppearance.FLASH_SECONDS;
        return mount != null && LayeredMechAppearance.trackFlash(
                mount.cycleRemaining, mount.component.cycleSeconds(), mount.burstRemaining,
                mount.burstTimer, mount.weaponDef().burstSpacing);
    }

    /** The arm gun mid-burst, else the first fitted one: the track the arm recoil follows. */
    private static MechWeaponMount activeArm(MechLoadoutComponent loadout) {
        for (MechMountSlot slot : MechLoadoutComponent.ARM_SLOTS) {
            MechWeaponMount mount = loadout.mount(slot);
            if (mount != null && mount.firing()) return mount;
        }
        return loadout.primaryArm();
    }

    private static boolean anyArmFlash(MechLoadoutComponent loadout) {
        for (MechMountSlot slot : MechLoadoutComponent.ARM_SLOTS) {
            if (trackFlash(loadout.mount(slot))) return true;
        }
        return false;
    }

    private static boolean anyTrackFlash(MechLoadoutComponent loadout, String weaponId) {
        for (MechWeaponMount mount : loadout.mounts()) {
            if (mount != null && sameRack(mount.weaponId(), weaponId) && trackFlash(mount)) {
                return true;
            }
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
            int[] pose, int[] flags, float manualFacing, boolean sprinting, boolean treating,
            HeldSpecialPose held) {

        int previousFlags = flags[row];
        boolean manualAim = Float.isFinite(manualFacing);
        // Nonzero applied velocity — true iff the mover translated this tick,
        // so held units (aim freeze, dwell with a retained path) idle cleanly.
        boolean moving = hasMovement && haveTravelDelta;
        boolean primaryUp = !sprinting && !treating && hasCombat && LiveAppearance.weaponUp(false, type.combatant,
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
        float targetFacing = manualAim ? manualFacing : haveTargetDelta
                ? LayeredAppearance.facingDegrees(targetDx, targetDy) : 0f;
        float torsoFacing = manualAim ? manualFacing : torsoTracksTarget
                ? targetFacing : LayeredAppearance.facingDegrees(torsoDx, torsoDy);
        if (manualAim) {
            // Infantry point fire uses this bearing immediately. The visible
            // weapon must share it rather than lag behind the player's shot.
            torsoFacing = manualFacing;
        } else if (!type.combatant) {
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
        headLook[row] = manualAim || haveTargetDelta
                ? LayeredAppearance.headLookDegrees(torsoFacing, targetFacing)
                : 0f;

        int authoredPose = LayeredAppearance.POSE_IDLE;
        float authoredPhase = 0f;
        int authoredFlags = moving ? LayeredAppearance.FLAG_MOVING : 0;

        if (inAim) {
            SpecialEquipmentDef secondary = (SpecialEquipmentDef) secondarySpec[row];
            float duration = secondary != null ? secondary.aimDuration() : 1f;
            float progress = clamp01((duration - actionTimer[row]) / duration);
            boolean fired = secondaryFired[row] != 0 || progress >= 0.5f;
            SpecialUsePose usePose = secondary.presentation().usePose();
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
        } else if (held != null && !sprinting && secondarySpec[row] instanceof SpecialEquipmentDef special
                && special.isDirectFireWeapon()) {
            // A controlled Marine whose left button is on a special carries it in place of the
            // primary, walking or standing: raised over the wind-up, then held at the aim, and
            // through its firing clip for the follow-through after each round.
            boolean launcher = special.presentation().usePose() == SpecialUsePose.SHOULDER_LAUNCHER;
            float followThrough = Math.max(TICK_FLOOR, special.aimDuration() * 0.5f);
            boolean fired = held.sinceFired() <= followThrough;
            authoredPose = launcher
                    ? fired ? LayeredAppearance.POSE_ROCKET_FIRE : LayeredAppearance.POSE_ROCKET_AIM
                    : fired ? LayeredAppearance.POSE_AMR_FIRE : LayeredAppearance.POSE_AMR_AIM;
            authoredPhase = fired ? clamp01(held.sinceFired() / followThrough) : clamp01(held.readiness());
            if (!fired && authoredPhase < LayeredAppearance.ACTION_ENTRY_BLEND_PHASE && moving) {
                authoredFlags |= LayeredAppearance.FLAG_ACTION_FROM_MOVING;
            }
            // Readied and walking, it is carried in the item's walking clip; stopping brings it back up to the aim.
            // Both ways it moves over a short blend, and a round snaps it to the aim at once.
            float carried = (previousFlags & LayeredAppearance.FLAG_SPECIAL_CARRIED) != 0 ? phase[row] : 0f;
            boolean carrying = !fired && moving && held.readiness() >= 1f && special.presentation().layerClips() != null
                    && special.presentation().layerClips().walking() != null;
            carried = fired ? 0f : clamp01(carried + (carrying ? TICK_FLOOR : -TICK_FLOOR) / CARRY_BLEND_SECONDS);
            if (carried > 0f) {
                authoredPhase = carried;
                authoredFlags |= LayeredAppearance.FLAG_SPECIAL_CARRIED;
            }
            if (fired && launcher) authoredFlags |= LayeredAppearance.FLAG_WEAPON_OVER_SHOULDER;
            if (fired && held.sinceFired() <= LayeredAppearance.ROCKET_FLASH_SECONDS) {
                authoredFlags |= LayeredAppearance.FLAG_MUZZLE_FLASH;
            }
        } else if (held != null && !sprinting && secondarySpec[row] instanceof SpecialEquipmentDef special
                && special.isHeldStream()) {
            // The flamer's wand in place of the primary, through the item's own clips: raised from the hip over
            // the wind-up, and in its firing clip while a charge of the jet is leaving. The jet is the flash.
            float followThrough = Math.max(TICK_FLOOR, special.weaponDef().flightSec);
            boolean streaming = held.sinceFired() <= followThrough;
            authoredPose = streaming ? LayeredAppearance.POSE_AMR_FIRE : LayeredAppearance.POSE_AMR_AIM;
            authoredPhase = streaming ? clamp01(held.sinceFired() / followThrough) : clamp01(held.readiness());
        } else if (treating && !moving) {
            // Kneeling over the patient. A medic following a moving patient
            // walks normally and only the battlefield mark says they are treating.
            authoredPose = LayeredAppearance.POSE_SATCHEL_PLANT;
            authoredPhase = LayeredAppearance.TREATING_PHASE;
            authoredFlags |= LayeredAppearance.FLAG_TREATING;
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
        } else if (manualAim && !sprinting) {
            authoredPose = LayeredAppearance.POSE_AIMED;
        }

        pose[row] = authoredPose;
        phase[row] = authoredPhase;
        flags[row] = authoredFlags;
    }

    /** A follow-through never shorter than one tick, so a round always shows its firing frame. */
    private static final float TICK_FLOOR = BattleSimulation.TICK_DT;
    /** Seconds a readied special takes to go from the aim to its walking carry, and back. */
    static final float CARRY_BLEND_SECONDS = 0.2f;

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

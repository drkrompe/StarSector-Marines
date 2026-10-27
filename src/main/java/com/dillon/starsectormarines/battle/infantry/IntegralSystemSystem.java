package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.ShotService;
import com.dillon.starsectormarines.battle.control.ManualIntent;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.CombatService;
import com.dillon.starsectormarines.battle.sim.MovementService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.marine.ExposedUnderFireSpec;
import com.dillon.starsectormarines.marine.FieldAidSpec;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.MissilePodSpec;
import com.dillon.starsectormarines.marine.MissilePodMountGeometry;
import com.dillon.starsectormarines.marine.SightedStandoffSpec;

import java.util.Locale;
import java.util.Random;
import java.util.TreeMap;

/**
 * Stateless per-tick sweep over the units whose armour pattern carries a
 * capability: drains their clocks, drops an expired effect, and decides when a
 * system is worth spending ({@code integral-armor-systems.md}).
 *
 * <p>A <b>System</b> (processor) — the live state lives on
 * {@link IntegralSystemService}, which this drives. It runs as its own sweep
 * rather than inside a behaviour's prep so a unit's clocks drain no matter what
 * that unit is doing; the cooldown-drain-during-a-long-approach lesson from
 * {@code InfantryUnitPrep.tickCooldowns} applies here for the same reason.
 *
 * <p><b>This sweep holds no judgement about any suit.</b> It dispatches on the
 * {@link com.dillon.starsectormarines.marine.SpecialAiPolicy} each system
 * declares and reads that system's own authored numbers to decide whether the
 * policy's moment has arrived. There is deliberately no threat radius, no range
 * band, and no per-effect special case left in this class: a constant here
 * would be one author's judgement about one suit imposed on every system that
 * will ever exist, which is exactly what the authored policy replaced
 * ({@code progression-nouns.md}).
 *
 * <p><b>What is a fact and what is a judgement.</b> Whether a marine is under
 * fire, how much of it, from where, whether the ground covers that bearing,
 * whether they are under way, what they are engaging, how far off it is, and
 * who else is standing near them are all facts, computed here and not
 * authorable — a suit does not get an opinion about whether it is being shot at
 * or whether it has stopped walking. The authored numbers are only ever the
 * ones that price the spend: how much incoming is worth a cooldown, how much
 * cover makes spending pointless. That split is why the occasions can be added to
 * without touching a catalog, and why retuning a suit never needs code.
 *
 * <p><b>Read facts that do not depend on where in the tick they are asked.</b>
 * This sweep runs <em>ahead</em> of the movement pass, so that an activation's
 * speed multiplier is already in place when the wearer steps. That means every
 * mover's applied velocity is the zero {@code MovementService.beginTick} just
 * wrote, and any trigger reading it is dead on arrival — which is exactly what
 * happened to this policy's predecessor, silently, for its whole shipped life.
 * Path state ({@code MovementService.settled}) and the incoming-fire signal
 * ({@code CombatService.incomingPressure}, written by {@code SquadAlertSystem}
 * earlier in the same tick) both read the same from anywhere in the tick.
 * A manually driven wearer has no path: its legal terrain-swept proposed step
 * supplies movement and heading without publishing velocity or advancing gait.
 *
 * <p><b>Nothing here reads faction.</b> A defender in a system-carrying pattern
 * reaches this sweep through the same component the player's marines do and is
 * offered the same reason to spend it; there is no defender branch and no
 * defender-only tuning field to add one with.
 *
 * <p><b>The pod picks its own target.</b> It does not read the wearer's
 * engaged target ({@code World#targetId}) — that would make the suit a second
 * trigger on the marine's own fight. A capability that starts choosing its own
 * fights is a deliberate step past "the suit changes how its wearer moves"
 * ({@code integral-armor-systems.md}'s open question, resolved this way
 * because it is the more interesting half of the model to prove out.
 */
public final class IntegralSystemSystem {

    /**
     * <b>TESTING SCAFFOLD — not a shipping behaviour.</b> Overrides when a
     * screen is raised so that "is the trigger too rare, or is the effect not
     * drawing?" can be answered by removing the first possibility.
     *
     * <p>Set with {@code -Dmarines.debug.screenTrigger=<mode>}:
     * <ul>
     *   <li>{@code off} — the shipped policy decides, as it does in a real game.
     *   <li>{@code damage} — raise it the moment anything hurts the wearer.
     *       Narrower than it sounds and narrower than the shipped policy:
     *       measured over a Conquest battle it fired 54 times against the
     *       policy's 78, because most rounds miss and a miss is still fire you
     *       are under.
     *   <li>{@code always} — raise it whenever it is off cooldown, with no
     *       reason at all. The only mode that makes the treatment continuously
     *       visible: duty cycle becomes duration/(duration+cooldown), roughly a
     *       fifth of the time, against the shipped policy's measured 0.6%.
     * </ul>
     *
     * <p>Every mode but {@code off} makes durability measurement meaningless —
     * a screen is up during fights that would not have had one.
     */
    public enum DebugScreenTrigger { OFF, DAMAGE, ALWAYS }

    /** The scaffold mode in force. {@link DebugScreenTrigger#OFF} is the shipping value. */
    public static final DebugScreenTrigger DEBUG_SCREEN_TRIGGER = DebugScreenTrigger.valueOf(
            System.getProperty("marines.debug.screenTrigger", "OFF").toUpperCase(Locale.ROOT));

    /**
     * How recently a hit has to have landed to still count as "just now" under
     * {@link DebugScreenTrigger#DAMAGE}. A third of a second, so one round
     * raises the screen once rather than re-raising it every tick of a burst.
     */
    private static final int DEBUG_RECENT_DAMAGE_TICKS = 10;

    static {
        if (DEBUG_SCREEN_TRIGGER != DebugScreenTrigger.OFF) {
            // Announced rather than silent. A scaffold that only shows up as
            // "the shields look wrong" is a scaffold that gets shipped.
            System.err.println("[starsector-marines] INTEGRAL SYSTEM DEBUG SCAFFOLD ACTIVE: "
                    + "screens raise on " + DEBUG_SCREEN_TRIGGER
                    + ", not on the shipped policy. Set"
                    + " -Dmarines.debug.screenTrigger=off (or change the default in"
                    + " IntegralSystemSystem) before judging balance.");
        }
    }

    /** Below this, the unit is standing still and has nothing to charge through. */
    private static final float MOVING_EPSILON = 1e-3f;

    private final UnitRosterService rosterService;
    private final BallisticResolver resolver;
    private final ShotService shots;
    private final Random rng;
    private final LongBucket nearbySquadmates = new LongBucket();
    private final TreeMap<Integer, PodCandidate> podCandidates = new TreeMap<>();
    /** Reused by the serial per-carrier pass; no policy keeps it past its own call. */
    private final PolicyMotion motionScratch = new PolicyMotion(false, 0f, 0f);
    private boolean laneDiscipline;

    private record PodCandidate(long shooter, long target, float bodyBearing,
                                MissilePodMountGeometry.Pose mount, IntegralSystemDef def,
                                Squad squad) {}

    public IntegralSystemSystem(UnitRosterService rosterService, BallisticResolver resolver,
                                ShotService shots, Random rng) {
        this.rosterService = rosterService;
        this.resolver = resolver;
        this.shots = shots;
        this.rng = rng;
    }

    /**
     * Whether a pod holds a salvo with a friend in its lane, or one whose resolved burst would reach a friend or
     * the wearer ({@link BallisticResolver#friendInLane}, {@link BallisticResolver#friendInBlast}): the fire
     * control a chassis already has. A held salvo spends nothing and is offered again on the next tick.
     */
    public void setLaneDiscipline(boolean laneDiscipline) {
        this.laneDiscipline = laneDiscipline;
    }

    /**
     * Drains every carrier's clocks and offers the ready ones a reason to fire.
     */
    public void tick(float dt, BattleSimulation sim) {
        IntegralSystemService systems = rosterService.integralSystems();
        MovementService movement = rosterService.movement();
        long[] live = rosterService.denseArray();
        int count = rosterService.liveCount();
        podCandidates.clear();
        for (int i = 0; i < count; i++) {
            long id = live[i];
            if (!systems.has(id)) continue;
            systems.tick(id, dt);
            if (!systems.canActivate(id)) continue;
            IntegralSystemDef def = systems.spec(id);
            if (def == null) continue;
            if (!permitsAutonomousActivation(def, sim.directControl().isControlling(id))) continue;
            ManualTerrainMotion.Result manualStep = null;
            if (sim.directControl().isControlling(id)) {
                ManualIntent intent = sim.directControl().intent();
                manualStep = movement.previewDirect(id, sim.getGrid(), intent.moveX(),
                        intent.moveY(), sim.physicalRadius(id), dt);
            }
            PolicyMotion motion = policyMotion(motionScratch, id, movement, manualStep, dt);
            switch (def.aiPolicy()) {
                case EXPOSED_UNDER_FIRE -> {
                    if (exposedUnderFire(id, def.exposedUnderFire(), sim, motion)) {
                        systems.activate(id);
                    }
                }
                case SIGHTED_STANDOFF_CONTACT -> {
                    Squad squad = rosterService.squad().hasSquad(id)
                            ? sim.getSquad(rosterService.squad().squadId(id)) : null;
                    if (squad != null && !squad.podCadence.ready(sim.getSimTickIndex())) continue;
                    long target = standoffTarget(id, def, sim);
                    if (target != 0L) {
                        float bodyBearing = podBodyBearing(id, sim, motion);
                        MissilePodMountGeometry.Pose mount = podPose(id, target, bodyBearing, def.missilePod());
                        // A protruding mount must never teleport a round across a wall.
                        // Prove the body-to-mouth segment before spending ammo/cooldown.
                        boolean legalOrigin = def.missilePod().mount() == null
                                || legalPodOrigin(sim.getGrid(), rosterService.world().renderX(id),
                                    rosterService.world().renderY(id), mount);
                        // A squadmate between the rail and the target is where the round goes first.
                        boolean clearLane = !laneDiscipline || !resolver.friendInLane(id,
                                mount.muzzleX(), mount.muzzleY(), rosterService.identity().faction(id),
                                rosterService.world().renderX(target),
                                rosterService.world().renderY(target), target);
                        if (legalOrigin && clearLane) {
                            PodCandidate candidate = new PodCandidate(id, target, bodyBearing,
                                    mount, def, squad);
                            if (squad == null) {
                                launchPod(candidate, systems, sim.getSimTickIndex());
                            } else if (squad.podCadence.ready(sim.getSimTickIndex())) {
                                PodCandidate incumbent = podCandidates.get(squad.id);
                                if (squad.podCadence.prefers(id,
                                        incumbent == null ? 0L : incumbent.shooter())) {
                                    podCandidates.put(squad.id, candidate);
                                }
                            }
                        }
                    }
                }
                case WOUNDED_SQUADMATE_IN_REACH -> {
                    long patient = worstWoundedInReach(id, def.fieldAid(), sim);
                    if (patient != 0L && systems.activate(id)) {
                        treat(patient, def.fieldAid());
                    }
                }
                default -> { /* No integral system declares the carried-item policies. */ }
            }
        }
        // All carriers' timers and live legality have been evaluated before
        // choosing a successor. Dense-roster order cannot monopolize the pod.
        for (PodCandidate candidate : podCandidates.values()) {
            launchPod(candidate, systems, sim.getSimTickIndex());
        }
        podCandidates.clear();
    }

    private void launchPod(PodCandidate candidate, IntegralSystemService systems, int tick) {
        MissilePodSpec pod = candidate.def().missilePod();
        BallisticResolver.Resolution[] salvo = resolveSalvo(candidate.shooter(), candidate.target(),
                pod, candidate.mount());
        // Where each round will actually stop, cover clipped near the rail included: a salvo with any
        // burst reaching a friend or the wearer stays on the rail.
        if (laneDiscipline && salvoEndangersFriends(candidate.shooter(), pod.weaponDef(), salvo)) return;
        if (!systems.activate(candidate.shooter())) return;
        fireMissilePod(candidate.shooter(), pod, candidate.mount(), salvo);
        systems.recordPodLaunch(candidate.shooter(), candidate.mount().muzzleX(),
                candidate.mount().muzzleY(), candidate.bodyBearing(),
                candidate.mount().bearingDegrees());
        if (candidate.squad() != null) {
            candidate.squad().podCadence.committed(candidate.shooter(), tick,
                    candidate.def().sightedStandoff().squadSalvoIntervalSeconds(),
                    BattleSimulation.TICK_DT);
        }
    }

    /**
     * The squadmate most worth a dressing: the lowest health fraction among
     * living allies within reach that is still below the authored threshold.
     * Returns {@code 0L} when nobody qualifies, in which case the satchel stays
     * shut — a finite supply spent on a scratch is the waste this policy's
     * threshold exists to prevent.
     *
     * <p><b>Never the carrier.</b> The whole value of this role is that it
     * belongs to the section rather than to the marine carrying it, and a medic
     * who treated themselves first would be a self-heal with a squad system's
     * name on it.
     */
    private long worstWoundedInReach(long carrier, FieldAidSpec aid, BattleSimulation sim) {
        if (aid == null) return 0L;
        UnitSpatialIndex index = sim.getUnitIndex();
        if (index == null) return 0L;
        Faction faction = rosterService.identity().faction(carrier);
        if (faction == null) return 0L;
        World world = rosterService.world();
        nearbySquadmates.clear();
        index.gatherFaction(world.x(carrier), world.y(carrier), aid.reachCells(),
                faction, nearbySquadmates);

        long worst = 0L;
        float worstFraction = aid.treatBelowHealthFraction();
        for (int i = 0; i < nearbySquadmates.size; i++) {
            long candidate = nearbySquadmates.ids[i];
            if (candidate == carrier) continue;
            if (!rosterService.isAliveById(candidate)) continue;
            if (!rosterService.identity().type(candidate).combatant) continue;
            float maximum = world.maxHp(candidate);
            if (maximum <= 0f) continue;
            float fraction = world.hp(candidate) / maximum;
            if (fraction < worstFraction) {
                worstFraction = fraction;
                worst = candidate;
            }
        }
        return worst;
    }

    /**
     * Puts a dressing on. Flat health rather than a fraction, clamped at the
     * patient's own maximum, so one satchel is worth the same everywhere and
     * simply goes further on somebody with less to lose.
     */
    private void treat(long patient, FieldAidSpec aid) {
        World world = rosterService.world();
        float maximum = world.maxHp(patient);
        world.setHp(patient, Math.min(maximum, world.hp(patient) + aid.restoredHealth()));
    }

    /** Facts for the early policy pass; manual preview never mutates movement. */
    static final class PolicyMotion {
        private boolean underway;
        private float vx;
        private float vy;

        PolicyMotion(boolean underway, float vx, float vy) {
            set(underway, vx, vy);
        }

        PolicyMotion set(boolean underway, float vx, float vy) {
            this.underway = underway;
            this.vx = vx;
            this.vy = vy;
            return this;
        }

        boolean underway() { return underway; }

        float vx() { return vx; }

        float vy() { return vy; }
    }

    /** Only the supported shield is player-owned; unrelated suit policies continue normally. */
    static boolean permitsAutonomousActivation(IntegralSystemDef def, boolean controlled) {
        return !controlled || !def.grantsMitigation();
    }

    static PolicyMotion policyMotion(long id, MovementService movement,
                                     ManualTerrainMotion.Result manualStep, float dt) {
        return policyMotion(new PolicyMotion(false, 0f, 0f), id, movement, manualStep, dt);
    }

    /** As above, filling {@code out} so the per-carrier pass allocates nothing. */
    static PolicyMotion policyMotion(PolicyMotion out, long id, MovementService movement,
                                     ManualTerrainMotion.Result manualStep, float dt) {
        if (manualStep == null) {
            if (!movement.has(id)) return out.set(false, 0f, 0f);
            return out.set(!movement.settled(id), movement.velX(id), movement.velY(id));
        }
        float vx = dt > 0f ? manualStep.dx() / dt : 0f;
        float vy = dt > 0f ? manualStep.dy() / dt : 0f;
        return out.set(Math.abs(vx) > MOVING_EPSILON
                || Math.abs(vy) > MOVING_EPSILON, vx, vy);
    }

    /**
     * Whether this carrier is taking fire it cannot presently answer, somewhere
     * the ground is not answering it either.
     *
     * <p>Read in the order the questions get cheaper to be wrong about. Enough
     * fire first, because a screen spent on a stray round is the whole waste
     * this policy exists to avoid and no later term can undo it. Then the
     * bearing's cover, because cover resolves ahead of a screen in the
     * durability model — fire the wall is already stopping is fire the screen
     * would be paid to stop twice. Only then the two occasions, which are the
     * cheap part.
     */
    private boolean exposedUnderFire(long id, ExposedUnderFireSpec spec, BattleSimulation sim,
                                     PolicyMotion motion) {
        CombatService combat = rosterService.combat();
        if (!combat.has(id)) return false;
        switch (DEBUG_SCREEN_TRIGGER) {
            case ALWAYS -> { return true; }
            case DAMAGE -> {
                return combat.ticksSinceDamaged(id, sim.getSimTickIndex())
                        <= DEBUG_RECENT_DAMAGE_TICKS;
            }
            case OFF -> { /* the shipped policy below decides */ }
        }
        float pressure = combat.incomingPressure(id, sim.getSimTickIndex());
        if (pressure < spec.incomingPressureThreshold()) return false;

        World world = rosterService.world();
        int cellX = world.cellX(id);
        int cellY = world.cellY(id);
        int fromX = combat.incomingFromX(id);
        int fromY = combat.incomingFromY(id);
        if (sim.getGrid().getCoverAt(cellX, cellY, fromX - cellX, fromY - cellY)
                > spec.maxCoverLevel()) {
            return false;
        }

        // Occasion one: crossing ground. AI follows an unexhausted path;
        // manual drive uses its legal proposed step before speed effects apply.
        if (motion.underway()) return true;

        // Occasion two: outranged. They can reach the carrier and the carrier
        // cannot reach back, so there is no version of shooting first that
        // solves this.
        float reach = combat.attackRange(id);
        if (reach <= 0f) return false;
        float dx = fromX + 0.5f - world.x(id);
        float dy = fromY + 0.5f - world.y(id);
        return dx * dx + dy * dy > reach * reach;
    }

    /**
     * The pod's own pick, independent of whatever the wearer's primary weapon
     * is engaging: the best visible hostile within the referenced weapon's
     * range, scored by {@link TacticalScoring} the same way any other mount
     * acquires a target it can actually reach, and no closer than the system's
     * authored standoff. Returns {@code 0L} when nothing qualifies, in which
     * case the pod simply waits — it never fires blind, and it never spends a
     * finite salvo on something the rifle already has in hand.
     */
    private long standoffTarget(long id, IntegralSystemDef def, BattleSimulation sim) {
        MissilePodSpec pod = def.missilePod();
        SightedStandoffSpec standoff = def.sightedStandoff();
        if (pod == null || standoff == null) return 0L;
        World world = rosterService.world();
        Faction faction = rosterService.identity().faction(id);
        if (faction == null) return 0L;
        int squadId = rosterService.squad().hasSquad(id)
                ? rosterService.squad().squadId(id) : Squad.NO_SQUAD;
        WeaponDef weapon = pod.weaponDef();
        // The authored standoff is the scorer's own minimum range, so a closer
        // contact is never a candidate rather than being picked and discarded.
        return sim.getTacticalScoring().findBestTargetWithinRange(
                world.x(id), world.y(id), faction, squadId, id,
                rosterService.vision().airLosRadius(id), /*allowNoLos*/ false,
                standoff.minimumStandoffCells(), weapon.range());
    }

    /**
     * Launches the pod's whole salvo at once: {@link WeaponDef#projectilesPerShot()}
     * independently resolved missiles, each a real traveling {@link Projectile}
     * carrying the weapon's own {@link PendingDetonation} payload. Reuses the
     * same ballistic-resolution, splash, and friendly-fire pipeline every other
     * explosive round in the catalog goes through
     * ({@code moddable-weapons-nouns.md}) — a pod gets no exemption from the
     * collateral discipline the shipped specials already carry.
     */
    private void fireMissilePod(long shooter, MissilePodSpec pod,
                                MissilePodMountGeometry.Pose mount,
                                BallisticResolver.Resolution[] salvo) {
        WeaponDef weapon = pod.weaponDef();
        Faction shooterFaction = rosterService.identity().faction(shooter);
        float fromX = mount.muzzleX();
        float fromY = mount.muzzleY();
        for (BallisticResolver.Resolution res : salvo) {
            rosterService.telemetry().recordRoundFired(shooter);
            PendingDetonation onArrival = res.impacts()
                    ? new PendingDetonation(shooter, res.endX(), res.endY(), res.flightTime(),
                            weapon.aoeRadius, weapon.damage(), weapon.penetration(),
                            weapon.wallDamage, shooterFaction, /*aerialDelivery*/ false,
                            weapon.wallDamageRadius, /*spawnDustOnWallBreak*/ false,
                            /*friendlyFireImmune*/ false)
                    : null;
            shots.queueProjectile(new Projectile(fromX, fromY, res.endX(), res.endY(),
                    /*hasBoostRamp*/ true, /*arcHeight*/ 0f, shooterFaction,
                    /*aerialDelivery*/ false, res.flightTime(), onArrival, weapon.id,
                    weapon.pointDefenseTarget));
            shots.postShot(ShotEvent.primary(fromX, fromY, 0f,
                    res.endX(), res.endY(), res.endZ(), res.hitIntended(), shooterFaction,
                    Math.max(res.flightTime(), 0.05f), weapon, 1f,
                    res.victimId() != 0L, res.kind(), shooter));
        }
        rosterService.telemetry().recordSecondaryUsed(shooter);
    }

    /**
     * Every missile of one salvo, each independently resolved from the muzzle, before any of it is committed:
     * {@link WeaponDef#projectilesPerShot()} rounds, never fewer than one.
     */
    private BallisticResolver.Resolution[] resolveSalvo(long shooter, long target, MissilePodSpec pod,
                                                        MissilePodMountGeometry.Pose mount) {
        WeaponDef weapon = pod.weaponDef();
        BallisticResolver.Source source = new BallisticResolver.Source(shooter, mount.muzzleX(),
                mount.muzzleY(), 0f, rosterService.identity().faction(shooter));
        BallisticResolver.Resolution[] salvo =
                new BallisticResolver.Resolution[Math.max(1, weapon.projectilesPerShot())];
        for (int i = 0; i < salvo.length; i++) {
            salvo[i] = resolver.resolve(source, target, weapon.accuracy(), weapon.hitSpread(),
                    weapon.roundVelocity(), weapon.range(), rng);
        }
        return salvo;
    }

    /** Whether any round of {@code salvo} would burst where a friend's footprint, or the wearer's, reaches. */
    private boolean salvoEndangersFriends(long shooter, WeaponDef weapon,
                                          BallisticResolver.Resolution[] salvo) {
        if (weapon.aoeRadius <= 0f) return false;
        Faction side = rosterService.identity().faction(shooter);
        for (BallisticResolver.Resolution res : salvo) {
            if (res.impacts() && resolver.friendInBlast(shooter, side, res.endX(), res.endY(),
                    weapon.aoeRadius)) return true;
        }
        return false;
    }

    static boolean legalPodOrigin(NavigationGrid grid, float bodyX, float bodyY,
                                   MissilePodMountGeometry.Pose mount) {
        int x = (int) Math.floor(mount.muzzleX());
        int y = (int) Math.floor(mount.muzzleY());
        if (x < 0 || y < 0 || x >= grid.getWidth() || y >= grid.getHeight()
                || grid.blocksStructuralLineOfSightAt(grid.index(x, y))) return false;
        return grid.firstWallOnLine(bodyX, bodyY, mount.muzzleX(), mount.muzzleY()) == -1L
                && grid.firstProjectileBlockingEdgeBarrierOnLine(bodyX, bodyY,
                        mount.muzzleX(), mount.muzzleY()) == null;
    }

    private MissilePodMountGeometry.Pose podPose(long id, long target, float bodyBearing,
                                                MissilePodSpec pod) {
        World world = rosterService.world();
        float x = world.renderX(id);
        float y = world.renderY(id);
        MissilePodMountGeometry.Pose initial = MissilePodMountGeometry.resolve(x, y, bodyBearing,
                bodyBearing, pod.mount());
        float bearing = MissilePodMountGeometry.bearing(world.renderX(target) - initial.mountX(),
                world.renderY(target) - initial.mountY());
        return MissilePodMountGeometry.resolve(x, y, bodyBearing, bearing, pod.mount());
    }

    /** Reads simulation intent; a mount never depends on an animation pose or a loaded texture. */
    private float podBodyBearing(long id, BattleSimulation sim, PolicyMotion motion) {
        World world = rosterService.world();
        if (sim.directControl().isControlling(id)) {
            ManualIntent intent = sim.directControl().intent();
            if (Float.isFinite(intent.aimX()) && Float.isFinite(intent.aimY())) {
                return MissilePodMountGeometry.bearing(intent.aimX() - world.renderX(id),
                        intent.aimY() - world.renderY(id));
            }
        }
        long target = world.hasCombat(id) ? world.targetId(id) : 0L;
        if (target != 0L && rosterService.isAliveById(target)) {
            return MissilePodMountGeometry.bearing(world.renderX(target) - world.renderX(id),
                    world.renderY(target) - world.renderY(id));
        }
        if (Math.abs(motion.vx()) > MOVING_EPSILON || Math.abs(motion.vy()) > MOVING_EPSILON) {
            return MissilePodMountGeometry.bearing(motion.vx(), motion.vy());
        }
        // Applied velocity is zero before this sweep. A live AI route still
        // owns its intended heading, including continuous waypoint coordinates.
        if (!sim.directControl().isControlling(id)) {
            float travel = podTravelBearing(id, rosterService.movement(),
                    world.renderX(id), world.renderY(id));
            if (Float.isFinite(travel)) return travel;
        }
        return 0f;
    }

    static float podTravelBearing(long id, MovementService movement, float x, float y) {
        if (!movement.has(id) || movement.settled(id)) return Float.NaN;
        int next = movement.nextWaypointIndex(id);
        int count = movement.waypointCount(id);
        while (next < count) {
            float dx = movement.waypointX(id, next) - x;
            float dy = movement.waypointY(id, next) - y;
            if (Math.abs(dx) > MOVING_EPSILON || Math.abs(dy) > MOVING_EPSILON) {
                return MissilePodMountGeometry.bearing(dx, dy);
            }
            next++;
        }
        return Float.NaN;
    }
}

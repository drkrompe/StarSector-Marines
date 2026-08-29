package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.ShotService;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
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
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.marine.ApproachingDeadGroundSpec;
import com.dillon.starsectormarines.marine.ExposedUnderFireSpec;
import com.dillon.starsectormarines.marine.FieldAidSpec;
import com.dillon.starsectormarines.marine.HoldingFiringPositionSpec;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.MissilePodSpec;
import com.dillon.starsectormarines.marine.PerceptionSweepSpec;
import com.dillon.starsectormarines.marine.SightedStandoffSpec;

import java.util.Locale;
import java.util.Random;

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
 * cover makes spending pointless, how far out is worth planting for, how close
 * is too close to be planted. That split is why the occasions can be added to
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
 *
 * <p><b>Nothing here reads faction.</b> A defender in a system-carrying pattern
 * reaches this sweep through the same component the player's marines do and is
 * offered the same reason to spend it; there is no defender branch and no
 * defender-only tuning field to add one with.
 *
 * <p><b>A sweep reveals to the player and to nobody else.</b> Its whole effect
 * is a temporary observer on {@link FogOfWarService}, rebuilt from live state
 * every tick and gone the tick the window closes. No decision layer reads it,
 * because fog is presentation authority and must not become a simulation input
 * ({@code fog-of-war-nouns.md}) — which is also what makes "information, not
 * authority" true by construction here rather than by discipline: there is no
 * path from a sweep to a shot.
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

    public IntegralSystemSystem(UnitRosterService rosterService, BallisticResolver resolver,
                                ShotService shots, Random rng) {
        this.rosterService = rosterService;
        this.resolver = resolver;
        this.shots = shots;
        this.rng = rng;
    }

    /**
     * Drains every carrier's clocks, offers the ready ones a reason to fire,
     * and republishes what the running sweeps are currently letting the player
     * see.
     *
     * <p>The sweep set is <em>replaced</em>, never appended to: a temporary
     * source lives for exactly as long as the system projecting it is still
     * running, which is the fog service's standing rule for its own channel
     * ({@code fog-of-war-nouns.md} law 5) and is what makes an expiry — or a
     * wearer's death, which drops the whole component — release its reveal with
     * nothing left over.
     */
    public void tick(float dt, BattleSimulation sim) {
        IntegralSystemService systems = rosterService.integralSystems();
        MovementService movement = rosterService.movement();
        FogOfWarService fog = sim.getFogOfWar();
        fog.clearCarriedSweepSources();
        long[] live = rosterService.denseArray();
        int count = rosterService.liveCount();
        for (int i = 0; i < count; i++) {
            long id = live[i];
            if (!systems.has(id)) continue;
            systems.tick(id, dt);
            projectSweep(id, systems, fog);
            if (!systems.canActivate(id)) continue;
            IntegralSystemDef def = systems.spec(id);
            if (def == null) continue;
            switch (def.aiPolicy()) {
                case EXPOSED_UNDER_FIRE -> {
                    if (exposedUnderFire(id, def.exposedUnderFire(), sim, movement)) {
                        systems.activate(id);
                    }
                }
                case SIGHTED_STANDOFF_CONTACT -> {
                    long target = standoffTarget(id, def, sim);
                    if (target != 0L && systems.activate(id)) {
                        fireMissilePod(id, target, def.missilePod());
                    }
                }
                case APPROACHING_DEAD_GROUND -> {
                    // KNOWN DEAD: this reads applied velocity, which is always
                    // the zero MovementService.beginTick wrote, because this
                    // sweep runs ahead of the movement pass by design. Unlike
                    // the crossing test above it cannot simply switch to path
                    // state — it needs a heading, not a yes/no — so it is
                    // pending the same treatment rather than fixed in passing.
                    ApproachingDeadGroundSpec ahead = def.approachingDeadGround();
                    if (isMoving(id, movement)
                            && deadGroundAhead(id, sim, movement, ahead.lookaheadCells())) {
                        systems.activate(id);
                    }
                }
                case HOLDING_A_FIRING_POSITION -> {
                    if (holdingFiringPosition(id, def.holdingFiringPosition(), sim, movement)) {
                        systems.activate(id);
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
    }

    /**
     * Publishes one running sweep as a temporary observer for this tick.
     *
     * <p>Called for every carrier before its activation is considered, so a
     * sweep that started on the previous tick is republished and one that has
     * just expired simply is not. The wall-read radius rides the shadowcast's
     * existing air-clearance parameter, which is the same bounded
     * "walls near the source are transparent" rule a flier already uses — the
     * sweep is a client of that, not a second visibility algorithm.
     */
    private void projectSweep(long id, IntegralSystemService systems, FogOfWarService fog) {
        PerceptionSweepSpec sweep = systems.activeSweep(id);
        if (sweep == null) return;
        World world = rosterService.world();
        fog.addCarriedSweepSource(world.cellX(id), world.cellY(id),
                Math.round(sweep.revealRangeCells()), sweep.wallReadRadiusCells());
    }

    /**
     * Whether the ground this carrier is heading into is ground they cannot see
     * into: the cell {@code lookaheadCells} along their current heading is
     * behind something their own line of sight does not reach past.
     *
     * <p>Deliberately the wearer's own sight rather than the player's reveal
     * bitmap. Fog is presentation, and a policy that read it would let what the
     * player has already been shown decide what a marine does.
     */
    private boolean deadGroundAhead(long id, BattleSimulation sim, MovementService movement,
                                    float lookaheadCells) {
        float vx = movement.velX(id);
        float vy = movement.velY(id);
        float speed = (float) Math.sqrt(vx * vx + vy * vy);
        if (speed <= MOVING_EPSILON) return false;
        World world = rosterService.world();
        int fromX = world.cellX(id);
        int fromY = world.cellY(id);
        int aheadX = Math.round(fromX + vx / speed * lookaheadCells);
        int aheadY = Math.round(fromY + vy / speed * lookaheadCells);
        NavigationGrid grid = sim.getGrid();
        // Walking off the edge of the map is not dead ground; there is nothing
        // out there for a sweep to read.
        if (aheadX < 0 || aheadX >= grid.getWidth() || aheadY < 0 || aheadY >= grid.getHeight()) {
            return false;
        }
        return !grid.hasLineOfSight(fromX, fromY, aheadX, aheadY);
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

    private static boolean isMoving(long id, MovementService movement) {
        float vx = movement.velX(id);
        float vy = movement.velY(id);
        return Math.abs(vx) > MOVING_EPSILON || Math.abs(vy) > MOVING_EPSILON;
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
                                     MovementService movement) {
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

        // Occasion one: crossing ground. Path state, not applied velocity —
        // this sweep deliberately runs ahead of the movement pass so an
        // activation's speed multiplier is in place before the wearer steps,
        // which means every mover's velocity here is the zero beginTick just
        // wrote. An unexhausted path is what "under way" actually means and it
        // reads the same wherever in the tick it is asked.
        if (!movement.settled(id)) return true;

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
     * Whether this carrier is standing where they mean to stand, shooting at
     * something worth being steady for, with nobody about to arrive.
     *
     * <p>The mirror of {@link #exposedUnderFire}, and read in the same order:
     * the cheapest disqualifier first, the authored judgements last. A marine
     * still under way has not chosen a position yet; one with nothing engaged
     * has nothing to be steady <em>for</em>; one shooting across a room gains
     * little, because accuracy has barely fallen off at that distance. The
     * break-off check is last because it is the only one that costs a spatial
     * query.
     *
     * <p><b>Path state, never applied velocity.</b> This sweep runs ahead of the
     * movement pass, so every mover's velocity here is the zero
     * {@code MovementService.beginTick} just wrote — a stance keyed off it would
     * fire on everybody, every tick, which is the same defect that made this
     * policy's sibling never fire at all.
     */
    private boolean holdingFiringPosition(long id, HoldingFiringPositionSpec spec,
                                          BattleSimulation sim, MovementService movement) {
        if (spec == null) return false;
        if (!movement.settled(id)) return false;

        CombatService combat = rosterService.combat();
        if (!combat.has(id)) return false;
        long target = combat.targetId(id);
        if (target == 0L || !rosterService.isAliveById(target)) return false;

        float reach = combat.attackRange(id);
        if (reach <= 0f) return false;
        World world = rosterService.world();
        float dx = world.x(target) - world.x(id);
        float dy = world.y(target) - world.y(id);
        float minimum = spec.minimumTargetRangeFraction() * reach;
        if (dx * dx + dy * dy < minimum * minimum) return false;

        // Planting with somebody about to be on top of you is the mistake the
        // commitment makes possible, so the suit that suffers most for it says
        // so in its own break-off distance.
        UnitSpatialIndex index = sim.getUnitIndex();
        if (index == null) return true;
        Faction faction = rosterService.identity().faction(id);
        if (faction == null) return false;
        return index.countOtherFactionCombatants(world.x(id), world.y(id),
                spec.breakOffRangeCells(), faction, id) == 0;
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
    private void fireMissilePod(long shooter, long target, MissilePodSpec pod) {
        World world = rosterService.world();
        WeaponDef weapon = pod.weaponDef();
        Faction shooterFaction = rosterService.identity().faction(shooter);
        float fromX = world.renderX(shooter);
        float fromY = world.renderY(shooter);
        int missiles = Math.max(1, weapon.projectilesPerShot());
        for (int i = 0; i < missiles; i++) {
            BallisticResolver.Resolution res = resolver.resolve(shooter, target,
                    weapon.accuracy(), weapon.hitSpread(), weapon.roundVelocity(), rng);
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
}

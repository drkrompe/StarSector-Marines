package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.control.ManualIntent;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.CombatService;
import com.dillon.starsectormarines.battle.sim.MovementService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.marine.ExposedUnderFireSpec;
import com.dillon.starsectormarines.marine.IntegralSystemDef;

import java.util.Locale;

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
    /** Reused by the serial per-carrier pass; no policy keeps it past its own call. */
    private final PolicyMotion motionScratch = new PolicyMotion(false, 0f, 0f);

    public IntegralSystemSystem(UnitRosterService rosterService) {
        this.rosterService = rosterService;
    }

    /**
     * Drains every carrier's clocks and offers the ready ones a reason to spend.
     */
    public void tick(float dt, BattleSimulation sim) {
        IntegralSystemService systems = rosterService.integralSystems();
        MovementService movement = rosterService.movement();
        long[] live = rosterService.denseArray();
        int count = rosterService.liveCount();
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
                // A field-aid kit makes its wearer a squad medic; SquadMedicSystem treats.
                case WOUNDED_SQUADMATE_IN_REACH -> { }
                default -> { /* No integral system declares the carried-item policies. */ }
            }
        }
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
}

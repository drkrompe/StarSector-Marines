package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.RuinsVaultObjective;
import com.dillon.starsectormarines.battle.satchel.SatchelChargeService.ChargeView;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The stir of an occupied ruins site in the delve ({@code ruins-pressure.md}): the pressure that keeps phase B tense.
 * Every second the company spends inside raises it, gunfire raises it faster, and a loud act (the power hall
 * restarting, a breaching charge) raises it at once. It never falls. Each {@link #STEP} of stir is a step of the
 * site's {@link Level}, and each step sends a {@link Push} out of the occupant's finite reserve, deeper in, toward the
 * company; the vault's breach releases what is left in one.
 *
 * <p>Where a push strikes is the {@link #aim aim} rule: it comes up in the layer ahead and hunts the company's noise,
 * unless the company has sat still (dug in, {@link Stillness}), when it comes up in the layer behind and sits on the
 * way back. Holding still is never safe: the site goes round a company that does.
 *
 * <p>This class decides the steps, the pushes and their aim. Turning a push into the occupant's units is the
 * occupant's (the story's later slices); until then {@link #pushes()} records them for the row and for probes.
 */
public final class RuinsPressure {

    /** The site's level, as the Delve row words it: a step at a time, never back. */
    public enum Level {
        QUIET("ruinsPressureQuiet"), STIRRING("ruinsPressureStirring"), ROUSED("ruinsPressureRoused"),
        HUNTING("ruinsPressureHunting");

        public final String key;

        Level(String key) { this.key = key; }
    }

    /** Where a push goes: at the company's noise, or onto the way back behind it. */
    public enum Aim { HUNT, WAY_BACK }

    /** How much of the reserve is left, as the close reading words it. */
    public enum ReserveBand { FULL, THINNED, SPENT }

    /**
     * One push out of the reserve.
     *
     * @param step   the stir step that sent it, from 1; 0 for the breach's
     * @param squads how many of the reserve's squads it takes
     * @param aim    where it goes
     * @param layer  the layer it comes up in: ahead of the company to hunt, behind it to sit on the way back
     */
    public record Push(int step, int squads, Aim aim, int layer) { }

    /**
     * What the aim reads of the company.
     *
     * @param dugIn        whether the company has held still for {@link #STILL_SECONDS}
     * @param companyLayer the deepest layer the company stands in now
     * @param firstInside  the first layer inside the interior: nothing comes up behind it, since the entry is the
     *                     company's for good
     * @param layers       how many layers the delve has
     */
    public record Situation(boolean dugIn, int companyLayer, int firstInside, int layers) { }

    /** Stir per step: a quiet company meets a push every this many seconds inside. UNTUNED. */
    public static final float STEP = 90f;
    /** Stir per second inside. UNTUNED. */
    static final float TIME_PER_SECOND = 1f;
    /** Further stir per second while the company is firing: a fight halves the time to the next push. UNTUNED. */
    static final float FIRING_PER_SECOND = 1f;
    /** Stir of the power hall restarting, the loudest work on the site. UNTUNED. */
    public static final float HALL_RESTART = 30f;
    /** Stir of a breaching charge going off. UNTUNED. */
    public static final float CHARGE = 15f;
    /** How long the company must hold still to be dug in. UNTUNED. */
    static final float STILL_SECONDS = 45f;
    /** How far the company's middle may wander and still be holding still, in cells. UNTUNED. */
    static final float STILL_CELLS = 8f;
    /** Seconds between samples of where the company stands. */
    static final float SAMPLE_SECONDS = 5f;
    /** Seconds a company shot counts as firing after it is fired. */
    static final float FIRING_HOLD_SECONDS = 1f;

    private final int initialReserve;
    private final RuinsDelve delve;
    private final Stillness stillness = new Stillness(STILL_SECONDS, SAMPLE_SECONDS, STILL_CELLS);
    private final List<Push> pushes = new ArrayList<>();
    private final Set<Long> charges = new HashSet<>();
    private float stir;
    private int steps;
    private int reserve;
    private boolean breached;
    private float elapsed;
    private float lastShot = -FIRING_HOLD_SECONDS;
    private int shotsHeard;

    RuinsPressure(int reserve, RuinsDelve delve) {
        if (reserve < 0) throw new IllegalArgumentException("A reserve is never negative");
        this.initialReserve = reserve;
        this.reserve = reserve;
        this.delve = delve;
    }

    /** The pressure of an occupied site of {@code grade}, over its delve. */
    public static RuinsPressure of(RuinsGrade grade, RuinsDelve delve) {
        return new RuinsPressure(reserve(grade), delve);
    }

    /** The occupant's reserve deeper in, in squads: larger ruins hold more. UNTUNED. */
    static int reserve(RuinsGrade grade) {
        return switch (grade) {
            case SCATTERED -> 2;
            case WIDESPREAD -> 3;
            case EXTENSIVE -> 5;
            case VAST -> 7;
        };
    }

    /** The squads a step's push takes before the reserve runs short: one, then two from the hunting level. */
    static int pushSize(int step) {
        return step >= Level.HUNTING.ordinal() ? 2 : 1;
    }

    /** The level after {@code steps} steps. */
    static Level level(int steps) {
        return Level.values()[Math.min(steps, Level.values().length - 1)];
    }

    /**
     * Where a push goes: behind a dug-in company, onto the stand of the layer behind it, if that layer is inside;
     * otherwise up in the layer ahead (the company's own at the last), to hunt it.
     */
    static Push aim(int step, int squads, Situation where) {
        int behind = where.companyLayer() - 1;
        if (where.dugIn() && behind >= where.firstInside()) return new Push(step, squads, Aim.WAY_BACK, behind);
        int ahead = Math.min(Math.max(where.companyLayer() + 1, where.firstInside()), where.layers() - 1);
        return new Push(step, squads, Aim.HUNT, ahead);
    }

    /**
     * Seconds inside: the stir rises by time, faster while firing, and every step it crosses sends a push while the
     * reserve lasts. Nothing rises once the vault is breached.
     *
     * @return the pushes this advance sent, in step order
     */
    List<Push> advance(float dt, boolean firing, Situation where) {
        if (breached) return List.of();
        stir += dt * (TIME_PER_SECOND + (firing ? FIRING_PER_SECOND : 0f));
        return steps(where);
    }

    /** A loud act: the stir rises by {@code amount} at once; its steps are counted on the next advance. */
    public void loud(float amount) {
        if (!breached && amount > 0f) stir += amount;
    }

    private List<Push> steps(Situation where) {
        List<Push> sent = new ArrayList<>();
        while (stir >= (steps + 1) * STEP) {
            steps++;
            int squads = Math.min(reserve, pushSize(steps));
            if (squads <= 0) continue;
            reserve -= squads;
            Push push = aim(steps, squads, where);
            pushes.add(push);
            sent.add(push);
        }
        return sent;
    }

    /**
     * The vault is breached: the occupant throws what is left of its reserve after the haul, in one push from the
     * vault's layer. The stir stops; the egress is the contingency's.
     *
     * @return the breach's push, or null when the reserve is spent or the breach was already met
     */
    Push breach(int layers) {
        if (breached) return null;
        breached = true;
        if (reserve <= 0) return null;
        Push push = new Push(0, reserve, Aim.HUNT, layers - 1);
        reserve = 0;
        pushes.add(push);
        return push;
    }

    public Level level() { return level(steps); }

    /** How far the stir is toward the next step, 0 to 1: the row's bar. */
    public float towardNext() { return Math.min(1f, (stir - steps * STEP) / STEP); }

    public float stir() { return stir; }

    public int steps() { return steps; }

    /** Squads left in the reserve. */
    public int reserve() { return reserve; }

    /** The reserve's band: untouched, thinned, or spent. */
    public ReserveBand reserveBand() {
        if (reserve <= 0) return ReserveBand.SPENT;
        return reserve == initialReserve ? ReserveBand.FULL : ReserveBand.THINNED;
    }

    /** Whether the company has held still long enough that the next push goes round it. */
    public boolean dugIn() { return stillness.dugIn(); }

    /** Every push sent so far, oldest first. */
    public List<Push> pushes() { return Collections.unmodifiableList(pushes); }

    /**
     * One step of the battle: in the delve, the company's gunfire and its charges going off are heard, its stillness
     * sampled, and the stir advanced. The vault's breach releases the reserve.
     */
    public void tick(BattleSimulation sim) {
        RuinsVaultObjective vault = vault(sim);
        if (vault == null || breached) return;
        float dt = BattleSimulation.TICK_DT;
        elapsed += dt;
        boolean shot = heardShots(sim);
        int charged = heardCharges(sim);
        if (vault.breached()) {
            breach(delve.layerCount());
            return;
        }
        if (vault.ruinsPhase() != RuinsVaultObjective.RuinsPhase.DELVE) return;
        if (shot) lastShot = elapsed;
        loud(charged * CHARGE);
        Situation where = situation(sim);
        if (where == null) return;
        advance(dt, elapsed - lastShot < FIRING_HOLD_SECONDS, where);
    }

    private static RuinsVaultObjective vault(BattleSimulation sim) {
        for (Objective objective : sim.getObjectives()) {
            if (objective instanceof RuinsVaultObjective ruins) return ruins;
        }
        return null;
    }

    /** Whether a company shot was fired in the steps since the last look. */
    private boolean heardShots(BattleSimulation sim) {
        List<ShotEvent> frame = sim.getShotsThisFrame();
        if (frame.size() < shotsHeard) shotsHeard = 0;
        boolean any = false;
        for (int i = shotsHeard; i < frame.size(); i++) {
            if (frame.get(i).shooterFaction == Faction.MARINE) any = true;
        }
        shotsHeard = frame.size();
        return any;
    }

    /** The company's charges that went off since the last look: the ones no longer armed. */
    private int heardCharges(BattleSimulation sim) {
        Set<Long> armed = new HashSet<>();
        for (ChargeView charge : sim.satchelCharges().activeCharges()) {
            if (charge.sourceFaction() == Faction.MARINE) armed.add(charge.id());
        }
        int gone = 0;
        for (long id : charges) if (!armed.contains(id)) gone++;
        charges.clear();
        charges.addAll(armed);
        return gone;
    }

    /** Where the company stands: its middle sampled for stillness, and the deepest layer any of it is in. */
    private Situation situation(BattleSimulation sim) {
        float sx = 0f, sy = 0f;
        int count = 0, deepest = -1;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE || !sim.identity().type(unit).combatant
                    || !sim.movement().has(unit) || sim.isRiding(unit)) continue;
            float x = sim.world().x(unit), y = sim.world().y(unit);
            sx += x;
            sy += y;
            count++;
            int cx = sim.world().cellX(unit), cy = sim.world().cellY(unit);
            for (RuinsDelve.Layer layer : delve.layers()) {
                if (layer.index() > deepest && layer.ground().contains(cx, cy)) deepest = layer.index();
            }
        }
        if (count == 0) return null;
        stillness.sample(elapsed, sx / count, sy / count);
        int firstInside = delve.interior() != null ? delve.interior().firstLayer() : 0;
        return new Situation(stillness.dugIn(), Math.max(deepest, 0), firstInside, delve.layerCount());
    }

    /**
     * Whether the company holds still: its middle sampled every {@code sampleSeconds}, and still once every sample of
     * the last {@code windowSeconds} lies within {@code cells} of the newest.
     */
    static final class Stillness {
        private final float window;
        private final float every;
        private final float cells;
        private final List<float[]> samples = new ArrayList<>();
        private float next;

        Stillness(float windowSeconds, float sampleSeconds, float cells) {
            this.window = windowSeconds;
            this.every = sampleSeconds;
            this.cells = cells;
        }

        /** The company's middle at {@code t} seconds; taken only every sample period. */
        void sample(float t, float x, float y) {
            if (t < next) return;
            next = t + every;
            samples.add(new float[]{t, x, y});
            while (samples.size() > 1 && t - samples.get(1)[0] >= window) samples.remove(0);
        }

        boolean dugIn() {
            if (samples.isEmpty()) return false;
            float[] newest = samples.get(samples.size() - 1);
            if (newest[0] - samples.get(0)[0] < window) return false;
            for (float[] s : samples) {
                float dx = s[1] - newest[1], dy = s[2] - newest[2];
                if (dx * dx + dy * dy > cells * cells) return false;
            }
            return true;
        }
    }
}

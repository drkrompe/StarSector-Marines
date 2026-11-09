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
 * <p>This class decides the steps, the pushes and their aim; {@link RuinsPushes} raises each push as the occupant's
 * units and commands them once it is {@linkplain #field fielded}. {@link #pushes()} records every push for the row,
 * the comms and probes.
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
     * @param closed       the layers whose wake point the company has collapsed: nothing comes up in them again
     */
    public record Situation(boolean dugIn, int companyLayer, int firstInside, int layers, Set<Integer> closed) {
        public Situation {
            closed = Set.copyOf(closed);
        }

        /** A situation with every layer open. */
        public Situation(boolean dugIn, int companyLayer, int firstInside, int layers) {
            this(dugIn, companyLayer, firstInside, layers, Set.of());
        }
    }

    /** Stir per step: a quiet company meets a push every this many seconds inside. UNTUNED. */
    public static final float STEP = 90f;
    /** Stir per second inside. UNTUNED. */
    static final float TIME_PER_SECOND = 1f;
    /** Further stir per second while the company is firing: a fight halves the time to the next push. UNTUNED. */
    static final float FIRING_PER_SECOND = 1f;
    /** The highest stake a prize carries. */
    static final int MAX_STAKES = 3;
    /** Reserve squads each stake of the prize adds. UNTUNED. */
    static final int RESERVE_PER_STAKE = 1;
    /** How much faster time and gunfire stir per stake: a stake-3 site steps in 90 s / 1.45, about a minute. UNTUNED. */
    static final float RATE_PER_STAKE = 0.15f;
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
    /** How fast time and gunfire stir this site: faster the more its vault's prize is worth. */
    private final float rate;
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
    private RuinsPushes field;
    private float companyX;
    private float companyY;
    private int noiseX;
    private int noiseY;
    private boolean placed;
    private boolean heardAny;
    private int shotX;
    private int shotY;

    RuinsPressure(int reserve, RuinsDelve delve) {
        this(reserve, 1f, delve);
    }

    RuinsPressure(int reserve, float rate, RuinsDelve delve) {
        if (reserve < 0) throw new IllegalArgumentException("A reserve is never negative");
        if (!(rate > 0f)) throw new IllegalArgumentException("A stir always rises");
        this.rate = rate;
        this.initialReserve = reserve;
        this.reserve = reserve;
        this.delve = delve;
    }

    /** The pressure of an occupied site of {@code grade}, over its delve. */
    public static RuinsPressure of(RuinsGrade grade, RuinsDelve delve) {
        return of(grade, 0, delve);
    }

    /**
     * The pressure of an occupied site of {@code grade} whose vault holds a prize of {@code stakes}
     * ({@code RuinsVaultPrize.stakes}, 0 to 3; {@code ruins-expedition.md}, Stakes): each stake holds one more squad
     * in the reserve and stirs the site faster.
     */
    public static RuinsPressure of(RuinsGrade grade, int stakes, RuinsDelve delve) {
        int stake = Math.max(0, Math.min(MAX_STAKES, stakes));
        return new RuinsPressure(reserve(grade) + stake * RESERVE_PER_STAKE, 1f + stake * RATE_PER_STAKE, delve);
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
     * Where a push goes: behind a dug-in company, onto the stand of the layer behind it, if that layer is inside and
     * its wake point stands; otherwise up in the layer ahead (the next open one deeper, the vault's at the last), to
     * hunt it.
     */
    static Push aim(int step, int squads, Situation where) {
        int behind = where.companyLayer() - 1;
        if (where.dugIn() && behind >= where.firstInside() && !where.closed().contains(behind)) {
            return new Push(step, squads, Aim.WAY_BACK, behind);
        }
        int last = where.layers() - 1;
        int ahead = Math.min(Math.max(where.companyLayer() + 1, where.firstInside()), last);
        // A collapsed layer sends nothing: the push comes up in the next open layer deeper in, and the vault's layer,
        // where the reserve is kept, is never shut.
        while (ahead < last && where.closed().contains(ahead)) ahead++;
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
        stir += dt * rate * (TIME_PER_SECOND + (firing ? FIRING_PER_SECOND : 0f));
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

    /** Has the occupant raise and command each push from now on. Once, at setup, after the occupant stands. */
    public void field(RuinsPushes pushes) {
        if (field != null) throw new IllegalStateException("A site's pushes are fielded once");
        field = pushes;
    }

    /** Who raises the pushes, or null on a site whose pushes are only recorded. */
    public RuinsPushes fielded() { return field; }

    /** Every push sent so far, oldest first. */
    public List<Push> pushes() { return Collections.unmodifiableList(pushes); }

    /**
     * One step of the battle: in the delve, the company's gunfire and its charges going off are heard, its stillness
     * sampled, and the stir advanced. The vault's breach releases the reserve.
     */
    public void tick(BattleSimulation sim) {
        RuinsVaultObjective vault = vault(sim);
        if (vault == null) return;
        float dt = BattleSimulation.TICK_DT;
        elapsed += dt;
        boolean shot = heardShots(sim);
        int charged = heardCharges(sim);
        if (!breached && vault.breached()) {
            situation(sim);
            send(sim, breach(delve.layerCount()));
        } else if (!breached && vault.ruinsPhase() == RuinsVaultObjective.RuinsPhase.DELVE) {
            if (shot) {
                lastShot = elapsed;
                heardAny = true;
                noiseX = shotX;
                noiseY = shotY;
            }
            loud(charged * CHARGE);
            Situation where = situation(sim);
            if (where != null) {
                for (Push push : advance(dt, elapsed - lastShot < FIRING_HOLD_SECONDS, where)) send(sim, push);
            }
        }
        if (field != null && placed) field.tick(sim, noiseX, noiseY);
    }

    /** Has the occupant raise a push just sent, near the company it was sent at. */
    private void send(BattleSimulation sim, Push push) {
        if (push != null && field != null && placed) field.field(sim, push, companyX, companyY);
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
            ShotEvent event = frame.get(i);
            if (event.shooterFaction != Faction.MARINE) continue;
            any = true;
            shotX = (int) Math.floor(event.fromX);
            shotY = (int) Math.floor(event.fromY);
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

    /**
     * Where the company stands: the deepest layer any of it is in, and the middle of its forward element there,
     * sampled for stillness. Only the forward element counts, so squads still landing or walking up behind never keep
     * a vanguard that holds a stand from being dug in.
     */
    private Situation situation(BattleSimulation sim) {
        List<int[]> in = new ArrayList<>();
        List<float[]> at = new ArrayList<>();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE || !sim.identity().type(unit).combatant
                    || !sim.movement().has(unit) || sim.isRiding(unit)) continue;
            int cx = sim.world().cellX(unit), cy = sim.world().cellY(unit);
            int layerIn = -1;
            for (RuinsDelve.Layer layer : delve.layers()) {
                if (layer.index() > layerIn && layer.ground().contains(cx, cy)) layerIn = layer.index();
            }
            in.add(new int[]{layerIn});
            at.add(new float[]{sim.world().x(unit), sim.world().y(unit)});
        }
        float[] front = forward(in, at);
        if (front == null) return null;
        int deepest = (int) front[2];
        int layers = delve.layerCount();
        companyX = front[0];
        companyY = front[1];
        // The company's last noise is where it last fired; before its first shot, where it stands.
        if (!heardAny) {
            noiseX = (int) Math.floor(companyX);
            noiseY = (int) Math.floor(companyY);
        }
        placed = true;
        stillness.sample(elapsed, companyX, companyY);
        int firstInside = delve.interior() != null ? delve.interior().firstLayer() : 0;
        Set<Integer> closed = new HashSet<>();
        RuinsWorks works = sim.getRuinsWorks();
        if (works != null) {
            for (RuinsWorks.WakePoint wake : works.wakePoints()) {
                if (works.wakeCollapsed(wake.layer())) closed.add(wake.layer());
            }
        }
        return new Situation(stillness.dugIn(), Math.max(deepest, 0), firstInside, layers, closed);
    }

    /**
     * The company's forward element: the middle of the units in the deepest layer any of them is in (-1 for none),
     * as {x, y, layer}; null with no units.
     *
     * @param layerOf each unit's layer, as a one-element array
     * @param at      each unit's position
     */
    static float[] forward(List<int[]> layerOf, List<float[]> at) {
        int deepest = Integer.MIN_VALUE;
        for (int[] layer : layerOf) deepest = Math.max(deepest, layer[0]);
        if (layerOf.isEmpty()) return null;
        float sx = 0f, sy = 0f;
        int n = 0;
        for (int i = 0; i < layerOf.size(); i++) {
            if (layerOf.get(i)[0] != deepest) continue;
            sx += at.get(i)[0];
            sy += at.get(i)[1];
            n++;
        }
        return new float[]{sx / n, sy / n, deepest};
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

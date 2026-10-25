package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.SwarmStage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One bore: a hole the swarm comes out of (`colony-breach-nouns.md`, Bore).
 *
 * <p>The body that is shot, satchelled and collapsed is an ordinary structure
 * entity ({@link #entityId()}); this is the swarm's side of it: how far it has
 * grown, the biomass it has been fed, and its surge clock. Growth and the
 * surge clock are plain arithmetic on this object, so they are asked directly
 * of it; {@link SwarmBores} owns the instances and drives them from the
 * battle.
 *
 * <p><b>Growth.</b> A bore grows on a slow base clock from its eruption,
 * scaled by a seeded per-bore rate and slowed by the damage it has taken, and
 * fast by biomass: {@link SwarmBores#feed} is the only fast path. Tier is
 * {@link BoreTier#forGrowth} of the total and never goes back.
 *
 * <p><b>Surges.</b> A bore releases bodies in timed pulses. The pulse is sized
 * by tier plus the biomass fed since the last pulse, and a feed pulls the
 * next pulse in, so a just-fed bore answers with a big surge soon.
 */
public final class SwarmBore {

    /** A birth tick long before any battle began: a growth weeks old. */
    public static final int OLD = Integer.MIN_VALUE / 2;

    /** Growth points a bore gains per second on its base clock, undamaged. */
    public static final float BASE_GROWTH_PER_SECOND = 0.6f;
    /** Growth points one unit of biomass (one colonist's body) buys. */
    public static final float GROWTH_PER_BIOMASS = 30f;
    /** Extra bodies a surge carries per unit of fresh biomass. */
    public static final float BODIES_PER_BIOMASS = 3f;
    /** The most extra bodies fresh biomass can add to one surge. */
    public static final int MAX_BIOMASS_BODIES = 24;
    /** Growth never slows below this share of its rate, however damaged the bore. */
    public static final float MIN_DAMAGED_GROWTH = 0.25f;
    /** Seconds from eruption to a bore's first surge. */
    public static final float FIRST_SURGE_SECONDS = 4f;
    /** Seconds from a feed to the surge it brings forward, at the latest. */
    public static final float FED_SURGE_SECONDS = 3f;
    /** The seeded surge interval swings by this share either way. */
    public static final float SURGE_JITTER = 0.2f;

    private final int number;
    private final boolean mother;
    private final long entityId;
    private final int x;
    private final int y;
    private final int eruptedTick;
    private final float rateScale;
    private float growth;
    private BoreTier tier;
    private final int[] tierTicks = new int[BoreTier.values().length];
    private float freshBiomass;
    private float biomassFed;
    private int feeds;
    private int surges;
    private int nextSurgeTick;
    private boolean dormant;
    private int bodiesSpawned;
    private final int[] stagesReleased = new int[SwarmStage.values().length];
    private boolean matriarchReleased;
    private long matriarch;
    private int collapsedTick = -1;
    private final List<Long> colonies = new ArrayList<>();
    private final List<Long> colonyView = Collections.unmodifiableList(colonies);
    private final List<Long> outposts = new ArrayList<>();
    private final List<Long> outpostView = Collections.unmodifiableList(outposts);
    /** The tick each colony and outpost sprouted, which its root grows from (`colony-breach-nouns.md`, Nest). */
    private final Map<Long, Integer> born = new HashMap<>();

    /**
     * @param number    the bore's number in eruption order, {@code 0} for the mother
     * @param rateScale the seeded multiplier on its base growth clock
     */
    SwarmBore(int number, boolean mother, long entityId, int x, int y, int eruptedTick,
              float rateScale) {
        this.number = number;
        this.mother = mother;
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.eruptedTick = eruptedTick;
        this.rateScale = rateScale;
        Arrays.fill(tierTicks, -1);
        BoreTier start = mother ? BoreTier.BORE : BoreTier.FISSURE;
        this.growth = start.growthThreshold;
        this.tier = start;
        for (BoreTier t : BoreTier.values()) if (t.ordinal() <= start.ordinal()) tierTicks[t.ordinal()] = eruptedTick;
        this.nextSurgeTick = eruptedTick + seconds(FIRST_SURGE_SECONDS);
    }

    public int number() { return number; }
    public boolean mother() { return mother; }
    /** The structure entity that is the bore's body. */
    public long entityId() { return entityId; }
    public int x() { return x; }
    public int y() { return y; }
    public int eruptedTick() { return eruptedTick; }
    public float growth() { return growth; }
    public BoreTier tier() { return tier; }
    /** The tick the bore reached {@code t}, or {@code -1} while it has not. */
    public int tierTick(BoreTier t) { return tierTicks[t.ordinal()]; }
    /** Biomass fed since the last surge, which the next surge spends. */
    public float freshBiomass() { return freshBiomass; }
    public float biomassFed() { return biomassFed; }
    public int feeds() { return feeds; }
    public int surges() { return surges; }
    public int nextSurgeTick() { return nextSurgeTick; }
    public int bodiesSpawned() { return bodiesSpawned; }
    /** Bodies its surges have released at {@code stage}. */
    public int releasedAt(SwarmStage stage) { return stagesReleased[stage.ordinal()]; }
    /** Whether this bore has put its Matriarch out; only a mother ever does, once. */
    public boolean matriarchReleased() { return matriarchReleased; }
    /** The Matriarch this mother released, or 0. */
    public long matriarch() { return matriarch; }
    public boolean collapsed() { return collapsedTick >= 0; }
    /** Every spore colony the bore has sprouted, living or not, in sprouting order. */
    public List<Long> colonies() { return colonyView; }
    /** Every spore colony the bore's creep has seeded away from the mouth ({@link CreepOutposts}), living or not. */
    public List<Long> outposts() { return outpostView; }
    public int collapsedTick() { return collapsedTick; }

    /**
     * The beat key that names this bore: {@code N} for a first-wave bore,
     * {@code mother} for Colony Breach's one mother (numbered 0) and
     * {@code mother-N} for each of a heart's several.
     */
    public String beatKey() { return mother ? (number == 0 ? "mother" : "mother-" + number) : String.valueOf(number); }

    /**
     * Advances the base clock by {@code dt} seconds at {@code integrity}, the
     * share of the bore's durability left (1 untouched, 0 dead). Returns the
     * tier reached this step, or {@code null}.
     */
    BoreTier grow(float dt, float integrity, int tick) {
        if (collapsed()) return null;
        float slowed = Math.max(MIN_DAMAGED_GROWTH, Math.min(1f, integrity));
        return addGrowth(BASE_GROWTH_PER_SECOND * rateScale * slowed * Math.max(0f, dt), tick);
    }

    /**
     * Biomass delivered into the bore: growth now, a bigger next surge, and
     * that surge brought forward. Returns the tier reached, or {@code null}.
     */
    BoreTier feed(float amount, int tick) {
        if (collapsed() || !(amount > 0f)) return null;
        freshBiomass += amount;
        biomassFed += amount;
        feeds++;
        nextSurgeTick = Math.min(nextSurgeTick, tick + seconds(FED_SURGE_SECONDS));
        return addGrowth(amount * GROWTH_PER_BIOMASS, tick);
    }

    /** Bodies the next surge carries: the tier's pulse plus what fresh biomass buys. */
    public int surgeSize() {
        int bonus = Math.min(MAX_BIOMASS_BODIES, Math.round(freshBiomass * BODIES_PER_BIOMASS));
        return tier.surgeSize + bonus;
    }

    /** Whether a surge is due on {@code tick}. */
    boolean surgeDue(int tick) {
        return !collapsed() && !dormant && tick >= nextSurgeTick;
    }

    /** Whether the bore is held quiet: it grows and feeds, but sends no surge until woken. */
    public boolean dormant() { return dormant; }

    void hold() { dormant = true; }

    /** Wakes the bore with a surge due on {@code tick}. */
    void surgeAt(int tick) {
        dormant = false;
        nextSurgeTick = Math.min(nextSurgeTick, tick);
    }

    /**
     * Records a surge starting on {@code tick} and schedules the next one at
     * the tier's interval, swung by {@code jitter} in {@code [-1, 1]}.
     */
    void surged(int tick, float jitter) {
        surges++;
        freshBiomass = 0f;
        float interval = tier.surgeIntervalSeconds * (1f + SURGE_JITTER * Math.max(-1f, Math.min(1f, jitter)));
        nextSurgeTick = tick + Math.max(1, seconds(interval));
    }

    /** Records one body of a surge out of the mouth, born at {@code stage}. */
    void released(SwarmStage stage) {
        bodiesSpawned++;
        stagesReleased[stage.ordinal()]++;
    }

    /** Records that the body just released was this mother's Matriarch. */
    void releasedMatriarch(long body) {
        matriarchReleased = true;
        matriarch = body;
    }

    /** Brings the next surge forward to {@code tick} at the latest: a tide. */
    void callSurge(int tick) {
        if (!collapsed()) nextSurgeTick = Math.min(nextSurgeTick, tick);
    }

    /** Holds a due surge back to {@code tick}; the bore stays quiet until then. */
    void deferSurge(int tick) {
        nextSurgeTick = Math.max(nextSurgeTick, tick);
    }

    void sprouted(long colony, int tick) {
        colonies.add(colony);
        born.put(colony, tick);
    }

    void outposted(long colony, int tick) {
        outposts.add(colony);
        born.put(colony, tick);
    }

    /** The tick the colony or outpost {@code id} sprouted, or {@link #OLD} for one grown weeks ago or not the bore's. */
    public int bornTick(long id) {
        return born.getOrDefault(id, OLD);
    }

    /** Its growths so far were grown weeks ago: the creep was matured at setup. */
    void aged() {
        born.replaceAll((id, tick) -> OLD);
    }

    void collapse(int tick) {
        if (collapsedTick < 0) collapsedTick = tick;
    }

    private BoreTier addGrowth(float points, int tick) {
        growth += points;
        BoreTier reached = BoreTier.forGrowth(growth);
        if (reached.ordinal() <= tier.ordinal()) return null;
        for (BoreTier t : BoreTier.values()) {
            if (t.ordinal() > tier.ordinal() && t.ordinal() <= reached.ordinal()) tierTicks[t.ordinal()] = tick;
        }
        tier = reached;
        return reached;
    }

    private static int seconds(float s) {
        return Math.round(s / BattleSimulation.TICK_DT);
    }
}

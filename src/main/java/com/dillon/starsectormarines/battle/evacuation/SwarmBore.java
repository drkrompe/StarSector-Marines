package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.SwarmStage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;

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
    /** Its living Bombard, or 0: at most one at a time. */
    private long bombard;
    private int collapsedTick = -1;
    private final List<Long> colonies = new ArrayList<>();
    private final List<Long> colonyView = Collections.unmodifiableList(colonies);
    private final List<Long> outposts = new ArrayList<>();
    private final List<Long> outpostView = Collections.unmodifiableList(outposts);
    /** Colonies on their way: each rises where its root ends once the root has come (`colony-breach-nouns.md`, Nest). */
    private final List<Growing> growing = new ArrayList<>();
    private final List<Growing> growingView = Collections.unmodifiableList(growing);
    /**
     * Its own living bodies by strain (`colony-breach-nouns.md`, Hold), which its hold counts. The dead are forgotten
     * before each surge; a collapsed bore's are orphans, held by no bore.
     */
    private final Map<BoreStrainTable.Slot, List<Long>> held = new EnumMap<>(BoreStrainTable.Slot.class);

    /**
     * A colony on its way: its root digs out from the mouth to cell ({@code x}, {@code y}) and thickens from
     * {@code startTick} over {@code leadTicks}, and the colony of {@code grade} rises there when it has come.
     */
    public record Growing(BoreTier grade, int x, int y, int startTick, int leadTicks) {
        /** How far its root has come at {@code tick}, 0 to 1. */
        public float progress(int tick) {
            return leadTicks <= 0 ? 1f : Math.max(0f, Math.min(1f, (tick - startTick) / (float) leadTicks));
        }

        /** Whether its root has come and the colony may rise. */
        public boolean arrived(int tick) {
            return tick - startTick >= leadTicks;
        }
    }

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

    /** Its living Bombard, or 0 for none: a bore may draw one only while this is 0. */
    public long bombard() { return bombard; }
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

    /** Records {@code body}, just released as {@code slot}, as one of this bore's own. */
    void adopt(BoreStrainTable.Slot slot, long body) {
        held.computeIfAbsent(slot, s -> new ArrayList<>()).add(body);
    }

    /** Forgets every body of its own that {@code living} no longer answers for. */
    void forgetDead(LongPredicate living) {
        for (List<Long> bodies : held.values()) bodies.removeIf(body -> !living.test(body));
    }

    /** How many of its own bodies of {@code slot} it last knew to be living. */
    public int held(BoreStrainTable.Slot slot) {
        List<Long> bodies = held.get(slot);
        return bodies == null ? 0 : bodies.size();
    }

    /** How many more of {@code slot} its hold at its tier lets it put on the field. */
    public int freeHold(BoreStrainTable.Slot slot) {
        return Math.max(0, tier.hold(slot) - held(slot));
    }

    /** How many more bodies of any strain its hold lets it put on the field. */
    public int freeHold() {
        int free = 0;
        for (BoreStrainTable.Slot slot : BoreStrainTable.Slot.values()) free += freeHold(slot);
        return free;
    }

    /** Records that the body just released is this bore's Bombard. */
    void releasedBombard(long body) {
        bombard = body;
    }

    /** Records that this bore's Bombard has died, so it may draw another. */
    void lostBombard(long body) {
        if (bombard == body) bombard = 0L;
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

    void sprouted(long colony) {
        colonies.add(colony);
    }

    void outposted(long colony) {
        outposts.add(colony);
    }

    /** Its colonies on their way, in the order they were begun. */
    public List<Growing> growing() { return growingView; }

    void beginGrowing(Growing g) {
        growing.add(g);
    }

    void doneGrowing(Growing g) {
        growing.remove(g);
    }

    void collapse(int tick) {
        if (collapsedTick < 0) collapsedTick = tick;
        growing.clear();
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

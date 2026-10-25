package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Packs and their leaders (`colony-breach-nouns.md`, Pack, pack leader).
 *
 * <p>A pack is the bodies of one pulse; its first elder (or the mother's
 * Matriarch) is its leader. While the leader lives, a member on its leash
 * shares the leader's quarry when it has none of its own close by, senses what
 * any member on the leash senses, and strikes faster. When the leader dies the
 * pack breaks for good: the bonuses go at once, members scatter to quarry they
 * sense themselves, and its whelps bolt back toward the bore, dropping what
 * they carry. A body outside any living leader's pack (a leaderless pulse, a
 * broken pack) joins a leader whose leash it comes within: that is how two
 * surges merge. The Matriarch leads every pack within her leash; an elder that
 * comes within it defers to her and brings its pack.
 *
 * <p>A pulse is split into packs of at most {@link #MAX_PACK}. A pack with no
 * elder at its head is <em>leaderless</em>: it has none of a leader's bonuses
 * and never breaks, but it is still a pack, so it moves as one
 * ({@link SwarmPackPlans}). Its members join a leader's pack whose leash they
 * come within only while that pack has room.
 *
 * <p>Everything here is written once a tick, serially ({@link #tick}), and read
 * by the parallel unit update, which never writes it.
 * {@code -Dbattle.swarm.packLeaders=false} is the control: no pack ever forms
 * and every body hunts alone. Stages still apply.
 */
public final class SwarmPacks {

    /** Cells from its leader within which a member has the pack's bonuses. */
    public static final float LEASH = 8f;
    /** A member with its own quarry nearer than this keeps it rather than taking the leader's. */
    public static final float OWN_QUARRY_CELLS = 3f;
    /** A cohesive member's attack cooldown against its own: 15% quicker on the blow. */
    public static final float TEMPO = 0.85f;
    /** Seconds a broken pack's members scatter, and its whelps bolt for the bore. */
    public static final float SCATTER_SECONDS = 2f;
    public static final float BOLT_SECONDS = 4f;
    /** The most bodies one pack takes; a bigger pulse splits evenly. */
    public static final int MAX_PACK = 8;

    /** Whether packs form. {@code -Dbattle.swarm.packLeaders=false} is the control. */
    public static boolean enabledByDefault() {
        return Boolean.parseBoolean(System.getProperty("battle.swarm.packLeaders", "true"));
    }

    /** One pack. */
    private static final class Pack {
        final int id;
        long leader;
        boolean broken;
        float leaderX;
        float leaderY;
        long quarry;
        /** Contacts sensed by members on the leash this tick, sorted. */
        long[] contacts = new long[0];
        /** Living members at the start of this tick, for room to recruit. */
        int size;

        Pack(int id, long leader) {
            this.id = id;
            this.leader = leader;
        }

        boolean led() { return leader != 0L && !broken; }
    }

    /** A pack that broke, as the beats and the scene read it. */
    public record Break(int tick, int pack, long leader, SwarmStage leaderStage) { }

    private final boolean enabled;
    private final Map<Integer, Pack> packs = new HashMap<>();
    /** Body to the pack it belongs to. */
    private final Map<Long, Pack> packOf = new HashMap<>();
    /** Body to the tick its scatter ends; body to the tick its bolt ends. */
    private final Map<Long, Integer> scatterUntil = new HashMap<>();
    private final Map<Long, Integer> boltUntil = new HashMap<>();
    private final List<Break> breaks = new ArrayList<>();
    private int nextId = 1;

    public SwarmPacks() {
        this(enabledByDefault());
    }

    public SwarmPacks(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() { return enabled; }

    // ---- serial: forming ----

    /** Opens a pack led by {@code leader} (an elder or a Matriarch, the first body of its pulse). Returns its id, or 0. */
    public int open(long leader) {
        if (!enabled) return 0;
        Pack pack = new Pack(nextId++, leader);
        packs.put(pack.id, pack);
        packOf.put(leader, pack);
        return pack.id;
    }

    /** Opens a leaderless pack whose first body is {@code first}. Returns its id, or 0 while packs are off. */
    public int openLeaderless(long first) {
        if (!enabled) return 0;
        Pack pack = new Pack(nextId++, 0L);
        packs.put(pack.id, pack);
        packOf.put(first, pack);
        return pack.id;
    }

    /**
     * How a pulse of {@code size} bodies splits into packs: as few packs as
     * fit {@link #MAX_PACK}, as even as they can be, larger first.
     */
    public static int[] packSizes(int size) {
        if (size <= 0) return new int[0];
        int count = (size + MAX_PACK - 1) / MAX_PACK;
        int[] sizes = new int[count];
        for (int i = 0; i < count; i++) sizes[i] = size / count + (i < size % count ? 1 : 0);
        return sizes;
    }

    /** Whether body {@code index} of a pulse of {@code size} is the first of a new pack. */
    public static boolean startsPack(int index, int size) {
        int start = 0;
        for (int s : packSizes(size)) {
            if (index == start) return true;
            start += s;
        }
        return false;
    }

    /** Puts {@code body} in pack {@code id}; nothing for id 0 (packs off). */
    public void join(int id, long body) {
        Pack pack = packs.get(id);
        if (pack != null) packOf.put(body, pack);
    }

    // ---- serial: once a tick ----

    /**
     * Breaks every pack whose leader has died, then refreshes each led pack's
     * leader position, quarry and pooled contacts, then lets packless bodies
     * (and an elder's pack within the Matriarch's leash) join a leader.
     */
    public void tick(BattleSimulation sim) {
        if (!enabled || packs.isEmpty()) return;
        int tick = sim.getSimTickIndex();
        scatterUntil.values().removeIf(until -> tick >= until);
        boltUntil.values().removeIf(until -> tick >= until);
        packOf.keySet().removeIf(body -> sim.resolveUnit(body) == 0L && !isLeaderOf(body));

        for (Pack pack : packs.values()) pack.size = 0;
        for (Pack pack : packOf.values()) pack.size++;
        List<Pack> led = new ArrayList<>();
        for (Pack pack : sortedPacks()) {
            if (!pack.led()) continue;
            if (sim.resolveUnit(pack.leader) == 0L || !sim.world().isAlive(pack.leader)) {
                breakPack(pack, sim, tick);
                continue;
            }
            pack.leaderX = sim.world().x(pack.leader);
            pack.leaderY = sim.world().y(pack.leader);
            pack.quarry = sim.resolveUnit(sim.combat().targetId(pack.leader));
            led.add(pack);
        }
        if (led.isEmpty()) return;
        recruit(sim, led);
        for (Pack pack : led) pool(pack, sim);
    }

    private boolean isLeaderOf(long body) {
        Pack pack = packOf.get(body);
        return pack != null && pack.leader == body;
    }

    private List<Pack> sortedPacks() {
        List<Pack> sorted = new ArrayList<>(packs.values());
        sorted.sort((a, b) -> Integer.compare(a.id, b.id));
        return sorted;
    }

    private void breakPack(Pack pack, BattleSimulation sim, int tick) {
        pack.broken = true;
        SwarmStage stage = sim.identity().swarmStage(pack.leader);
        breaks.add(new Break(tick, pack.id, pack.leader, stage));
        sim.swarmCues().post(SwarmCues.Kind.PACK_BROKEN, pack.leader, sim);
        int scatter = tick + ticks(SCATTER_SECONDS);
        int bolt = tick + ticks(BOLT_SECONDS);
        long[] members = membersOf(pack);
        for (long body : members) {
            if (body == pack.leader || sim.resolveUnit(body) == 0L) continue;
            scatterUntil.put(body, scatter);
            sim.combat().setTargetId(body, 0L);
            if (sim.identity().swarmStage(body) == SwarmStage.WHELP) {
                boltUntil.put(body, bolt);
                sim.swarmHarvest().drop(sim, body);
                sim.clearPath(body);
            }
        }
    }

    /** Packless bodies within a led pack's leash join it; an elder's pack within the Matriarch's leash merges into hers. */
    private void recruit(BattleSimulation sim, List<Pack> led) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long body = sim.liveUnitAt(i);
            if (!sim.identity().type(body).isSwarm() || sim.identity().type(body).isSwarmWorker()
                    || boltUntil.containsKey(body)) continue;
            Pack own = packOf.get(body);
            boolean ownLed = own != null && own.led();
            for (Pack pack : led) {
                // A pack that deferred to the Matriarch earlier in this pass leads no one now.
                if (pack == own || !pack.led()) continue;
                boolean matriarch = sim.identity().type(pack.leader) == UnitType.SWARM_MATRIARCH;
                if (ownLed && !matriarch) continue;
                if (ownLed && sim.identity().type(own.leader) == UnitType.SWARM_MATRIARCH) continue;
                // A leaderless pack's member comes over only while the leader's pack has room.
                if (!ownLed && own != null && !own.broken && pack.size >= MAX_PACK) continue;
                if (!onLeash(sim.world().x(body), sim.world().y(body), pack)) continue;
                if (ownLed && own.leader == body) {
                    // An elder defers to her and brings its pack.
                    for (long member : membersOf(own)) packOf.put(member, pack);
                    own.broken = true;
                    own.leader = 0L;
                } else {
                    if (own != null) own.size--;
                    packOf.put(body, pack);
                    pack.size++;
                }
                break;
            }
        }
    }

    /** Pools what members on the leash have in their sights: what one senses, the pack senses. */
    private void pool(Pack pack, BattleSimulation sim) {
        long[] members = membersOf(pack);
        long[] contacts = new long[members.length];
        int n = 0;
        for (long body : members) {
            if (sim.resolveUnit(body) == 0L || !onLeash(sim.world().x(body), sim.world().y(body), pack)) continue;
            long target = sim.resolveUnit(sim.combat().targetId(body));
            if (target == 0L) continue;
            float dx = sim.world().x(target) - sim.world().x(body);
            float dy = sim.world().y(target) - sim.world().y(body);
            float sense = sim.vision().visionRange(body);
            if (dx * dx + dy * dy > sense * sense) continue;
            contacts[n++] = target;
        }
        long[] pooled = Arrays.copyOf(contacts, n);
        Arrays.sort(pooled);
        pack.contacts = Arrays.stream(pooled).distinct().toArray();
    }

    private long[] membersOf(Pack pack) {
        long[] members = packOf.entrySet().stream()
                .filter(e -> e.getValue() == pack).mapToLong(Map.Entry::getKey).toArray();
        Arrays.sort(members);
        return members;
    }

    private static boolean onLeash(float x, float y, Pack pack) {
        float dx = x - pack.leaderX, dy = y - pack.leaderY;
        return dx * dx + dy * dy <= LEASH * LEASH;
    }

    private static int ticks(float seconds) {
        return Math.round(seconds / BattleSimulation.TICK_DT);
    }

    // ---- read by the parallel update ----

    private Pack cohesive(long body, BattleSimulation sim) {
        Pack pack = packOf.get(body);
        if (pack == null || !pack.led()) return null;
        if (body == pack.leader) return pack;
        return onLeash(sim.world().x(body), sim.world().y(body), pack) ? pack : null;
    }

    /** Whether {@code body} is on a living leader's leash (the leader itself included). */
    public boolean isCohesive(long body, BattleSimulation sim) {
        return cohesive(body, sim) != null;
    }

    /** Whether {@code body} leads a pack that has not broken. */
    public boolean isLeader(long body) {
        Pack pack = packOf.get(body);
        return pack != null && pack.led() && pack.leader == body;
    }

    /** The leader of {@code body}'s pack while it lives, or 0. */
    public long leaderOf(long body) {
        Pack pack = packOf.get(body);
        return pack != null && pack.led() ? pack.leader : 0L;
    }

    /** {@code cooldown} as a body of a cohesive pack strikes: {@link #TEMPO} of it. */
    public float cooldown(long body, float cooldown, BattleSimulation sim) {
        return cohesive(body, sim) != null ? cooldown * TEMPO : cooldown;
    }

    /**
     * The quarry {@code body} hunts given the one it chose itself: a cohesive
     * member with nothing of its own within {@link #OWN_QUARRY_CELLS} takes the
     * leader's, so the pack hits one point.
     */
    public long sharedQuarry(long body, long own, BattleSimulation sim) {
        Pack pack = cohesive(body, sim);
        if (pack == null || body == pack.leader || pack.quarry == 0L) return own;
        if (sim.resolveUnit(pack.quarry) == 0L || !sim.world().isAlive(pack.quarry)) return own;
        if (own != 0L && sim.resolveUnit(own) != 0L) {
            float dx = sim.world().x(own) - sim.world().x(body);
            float dy = sim.world().y(own) - sim.world().y(body);
            if (dx * dx + dy * dy <= OWN_QUARRY_CELLS * OWN_QUARRY_CELLS) return own;
        }
        return pack.quarry;
    }

    /** Contacts the pack senses, for a cohesive member's own choice of quarry; empty otherwise. */
    public long[] sharedContacts(long body, BattleSimulation sim) {
        Pack pack = cohesive(body, sim);
        return pack == null ? EMPTY : pack.contacts;
    }

    /** Whether {@code body} took {@code target} from its leader: the pack's cry, not its own. */
    public boolean followsLeader(long body, long target, BattleSimulation sim) {
        Pack pack = cohesive(body, sim);
        return pack != null && body != pack.leader && target != 0L && target == pack.quarry;
    }

    /** Whether {@code body} is scattering from a broken pack: it hunts only what it senses itself. */
    public boolean scattering(long body) {
        return scatterUntil.containsKey(body);
    }

    /** Whether {@code body} is a whelp bolting back to its bore after its leader died. */
    public boolean bolting(long body) {
        return boltUntil.containsKey(body);
    }

    /** The id of the pack {@code body} belongs to, or 0. */
    public int packIdOf(long body) {
        Pack pack = packOf.get(body);
        return pack == null ? 0 : pack.id;
    }

    /** Whether pack {@code id} still stands: it exists and has not broken (a leaderless pack never breaks). */
    public boolean isStanding(int id) {
        Pack pack = packs.get(id);
        return pack != null && !pack.broken;
    }

    /** The living leader of pack {@code id}, or 0 for a leaderless or broken pack. */
    public long leaderOfPack(int id) {
        Pack pack = packs.get(id);
        return pack != null && pack.led() ? pack.leader : 0L;
    }

    /** Packs broken this battle, oldest first. */
    public List<Break> breaks() { return breaks; }

    private static final long[] EMPTY = new long[0];
}

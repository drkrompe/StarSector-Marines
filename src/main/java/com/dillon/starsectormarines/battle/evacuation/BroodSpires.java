package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierLook;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierPack;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierPatrol;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierRules;
import com.dillon.starsectormarines.battle.evacuation.flier.SwarmFliers;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where Harrier packs come from in a battle (`colony-breach-nouns.md`, Brood spire and Harrier): the brood spires
 * an old bloom has grown, and a Matriarch's brood.
 *
 * <ul>
 *   <li><b>Only an old bloom has fliers.</b> Spires grow, and broods rise, only once the battle's creep has been
 *   matured at setup ({@link #grow}): the Cordon and the heart, never a fresh Colony Breach.</li>
 *   <li><b>A spire stands on its bore's creep.</b> One off each living satellite (non-mother) bore at maw, on
 *   creep-held open ground between {@link #STANDOFF_MIN} and {@link #STANDOFF_MAX} cells from the mouth, and no
 *   nearer than {@link #SPACING} to another spire, so a map carries a few, not one per bore
 *   ({@link #choose}). It is a structure on the swarm's side, fought as a bore's body is, and withers when its
 *   bore collapses.</li>
 *   <li><b>A spire launches with its bore's surge</b>: surges, not trickles. On each of its bore's surges it
 *   releases a pack of {@link #PACK} when {@link #releases} allows it, hatching out of its crown one by one
 *   ({@link SwarmFliers#hatch}).</li>
 *   <li><b>A quiet spire patrols.</b> A spire with its roost full and no prey within {@link #WAKE_RANGE} puts
 *   {@link #PATROL} of its roost up every {@link #PATROL_GAP_MIN_SECONDS} to {@link #PATROL_GAP_MAX_SECONDS}
 *   ({@link #patrols}) to fly a loop over its bore ({@link HarrierPatrol}) and land back on its hooks. A patrol that
 *   comes home short leaves the roost short, to fill back as after a launch ({@link #roostAfterPatrol}).</li>
 *   <li><b>A Matriarch comes with her brood.</b> When a mother puts her Matriarch out, a pack of
 *   {@link #BROOD_PACK} hatches off her back one by one as she climbs out, and flies as her escort.</li>
 *   <li><b>The flier law holds.</b> No pack rises while the marines and their allies have no anti-air on the map
 *   ({@link SwarmFliers#hasAirCover}).</li>
 * </ul>
 *
 * Runs once a tick after the director, reading the bores' events from its own cursor, and before the fliers fly,
 * so a pack released this tick flies this tick. Deterministic: bores in opening order, no random draw.
 * {@code -Dbattle.swarm.broodSpires=false} is the control: no spires and no broods.
 */
public final class BroodSpires {

    /** Control-run switch; spires and broods unless it reads {@code false}. */
    public static final String PROPERTY = "battle.swarm.broodSpires";
    /** The spire's structure. */
    public static final String STRUCTURE = "structure.brood-spire";
    /** Harriers in a pack a spire launches. */
    public static final int PACK = 7;
    /** Harriers in a Matriarch's brood. */
    public static final int BROOD_PACK = 6;
    /** A spire launches only while a marine or an ally stands within this of it: what its brood would sense. */
    public static final float WAKE_RANGE = HarrierRules.SENSE_RANGE;
    /** Least seconds between one spire's packs, however often its bore surges, before skitterers tend it. */
    public static final float MIN_GAP_SECONDS = 45f;
    /** A tended spire's gap never falls under this, however many skitterers feed it (`colony-breach-nouns.md`, Skitterer). */
    public static final float TENDED_GAP_FLOOR_SECONDS = 20f;
    /** Nearest and furthest a spire stands from its bore's mouth, in cells. */
    public static final float STANDOFF_MIN = 16f, STANDOFF_MAX = 28f;
    /** Two spires never stand nearer each other than this, in cells. */
    public static final float SPACING = 64f;
    /** A spire stands at least this far from any bore or spore colony, in cells. */
    static final float CLEARANCE = 5f;
    /** Directions tried on each ring. */
    static final int DIRECTIONS = 24;
    /** Hooks a spire's brood clings to while it waits; drawn only (`colony-breach-nouns.md`, Brood spire). */
    public static final int PERCHES = 5;
    /** Harriers in a patrol off a spire's roost. */
    public static final int PATROL = 3;
    /** Least and most seconds between one spire's patrols, counted from the last one's return. */
    public static final float PATROL_GAP_MIN_SECONDS = 30f, PATROL_GAP_MAX_SECONDS = 60f;
    /** Damage that withers a spire outright when its bore collapses. */
    private static final float WITHER_DAMAGE = 1_000_000f;

    /** One spire: its body, the bore it grew off, its cell, and its last pack. */
    public static final class Spire {
        public final long entityId;
        public final SwarmBore bore;
        public final int x, y;
        int lastReleaseTick = Integer.MIN_VALUE / 2;
        /** Ticks the skitterers have fed off the current wait. */
        int tendedTicks;
        HarrierPack pack;
        int packs;
        /** Its patrol in the air, or {@code null}; and when the last came home. */
        HarrierPack patrol;
        int lastPatrolTick;
        int patrols;

        Spire(long entityId, SwarmBore bore, int x, int y) {
            this.entityId = entityId;
            this.bore = bore;
            this.x = x;
            this.y = y;
        }

        /** How many packs it has launched. */
        public int packs() { return packs; }

        /** How many patrols it has put up. */
        public int patrols() { return patrols; }

        /** Its patrol in the air, or {@code null}. */
        public HarrierPack patrol() { return patrol; }

        /** Ticks the skitterers have fed off the wait since its last pack. */
        public int tendedTicks() { return tendedTicks; }
    }

    /** A pack rose, for beats and evidence: off a spire ({@code spire} its body) or as a brood ({@code spire} 0). */
    public record Launch(int tick, long spire, long matriarch, int pack, int size) { }

    private final boolean enabled = !"false".equalsIgnoreCase(System.getProperty(PROPERTY, "true"));
    private final List<Spire> spires = new ArrayList<>();
    private final List<Launch> launches = new ArrayList<>();
    private boolean matured;
    private int cursor;

    public List<Spire> spires() { return Collections.unmodifiableList(spires); }

    public List<Launch> launches() { return Collections.unmodifiableList(launches); }

    /**
     * How many of a spire's next brood cling to its hooks: none while its own pack is in the air, then filling back
     * one by one over its gap of {@code gapTicks} after a launch (shorter when tended), so a full roost is a spire
     * ready to launch again.
     */
    public static int perched(boolean packAloft, int ticksSinceLast, int gapTicks) {
        if (packAloft) return 0;
        return (int) Math.min(PERCHES, (long) Math.max(0, ticksSinceLast) * PERCHES / Math.max(1, gapTicks));
    }

    /** How many Harriers cling to {@code s}'s hooks now ({@link #perched(boolean, int, int)}); none on a dead spire. */
    public int perched(BattleSimulation sim, Spire s) {
        if (sim.resolveUnit(s.entityId) == 0L || !sim.world().isAlive(s.entityId)) return 0;
        // A pack still hatching leaves its roost one by one: the hooks hold those still to come out.
        if (s.pack != null && s.pack.unhatched() > 0 && sim.swarmFliers().packs().contains(s.pack)) {
            return Math.min(PERCHES, s.pack.unhatched());
        }
        int roost = perched(aloft(sim, s), sim.getSimTickIndex() - s.lastReleaseTick, gapTicks(s.tendedTicks));
        // A patrol's birds are off the hooks while they fly; those still to hatch cling on.
        if (patrolling(sim, s)) roost = Math.max(0, roost - s.patrol.members.size());
        return roost;
    }

    private static boolean patrolling(BattleSimulation sim, Spire s) {
        return s.patrol != null && sim.swarmFliers().packs().contains(s.patrol);
    }

    /**
     * Whether a spire puts a patrol up: only under anti-air ({@code airCover}), with no pack nor patrol of its own in
     * the air, its roost full, nothing to hunt within {@link #WAKE_RANGE} (that is a launch's work), and its
     * {@code gapTicks} since the last patrol came home.
     */
    public static boolean patrols(boolean airCover, boolean packAloft, boolean patrolAloft, boolean roostFull,
                                  boolean preyNear, int ticksSincePatrol, int gapTicks) {
        return airCover && !packAloft && !patrolAloft && roostFull && !preyNear && ticksSincePatrol >= gapTicks;
    }

    /** Ticks between a spire's {@code n}th patrol and its next: seeded off the spire, in the patrol gap. */
    public static int patrolGapTicks(long spire, int n) {
        float u = SwarmBores.unit(SwarmBores.mix(spire * 0x2545F4914F6CDD1DL + n));
        return Math.round((PATROL_GAP_MIN_SECONDS + (PATROL_GAP_MAX_SECONDS - PATROL_GAP_MIN_SECONDS) * u)
                / BattleSimulation.TICK_DT);
    }

    /**
     * The spire's last-launch tick once a patrol came home at {@code tick} with {@code lost} of its birds lost: its
     * roost ({@link #perched(boolean, int, int)}) holds {@link #PERCHES} less the lost and fills back from there over
     * the gap, which also holds off its next launch. A patrol home whole leaves the roost as it was.
     */
    public static int roostAfterPatrol(int lastReleaseTick, int tick, int lost, int gapTicks) {
        int held = Math.max(0, PERCHES - Math.max(0, lost));
        return Math.max(lastReleaseTick, tick - (int) ((long) held * gapTicks / PERCHES));
    }

    private static boolean aloft(BattleSimulation sim, Spire s) {
        return s.pack != null && (!s.pack.members.isEmpty() || s.pack.unhatched() > 0)
                && sim.swarmFliers().packs().contains(s.pack);
    }

    /** The spire whose body is {@code entityId}, or {@code null}. */
    public Spire byEntity(long entityId) {
        for (Spire s : spires) if (s.entityId == entityId) return s;
        return null;
    }

    /**
     * Grows the old bloom's spires: called once the battle's creep has been matured at setup. One off each living
     * satellite bore at maw, in opening order, where {@link #choose} finds ground. From here on the battle's
     * Matriarchs also come with their broods.
     */
    public void grow(BattleSimulation sim) {
        if (!enabled || matured) return;
        matured = true;
        CreepField field = sim.creepFieldIfPresent();
        if (field == null) return;
        NavigationGrid grid = sim.getGrid();
        CreepOutposts.Ground ground = new CreepOutposts.Ground() {
            @Override public boolean creeped(int x, int y) { return field.isCreeped(x, y); }

            @Override public boolean open(int x, int y) {
                return grid.inBounds(x, y) && grid.isWalkable(x, y)
                        && !sim.isInsideRescueOpeningProtectedZone(x, y);
            }
        };
        for (SwarmBore bore : sim.swarmBores().bores()) {
            if (bore.collapsed() || bore.mother() || bore.tier() != BoreTier.MAW) continue;
            List<float[]> placed = new ArrayList<>();
            for (Spire s : spires) placed.add(new float[]{s.x + 0.5f, s.y + 0.5f});
            int[] cell = choose(ground, bore.x(), bore.y(), turn(bore), placed, structures(sim));
            if (cell == null || SwarmBores.nearBody(sim, cell[0], cell[1], 1f)) continue;
            String name = "Bore " + bore.beatKey() + " Brood Spire";
            long id = sim.spawn(SwarmBores.bodySpec(name, STRUCTURE, cell[0], cell[1]));
            spires.add(new Spire(id, bore, cell[0], cell[1]));
        }
    }

    /**
     * The cell a spire grows on off a bore at {@code (boreX, boreY)}: rings outward from {@link #STANDOFF_MIN} to
     * {@link #STANDOFF_MAX}, {@link #DIRECTIONS} a ring starting at {@code turn} (a share of a lap), the first cell
     * that is open, inside the creep by {@link CreepOutposts#INTERIOR}, at least {@link #SPACING} from every spire
     * in {@code spires} and {@link #CLEARANCE} from every bore and colony in {@code structures} ({@code {x, y}}
     * cell centres). Returns {@code {x, y}}, or {@code null}.
     */
    public static int[] choose(CreepOutposts.Ground ground, int boreX, int boreY, float turn, List<float[]> spires,
                               List<float[]> structures) {
        for (float[] s : spires) {
            float dx = s[0] - (boreX + 0.5f), dy = s[1] - (boreY + 0.5f);
            if (dx * dx + dy * dy < (SPACING - STANDOFF_MAX) * (SPACING - STANDOFF_MAX)) return null;
        }
        for (float r = STANDOFF_MIN; r <= STANDOFF_MAX; r += 1f) {
            for (int k = 0; k < DIRECTIONS; k++) {
                double angle = 2.0 * Math.PI * (turn + k / (double) DIRECTIONS);
                int x = (int) Math.floor(boreX + 0.5 + Math.cos(angle) * r);
                int y = (int) Math.floor(boreY + 0.5 + Math.sin(angle) * r);
                if (CreepOutposts.good(ground, x, y, CLEARANCE, structures) && apart(x, y, spires)) {
                    return new int[]{x, y};
                }
            }
        }
        return null;
    }

    private static boolean apart(int x, int y, List<float[]> spires) {
        for (float[] s : spires) {
            float dx = s[0] - (x + 0.5f), dy = s[1] - (y + 0.5f);
            if (dx * dx + dy * dy < SPACING * SPACING) return false;
        }
        return true;
    }

    /**
     * Whether a spire launches on its bore's surge: only under anti-air on the side it hunts ({@code airCover}),
     * never while a pack of its own is still in the air, never sooner than {@link #MIN_GAP_SECONDS} after its
     * last, and only with prey within {@link #WAKE_RANGE}.
     */
    public static boolean releases(boolean airCover, boolean packAloft, int ticksSinceLast, boolean preyNear) {
        return releases(airCover, packAloft, ticksSinceLast, 0, preyNear);
    }

    /** As {@link #releases(boolean, boolean, int, boolean)}, with {@code tendedTicks} fed off the wait. */
    public static boolean releases(boolean airCover, boolean packAloft, int ticksSinceLast, int tendedTicks,
                                   boolean preyNear) {
        return airCover && !packAloft && preyNear && ticksSinceLast >= gapTicks(tendedTicks);
    }

    /**
     * Ticks a spire waits after a pack with {@code tendedTicks} fed off by skitterers: {@link #MIN_GAP_SECONDS} less
     * what they fed, never under {@link #TENDED_GAP_FLOOR_SECONDS}.
     */
    public static int gapTicks(int tendedTicks) {
        int full = Math.round(MIN_GAP_SECONDS / BattleSimulation.TICK_DT);
        int floor = Math.round(TENDED_GAP_FLOOR_SECONDS / BattleSimulation.TICK_DT);
        return Math.max(floor, full - Math.max(0, tendedTicks));
    }

    /** Whether {@code s} stands and is still waiting out its gap at {@code tick}: worth a skitterer's feed. */
    public static boolean waiting(Spire s, int tick) {
        return tick - s.lastReleaseTick < gapTicks(s.tendedTicks);
    }

    /** A skitterer fed {@code s}: {@code seconds} off its current wait, never past {@link #TENDED_GAP_FLOOR_SECONDS}. */
    public void tend(Spire s, float seconds) {
        s.tendedTicks += Math.round(seconds / BattleSimulation.TICK_DT);
    }

    /** Once a tick, after the director and before the fliers: wither, launch on surges, raise broods. */
    public void tick(BattleSimulation sim) {
        if (!enabled || !matured) return;
        int tick = sim.getSimTickIndex();
        for (Spire s : spires) {
            if (s.bore.collapsed() && sim.resolveUnit(s.entityId) != 0L && sim.world().isAlive(s.entityId)) {
                sim.applyDamage(s.entityId, WITHER_DAMAGE, WITHER_DAMAGE);
            }
        }
        for (Spire s : spires) patrol(sim, s, tick);
        List<SwarmBores.Event> events = sim.swarmBores().events();
        while (cursor < events.size()) {
            SwarmBores.Event e = events.get(cursor++);
            if (e.kind() == SwarmBores.EventKind.SURGE) {
                for (Spire s : spires) if (s.bore == e.bore()) surged(sim, s, tick);
            } else if (e.kind() == SwarmBores.EventKind.MATRIARCH) {
                brood(sim, e.bore().matriarch(), tick);
            }
        }
    }

    private void surged(BattleSimulation sim, Spire s, int tick) {
        if (sim.resolveUnit(s.entityId) == 0L || !sim.world().isAlive(s.entityId)) return;
        float x = s.x + 0.5f, y = s.y + 0.5f;
        if (!releases(SwarmFliers.hasAirCover(sim), aloft(sim, s), tick - s.lastReleaseTick, s.tendedTicks,
                preyNear(sim, x, y))) return;
        // The pack hatches out of the crown one by one; with emergence off it rises off it whole.
        s.pack = sim.swarmFliers().hatchUnderCover(sim, HarrierLook.DEFAULT, s.entityId, x, y, x, y, PACK, 0L);
        if (!sim.swarmEmergence().enabled()) sim.swarmFliers().risingFrom(sim, s.pack, x, y);
        s.lastReleaseTick = tick;
        s.tendedTicks = 0;
        s.packs++;
        launches.add(new Launch(tick, s.entityId, 0L, s.pack.id, PACK));
    }

    /** Brings a patrol home to the roost, or puts one up when the spire is quiet. */
    private void patrol(BattleSimulation sim, Spire s, int tick) {
        if (s.patrol != null && !patrolling(sim, s)) {
            int lost = s.patrol.patrol().size() - s.patrol.patrol().landed();
            s.lastReleaseTick = roostAfterPatrol(s.lastReleaseTick, tick, lost, gapTicks(s.tendedTicks));
            s.patrol = null;
            s.lastPatrolTick = tick;
        }
        if (sim.resolveUnit(s.entityId) == 0L || !sim.world().isAlive(s.entityId)) return;
        float x = s.x + 0.5f, y = s.y + 0.5f;
        boolean full = perched(aloft(sim, s), tick - s.lastReleaseTick, gapTicks(s.tendedTicks)) >= PERCHES;
        if (!patrols(SwarmFliers.hasAirCover(sim), aloft(sim, s), s.patrol != null, full, preyNear(sim, x, y),
                tick - s.lastPatrolTick, patrolGapTicks(s.entityId, s.patrols))) return;
        NavigationGrid grid = sim.getGrid();
        float[] route = HarrierPatrol.route(s.bore.x() + 0.5f, s.bore.y() + 0.5f, x, y,
                s.entityId * 31L + s.patrols, grid.getWidth(), grid.getHeight());
        s.patrol = sim.swarmFliers().patrolUnderCover(sim, HarrierLook.DEFAULT, s.entityId, x, y, route, PATROL);
        s.patrols++;
    }

    private void brood(BattleSimulation sim, long matriarch, int tick) {
        if (matriarch == 0L || !sim.world().isAlive(matriarch) || !SwarmFliers.hasAirCover(sim)) return;
        HarrierPack pack = sim.swarmFliers().releaseUnderCover(sim, HarrierLook.DEFAULT, sim.world().x(matriarch),
                sim.world().y(matriarch), BROOD_PACK, matriarch);
        launches.add(new Launch(tick, 0L, matriarch, pack.id, BROOD_PACK));
    }

    /** Whether a marine or an ally on foot or in a hull stands within {@link #WAKE_RANGE} of {@code (x, y)}. */
    private static boolean preyNear(BattleSimulation sim, float x, float y) {
        float r2 = WAKE_RANGE * WAKE_RANGE;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long u = sim.liveUnitAt(i);
            Faction f = sim.identity().faction(u);
            if (f != Faction.MARINE && f != Faction.ALLY) continue;
            float dx = sim.world().x(u) - x, dy = sim.world().y(u) - y;
            if (dx * dx + dy * dy <= r2) return true;
        }
        return false;
    }

    /** Every standing bore's and living colony's cell centre: a spire keeps its {@link #CLEARANCE} from them. */
    private static List<float[]> structures(BattleSimulation sim) {
        List<float[]> out = new ArrayList<>();
        for (SwarmBore bore : sim.swarmBores().bores()) {
            if (bore.collapsed()) continue;
            out.add(new float[]{bore.x() + 0.5f, bore.y() + 0.5f});
            for (long id : bore.colonies()) {
                if (sim.resolveUnit(id) != 0L) out.add(new float[]{sim.world().x(id), sim.world().y(id)});
            }
            for (long id : bore.outposts()) {
                if (sim.resolveUnit(id) != 0L) out.add(new float[]{sim.world().x(id), sim.world().y(id)});
            }
        }
        return out;
    }

    /** A seeded share of a lap per bore, so spires do not all stand on the same side of their bores. */
    private static float turn(SwarmBore bore) {
        return SwarmBores.unit(SwarmBores.mix(bore.entityId() * 0xD6E8FEB86659FD93L + bore.number()));
    }
}

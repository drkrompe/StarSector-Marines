package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierLook;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierPack;
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
 *   releases a pack of {@link #PACK} when {@link #releases} allows it, rising off its crown
 *   ({@link SwarmFliers#risingFrom}).</li>
 *   <li><b>A Matriarch comes with her brood.</b> When a mother puts her Matriarch out, a pack of
 *   {@link #BROOD_PACK} rises over her and flies as her escort.</li>
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
    /** Least seconds between one spire's packs, however often its bore surges. */
    public static final float MIN_GAP_SECONDS = 45f;
    /** Nearest and furthest a spire stands from its bore's mouth, in cells. */
    public static final float STANDOFF_MIN = 16f, STANDOFF_MAX = 28f;
    /** Two spires never stand nearer each other than this, in cells. */
    public static final float SPACING = 64f;
    /** A spire stands at least this far from any bore or spore colony, in cells. */
    static final float CLEARANCE = 5f;
    /** Directions tried on each ring. */
    static final int DIRECTIONS = 24;
    /** Damage that withers a spire outright when its bore collapses. */
    private static final float WITHER_DAMAGE = 1_000_000f;

    /** One spire: its body, the bore it grew off, its cell, and its last pack. */
    public static final class Spire {
        public final long entityId;
        public final SwarmBore bore;
        public final int x, y;
        int lastReleaseTick = Integer.MIN_VALUE / 2;
        HarrierPack pack;
        int packs;

        Spire(long entityId, SwarmBore bore, int x, int y) {
            this.entityId = entityId;
            this.bore = bore;
            this.x = x;
            this.y = y;
        }

        /** How many packs it has launched. */
        public int packs() { return packs; }
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
        return airCover && !packAloft && preyNear
                && ticksSinceLast >= Math.round(MIN_GAP_SECONDS / BattleSimulation.TICK_DT);
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
        boolean aloft = s.pack != null && !s.pack.members.isEmpty() && sim.swarmFliers().packs().contains(s.pack);
        float x = s.x + 0.5f, y = s.y + 0.5f;
        if (!releases(SwarmFliers.hasAirCover(sim), aloft, tick - s.lastReleaseTick, preyNear(sim, x, y))) return;
        s.pack = sim.swarmFliers().releaseUnderCover(sim, HarrierLook.DEFAULT, x, y, PACK, 0L);
        sim.swarmFliers().risingFrom(sim, s.pack, x, y);
        s.lastReleaseTick = tick;
        s.packs++;
        launches.add(new Launch(tick, s.entityId, 0L, s.pack.id, PACK));
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

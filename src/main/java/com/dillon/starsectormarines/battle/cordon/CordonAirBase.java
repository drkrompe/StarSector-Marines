package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.EnemyConcentration;
import com.dillon.starsectormarines.battle.airsupport.AirPoolKind;
import com.dillon.starsectormarines.battle.airsupport.AirPoolService;
import com.dillon.starsectormarines.battle.airsupport.AirStrikeDirector;
import com.dillon.starsectormarines.battle.airsupport.AirTasking;
import com.dillon.starsectormarines.battle.airsupport.AlliedAirWing;
import com.dillon.starsectormarines.battle.airsupport.CommsOperators;
import com.dillon.starsectormarines.battle.airsupport.FieldAirBase;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.nav.ElevationMap;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * The allies' air base behind the line ({@code cordon-nouns.md}, Air base): the military's air wing on hardstands of its
 * own in the rear, near the logistics base, and the base's operations room tasking the wing's own sorties.
 *
 * <p>The wing is the one {@link AlliedAirWing} gives the allies, and the base is where it lives
 * ({@link AlliedAirWing#baseAt}): its aircraft stand on the apron, take off from it for every sortie, the observers'
 * calls included, come home to it to be rearmed and refuelled, and a loss is replaced on its stand on the wing's own
 * timer. On top of the observers' calls the base flies its own sorties on its own cadence, from what the side can
 * fairly know: the swarm massing on a redoubt that is calling for relief (the call is radio; the mass must be in an
 * allied soldier's sight), a swarm mass some allied soldier sees, and the bores, which the garrison has watched for
 * weeks. It keeps one airframe of each kind back for the radio, never strikes within the director's danger-close ring
 * of a friendly soldier, and skips ground whose believed anti-air would cost more than the director's ceiling. Allied
 * command, never the player's.
 *
 * <p>Air cannot clear the map: four airframes, a turnaround after every sortie, one self-tasked sortie at most every
 * {@link #SORTIE_INTERVAL_SECONDS}, and the bores' unseen spore colonies cost what they cost. UNTUNED throughout.
 */
public final class CordonAirBase {

    /** Sim seconds before the base flies its first sortie of its own: the swarm has not come yet. UNTUNED. */
    static final float FIRST_SORTIE_SECONDS = 60f;
    /** Sim seconds between one self-tasked sortie and the next. UNTUNED. */
    static final float SORTIE_INTERVAL_SECONDS = 20f;
    /** Sim seconds before the base looks again when it had nothing to send or nothing worth sending at. */
    static final float RETRY_SECONDS = 5f;
    /** Airframes of each kind the base leaves on the apron for the observers' calls. UNTUNED. */
    static final int RADIO_RESERVE = 1;
    /** Cells about a calling redoubt's middle within which a seen mass is the swarm on that redoubt. */
    static final float RELIEF_REACH = 30f;
    /** Cells within which an allied soldier with a clear line sees a swarm body. */
    static final float SIGHT_CELLS = 30f;
    /** Cells within which a point counts as one the base struck lately. */
    static final float REPEAT_CELLS = 12f;
    /** Sim seconds before a struck point is a candidate again. UNTUNED. */
    static final float REPEAT_SECONDS = 60f;

    /** Cells along the long axis from one stand to the next. */
    static final int STAND_PITCH = 6;
    /** A stand's clear footprint either side of its middle: a garrison hardstand's. */
    static final int STAND_HALF = 2;
    /** Cells behind the logistics base, toward the rear edge, the apron is sought from. */
    static final int REARWARD = 24;
    /** How far from where it is sought the apron may stand. */
    static final int SITE_REACH = 70;
    /** Cells a stand keeps off the relief corridor. */
    static final int CORRIDOR_KEEP = 8;
    /** Cells a stand keeps off a road's line. */
    static final int ROAD_KEEP = 4;
    /** Cells a stand keeps off the company's landing pads and the base's apron. */
    static final int PAD_KEEP = 12;

    /** Why a point is worth an aircraft, most urgent first. */
    enum Reason { RELIEF, MASS, BORE }

    /**
     * A point the base could strike. {@code weight} breaks ties within a reason, larger first: the bodies about a mass,
     * a bore's nearness to the most pressed redoubt. {@code risk} is the believed share of a bomber lost on one pass.
     */
    record Candidate(Reason reason, float x, float y, AirTasking.Ground ground, int bodies, float weight, float risk) {}

    /** What the base sends, and where. */
    record Pick(AirPoolKind kind, float x, float y, Reason reason) {}

    private record Struck(float x, float y, int tick) {}

    private final FieldAirBase field;
    private final List<Struck> struck = new ArrayList<>();
    private int nextTick = ticks(FIRST_SORTIE_SECONDS);
    private int sorties;

    private final Ground ground;

    CordonAirBase(FieldAirBase field, Ground ground) {
        this.field = field;
        this.ground = ground;
    }

    /** Where the wing stands: the map's airbase lot, or the open-ground fallback. */
    public Ground ground() { return ground; }

    /** Where the wing stands: the airbase lot the map reserved, or the open-ground fallback where it reserved none. */
    public enum Ground { LOT, OPEN_GROUND }

    private static final Logger LOG = Global.getLogger(CordonAirBase.class);

    /**
     * Bases the allies' wing on the airbase lot the Cordon's map reserved in a rear corner ({@code cordon-nouns.md},
     * Air base; {@link CordonPlan#airBase}): its sheds, rolling off the lot's strip, and its apron's berths
     * ({@link AlliedAirWing#baseOnLot}). Where the map laid no lot, the open-ground fallback ({@link #lay}), and the log
     * says so. {@code null} when there is no wing, or neither finds room.
     */
    public static CordonAirBase base(BattleSimulation sim, MapResult map, int anchorX, int anchorY, int frontward,
                                     List<int[]> corridor, List<int[]> roadLines, List<int[]> keepOff) {
        Laid lot = map.layout == null ? null : map.layout.find(CordonPlan.AIR_BASE_PLACE);
        if (lot != null && lot.isLaid()) {
            FieldAirBase field = AlliedAirWing.baseOnLot(sim, map, lot);
            if (field != null) return new CordonAirBase(field, Ground.LOT);
        }
        if (sim.airPool() == null || !sim.airPool().hasPool(Faction.ALLY)) return null;
        LOG.warn("cordon: the map laid no airbase lot; the wing stands on open ground behind the logistics base");
        return lay(sim, anchorX, anchorY, frontward, corridor, roadLines, keepOff);
    }

    /**
     * The open-ground fallback: a row of stands, one for each airframe of the wing, on open ground behind
     * {@code (anchorX, anchorY)} (the logistics base), clear of the corridor, the roads, the company's pads and the
     * base's apron. {@code null} when the battle has no wing or the rear has no room, and the wing then flies in over
     * the map edge as before.
     */
    static CordonAirBase lay(BattleSimulation sim, int anchorX, int anchorY, int frontward, List<int[]> corridor,
                                    List<int[]> roadLines, List<int[]> keepOff) {
        NavigationGrid grid = sim.getGrid();
        int w = grid.getWidth(), h = grid.getHeight();
        boolean[] blocked = new boolean[w * h];
        mark(blocked, w, h, corridor, CORRIDOR_KEEP);
        mark(blocked, w, h, roadLines, ROAD_KEEP);
        mark(blocked, w, h, keepOff, PAD_KEEP);
        ElevationMap elevation = grid.elevation();
        int ax = anchorX, ay = Math.max(1, Math.min(h - 2, anchorY - frontward * REARWARD));
        List<int[]> sites = site((x, y) -> grid.inBounds(x, y) && !blocked[y * w + x] && grid.isWalkable(x, y)
                        && (elevation == null || elevation.tier(x, y) == elevation.tier(ax, ay)),
                ax, ay, AlliedAirWing.stands(), STAND_PITCH, STAND_HALF, SITE_REACH);
        if (sites == null) return null;
        FieldAirBase field = AlliedAirWing.baseAt(sim, sites, AirBody.facingToward(0f, frontward));
        return field == null ? null : new CordonAirBase(field, Ground.OPEN_GROUND);
    }

    /** The wing's stands. */
    public FieldAirBase field() { return field; }

    /** Sorties the base has tasked on its own so far. */
    public int sorties() { return sorties; }

    /** The middle of the apron, as {x, y}. */
    public int[] centre() {
        List<FieldAirBase.Stand> stands = field.stands();
        int sx = 0, sy = 0;
        for (FieldAirBase.Stand s : stands) {
            sx += s.x();
            sy += s.y();
        }
        return new int[]{sx / stands.size(), sy / stands.size()};
    }

    /** One tick: the apron's ground crew and replacements, and now and then a sortie of the base's own. */
    public void advance(BattleSimulation sim, List<CordonRedoubt> redoubts, Consumer<String> beats) {
        field.advance(sim, BattleSimulation.TICK_DT);
        int tick = sim.getSimTickIndex();
        if (tick < nextTick) return;
        nextTick = tick + ticks(RETRY_SECONDS);
        AirPoolService pool = sim.airPool();
        if (pool == null || pool.base(Faction.ALLY) == null) return;
        int bombers = Math.max(0, pool.available(Faction.ALLY, AirPoolKind.BOMBER) - RADIO_RESERVE);
        int fighters = Math.max(0, pool.available(Faction.ALLY, AirPoolKind.FIGHTER) - RADIO_RESERVE);
        if (bombers + fighters == 0) return;
        struck.removeIf(s -> tick - s.tick > ticks(REPEAT_SECONDS));
        Pick pick = choose(candidates(sim, redoubts, pool.director(Faction.ALLY)), bombers, fighters);
        if (pick == null) return;
        if (pool.dispatch(sim, Faction.ALLY, pick.kind(), pick.x(), pick.y()) != AirPoolService.Outcome.CALLING) return;
        struck.add(new Struck(pick.x(), pick.y(), tick));
        sorties++;
        nextTick = tick + ticks(SORTIE_INTERVAL_SECONDS);
        beats.accept("air_sortie_" + sorties);
    }

    /**
     * The sortie the base sends, or {@code null}: the most urgent reason first (a calling redoubt's swarm, then a seen
     * mass, then a bore), the heaviest within it. A mass gets the airframe its ground calls for ({@link AirTasking}),
     * never a fighter against works or armour; a bore only a bomber; nothing whose believed anti-air over the kind's
     * pass would cost more than the director's ceiling.
     *
     * @param bombers  bombers the base may send now
     * @param fighters fighters the base may send now
     */
    static Pick choose(List<Candidate> candidates, int bombers, int fighters) {
        List<Candidate> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator.comparingInt((Candidate c) -> c.reason().ordinal())
                .thenComparing(Comparator.comparingDouble(Candidate::weight).reversed()));
        for (Candidate c : ranked) {
            AirPoolKind kind = c.reason() == Reason.BORE
                    ? (bombers > 0 ? AirPoolKind.BOMBER : null)
                    : AirTasking.choose(new AirTasking.Target(c.ground(), c.bodies()), bombers, fighters);
            if (kind == null) continue;
            if (kind == AirPoolKind.FIGHTER && c.ground() != AirTasking.Ground.OPEN) continue;
            if (c.risk() * kind.aaExposure() > AirStrikeDirector.RISK_CEILING) continue;
            return new Pick(kind, c.x(), c.y(), c.reason());
        }
        return null;
    }

    /** What the side can fairly strike now, danger-close and lately struck ground left out. */
    private List<Candidate> candidates(BattleSimulation sim, List<CordonRedoubt> redoubts, AirStrikeDirector director) {
        List<Candidate> out = new ArrayList<>();
        World world = sim.world();
        // The swarm on a redoubt calling for relief: the call is radio, the bodies must be in an allied soldier's sight.
        for (CordonRedoubt r : redoubts) {
            if (r.state() != CordonRedoubt.State.CALLING) continue;
            long body = EnemyConcentration.densestOnGround(sim, Faction.ALLY,
                    u -> Math.hypot(world.x(u) - r.centreX(), world.y(u) - r.centreY()) <= RELIEF_REACH && seen(sim, u));
            if (body != 0L) add(sim, director, out, Reason.RELIEF, world.x(body), world.y(body), 0f);
        }
        long mass = EnemyConcentration.densestOnGround(sim, Faction.ALLY, u -> seen(sim, u));
        if (mass != 0L) add(sim, director, out, Reason.MASS, world.x(mass), world.y(mass), 0f);
        // The bores stand in plain sight across open country: known from the start.
        CordonRedoubt pressed = mostPressed(redoubts);
        for (SwarmBore bore : sim.swarmBores().bores()) {
            if (!world.isAlive(bore.entityId())) continue;
            float near = pressed == null ? 0f
                    : -(float) Math.hypot(bore.x() - pressed.centreX(), bore.y() - pressed.centreY());
            add(sim, director, out, Reason.BORE, bore.x() + 0.5f, bore.y() + 0.5f, near);
        }
        return out;
    }

    private void add(BattleSimulation sim, AirStrikeDirector director, List<Candidate> out, Reason reason,
                     float x, float y, float weight) {
        if (director.dangerClose(sim, x, y)) return;
        for (Struck s : struck) if (Math.hypot(s.x - x, s.y - y) < REPEAT_CELLS) return;
        AirTasking.Target target = AirTasking.classify(sim, Faction.ALLY, x, y);
        float w = reason == Reason.BORE ? weight : target.combatants();
        out.add(new Candidate(reason, x, y, target.ground(), target.combatants(), w, director.risk(sim, x, y)));
    }

    /** The manned redoubt worst off: one calling first, then the fewest standing. */
    static CordonRedoubt mostPressed(List<CordonRedoubt> redoubts) {
        CordonRedoubt best = null;
        for (CordonRedoubt r : redoubts) {
            if (!r.manned()) continue;
            if (best == null) {
                best = r;
                continue;
            }
            boolean calling = r.state() == CordonRedoubt.State.CALLING;
            boolean bestCalling = best.state() == CordonRedoubt.State.CALLING;
            if (calling && !bestCalling || calling == bestCalling && r.garrison() < best.garrison()) best = r;
        }
        return best;
    }

    /** Whether an allied soldier has a clear line to {@code unit} within {@link #SIGHT_CELLS}. */
    private static boolean seen(BattleSimulation sim, long unit) {
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        long[] allies = roster.factionDenseArray(Faction.ALLY);
        int ux = world.cellX(unit), uy = world.cellY(unit);
        for (int i = 0, n = roster.factionLiveCount(Faction.ALLY); i < n; i++) {
            long ally = allies[i];
            if (!sim.identity().type(ally).combatant) continue;
            int ax = world.cellX(ally), ay = world.cellY(ally);
            if (Math.abs(ax - ux) > SIGHT_CELLS || Math.abs(ay - uy) > SIGHT_CELLS) continue;
            if (CommsOperators.lineClear(sim.getGrid(), ax, ay, ux, uy, SIGHT_CELLS)) return true;
        }
        return false;
    }

    /** A test on a cell. */
    interface CellTest {
        boolean test(int x, int y);
    }

    /**
     * A row of {@code count} stand middles across the long axis, {@code pitch} apart, each with its whole
     * {@code half}-cell footprint on {@code free} ground, centred as near {@code (x, y)} as reaches {@code reach}; or
     * {@code null}.
     */
    static List<int[]> site(CellTest free, int x, int y, int count, int pitch, int half, int reach) {
        for (int r = 0; r <= reach; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    List<int[]> row = row(free, x + dx, y + dy, count, pitch, half);
                    if (row != null) return row;
                }
            }
        }
        return null;
    }

    private static List<int[]> row(CellTest free, int cx, int cy, int count, int pitch, int half) {
        List<int[]> row = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int sx = cx + Math.round((i - (count - 1) / 2f) * pitch);
            for (int fy = cy - half; fy <= cy + half; fy++) {
                for (int fx = sx - half; fx <= sx + half; fx++) {
                    if (!free.test(fx, fy)) return null;
                }
            }
            row.add(new int[]{sx, cy});
        }
        return row;
    }

    private static void mark(boolean[] blocked, int w, int h, List<int[]> cells, int keep) {
        if (cells == null) return;
        for (int[] c : cells) {
            for (int y = Math.max(0, c[1] - keep); y <= Math.min(h - 1, c[1] + keep); y++) {
                for (int x = Math.max(0, c[0] - keep); x <= Math.min(w - 1, c[0] + keep); x++) blocked[y * w + x] = true;
            }
        }
    }

    private static int ticks(float seconds) {
        return Math.round(seconds / BattleSimulation.TICK_DT);
    }
}

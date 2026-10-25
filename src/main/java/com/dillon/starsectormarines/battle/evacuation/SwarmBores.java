package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.combat.fx.Decal;
import com.dillon.starsectormarines.battle.combat.fx.DecalKind;
import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The battle's bores (`colony-breach-nouns.md`, Bore): opening them on clear
 * ground, growing them, noticing when one has been collapsed, and the
 * {@link #feed} seam biomass arrives through.
 *
 * <p><b>A bore is fought as a structure.</b> Its body is an ordinary
 * {@link UnitType#TURRET} entity of the {@link UnitRole#STRUCTURE} role, as a
 * fort aspect core is: it is targeted, shot, satchelled and destroyed through
 * the ordinary paths and dies through the ordinary death and demolition path,
 * which leaves a smoking wreck. Nothing here kills a bore.
 *
 * <p><b>Spore colonies are the umbrella.</b> A bore sprouts discrete growths on
 * the {@link BoreGrowthSites} around its mouth, one at eruption and more as it
 * reaches each tier, each at that tier's grade ({@link BoreTier#sporeColonyStructure}).
 * A colony is its own destroyable anti-air structure, flown by the ground
 * anti-air system as any flak position is, so killing colonies one by one opens
 * the sky without collapsing the bore. A dead colony is not regrown. When the
 * bore collapses its living colonies wither: they are killed here, which is the
 * one death this class deals, and it is a colony's, never a bore's.
 *
 * <p><b>A bore does not seal its mouth.</b> It is a hole the swarm climbs out
 * of, on one cell of open ground, and anything can cross its lip. Opening it
 * changes no route, so an eruption costs the navigation layers nothing, and a
 * sapper can stand beside it to set a charge.
 *
 * <p><b>Opening never lands on a body.</b> A site whose ground has someone
 * standing on or beside it waits: {@link #open} queues it and {@link #tick}
 * tries again every tick until the ground is clear.
 *
 * <p>The director ({@link SwarmReinforcementSystem}) reads the living bores to
 * spawn its surges; the breach sequence reads {@link #events} for its beats;
 * the flow fields are told where the living bores are
 * ({@link SwarmGoal#BORE}), each time one opens or collapses.
 */
public final class SwarmBores {

    /** The first-wave bore's structure. */
    public static final String BORE_STRUCTURE = "structure.swarm-bore";
    /** The mother bore's structure: the same hole, much harder to collapse. */
    public static final String MOTHER_STRUCTURE = "structure.swarm-mother-bore";
    /** How far from its site a bore may open, in cells. */
    public static final int SITE_SEARCH_RADIUS = 4;
    /** No body may stand nearer than this to the cell a bore opens on. */
    public static final float BODY_CLEARANCE = 1.5f;
    /** Two bores never open nearer each other than this. */
    public static final int BORE_SPACING = 4;
    /** The mouth a surge pours out of: open cells within this many of the bore. */
    public static final int MOUTH_RADIUS = 2;
    /** Damage that withers a colony outright when its bore collapses. */
    private static final float WITHER_DAMAGE = 1_000_000f;
    /** A first-wave bore's growth clock runs at a seeded rate within this share of the base. */
    public static final float RATE_SPREAD = 0.15f;
    /**
     * Seconds a mouth colony's root takes to dig out and thicken before the colony rises at its end
     * (`colony-breach-nouns.md`, Nest, Roots grow). Not foreseen: a colony sprouts the moment a feed crosses a tier,
     * so it stands this much later than the crossing.
     */
    public static final float COLONY_LEAD_SECONDS = 5f;

    /** What happened to a bore, for the beats. */
    public enum EventKind { GREW, COLLAPSED, SURGE, FED, MATRIARCH, MATRIARCH_MATURED }

    /** One fact about a bore on a tick. {@code tier} is the tier reached, or the bore's tier at the time. */
    public record Event(int tick, EventKind kind, SwarmBore bore, BoreTier tier) { }

    private record Pending(int number, boolean mother, int x, int y) { }

    private final long seed;
    private final List<SwarmBore> bores = new ArrayList<>();
    private final List<SwarmBore> view = Collections.unmodifiableList(bores);
    private final List<Pending> pending = new ArrayList<>();
    private final List<Event> events = new ArrayList<>();
    private final List<Event> eventView = Collections.unmodifiableList(events);
    private final BoreCues cues = new BoreCues();

    public SwarmBores(long seed) {
        this.seed = seed;
    }

    /** Every bore that has opened, in the order they opened, living and collapsed. */
    public List<SwarmBore> bores() { return view; }

    /** Facts in the order they happened; readers keep their own cursor. */
    public List<Event> events() { return eventView; }

    public BoreCues cues() { return cues; }

    /** Whether a site is still waiting for clear ground. */
    public boolean hasPending() { return !pending.isEmpty(); }

    /** How many bores are open and not collapsed. */
    public int livingCount() {
        int n = 0;
        for (SwarmBore bore : bores) if (!bore.collapsed()) n++;
        return n;
    }

    /** The bore whose body is {@code entityId}, or {@code null}. */
    public SwarmBore byEntity(long entityId) {
        for (SwarmBore bore : bores) if (bore.entityId() == entityId) return bore;
        return null;
    }

    /**
     * A tide ({@code cordon-nouns.md}): every living bore's next surge is
     * brought forward to {@code tick}, so they all go at once. Returns how
     * many bores were called.
     */
    public int tide(int tick) {
        int called = 0;
        for (SwarmBore bore : bores) {
            if (bore.collapsed()) continue;
            bore.callSurge(tick);
            called++;
        }
        return called;
    }

    /** The living bore nearest a point, or {@code null} when none lives. */
    public SwarmBore nearestLiving(float x, float y) {
        SwarmBore best = null;
        float bestDistance = Float.MAX_VALUE;
        for (SwarmBore bore : bores) {
            if (bore.collapsed()) continue;
            float dx = bore.x() + 0.5f - x, dy = bore.y() + 0.5f - y;
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                best = bore;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Opens a bore near {@code (siteX, siteY)}: the first clear cell outward
     * from the site. Returns it, or {@code null} when the ground is not clear
     * yet, in which case the site waits and {@link #tick} opens it later.
     *
     * @param number the bore's number in eruption order, {@code 0} for the mother
     */
    public SwarmBore open(BattleSimulation sim, int number, boolean mother, int siteX, int siteY) {
        SwarmBore bore = tryOpen(sim, number, mother, siteX, siteY);
        if (bore == null) pending.add(new Pending(number, mother, siteX, siteY));
        return bore;
    }

    /**
     * The biomass seam (`colony-breach-nouns.md`, Harvest): {@code amount} delivered into
     * {@code bore}, in colonists' bodies. It advances the bore's growth fast
     * and sizes and brings forward its next surge. Returns {@code false},
     * having changed nothing, for a collapsed bore or a non-positive amount:
     * collapse is permanent and a dead bore's delivery is lost.
     */
    public boolean feed(BattleSimulation sim, SwarmBore bore, float amount) {
        if (bore == null || bore.collapsed() || !(amount > 0f) || !bores.contains(bore)) return false;
        int tick = sim.getSimTickIndex();
        BoreTier from = bore.tier();
        BoreTier reached = bore.feed(amount, tick);
        events.add(new Event(tick, EventKind.FED, bore, bore.tier()));
        if (reached != null) grew(sim, bore, from, reached, tick);
        if (mature(sim, bore)) events.add(new Event(tick, EventKind.MATRIARCH_MATURED, bore, bore.tier()));
        return true;
    }

    /**
     * The Matriarch matures in the field (`colony-breach-nouns.md`, The
     * Matriarch's stage): fed from her mother, she steps up a stage when the
     * mother's lifetime biomass crosses the next threshold while she lives.
     * Her health and plate keep their fractions; her size, plate, blow, pace
     * and voice step with her, and she moults with a roar. True when she did.
     */
    static boolean mature(BattleSimulation sim, SwarmBore bore) {
        long body = bore.matriarch();
        if (body == 0L || !SwarmReinforcementSystem.growthStagesByDefault()
                || sim.resolveUnit(body) == 0L || !sim.world().isAlive(body)) return false;
        SwarmStage now = sim.identity().swarmStage(body);
        SwarmStage fed = SwarmStageStats.matriarchStage((int) bore.biomassFed());
        if (fed.ordinal() <= now.ordinal()) return false;
        SwarmStage next = now.older();
        SwarmStageStats.Stats kit = SwarmStageStats.of(UnitType.SWARM_MATRIARCH, next);
        float health = sim.world().hp(body) / Math.max(1e-3f, sim.world().maxHp(body));
        sim.world().setMaxHp(body, kit.hp());
        sim.world().setHp(body, health * kit.hp());
        if (sim.world().hasArmor(body)) {
            float plate = sim.world().armor(body) / Math.max(1e-3f, sim.world().maxArmor(body));
            sim.world().setMaxArmor(body, kit.armor());
            sim.world().setArmor(body, plate * kit.armor());
        }
        sim.world().setAttackDamage(body, kit.damage());
        sim.movement().setBaseMoveSpeed(body, kit.speed());
        sim.identity().setSwarmStage(body, next);
        sim.swarmCues().post(SwarmCues.Kind.MOULT, body, sim);
        return true;
    }

    /**
     * Once per simulation tick, before the director: open the sites that were
     * waiting, run every living bore's growth clock, and notice collapses.
     */
    public void tick(BattleSimulation sim) {
        cues.beginFrame();
        int tick = sim.getSimTickIndex();
        rise(sim, tick);
        for (int i = 0; i < pending.size(); ) {
            Pending p = pending.get(i);
            if (tryOpen(sim, p.number, p.mother, p.x, p.y) != null) pending.remove(i);
            else i++;
        }
        boolean collapsed = false;
        for (SwarmBore bore : bores) {
            if (bore.collapsed()) continue;
            long id = bore.entityId();
            if (sim.resolveUnit(id) == 0L || !sim.world().isAlive(id)) {
                collapse(sim, bore, tick);
                collapsed = true;
                continue;
            }
            BoreTier from = bore.tier();
            BoreTier reached = bore.grow(BattleSimulation.TICK_DT, integrity(sim, id), tick);
            if (reached != null) grew(sim, bore, from, reached, tick);
        }
        if (collapsed) declareGoal(sim);
    }

    /** How many of a bore's spore colonies are alive. */
    public static int livingColonies(BattleSimulation sim, SwarmBore bore) {
        int n = 0;
        for (long colony : bore.colonies()) if (sim.resolveUnit(colony) != 0L) n++;
        return n;
    }

    /** Records that {@code bore}, a mother, put its Matriarch out. */
    void matriarchEmerged(SwarmBore bore, int tick, long body) {
        bore.releasedMatriarch(body);
        events.add(new Event(tick, EventKind.MATRIARCH, bore, bore.tier()));
    }

    /**
     * Holds {@code bore} quiet: it keeps growing and feeding but sends no surge
     * until {@link #surgeNow} wakes it. A mission's sleeping bore (the heart's
     * mothers before the push wakes them, `heart-nouns.md`).
     */
    public void hold(SwarmBore bore) {
        bore.hold();
    }

    /**
     * Wakes {@code bore} with a surge due now. The director still releases it
     * only when it fits under the living bores' capacity, as any surge.
     */
    public void surgeNow(SwarmBore bore, int tick) {
        if (!bore.collapsed()) bore.surgeAt(tick);
    }

    /** Records that the director released a surge from {@code bore}. */
    void surged(SwarmBore bore, int tick) {
        events.add(new Event(tick, EventKind.SURGE, bore, bore.tier()));
        cues.add(BoreCues.Kind.SURGE, bore, tick);
    }

    /** Open cells within {@link #MOUTH_RADIUS} of the bore, nearest first, in a fixed order. */
    public static List<int[]> mouthCells(NavigationGrid grid, SwarmBore bore) {
        List<int[]> cells = new ArrayList<>();
        for (int r = 0; r <= MOUTH_RADIUS; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int x = bore.x() + dx, y = bore.y() + dy;
                    if (grid.inBounds(x, y) && grid.isWalkable(x, y)) cells.add(new int[]{x, y});
                }
            }
        }
        return cells;
    }

    /** The spawn spec of a bore's body on the swarm's side, as a fort aspect core is specced. */
    public static EntitySpec bodySpec(String name, String structureId, int cellX, int cellY) {
        StructureDef structure = TurretCatalogRegistry.requireStructure(structureId);
        return new EntitySpec(name, Faction.DEFENDER, UnitType.TURRET, cellX, cellY)
                .health(structure.maxStructure)
                .armor(structure.armorCapacity, structure.armorRating)
                .attackDamage(0f)
                .attackRange(0f)
                .accuracy(0f)
                .moveSpeed(0f)
                .visionRange(0f)
                .role(UnitRole.STRUCTURE)
                .turretStructureId(structure.id);
    }

    private SwarmBore tryOpen(BattleSimulation sim, int number, boolean mother, int siteX, int siteY) {
        int[] cell = clearGround(sim, siteX, siteY);
        if (cell == null) return null;
        int tick = sim.getSimTickIndex();
        String name = mother ? "Mother Bore" : "Bore " + number;
        long id = sim.spawn(bodySpec(name, mother ? MOTHER_STRUCTURE : BORE_STRUCTURE, cell[0], cell[1]));
        float rate = mother ? 1f : 1f + RATE_SPREAD * (2f * unit(mix(seed ^ number * 0x9E3779B97F4A7C15L)) - 1f);
        SwarmBore bore = new SwarmBore(number, mother, id, cell[0], cell[1], tick, rate);
        bores.add(bore);
        sprout(sim, bore, bore.tier(), bore.tier().sporeColonies);
        float cx = cell[0] + 0.5f, cy = cell[1] + 0.5f;
        sim.addDecal(new Decal(cx, cy, DecalKind.CRATER_MEDIUM_A.index, rotation(number, 1), mother ? 2.6f : 2.1f));
        sim.addDecal(new Decal(cx, cy, DecalKind.RUBBLE.index, rotation(number, 2), mother ? 2.2f : 1.7f));
        cues.add(BoreCues.Kind.ERUPTED, bore, tick);
        declareGoal(sim);
        return bore;
    }

    /**
     * The bore grew from {@code from} to {@code reached}. A large feed can cross
     * more than one tier at once; each tier crossed is its own event and sprouts
     * its own colonies at its own grade, so a fed maw still has a bore-grade
     * colony beside its maw-grade one.
     */
    private void grew(BattleSimulation sim, SwarmBore bore, BoreTier from, BoreTier reached, int tick) {
        for (BoreTier tier = from.next(); tier != null && tier.ordinal() <= reached.ordinal(); tier = tier.next()) {
            events.add(new Event(tick, EventKind.GREW, bore, tier));
            sprout(sim, bore, tier, tier.sporeColonies - bore.colonies().size() - bore.growing().size());
        }
        cues.add(BoreCues.Kind.GREW, bore, tick);
    }

    /**
     * Sprouts up to {@code count} colonies of {@code grade} on the bore's free growth sites. At setup a colony stands
     * at once, its root grown; in play its root digs out first and the colony rises when it has come ({@link #rise}).
     */
    private void sprout(BattleSimulation sim, SwarmBore bore, BoreTier grade, int count) {
        List<int[]> sites = BoreGrowthSites.cells(sim.getGrid(), bore, seed);
        int tick = sim.getSimTickIndex();
        for (int[] site : sites) {
            if (count <= 0) break;
            if (taken(sim, bore, site) || nearBody(sim, site[0], site[1], 0.9f)) continue;
            if (tick == 0) {
                standColony(sim, bore, grade, site[0], site[1]);
            } else {
                bore.beginGrowing(new SwarmBore.Growing(grade, site[0], site[1], tick,
                        Math.round(COLONY_LEAD_SECONDS / BattleSimulation.TICK_DT)));
            }
            count--;
        }
    }

    /**
     * Raises every colony whose root has come, where the root ends, in a burst of creep thrown up off the ground; one
     * whose site a body is standing on waits for it to move.
     */
    private void rise(BattleSimulation sim, int tick) {
        for (SwarmBore bore : bores) {
            if (bore.collapsed() || bore.growing().isEmpty()) continue;
            for (SwarmBore.Growing g : new ArrayList<>(bore.growing())) {
                if (!g.arrived(tick) || nearBody(sim, g.x(), g.y(), 0.9f)) continue;
                standColony(sim, bore, g.grade(), g.x(), g.y());
                bore.doneGrowing(g);
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI * 2 * i / 6;
                    sim.emitGroundDust(g.x() + 0.5f, g.y() + 0.5f, (float) Math.cos(a) * 0.9f, (float) Math.sin(a) * 0.9f,
                            0.45f, EffectsService.DUST_WHEEL);
                }
            }
        }
    }

    private static void standColony(BattleSimulation sim, SwarmBore bore, BoreTier grade, int x, int y) {
        String name = (bore.mother() ? "Mother Bore" : "Bore " + bore.number())
                + " Spore Colony " + (bore.colonies().size() + 1);
        bore.sprouted(sim.spawn(bodySpec(name, grade.sporeColonyStructure(), x, y)));
    }

    private static boolean taken(BattleSimulation sim, SwarmBore bore, int[] site) {
        for (SwarmBore.Growing g : bore.growing()) if (g.x() == site[0] && g.y() == site[1]) return true;
        for (long colony : bore.colonies()) {
            if (sim.resolveUnit(colony) == 0L) continue;
            if (sim.world().cellX(colony) == site[0] && sim.world().cellY(colony) == site[1]) return true;
        }
        return false;
    }

    private void collapse(BattleSimulation sim, SwarmBore bore, int tick) {
        bore.collapse(tick);
        for (long colony : bore.colonies()) {
            if (sim.resolveUnit(colony) != 0L) sim.applyDamage(colony, WITHER_DAMAGE, WITHER_DAMAGE);
        }
        for (long outpost : bore.outposts()) {
            if (sim.resolveUnit(outpost) != 0L) sim.applyDamage(outpost, WITHER_DAMAGE, WITHER_DAMAGE);
        }
        float cx = bore.x() + 0.5f, cy = bore.y() + 0.5f;
        sim.addDecal(new Decal(cx, cy, DecalKind.CRATER_MEDIUM_B.index, rotation(bore.number(), 3),
                bore.mother() ? 2.8f : 2.3f));
        events.add(new Event(tick, EventKind.COLLAPSED, bore, bore.tier()));
        cues.add(BoreCues.Kind.COLLAPSED, bore, tick);
    }

    /** Tells the flow fields where the living bores are, or that there are none. */
    private void declareGoal(BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        int[] cells = new int[bores.size()];
        int n = 0;
        for (SwarmBore bore : bores) {
            if (!bore.collapsed()) cells[n++] = grid.index(bore.x(), bore.y());
        }
        int[] living = new int[n];
        System.arraycopy(cells, 0, living, 0, n);
        SwarmMovement.declareGoalCells(sim, SwarmGoal.BORE, n == 0 ? null : living);
    }

    /** The share of a body's hit points and armour left, 1 untouched. */
    private static float integrity(BattleSimulation sim, long id) {
        StructureDef structure = sim.turretState().structure(id);
        float full = structure.maxStructure + structure.armorCapacity;
        return full <= 0f ? 1f : (sim.world().hp(id) + sim.world().armor(id)) / full;
    }

    /** The first clear cell outward from a site: open, unpeopled, apart from other bores and the protected zones. */
    private int[] clearGround(BattleSimulation sim, int siteX, int siteY) {
        NavigationGrid grid = sim.getGrid();
        for (int r = 0; r <= SITE_SEARCH_RADIUS; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int x = siteX + dx, y = siteY + dy;
                    if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) continue;
                    if (sim.isInsideRescueOpeningProtectedZone(x, y)) continue;
                    if (nearBore(x, y) || nearBody(sim, x, y, BODY_CLEARANCE)) continue;
                    return new int[]{x, y};
                }
            }
        }
        return null;
    }

    private boolean nearBore(int x, int y) {
        for (SwarmBore bore : bores) {
            if (Math.max(Math.abs(bore.x() - x), Math.abs(bore.y() - y)) < BORE_SPACING) return true;
        }
        return false;
    }

    static boolean nearBody(BattleSimulation sim, int x, int y, float clearance) {
        float cx = x + 0.5f, cy = y + 0.5f;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!sim.world().hasPosition(unit) || sim.isRiding(unit)) continue;
            float dx = sim.world().x(unit) - cx, dy = sim.world().y(unit) - cy;
            if (dx * dx + dy * dy < clearance * clearance) return true;
        }
        return false;
    }

    private float rotation(int number, int salt) {
        return unit(mix(seed ^ (number * 31L + salt) * 0xBF58476D1CE4E5B9L)) * 360f;
    }

    static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    /** A hash folded to {@code [0, 1)}. */
    static float unit(long hash) {
        return (hash >>> 40) / (float) (1L << 24);
    }
}

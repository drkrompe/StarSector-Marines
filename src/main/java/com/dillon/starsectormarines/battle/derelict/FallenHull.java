package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.drone.Flock;
import com.dillon.starsectormarines.battle.drone.FlockLivery;
import com.dillon.starsectormarines.battle.drone.FlockWeights;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.DeathEvent;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A fallen hull's claim in battle (`fallen-hull-nouns.md`, In a battle): the root zone at the hull with the autoforge
 * as its vault and node, the claim lattice, the stakes on it, the forge clock, the stake trains and printed walkers it
 * sends out, the strippers and haulers feeding it, and the stranding of chains. {@link #install} stands it on a
 * {@link FallenHullSite} the ground laid; the network's {@link DerelictSecurityDirector} runs it each pulse
 * ({@link #pulse}) and leaves the lances it {@linkplain #commands commands} to it. A raised stake is an ordinary
 * {@link SecurityZone} with its node, its flock's cradle and its repair drone; from established on, its service bay,
 * folded posts and store.
 */
public final class FallenHull {

    /** The hull's zone: the network's root. */
    public static final int HULL_ZONE = 0;
    /** A stake's zone is this plus its lattice point's id. */
    public static final int STAKE_ZONE_BASE = 100;
    /** A planted stake's folded pylon: armoured, unarmed. UNTUNED. */
    public static final float PYLON_STRUCTURE = 300f;
    public static final float PYLON_ARMOR = 420f;
    /** How near the cell it is to hold, in cells, a lance the hull walks counts as there. */
    static final int HOLD_RADIUS = 2;
    /** A standing order whose cell moved less than this is not re-issued. */
    static final int REISSUE_CELLS = 2;

    private final FallenHullSite site;
    private final ClaimLattice lattice;
    private final Autoforge forge;
    private final Feedstock hullStore;
    private final HaulRoads roads;
    private final int stakePerimeter;
    private long forgeId;
    private float forgeWhole;
    private FlockSystemPort port;
    private FlockLivery livery = FlockLivery.DOMAIN;

    private final Map<Integer, SurveyStake> stakes = new TreeMap<>();
    private final Map<Integer, Long> pylons = new TreeMap<>();
    /** Each established stake's store place: its service bay, else its point. */
    private final Map<Integer, int[]> storeAt = new TreeMap<>();
    /** Anchors in the order they established: the newest is the frontier printed walkers are posted to. */
    private final List<Integer> established = new ArrayList<>();
    private final List<StakeTrain> trains = new ArrayList<>();
    private final Map<Integer, StripperWork> strippers = new TreeMap<>();
    private final Map<Integer, HaulerWork> haulers = new TreeMap<>();
    private final Map<Integer, HaulRoads.Leg> legs = new TreeMap<>();
    private final Map<Integer, int[]> legEnds = new TreeMap<>();
    /** Escorts held at a planted stake until it rises, by lance: the stake's point. */
    private final Map<Integer, Integer> guarding = new TreeMap<>();
    private final Set<Integer> commanded = new TreeSet<>();
    private final Map<Integer, int[]> ordered = new TreeMap<>();
    private final List<Salvage> salvage = new ArrayList<>();
    private final Set<Long> salvagedWrecks = new HashSet<>();
    private final List<DeathEvent> pendingDeaths = new ArrayList<>();
    private long nextSalvage = 1L;
    private int scrappedTrains;
    private int scrappedStakes;
    private final Set<Long> taken = new HashSet<>();

    FallenHull(FallenHullSite site, BuildQueue queue) {
        this.site = site;
        this.lattice = new ClaimLattice(site.sections(), site.lattice(), site.linkCells());
        this.forge = new Autoforge(queue);
        this.hullStore = new Feedstock(site.feedstock());
        this.roads = new HaulRoads(site.haulRoads());
        this.stakePerimeter = Math.max(4, site.linkCells() / 2 + 1);
        for (FallenHullSite.Pile pile : site.salvage()) {
            salvage.add(new Salvage(nextSalvage++, Salvage.Kind.STRUCTURE, pile.x() + 0.5f, pile.y() + 0.5f,
                    pile.metal(), null));
        }
    }

    // ---- install ----

    /** As {@link #install(BattleSimulation, FallenHullSite, BuildQueue, FlockLivery)}, on the survey program in Domain livery. */
    public static FallenHull install(BattleSimulation sim, FallenHullSite site) {
        return install(sim, site, BuildQueue.survey(), FlockLivery.DOMAIN);
    }

    /**
     * Stands a fallen hull's watch on a battle: the hull's zone over its footprint with the autoforge as its node and
     * vault, its flock at the mouth, its service bay and repair drone; the stakes the ground already stands, each at
     * its stage; the walkers already out; and the network's director, installed on the battle, with this claim on it.
     */
    public static FallenHull install(BattleSimulation sim, FallenHullSite site, BuildQueue queue, FlockLivery livery) {
        FallenHull hull = new FallenHull(site, queue);
        hull.livery = livery == null ? FlockLivery.DOMAIN : livery;
        SecurityNetwork network = new SecurityNetwork();
        // The root zone keeps the ground off every section; its node is the forge.
        network.add(new SecurityZone(HULL_ZONE, site.forgeX(), site.forgeY(), site.perimeterCells(), site.sections()));
        List<int[]> egress = site.egress().isEmpty() ? List.of(new int[] {site.mouthX(), site.mouthY()}) : site.egress();
        DerelictSecurityDirector director = new DerelictSecurityDirector(network, List.of(), egress, site.forgeX(),
                site.forgeY());
        director.attachHull(hull);
        director.wearLook(DerelictLook.of(hull.livery));

        hull.forgeId = sim.spawn(DerelictNode.create("autoforge", Faction.DEFENDER, site.forgeX(), site.forgeY())
                .health(Autoforge.MAX_STRUCTURE).armor(Autoforge.ARMOR_CAPACITY, DerelictNode.ARMOR_RATING));
        hull.forgeWhole = Autoforge.MAX_STRUCTURE + Autoforge.ARMOR_CAPACITY;
        hull.taken.add(DerelictSecurity.cellKey(site.forgeX(), site.forgeY()));
        if (hull.forgeId != 0L) director.standNode(HULL_ZONE, hull.forgeId);

        hull.port = new FlockSystemPort();
        hull.port.through(sim.flocks());
        director.connectFlocks(hull.port);
        // The hull's flock homes at the mouth and is leashed to the far end of the ground the root zone keeps.
        int span = 0;
        for (FallenHullSite.Rect r : site.sections()) {
            span = Math.max(span, Math.max(Math.max(Math.abs(r.left() - site.mouthX()), Math.abs(r.right() - site.mouthX())),
                    Math.max(Math.abs(r.bottom() - site.mouthY()), Math.abs(r.top() - site.mouthY()))));
        }
        hull.launchFlock(sim, HULL_ZONE, site.mouthX(), site.mouthY(), span + site.perimeterCells());
        hull.standBay(sim, director, HULL_ZONE, site.mouthX(), site.mouthY());
        DerelictSecurity.standRepairDrone(sim, HULL_ZONE, director, hull.livery);

        hull.standStartingStakes(sim, director);
        for (FallenHullSite.Posting posting : site.walkers()) {
            SecurityZone zone = network.zoneAt(posting.x(), posting.y());
            hull.print(sim, director, posting.frame(), zone == null ? HULL_ZONE : zone.id, posting.x(), posting.y(),
                    posting.x(), posting.y());
        }
        sim.subscribeDeath(hull.pendingDeaths::add);
        director.armWalkers(sim);
        sim.installDerelictSecurity(director);
        return hull;
    }

    /** The ground's stakes, nearest the hull first, each chained through the anchors before it. */
    private void standStartingStakes(BattleSimulation sim, DerelictSecurityDirector director) {
        List<FallenHullSite.StartingStake> order = new ArrayList<>(site.stakes());
        order.sort(Comparator.comparingLong((FallenHullSite.StartingStake s) -> lattice.hullDistance(
                lattice.point(s.pointId()))).thenComparingInt(FallenHullSite.StartingStake::pointId));
        for (FallenHullSite.StartingStake start : order) {
            ClaimLattice.Point p = lattice.point(start.pointId());
            Set<Integer> anchors = new TreeSet<>();
            for (SurveyStake s : stakes.values()) if (s.anchors()) anchors.add(s.pointId);
            ClaimLattice.Plan plan = lattice.nextPlantable(Set.of(p.id()), anchors);
            // A stake the ground stands out of reach of the chain is stranded from the start.
            int parent = plan == null ? Integer.MIN_VALUE : plan.parent();
            SurveyStake stake = new SurveyStake(p.id(), parent, start.stage());
            stakes.put(p.id(), stake);
            switch (start.stage()) {
                case PLANTED -> standPylon(sim, p);
                case RAISED -> raise(sim, director, stake);
                case ESTABLISHED -> {
                    raise(sim, director, stake);
                    establish(sim, director, stake);
                }
                case SCRAP -> { }
            }
            if (plan == null) stake.strand();
        }
    }

    // ---- what the director asks ----

    public Autoforge forge() {
        return forge;
    }

    public ClaimLattice lattice() {
        return lattice;
    }

    public Feedstock hullStore() {
        return hullStore;
    }

    /** The autoforge's entity: the hull zone's node and the root zone's vault. */
    public long forgeId() {
        return forgeId;
    }

    /** Every stake on the claim, by its lattice point; a scrapped stake is gone. */
    public Map<Integer, SurveyStake> stakes() {
        return Collections.unmodifiableMap(stakes);
    }

    /** The folded pylon of each planted stake, by its point. */
    public Map<Integer, Long> pylons() {
        return Collections.unmodifiableMap(pylons);
    }

    public List<StakeTrain> trains() {
        return Collections.unmodifiableList(trains);
    }

    public Map<Integer, StripperWork> strippers() {
        return Collections.unmodifiableMap(strippers);
    }

    public Map<Integer, HaulerWork> haulers() {
        return Collections.unmodifiableMap(haulers);
    }

    public List<Salvage> salvage() {
        return Collections.unmodifiableList(salvage);
    }

    /** Trains whose hauler died before planting. */
    public int scrappedTrains() {
        return scrappedTrains;
    }

    /** Stakes killed planted. */
    public int scrappedStakes() {
        return scrappedStakes;
    }

    /** Metal lost with haulers destroyed loaded, over the battle. */
    public float metalLost() {
        float lost = 0f;
        for (HaulerWork h : haulers.values()) lost += h.lost();
        return lost;
    }

    /** The zone a stake rises into. */
    public static int zoneOf(int pointId) {
        return STAKE_ZONE_BASE + pointId;
    }

    /** Whether the hull walks this lance itself: a worker, a train's hauler, or an escort on the way or on guard. */
    public boolean commands(int squadId) {
        return commanded.contains(squadId);
    }

    /** Whether the autoforge is breached: its plate and structure below {@link Autoforge#BREACH_SHARE}, or gone. */
    public boolean breached(BattleSimulation sim) {
        if (forgeId == 0L) return false;
        if (sim.resolveUnit(forgeId) == 0L) return true;
        float now = sim.world().hp(forgeId) + sim.world().armor(forgeId);
        return Autoforge.breached(now / forgeWhole);
    }

    // ---- the pulse ----

    /** One pulse of the claim, after the network has heard what it heard and before its walkers take their postures. */
    public void pulse(BattleSimulation sim, DerelictSecurityDirector director, int tick) {
        float dt = DerelictSecurityDirector.PULSE_TICKS * BattleSimulation.TICK_DT;
        if (forge.lit() && (forgeId == 0L || sim.resolveUnit(forgeId) == 0L)) forge.putOut();
        watchPylons(sim);
        strand(director.network());
        for (SurveyStake stake : new ArrayList<>(stakes.values())) {
            SurveyStake.Stage rose = stake.tick(dt);
            if (rose == SurveyStake.Stage.RAISED) raise(sim, director, stake);
            else if (rose == SurveyStake.Stage.ESTABLISHED) establish(sim, director, stake);
        }
        runTrains(sim, director);
        gatherSalvage(sim, director);
        List<Salvage> claim = claim(director.network());
        runStrippers(sim, claim, dt);
        runHaulers(sim, dt);
        print(sim, director, dt);
    }

    /** A planted stake whose pylon is gone is scrap, and its point open again. */
    private void watchPylons(BattleSimulation sim) {
        for (Map.Entry<Integer, Long> pylon : new ArrayList<>(pylons.entrySet())) {
            SurveyStake stake = stakes.get(pylon.getKey());
            if (stake == null || stake.stage() != SurveyStake.Stage.PLANTED) {
                pylons.remove(pylon.getKey());
                continue;
            }
            if (sim.resolveUnit(pylon.getValue()) != 0L) continue;
            stake.kill();
            stakes.remove(pylon.getKey());
            pylons.remove(pylon.getKey());
            scrappedStakes++;
            for (Map.Entry<Integer, Integer> guard : new ArrayList<>(guarding.entrySet())) {
                if (guard.getValue().equals(pylon.getKey())) release(guard.getKey());
            }
        }
    }

    /** Strands every stake whose chain is cut: by its own node, a node on the way, or the forge going out. */
    private void strand(SecurityNetwork network) {
        Map<Integer, Integer> parents = new TreeMap<>();
        for (SurveyStake stake : stakes.values()) parents.put(stake.pointId, stake.parent);
        Set<Integer> cut = RelayChain.cut(parents, point -> {
            SurveyStake stake = stakes.get(point);
            if (stake == null) return false;
            if (stake.stage() == SurveyStake.Stage.PLANTED) return true;
            SecurityZone zone = network.zone(zoneOf(point));
            return zone != null && zone.nodeAlive();
        }, forge.lit());
        for (int point : cut) stakes.get(point).strand();
    }

    /** The planted stake's pylon at its point. */
    private void standPylon(BattleSimulation sim, ClaimLattice.Point p) {
        int[] cell = freeCellNear(sim.getGrid(), p.x(), p.y(), 0);
        long id = sim.spawn(DerelictNode.create("survey-stake-" + p.id(), Faction.DEFENDER, cell[0], cell[1])
                .health(PYLON_STRUCTURE).armor(PYLON_ARMOR, DerelictNode.ARMOR_RATING));
        if (id == 0L) return;
        taken.add(DerelictSecurity.cellKey(cell[0], cell[1]));
        pylons.put(p.id(), id);
    }

    /**
     * The pylon opens into a node and a cradle: a new dormant zone on the stake's point, linked to the zone its chain
     * runs through, its flock docked and its repair drone up. The escort that brought it is posted to it.
     */
    private void raise(BattleSimulation sim, DerelictSecurityDirector director, SurveyStake stake) {
        ClaimLattice.Point p = lattice.point(stake.pointId);
        Long pylon = pylons.remove(p.id());
        int nodeX = p.x();
        int nodeY = p.y();
        if (pylon != null && sim.resolveUnit(pylon) != 0L) {
            nodeX = sim.world().cellX(pylon);
            nodeY = sim.world().cellY(pylon);
            sim.takeOffTheField(pylon);
            taken.remove(DerelictSecurity.cellKey(nodeX, nodeY));
        }
        SecurityNetwork network = director.network();
        int zoneId = zoneOf(p.id());
        if (network.zone(zoneId) == null) network.add(new SecurityZone(zoneId, p.x(), p.y(), stakePerimeter));
        int parentZone = stake.parent == ClaimLattice.HULL ? HULL_ZONE : zoneOf(stake.parent);
        if (stake.parent != Integer.MIN_VALUE && network.zone(parentZone) != null) network.link(parentZone, zoneId);
        int[] cell = pylon != null ? new int[] {nodeX, nodeY} : freeCellNear(sim.getGrid(), p.x(), p.y(), 0);
        long node = sim.spawn(DerelictNode.create("survey-node-" + p.id(), Faction.DEFENDER, cell[0], cell[1]));
        if (node != 0L) {
            taken.add(DerelictSecurity.cellKey(cell[0], cell[1]));
            director.standNode(zoneId, node);
            DerelictSecurity.standRepairDrone(sim, zoneId, director, livery);
        }
        launchFlock(sim, zoneId, p.x(), p.y(), stakePerimeter);
        int n = 0;
        for (Map.Entry<Integer, Integer> guard : new ArrayList<>(guarding.entrySet())) {
            if (guard.getValue() != p.id()) continue;
            int[] post = ring(p.x(), p.y(), 3, n++);
            director.repost(guard.getKey(), zoneId, post[0], post[1]);
            release(guard.getKey());
        }
    }

    /** Folded posts, a service bay and a store: the claim reaches on from here, and walkers are posted to it. */
    private void establish(BattleSimulation sim, DerelictSecurityDirector director, SurveyStake stake) {
        ClaimLattice.Point p = lattice.point(stake.pointId);
        int zoneId = zoneOf(p.id());
        int[] bay = standBay(sim, director, zoneId, p.x(), p.y());
        storeAt.put(p.id(), bay != null ? bay : new int[] {p.x(), p.y()});
        for (int i = 0; i < DerelictPost.PER_ZONE; i++) {
            int[] at = ring(p.x(), p.y(), 4, i * 2 + 1);
            int[] cell = freeCellNear(sim.getGrid(), at[0], at[1], 0);
            if (DerelictSecurity.standPost(sim, "survey-post-" + p.id() + "-" + i, zoneId, cell[0], cell[1],
                    director) != 0L) {
                taken.add(DerelictSecurity.cellKey(cell[0], cell[1]));
            }
        }
        established.add(p.id());
    }

    private int[] standBay(BattleSimulation sim, DerelictSecurityDirector director, int zoneId, int x, int y) {
        int[] cell = freeCellNear(sim.getGrid(), x, y, 2);
        long id = sim.spawn(DerelictNode.create("derelict-service-bay-" + zoneId, Faction.DEFENDER, cell[0], cell[1])
                .health(ServiceBay.MAX_STRUCTURE).armor(ServiceBay.ARMOR_CAPACITY, DerelictNode.ARMOR_RATING));
        if (id == 0L) return null;
        taken.add(DerelictSecurity.cellKey(cell[0], cell[1]));
        director.standServiceBay(zoneId, id);
        return cell;
    }

    private void launchFlock(BattleSimulation sim, int zoneId, int x, int y, int perimeter) {
        if (port == null || sim.flocks() == null) return;
        NavigationGrid grid = sim.getGrid();
        int[] home = DerelictSecurity.nearOpen(new int[] {x, y}, 1,
                (cx, cy) -> grid.inBounds(cx, cy) && grid.isWalkable(cx, cy),
                (cx, cy) -> sim.getTopology() != null && sim.getTopology().isRoofIntact(cx, cy), taken);
        if (home == null) return;
        Flock flock = sim.flocks().launch(sim, Faction.DEFENDER, home[0] + 0.5f, home[1] + 0.5f,
                DerelictSecurity.FLOCK_SIZE, FlockWeights.WASP, perimeter + FlockTasking.LEASH_CELLS, livery);
        if (flock != null) port.add(flock, zoneId);
    }

    // ---- trains ----

    private void runTrains(BattleSimulation sim, DerelictSecurityDirector director) {
        World world = sim.world();
        for (StakeTrain train : new ArrayList<>(trains)) {
            boolean stands = sim.squadMemberCount(train.haulerSquad) > 0;
            long lead = stands ? sim.squadMemberAt(train.haulerSquad, 0) : 0L;
            StakeTrain.State now = train.step(stands, stands ? world.x(lead) : 0f, stands ? world.y(lead) : 0f);
            if (now == StakeTrain.State.SCRAP) {
                trains.remove(train);
                scrappedTrains++;
                legs.remove(train.haulerSquad);
                // Should a repair drone raise its hauler, it comes back a working hauler, empty.
                haulers.put(train.haulerSquad, new HaulerWork());
                for (int escort : train.escort) {
                    int[] post = frontierPost(0);
                    director.repost(escort, post[2], post[0], post[1]);
                    release(escort);
                }
                continue;
            }
            if (now == StakeTrain.State.PLANTED) {
                trains.remove(train);
                ClaimLattice.Point p = train.plan.point();
                stakes.put(p.id(), new SurveyStake(p.id(), train.plan.parent()));
                standPylon(sim, p);
                for (int escort : train.escort) guarding.put(escort, p.id());
                // The hauler that carried it out goes to work hauling.
                legs.remove(train.haulerSquad);
                haulers.put(train.haulerSquad, new HaulerWork());
                continue;
            }
            ClaimLattice.Point p = train.plan.point();
            int[] to = walkLeg(sim, train.haulerSquad, lead, p.x(), p.y());
            order(sim, train.haulerSquad, to[0], to[1], "survey hauler carries a stake out");
            for (int escort : train.escort) {
                if (sim.squadMemberCount(escort) <= 0) continue;
                order(sim, escort, world.cellX(lead), world.cellY(lead), "escort walks round its stake train");
            }
        }
        for (Map.Entry<Integer, Integer> guard : guarding.entrySet()) {
            if (sim.squadMemberCount(guard.getKey()) <= 0) continue;
            ClaimLattice.Point p = lattice.point(guard.getValue());
            order(sim, guard.getKey(), p.x(), p.y(), "escort guards its planted stake");
        }
    }

    // ---- feedstock ----

    /** Wrecks of the watch lying on the field, and the company's and garrison's machines that fell, become salvage. */
    private void gatherSalvage(BattleSimulation sim, DerelictSecurityDirector director) {
        for (Wreck wreck : director.wrecks()) {
            if (!wreck.raisable() || !salvagedWrecks.add(wreck.id)) continue;
            salvage.add(new Salvage(nextSalvage++, Salvage.Kind.WRECK, wreck.x, wreck.y,
                    wreck.kind == Wreck.Kind.WALKER ? Salvage.WRECK_METAL : Salvage.LIGHT_WRECK_METAL, wreck));
        }
        for (DeathEvent death : pendingDeaths) {
            Faction side = sim.identity().faction(death.unitId());
            if (side == Faction.DEFENDER) continue;
            UnitType type = sim.identity().type(death.unitId());
            float metal = type.isMech() ? Salvage.WRECK_METAL
                    : type.isGroundVehicle() || type.isTurret() ? Salvage.LIGHT_WRECK_METAL : 0f;
            if (metal > 0f) salvage.add(new Salvage(nextSalvage++, Salvage.Kind.WRECK, death.x(), death.y(), metal, null));
        }
        pendingDeaths.clear();
        salvage.removeIf(s -> !s.cuttable());
    }

    /** The salvage on the claim: inside the hull's perimeter or a linked stake zone's. */
    private List<Salvage> claim(SecurityNetwork network) {
        List<SecurityZone> zones = new ArrayList<>();
        zones.add(network.zone(HULL_ZONE));
        for (SurveyStake stake : stakes.values()) {
            SecurityZone zone = network.zone(zoneOf(stake.pointId));
            if (zone != null && stake.builds()) zones.add(zone);
        }
        List<Salvage> out = new ArrayList<>();
        for (Salvage s : salvage) {
            for (SecurityZone zone : zones) {
                if (zone.inside((int) Math.floor(s.x), (int) Math.floor(s.y))) {
                    out.add(s);
                    break;
                }
            }
        }
        return out;
    }

    private void runStrippers(BattleSimulation sim, List<Salvage> claim, float dt) {
        World world = sim.world();
        for (Map.Entry<Integer, StripperWork> entry : strippers.entrySet()) {
            int squad = entry.getKey();
            if (sim.squadMemberCount(squad) <= 0) continue;
            long lead = sim.squadMemberAt(squad, 0);
            float x = world.x(lead);
            float y = world.y(lead);
            int[] post = {site.mouthX(), site.mouthY()};
            StripperWork.Goal goal = entry.getValue().decide(x, y, post[0] + 0.5f, post[1] + 0.5f, claim);
            if (goal.kind() == StripperWork.Kind.CUT) {
                for (Salvage s : claim) if (s.id == goal.salvageId()) entry.getValue().cut(dt, s);
            }
            String why = switch (goal.kind()) {
                case STAND_BY -> "stripper stands by: nothing on the claim to cut";
                case GO -> "stripper walks to metal on the claim";
                case CUT -> "stripper cuts metal into feedstock";
                case FULL -> "stripper waits full for a hauler";
            };
            order(sim, squad, (int) Math.floor(goal.x()), (int) Math.floor(goal.y()), why);
        }
    }

    private void runHaulers(BattleSimulation sim, float dt) {
        World world = sim.world();
        List<HaulerWork.Holder> holders = new ArrayList<>();
        for (Map.Entry<Integer, StripperWork> s : strippers.entrySet()) {
            if (sim.squadMemberCount(s.getKey()) <= 0 || s.getValue().load() <= 0f) continue;
            long lead = sim.squadMemberAt(s.getKey(), 0);
            holders.add(new HaulerWork.Holder(s.getKey(), world.x(lead), world.y(lead), s.getValue().load()));
        }
        List<HaulerWork.Store> stores = new ArrayList<>();
        if (forge.lit()) {
            stores.add(new HaulerWork.Store(ClaimLattice.HULL, site.mouthX() + 0.5f, site.mouthY() + 0.5f));
            for (int point : established) {
                SurveyStake stake = stakes.get(point);
                if (stake == null || !stake.anchors()) continue;
                int[] at = storeAt.get(point);
                stores.add(new HaulerWork.Store(point, at[0] + 0.5f, at[1] + 0.5f));
            }
        }
        for (Map.Entry<Integer, HaulerWork> entry : haulers.entrySet()) {
            int squad = entry.getKey();
            HaulerWork rules = entry.getValue();
            if (sim.squadMemberCount(squad) <= 0) {
                rules.lose();
                continue;
            }
            long lead = sim.squadMemberAt(squad, 0);
            float x = world.x(lead);
            float y = world.y(lead);
            int[] post = {site.mouthX(), site.mouthY()};
            HaulerWork.Goal goal = rules.decide(x, y, post[0] + 0.5f, post[1] + 0.5f, holders, stores);
            if (goal.kind() == HaulerWork.Kind.TAKE) {
                StripperWork from = strippers.get(goal.with());
                if (from != null) rules.takeOn(from.handOver(rules.room()));
            } else if (goal.kind() == HaulerWork.Kind.UNLOAD) {
                rules.unload(goal.with() == ClaimLattice.HULL ? hullStore : stakes.get(goal.with()).store());
            }
            int gx = (int) Math.floor(goal.x());
            int gy = (int) Math.floor(goal.y());
            int[] to = goal.kind() == HaulerWork.Kind.GO ? walkLeg(sim, squad, lead, gx, gy) : new int[] {gx, gy};
            String why = switch (goal.kind()) {
                case STAND_BY -> "hauler stands by: nothing to carry";
                case GO -> "hauler walks the haul road";
                case TAKE -> "hauler takes on a stripper's metal";
                case UNLOAD -> "hauler unloads feedstock into a store";
            };
            order(sim, squad, to[0], to[1], why);
        }
    }

    /** The waypoint a lance walks to on its way to {@code (toX, toY)}, along a haul road where one serves. */
    private int[] walkLeg(BattleSimulation sim, int squad, long lead, int toX, int toY) {
        World world = sim.world();
        int[] end = legEnds.get(squad);
        HaulRoads.Leg leg = legs.get(squad);
        if (leg == null || end == null || end[0] != toX || end[1] != toY) {
            leg = new HaulRoads.Leg(roads.route(world.cellX(lead), world.cellY(lead), toX, toY));
            legs.put(squad, leg);
            legEnds.put(squad, new int[] {toX, toY});
        }
        return leg.toward(world.x(lead), world.y(lead));
    }

    // ---- the forge ----

    /** The forge cycle: what it prints walks out of the hull's mouth. */
    private void print(BattleSimulation sim, DerelictSecurityDirector director, float dt) {
        ClaimLattice.Plan plan = lattice.nextPlantable(openPoints(), anchors());
        List<Feedstock> stores = new ArrayList<>();
        stores.add(hullStore);
        for (int point : established) {
            SurveyStake stake = stakes.get(point);
            if (stake != null && stake.anchors()) stores.add(stake.store());
        }
        BuildQueue.Item item = forge.tick(dt, stores, i -> i.kind() != BuildQueue.Kind.STAKE || plan != null);
        if (item == null) return;
        int mx = site.mouthX();
        int my = site.mouthY();
        if (item.kind() == BuildQueue.Kind.STAKE) {
            int carrier = print(sim, director, DerelictWalkerFrame.HAULER, HULL_ZONE, mx, my, mx, my);
            if (carrier < 0) return;
            // The carrier is the train's until it plants, not a working hauler.
            haulers.remove(carrier);
            commanded.add(carrier);
            int parentZone = plan.parent() == ClaimLattice.HULL ? HULL_ZONE : zoneOf(plan.parent());
            List<Integer> escort = new ArrayList<>();
            for (DerelictWalkerFrame frame : item.escort()) {
                int squad = print(sim, director, frame, parentZone, mx, my, plan.point().x(), plan.point().y());
                if (squad < 0) continue;
                commanded.add(squad);
                escort.add(squad);
            }
            trains.add(new StakeTrain(plan, carrier, escort));
            return;
        }
        int[] post = frontierPost(director.walkers().size());
        print(sim, director, item.frame(), post[2], mx, my, post[0], post[1]);
    }

    /**
     * Stands one walker of the watch near {@code (x, y)} and gives it to the network in {@code zoneId}, posted at
     * {@code (postX, postY)}; a stripper or hauler goes to work under the hull. Returns its lance, or -1.
     */
    private int print(BattleSimulation sim, DerelictSecurityDirector director, DerelictWalkerFrame frame, int zoneId,
                      int x, int y, int postX, int postY) {
        int squad = spawnNear(sim, frame, x, y);
        if (squad < 0) return -1;
        sim.claimSquadCommand(squad, CommandAuthority.MISSION_COMMAND, DerelictSecurityDirector.ISSUER,
                "fallen hull " + frame.designation());
        director.addWalker(sim, new DerelictSecurityDirector.Walker(squad, frame, zoneId, postX, postY));
        if (frame == DerelictWalkerFrame.STRIPPER) {
            strippers.put(squad, new StripperWork());
            commanded.add(squad);
        } else if (frame == DerelictWalkerFrame.HAULER) {
            haulers.put(squad, new HaulerWork());
            commanded.add(squad);
        }
        return squad;
    }

    /** How far from the mouth, cells, a printed walker may come out when the mouth is crowded. */
    static final int SPAWN_SEARCH_CELLS = 6;

    /** One walker in a lance of its own at the nearest free ground to {@code (x, y)}, ring by ring; -1 when none. */
    private static int spawnNear(BattleSimulation sim, DerelictWalkerFrame frame, int x, int y) {
        for (int r = 0; r <= SPAWN_SEARCH_CELLS; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    if (!sim.getGrid().inBounds(x + dx, y + dy) || !sim.getGrid().isWalkable(x + dx, y + dy)
                            || !DerelictSecurity.standable(sim, frame, x + dx, y + dy)) continue;
                    return DerelictSecurity.spawn(sim, frame, x + dx, y + dy);
                }
            }
        }
        return -1;
    }

    /** Where a printed walker is posted: round the newest anchor, else the hull's mouth; {x, y, zone}. */
    private int[] frontierPost(int n) {
        for (int i = established.size() - 1; i >= 0; i--) {
            SurveyStake stake = stakes.get(established.get(i));
            if (stake == null || !stake.anchors()) continue;
            ClaimLattice.Point p = lattice.point(stake.pointId);
            int[] at = ring(p.x(), p.y(), 4, n);
            return new int[] {at[0], at[1], zoneOf(p.id())};
        }
        int[] at = ring(site.mouthX(), site.mouthY(), 3, n);
        return new int[] {at[0], at[1], HULL_ZONE};
    }

    /** Open lattice points: no stake on them and no train bound for them. */
    private Set<Integer> openPoints() {
        Set<Integer> open = new TreeSet<>();
        for (ClaimLattice.Point p : lattice.points()) open.add(p.id());
        open.removeAll(stakes.keySet());
        for (StakeTrain train : trains) open.remove(train.plan.point().id());
        return open;
    }

    private Set<Integer> anchors() {
        Set<Integer> out = new TreeSet<>();
        for (SurveyStake stake : stakes.values()) if (stake.anchors()) out.add(stake.pointId);
        return out;
    }

    // ---- orders ----

    private void release(int squad) {
        commanded.remove(squad);
        guarding.remove(squad);
        ordered.remove(squad);
    }

    /** Walks a lance the hull commands to a cell and holds it there; not re-given while the cell barely moves. */
    private void order(BattleSimulation sim, int squad, int cellX, int cellY, String why) {
        if (sim.squadMemberCount(squad) <= 0) return;
        long lead = sim.squadMemberAt(squad, 0);
        boolean far = Math.max(Math.abs(sim.world().x(lead) - (cellX + 0.5f)),
                Math.abs(sim.world().y(lead) - (cellY + 0.5f))) > HOLD_RADIUS;
        int[] last = ordered.get(squad);
        int mode = far ? 1 : 0;
        if (last != null && last[2] == mode
                && Math.max(Math.abs(last[0] - cellX), Math.abs(last[1] - cellY)) < REISSUE_CELLS) {
            return;
        }
        ordered.put(squad, new int[] {cellX, cellY, mode});
        ObjectiveAssignment assignment = far ? DerelictSecurityDirector.walk(squad, frameOf(squad), cellX, cellY)
                : ObjectiveAssignment.defendArea(squad, cellX, cellY, HOLD_RADIUS);
        sim.assignSquadCommand(assignment, CommandAuthority.MISSION_COMMAND, DerelictSecurityDirector.ISSUER, why);
    }

    /** The frame of a lance the hull printed: its workers' and trains' frames; armed otherwise. */
    private DerelictWalkerFrame frameOf(int squad) {
        if (strippers.containsKey(squad)) return DerelictWalkerFrame.STRIPPER;
        if (haulers.containsKey(squad)) return DerelictWalkerFrame.HAULER;
        for (StakeTrain train : trains) if (train.haulerSquad == squad) return DerelictWalkerFrame.HAULER;
        return DerelictWalkerFrame.LINE;
    }

    /** The {@code n}-th of eight places round a cell at {@code r} cells. */
    static int[] ring(int x, int y, int r, int n) {
        int[][] dirs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
        int[] d = dirs[Math.floorMod(n, dirs.length)];
        return new int[] {x + d[0] * r, y + d[1] * r};
    }

    /**
     * The nearest walkable, untaken cell to {@code (x, y)} at least {@code minCells} off it whose four neighbours are
     * walkable, so a structure stood there blocks no way; the cell itself when none is near.
     */
    private int[] freeCellNear(NavigationGrid grid, int x, int y, int minCells) {
        for (int r = minCells; r <= minCells + 8; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int cx = x + dx;
                    int cy = y + dy;
                    if (taken.contains(DerelictSecurity.cellKey(cx, cy)) || !open(grid, cx, cy)) continue;
                    if (open(grid, cx + 1, cy) && open(grid, cx - 1, cy) && open(grid, cx, cy + 1)
                            && open(grid, cx, cy - 1)) {
                        return new int[] {cx, cy};
                    }
                }
            }
        }
        return new int[] {x, y};
    }

    private static boolean open(NavigationGrid grid, int x, int y) {
        return grid.inBounds(x, y) && grid.isWalkable(x, y);
    }
}

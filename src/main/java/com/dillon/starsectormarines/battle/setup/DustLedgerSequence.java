package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.objective.side.SideObjective;
import com.dillon.starsectormarines.battle.objective.side.SideObjectiveService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.story.DustLedgerProgress;
import com.dillon.starsectormarines.battle.story.DustLedgerProgress.Broker;
import com.dillon.starsectormarines.battle.story.DustLedgerProgress.Facts;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.ScanCover;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.i18n.Strings;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Dust Ledger at Saltwell Cross ({@code dust-ledger-crossing.md}): the company walks in as Wayline's freight drop,
 * chooses its moment, and pays for preparation with time. This adapter reads the battle into
 * {@link DustLedgerProgress}, which owns the rules, and carries out what they decide: the quiet-phase
 * {@link ScanCover}, the Raid alarm, the cantina drinkers, the Harrow salvage team, the spotters' view of the yard,
 * and the broker's walk. Its four side objectives are toolkit jobs ({@link DustLedgerSideObjectives}) the battle's
 * side-objective service runs; the payoffs here read their state.
 *
 * <p>The Raid itself (seize the ledger room, return the records to the apron) is the {@link RaidObjective} the setup
 * installs beside this; the ledger burn stops at its seizure. This objective completes with the Raid and never fails,
 * so a fully burned ledger or an escaped broker still ends in victory and only changes the debrief.
 *
 * <p>Everything it reads of the map comes through {@link Sites}, so it runs on any grid that provides them.
 */
public final class DustLedgerSequence implements Objective {
    /** Margin, in cells, {@link Sites#ring} grows the compound grounds by. UNTUNED: the doc's "short margin". */
    public static final int RING_MARGIN_CELLS = 4;
    /** A company combatant this close to the broker takes his surrender. */
    public static final float BROKER_REACH_CELLS = 2f;
    /** The broker this close to the skimmer pad has boarded. */
    public static final float PAD_REACH_CELLS = 1.5f;
    /** A company combatant this close to the coupling crate picks it up. */
    public static final float CRATE_REACH_CELLS = 1.5f;
    /** Sight assumed for a watcher without a vision component. */
    static final float DEFAULT_SIGHT_CELLS = 18f;
    /** How often, in ticks, the broker re-plans toward a moving captor. */
    private static final int BROKER_REPLAN_TICKS = 15;
    private static final String ISSUER = "dust-ledger";
    /** Beat ids are the narration catalog's events ({@code wayline_narration.json}, {@code corvus.dust_ledger.*}). */
    private static final String BEAT = "corvus.dust_ledger.";
    /**
     * Grants side objectives before play ({@code coupling}, {@code warden}, {@code feed}, comma separated, or
     * {@code all} for the three): a standing evidence lever, so a headless run can play the prepared approach the autonomous company
     * never chooses, beside the run that plays none.
     */
    public static final String SIDE_OBJECTIVES_PROPERTY = "battle.dustLedger.sideObjectives";
    /** Lift-off seconds left at which Castle calls the clock short. */
    static final float CLOCK_SHORT_SECONDS = 60f;

    public record Cell(int x, int y) { }

    /** A set of cells: a room, a yard, the exclusion ring. Never empty. */
    public record Area(Set<Cell> cells) {
        public Area {
            if (cells == null || cells.isEmpty()) throw new IllegalArgumentException("an area needs at least one cell");
            cells = Set.copyOf(cells);
        }

        public static Area rect(int left, int top, int right, int bottom) {
            Set<Cell> cells = new HashSet<>();
            for (int y = top; y <= bottom; y++) for (int x = left; x <= right; x++) cells.add(new Cell(x, y));
            return new Area(cells);
        }

        public static Area of(Cell... cells) { return new Area(Set.of(cells)); }

        public boolean contains(int x, int y) { return cells.contains(new Cell(x, y)); }

        /** This area and every cell within {@code margin} cells of it, by king's moves. */
        public Area grown(int margin) {
            Set<Cell> grown = new HashSet<>();
            for (Cell c : cells)
                for (int dy = -margin; dy <= margin; dy++)
                    for (int dx = -margin; dx <= margin; dx++) grown.add(new Cell(c.x() + dx, c.y() + dy));
            return new Area(grown);
        }

        /** The member cell nearest the area's centroid, so a concave area still names one of its own cells. */
        public Cell centre() {
            double sx = 0, sy = 0;
            for (Cell c : cells) { sx += c.x(); sy += c.y(); }
            double cx = sx / cells.size(), cy = sy / cells.size();
            Cell best = null;
            double bestD = Double.MAX_VALUE;
            for (Cell c : cells) {
                double d = (c.x() - cx) * (c.x() - cx) + (c.y() - cy) * (c.y() - cy);
                if (d < bestD || d == bestD && (c.y() < best.y() || c.y() == best.y() && c.x() < best.x())) { bestD = d; best = c; }
            }
            return best;
        }

        /** Farthest distance, in cells, from {@code from} to any member cell. */
        public int reach(Cell from) {
            double max = 0;
            for (Cell c : cells) max = Math.max(max, Math.hypot(c.x() - from.x(), c.y() - from.y()));
            return (int) Math.ceil(max);
        }

        /** The member cells with a four-neighbour outside the area: the outline a map marker draws. */
        public List<Cell> outline() {
            List<Cell> out = new ArrayList<>();
            for (Cell c : cells)
                if (!contains(c.x() + 1, c.y()) || !contains(c.x() - 1, c.y())
                        || !contains(c.x(), c.y() + 1) || !contains(c.x(), c.y() - 1)) out.add(c);
            out.sort((a, b) -> a.y() != b.y() ? Integer.compare(a.y(), b.y()) : Integer.compare(a.x(), b.x()));
            return out;
        }
    }

    /**
     * The named places the mission reads, in map cells. The map's layout maps onto this; nothing else of the map is
     * read.
     *
     * @param apron            the freight apron: landing ground, crate pickup, and where a held broker is delivered
     * @param crate            where the coupling crate waits on the apron
     * @param ledgerRoom       the ledger room (its seizure is the Raid's; this is for the marker)
     * @param burnBarrel       the burn barrel inside the ledger room; the drinkers make for it
     * @param brokerOffice     where the broker stands until the alarm
     * @param skimmerPad       the broker's skimmer pad beyond the southern canal crossing
     * @param exclusionRing    compound grounds plus margin ({@link #ring}): company units seen inside raise the alarm
     * @param compoundYard     the walled yard the rooftop spotters reveal
     * @param spotterPosts     the watch's rooftop posts, for their markers
     * @param cantina          the cantina interior
     * @param drinkerCells     walkable cells in the cantina, one per drinker
     * @param wellHouse        the warden's well house, where a squad leader waits for the nod
     * @param harrowYard       the Harrow salvage yard; the crate is delivered when carried inside
     * @param salvageTeamCells walkable cells on the crawler road, one per salvage team member
     * @param eastGate         the compound's east gate, where the salvage team advances
     * @param feedJunction     the power tap's junction box and the ground a squad works it from, outside the ring
     */
    public record Sites(Area apron, Cell crate, Area ledgerRoom, Cell burnBarrel, Cell brokerOffice, Cell skimmerPad,
                        Area exclusionRing, Area compoundYard, List<Cell> spotterPosts, Area cantina,
                        List<Cell> drinkerCells, Area wellHouse, Area harrowYard, List<Cell> salvageTeamCells,
                        Cell eastGate, Area feedJunction) {
        public Sites {
            for (Object site : new Object[]{apron, crate, ledgerRoom, burnBarrel, brokerOffice, skimmerPad, exclusionRing,
                    compoundYard, spotterPosts, cantina, drinkerCells, wellHouse, harrowYard, salvageTeamCells, eastGate, feedJunction})
                if (site == null) throw new IllegalArgumentException("every Dust Ledger site is required");
            spotterPosts = List.copyOf(spotterPosts);
            drinkerCells = List.copyOf(drinkerCells);
            salvageTeamCells = List.copyOf(salvageTeamCells);
            if (!exclusionRing.contains(brokerOffice.x(), brokerOffice.y()) || !exclusionRing.contains(burnBarrel.x(), burnBarrel.y()))
                throw new IllegalArgumentException("the broker's office and the burn barrel lie inside the exclusion ring");
            for (Area outside : List.of(apron, wellHouse, harrowYard, feedJunction))
                for (Cell c : outside.cells())
                    if (exclusionRing.contains(c.x(), c.y()))
                        throw new IllegalArgumentException("the apron and every side objective lie outside the exclusion ring");
        }

        /** The exclusion ring for compound grounds: the grounds grown by {@link #RING_MARGIN_CELLS}. */
        public static Area ring(Area grounds) { return grounds.grown(RING_MARGIN_CELLS); }
    }

    /** One Sable combatant watching: its cell and how far it sees. */
    record Watcher(int x, int y, float sight) { }

    /**
     * Who the quiet phase hides from hostile scans: all of Sable (the company holds fire), and the company and its
     * allies outside the exclusion ring (Sable does not treat them as hostile there). Inside the ring the company
     * is fair game, and being seen there raises the alarm.
     */
    static boolean covered(Area ring, Faction faction, float x, float y) {
        return switch (faction) {
            case DEFENDER -> true;
            case MARINE, ALLY -> !ring.contains((int) Math.floor(x), (int) Math.floor(y));
            case CIVILIAN -> false;
        };
    }

    /** Whether any watcher has a line of sight within its range to any company cell inside the ring. */
    static boolean sighted(NavigationGrid grid, Area ring, List<Watcher> watchers, List<Cell> company) {
        for (Cell c : company) {
            if (!ring.contains(c.x(), c.y())) continue;
            for (Watcher w : watchers)
                if (grid.hasLineOfSightWithin(w.x(), w.y(), c.x(), c.y(), w.sight())) return true;
        }
        return false;
    }

    /** Whether a shot or round from this side breaks the quiet: anything but a civilian's. */
    static boolean breaksQuiet(Faction shooter) {
        return shooter != null && shooter != Faction.CIVILIAN;
    }

    private final Sites sites;
    private final RaidObjective raid;
    private final List<EntitySpec> drinkers, salvageTeam;
    private final StoryBeatLog log;
    private final DustLedgerProgress progress;
    private final SideObjective coupling, warden, feed, brokerJob;
    private long broker;
    private int brokerGoalX = -1, brokerGoalY = -1, brokerPlannedTick = -1;
    private boolean begun, alarmApplied, spotterSources;
    private int drinkerSquad = -1, salvageSquad = -1;
    private final List<Long> nests = new ArrayList<>();
    private final List<Cell> nestCells = new ArrayList<>();
    private final List<Integer> pillboxSquads = new ArrayList<>();
    private DustLedgerTechnical technical;

    public DustLedgerSequence(Sites sites, RaidObjective raid, List<EntitySpec> drinkers, List<EntitySpec> salvageTeam,
                              StoryBeatLog log) {
        this(sites, raid, drinkers, salvageTeam, log, new DustLedgerProgress());
    }

    DustLedgerSequence(Sites sites, RaidObjective raid, List<EntitySpec> drinkers, List<EntitySpec> salvageTeam,
                       StoryBeatLog log, DustLedgerProgress progress) {
        if (sites == null || raid == null || drinkers == null || salvageTeam == null || log == null)
            throw new IllegalArgumentException("the Dust Ledger needs its sites, its Raid and both reserves");
        if (drinkers.size() > sites.drinkerCells().size() || salvageTeam.size() > sites.salvageTeamCells().size())
            throw new IllegalArgumentException("one spawn cell per drinker and per salvage team member");
        this.sites = sites; this.raid = raid; this.log = log; this.progress = progress;
        this.drinkers = List.copyOf(drinkers); this.salvageTeam = List.copyOf(salvageTeam);
        coupling = DustLedgerSideObjectives.coupling(sites, progress);
        warden = DustLedgerSideObjectives.warden(sites, progress, this::spottersUp);
        feed = DustLedgerSideObjectives.feed(sites, progress, sim -> { for (long nest : nests) sim.turretState().cutPower(nest); },
                this::nestCells, () -> !feedCut());
        brokerJob = DustLedgerSideObjectives.broker(sites, progress, () -> broker, this::brokerJob);
    }

    private SideObjective brokerJob() { return brokerJob; }

    /** Offers the four side objectives on the battle's service. Once, at setup. */
    void offer(SideObjectiveService service) {
        for (SideObjective job : List.of(coupling, warden, feed, brokerJob)) service.install(job);
    }

    /** Grants the side objectives a control run names; unknown names fail loudly rather than play the wrong run. */
    void grantForced(String names, SideObjectiveService service, BattleSimulation sim) {
        for (String name : names.split(",")) {
            String key = name.trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty()) continue;
            if (key.equals("all")) {
                for (String job : DustLedgerSideObjectives.QUIET_JOBS) if (!service.job(job).done()) service.grant(job, sim);
                continue;
            }
            String job = "dust-ledger-" + key;
            if (!DustLedgerSideObjectives.QUIET_JOBS.contains(job))
                throw new IllegalArgumentException(SIDE_OBJECTIVES_PROPERTY + ": unknown side objective " + name);
            if (!service.job(job).done()) service.grant(job, sim);
        }
    }

    /** Stands the broker in his office and puts the quiet phase over the scans. Once, at setup. */
    public void begin(BattleSimulation sim) {
        if (begun) throw new IllegalStateException("the Dust Ledger begins once");
        begun = true;
        Cell office = sites.brokerOffice();
        broker = sim.spawn(new EntitySpec(Strings.get("dustLedgerHudBrokerName"), Faction.CIVILIAN, UnitType.CIVILIAN,
                office.x(), office.y()).role(UnitRole.VIP));
        offer(sim.getSideObjectives());
        grantForced(System.getProperty(SIDE_OBJECTIVES_PROPERTY, ""), sim.getSideObjectives(), sim);
        Area ring = sites.exclusionRing();
        sim.getUnitIndex().cover((faction, x, y) -> progress.quiet() && covered(ring, faction, x, y));
    }

    /**
     * What the debrief reads: the ledger's grade at seizure (null if never seized) and whether the broker was caught,
     * which counts only once he reached the apron alive (the broker side objective done).
     */
    public record Report(DustLedgerProgress.Grade grade, boolean brokerCaught) { }
    public Report report() { return new Report(progress.grade(), brokerJob.done()); }

    /**
     * The compound's defences, recorded at setup: the powered gun nests (turret ids) a cut feed darkens, and the
     * squads manning the pillboxes, whose first contact is called.
     */
    public void defences(BattleSimulation sim, List<Long> nestIds, List<Integer> pillboxSquadIds) {
        if (!nests.isEmpty() || !pillboxSquads.isEmpty()) throw new IllegalStateException("record the defences once");
        nests.addAll(nestIds);
        for (long nest : nestIds) nestCells.add(new Cell(sim.world().cellX(nest), sim.world().cellY(nest)));
        pillboxSquads.addAll(pillboxSquadIds);
        if (feedCut()) for (long nest : nests) sim.turretState().cutPower(nest);
    }

    /**
     * Sable's rocket truck, recorded at setup with the stands the map leaves it: covered like every Sable
     * body while quiet, and from the alarm working the yard's corners against the company's armor
     * ({@link DustLedgerTechnical}).
     */
    public void technical(BattleSimulation sim, long id, List<Cell> stands) {
        if (technical != null) throw new IllegalStateException("record the technical once");
        technical = new DustLedgerTechnical(sim, id, stands);
    }

    /** The truck's hull id, or 0 when the mission fields none. */
    public long technical() { return technical == null ? 0L : technical.id(); }

    public List<Long> nests() { return List.copyOf(nests); }
    /** Where the nests stand, for their markers. */
    public List<Cell> nestCells() { return List.copyOf(nestCells); }
    public List<Integer> pillboxSquads() { return List.copyOf(pillboxSquads); }

    public Sites sites() { return sites; }
    public DustLedgerProgress progress() { return progress; }
    public long broker() { return broker; }
    /** The salvage team answers the alarm only if the coupling reached them first. */
    public boolean salvageTeamJoins() { return coupling.done(); }
    /** The cantina drinkers arm and join unless the warden's watch holds the cantina. */
    public boolean drinkersJoin() { return !warden.done(); }
    /** The watch's rooftop spotters reveal the compound yard from the nod until the alarm. */
    public boolean spottersUp() { return warden.done() && progress.quiet(); }
    /** The gun nests are dead when the alarm comes if the feed was cut first. */
    public boolean feedCut() { return feed.done(); }
    /** Reserve members not yet on the field, for the setup's pending-arrivals count. */
    public int pendingMembers(Faction faction) {
        if (alarmApplied) return 0;
        if (faction == Faction.DEFENDER) return drinkersJoin() ? drinkers.size() : 0;
        return faction == Faction.ALLY && salvageTeamJoins() ? salvageTeam.size() : 0;
    }

    public void advance(BattleSimulation sim) {
        if (!begun || sim.isComplete()) return;
        boolean wasQuiet = progress.quiet();
        boolean shot = wasQuiet && shotFired(sim);
        boolean sighted = wasQuiet && !shot && sentrySighting(sim);
        boolean brokerAlive = onField(sim, broker);
        float bx = brokerAlive ? sim.world().x(broker) : -1, by = brokerAlive ? sim.world().y(broker) : -1;
        Cell pad = sites.skimmerPad();
        Facts facts = new Facts(shot, sighted, raid.targetSecured(), brokerAlive,
                brokerAlive && companyWithin(sim, bx, by, BROKER_REACH_CELLS, true) != 0L,
                brokerAlive && Math.hypot(bx - (pad.x() + .5f), by - (pad.y() + .5f)) <= PAD_REACH_CELLS);
        Broker brokerBefore = progress.broker();
        boolean seizedBefore = progress.seized();
        progress.advance(BattleSimulation.TICK_DT, facts);
        if (wasQuiet && progress.clockRemaining() <= CLOCK_SHORT_SECONDS) beat(sim, "clock_short");
        if (spottersUp()) beat(sim, "spotters");
        if (wasQuiet && !progress.quiet()) onAlarm(sim);
        if (!seizedBefore && progress.seized()) beat(sim, "ledger_seized");
        if (!progress.seized() && !progress.quiet()) {
            if (progress.burnedShare() >= .5f) beat(sim, "ledger_half");
            if (progress.burnedShare() >= 1f) beat(sim, "ledger_burned");
        }
        if (brokerBefore != progress.broker()) {
            // His surrender, delivery and death are the broker side objective's lines.
            String told = switch (progress.broker()) {
                case FLEEING -> "broker_flees";
                // The clock's departure is told by alarm_clock; a boarding at the pad is told here.
                case ESCAPED -> brokerBefore == Broker.IN_OFFICE ? null : "broker_boarded";
                default -> null;
            };
            if (told != null) beat(sim, told);
        }
        if (progress.broker() == Broker.ESCAPED && onField(sim, broker)) sim.takeOffTheField(broker);
        for (long nest : nests) if (!onField(sim, nest)) beat(sim, "nest_destroyed");
        for (int squad : pillboxSquads) {
            Squad crew = sim.getSquad(squad);
            if (crew != null && sim.squadMemberCount(squad) > 0 && crew.hasDirectContactThisTick()) beat(sim, "pillbox_contact");
        }
        walkBroker(sim);
        updateSpotters(sim);
        if (technical != null && !progress.quiet()) technical.tick(sim);
    }

    private void onAlarm(BattleSimulation sim) {
        alarmApplied = true;
        int tick = sim.getSimTickIndex();
        raid.raiseAlarm(tick);
        sim.getUnitIndex().cover(null);
        beat(sim, progress.alarm() == DustLedgerProgress.Alarm.SHOTS ? "alarm_shot" : "alarm_" + lower(progress.alarm().name()));
        beat(sim, "ledger_burning");
        if (feedCut() && !nests.isEmpty()) beat(sim, "nests_dark");
        if (drinkersJoin() && !drinkers.isEmpty()) {
            drinkerSquad = release(sim, drinkers, sites.drinkerCells(), Faction.DEFENDER);
            sim.assignSquadCommand(ObjectiveAssignment.attackMove(drinkerSquad, sites.burnBarrel().x(), sites.burnBarrel().y()),
                    CommandAuthority.SCRIPTED, ISSUER, "Cantina drinkers arm and make for the pump house");
            beat(sim, "cantina_joins");
        } else if (!drinkersJoin()) beat(sim, "cantina_held");
        if (salvageTeamJoins() && !salvageTeam.isEmpty()) {
            salvageSquad = release(sim, salvageTeam, sites.salvageTeamCells(), Faction.ALLY);
            sim.assignSquadCommand(ObjectiveAssignment.attackMove(salvageSquad, sites.eastGate().x(), sites.eastGate().y()),
                    CommandAuthority.SCRIPTED, ISSUER, "Harrow salvage team advances on the east gate");
            beat(sim, "salvage_team");
        }
    }

    private static int release(BattleSimulation sim, List<EntitySpec> reserve, List<Cell> cells, Faction faction) {
        int squad = sim.mintSquad(faction, reserve.get(0).type);
        for (int i = 0; i < reserve.size(); i++) {
            Cell cell = cells.get(i);
            sim.spawn(reserve.get(i).atPosition(cell.x() + .5f, cell.y() + .5f).squad(squad));
        }
        sim.getSquad(squad).originalSize = reserve.size();
        return squad;
    }

    private static boolean shotFired(BattleSimulation sim) {
        for (ShotEvent shot : sim.getShotsThisFrame()) if (breaksQuiet(shot.shooterFaction)) return true;
        for (Projectile round : sim.getActiveProjectiles()) if (breaksQuiet(round.shooterFaction)) return true;
        return false;
    }

    private boolean sentrySighting(BattleSimulation sim) {
        List<Cell> inside = new ArrayList<>();
        List<Watcher> watchers = new ArrayList<>();
        Area ring = sites.exclusionRing();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long id = sim.liveUnitAt(i);
            if (!sim.world().hasPosition(id) || sim.isRiding(id)) continue;
            Faction faction = sim.identity().faction(id);
            int x = sim.world().cellX(id), y = sim.world().cellY(id);
            if (faction == Faction.MARINE && ring.contains(x, y)) inside.add(new Cell(x, y));
            else if (faction == Faction.DEFENDER && sim.identity().type(id).combatant)
                watchers.add(new Watcher(x, y, sim.vision().has(id) ? sim.vision().visionRange(id) : DEFAULT_SIGHT_CELLS));
        }
        return !inside.isEmpty() && sighted(sim.getGrid(), ring, watchers, inside);
    }

    /** Nearest company combatant within {@code reach} of a point, or 0. */
    private static long companyWithin(BattleSimulation sim, float x, float y, float reach, boolean combatantOnly) {
        long best = 0L;
        double bestD = reach;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long id = sim.liveUnitAt(i);
            if (sim.identity().faction(id) != Faction.MARINE || !sim.world().hasPosition(id) || sim.isRiding(id)) continue;
            if (combatantOnly && !sim.identity().type(id).combatant) continue;
            double d = Math.hypot(sim.world().x(id) - x, sim.world().y(id) - y);
            if (d <= bestD) { bestD = d; best = id; }
        }
        return best;
    }

    private static boolean onField(BattleView sim, long id) {
        return id != 0L && sim.resolveUnit(id) != 0L && sim.world().hasPosition(id) && sim.world().isAlive(id);
    }

    /** Fleeing, he runs for the pad; held, he keeps to the nearest of his captors until delivered; otherwise he stands. */
    private void walkBroker(BattleSimulation sim) {
        if (!onField(sim, broker)) return;
        int goalX, goalY;
        switch (progress.broker()) {
            case FLEEING -> { goalX = sites.skimmerPad().x(); goalY = sites.skimmerPad().y(); }
            case HELD -> {
                if (brokerJob.done()) { sim.clearPath(broker); return; }
                long captor = companyWithin(sim, sim.world().x(broker), sim.world().y(broker), Float.MAX_VALUE, false);
                if (captor == 0L) return;
                goalX = sim.world().cellX(captor); goalY = sim.world().cellY(captor);
            }
            default -> { sim.clearPath(broker); return; }
        }
        int x = sim.world().cellX(broker), y = sim.world().cellY(broker);
        int tick = sim.getSimTickIndex();
        int[] path = sim.movement().path(broker);
        boolean spent = Paths.isEmpty(path) || sim.movement().pathIdx(broker) >= Paths.cellCount(path);
        boolean moved = Math.abs(goalX - brokerGoalX) > 2 || Math.abs(goalY - brokerGoalY) > 2;
        if ((spent || moved && tick - brokerPlannedTick >= BROKER_REPLAN_TICKS)
                && sim.movement().mayRepath(broker) && sim.getGrid().inBounds(x, y)) {
            sim.setPath(broker, sim.findPath(x, y, goalX, goalY));
            brokerGoalX = goalX; brokerGoalY = goalY; brokerPlannedTick = tick;
        }
        sim.advanceMovement(broker);
    }

    /** While the watch spots for the company, the compound yard is held open in the player's view. */
    private void updateSpotters(BattleSimulation sim) {
        boolean up = spottersUp();
        if (!up && !spotterSources) return;
        FogOfWarService fog = sim.getFogOfWar();
        fog.clearStandingSources();
        spotterSources = up;
        if (!up) return;
        Area yard = sites.compoundYard();
        Cell centre = yard.centre();
        fog.addStandingSource(centre.x(), centre.y(), yard.reach(centre) + 1, 0f);
    }

    private void beat(BattleSimulation sim, String key) {
        log.emitOnce(sim.getSimTickIndex(), BEAT + key, StoryBeatLog.Audience.PLAYER);
    }

    private static String lower(String name) { return name.toLowerCase(Locale.ROOT); }

    @Override public Faction owningFaction() { return Faction.MARINE; }
    @Override public void tick(BattleView sim) { }
    /** Complete with the Raid: grade and broker change only the debrief. */
    @Override public boolean isComplete() { return raid.isComplete(); }
    @Override public boolean isFailed() { return false; }
    @Override public String displayName() { return Strings.get("dustLedgerHudClockName"); }
}

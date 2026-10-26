package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.story.DustLedgerProgress;
import com.dillon.starsectormarines.battle.story.DustLedgerProgress.Broker;
import com.dillon.starsectormarines.battle.story.DustLedgerProgress.Facts;
import com.dillon.starsectormarines.battle.story.DustLedgerProgress.Side;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.ScanCover;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.i18n.Strings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Dust Ledger at Saltwell Cross ({@code dust-ledger-crossing.md}): the company walks in as Wayline's freight drop,
 * chooses its moment, and pays for preparation with time. This adapter reads the battle into
 * {@link DustLedgerProgress}, which owns the rules, and carries out what they decide: the quiet-phase
 * {@link ScanCover}, the Raid alarm, the cantina drinkers, the Harrow salvage team, the spotters' view of the yard,
 * and the broker's walk.
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
     * {@code all}): a standing evidence lever, so a headless run can play the prepared approach the autonomous company
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

    /** The coupling crate: lying somewhere, carried by one company unit, or delivered. */
    static final class Crate {
        int x, y;
        long carrier;
        boolean delivered;

        Crate(Cell start) { x = start.x(); y = start.y(); }

        /**
         * One tick. While carried, the crate is wherever its carrier stands, delivered the moment that is inside the
         * yard, and dropped where the carrier fell if it dies. While lying, the company unit {@code pickup} (0 for
         * none in reach) takes it up.
         */
        void step(boolean carrierAlive, int carrierX, int carrierY, long pickup, Area yard) {
            if (delivered) return;
            if (carrier != 0L) {
                if (!carrierAlive) { carrier = 0L; return; }
                x = carrierX; y = carrierY;
                if (yard.contains(x, y)) { delivered = true; carrier = 0L; }
                return;
            }
            if (pickup != 0L) carrier = pickup;
        }
    }

    /** One Sable Wake combatant watching: its cell and how far it sees. */
    record Watcher(int x, int y, float sight) { }

    /**
     * Who the quiet phase hides from hostile scans: all of Sable Wake (the company holds fire), and the company and its
     * allies outside the exclusion ring (Sable Wake does not treat them as hostile there). Inside the ring the company
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
    private final Crate crate;
    private long broker;
    private int brokerGoalX = -1, brokerGoalY = -1, brokerPlannedTick = -1;
    private boolean begun, alarmApplied, spotterSources;
    private int drinkerSquad = -1, salvageSquad = -1;
    private final List<Long> nests = new ArrayList<>();
    private final List<Cell> nestCells = new ArrayList<>();
    private final List<Integer> pillboxSquads = new ArrayList<>();

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
        this.crate = new Crate(sites.crate());
    }

    /** Pays the side objectives a control run names; unknown names fail loudly rather than play the wrong run. */
    void grantForced(String names) {
        for (String name : names.split(",")) {
            String key = name.trim().toUpperCase(Locale.ROOT);
            if (key.isEmpty()) continue;
            for (DustLedgerProgress.SideObjective which : DustLedgerProgress.SideObjective.values())
                if (key.equals("ALL") || key.equals(which.name())) progress.grant(which);
            if (!key.equals("ALL") && Arrays.stream(DustLedgerProgress.SideObjective.values()).noneMatch(w -> w.name().equals(key)))
                throw new IllegalArgumentException(SIDE_OBJECTIVES_PROPERTY + ": unknown side objective " + name);
        }
        if (progress.coupling() == Side.PAID) crate.delivered = true;
    }

    /** Stands the broker in his office and puts the quiet phase over the scans. Once, at setup. */
    public void begin(BattleSimulation sim) {
        if (begun) throw new IllegalStateException("the Dust Ledger begins once");
        begun = true;
        Cell office = sites.brokerOffice();
        broker = sim.spawn(new EntitySpec(Strings.get("dustLedgerHudBrokerName"), Faction.CIVILIAN, UnitType.CIVILIAN,
                office.x(), office.y()).role(UnitRole.VIP));
        grantForced(System.getProperty(SIDE_OBJECTIVES_PROPERTY, ""));
        Area ring = sites.exclusionRing();
        sim.getUnitIndex().cover((faction, x, y) -> progress.quiet() && covered(ring, faction, x, y));
    }

    /** What the debrief reads: the ledger's grade at seizure (null if never seized) and where the broker ended up. */
    public record Report(DustLedgerProgress.Grade grade, DustLedgerProgress.Broker broker) {
        /** Only a broker who reached the apron alive counts as caught. */
        public boolean brokerCaught() { return broker == DustLedgerProgress.Broker.DELIVERED; }
    }
    public Report report() { return new Report(progress.grade(), progress.broker()); }

    /**
     * The compound's defences, recorded at setup: the powered gun nests (turret ids) a cut feed darkens, and the
     * squads manning the pillboxes, whose first contact is called.
     */
    public void defences(BattleSimulation sim, List<Long> nestIds, List<Integer> pillboxSquadIds) {
        if (!nests.isEmpty() || !pillboxSquads.isEmpty()) throw new IllegalStateException("record the defences once");
        nests.addAll(nestIds);
        for (long nest : nestIds) nestCells.add(new Cell(sim.world().cellX(nest), sim.world().cellY(nest)));
        pillboxSquads.addAll(pillboxSquadIds);
        if (!progress.nestsPowered()) for (long nest : nests) sim.turretState().cutPower(nest);
    }

    public List<Long> nests() { return List.copyOf(nests); }
    /** Where the nests stand, for their markers. */
    public List<Cell> nestCells() { return List.copyOf(nestCells); }
    public List<Integer> pillboxSquads() { return List.copyOf(pillboxSquads); }

    public Sites sites() { return sites; }
    public DustLedgerProgress progress() { return progress; }
    public long broker() { return broker; }
    public int crateX() { return crate.x; }
    public int crateY() { return crate.y; }
    public boolean crateCarried() { return crate.carrier != 0L; }
    public boolean crateDelivered() { return crate.delivered; }
    /** Reserve members not yet on the field, for the setup's pending-arrivals count. */
    public int pendingMembers(Faction faction) {
        if (alarmApplied) return 0;
        if (faction == Faction.DEFENDER) return progress.drinkersJoin() ? drinkers.size() : 0;
        return faction == Faction.ALLY && progress.salvageTeamJoins() ? salvageTeam.size() : 0;
    }

    public void advance(BattleSimulation sim) {
        if (!begun || sim.isComplete()) return;
        boolean wasQuiet = progress.quiet();
        boolean shot = wasQuiet && shotFired(sim);
        boolean sighted = wasQuiet && !shot && sentrySighting(sim);
        stepCrate(sim);
        boolean brokerAlive = onField(sim, broker);
        float bx = brokerAlive ? sim.world().x(broker) : -1, by = brokerAlive ? sim.world().y(broker) : -1;
        Cell pad = sites.skimmerPad();
        boolean leaderAtWell = leaderAtWell(sim), atFeed = companyIn(sim, sites.feedJunction());
        Facts facts = new Facts(shot, sighted, raid.targetSecured(), brokerAlive,
                brokerAlive && companyWithin(sim, bx, by, BROKER_REACH_CELLS, true) != 0L,
                brokerAlive && Math.hypot(bx - (pad.x() + .5f), by - (pad.y() + .5f)) <= PAD_REACH_CELLS,
                brokerAlive && sites.apron().contains((int) Math.floor(bx), (int) Math.floor(by)),
                crate.delivered, leaderAtWell, atFeed);
        Broker brokerBefore = progress.broker();
        boolean seizedBefore = progress.seized();
        Side couplingBefore = progress.coupling(), wardenBefore = progress.warden(), feedBefore = progress.feed();
        progress.advance(BattleSimulation.TICK_DT, facts);
        if (wasQuiet) {
            if (crate.carrier != 0L) beat(sim, "harrow_crate");
            if (leaderAtWell) beat(sim, "warden_approach");
            if (atFeed) beat(sim, "feed_approach");
            if (progress.clockRemaining() <= CLOCK_SHORT_SECONDS) beat(sim, "clock_short");
        }
        if (couplingBefore != progress.coupling() && progress.coupling() == Side.PAID) beat(sim, "harrow_delivered");
        if (wardenBefore != progress.warden() && progress.warden() == Side.PAID) beat(sim, "warden_nod");
        if (progress.spottersUp()) beat(sim, "spotters");
        if (feedBefore != progress.feed()) {
            beat(sim, progress.feed() == Side.PAID ? "feed_cut" : "feed_lapsed");
            // The nests hold fire through the quiet phase anyway; cut now, they are dead when the alarm comes.
            if (progress.feed() == Side.PAID) for (long nest : nests) sim.turretState().cutPower(nest);
        }
        if (wasQuiet && !progress.quiet()) onAlarm(sim);
        if (!seizedBefore && progress.seized()) beat(sim, "ledger_seized");
        if (!progress.seized() && !progress.quiet()) {
            if (progress.burnedShare() >= .5f) beat(sim, "ledger_half");
            if (progress.burnedShare() >= 1f) beat(sim, "ledger_burned");
        }
        if (brokerBefore != progress.broker()) {
            String told = switch (progress.broker()) {
                case FLEEING -> "broker_flees";
                case HELD -> "broker_surrendered";
                case DELIVERED -> "broker_apron";
                // The clock's departure is told by alarm_clock; a boarding at the pad is told here.
                case ESCAPED -> brokerBefore == Broker.IN_OFFICE ? null : "broker_boarded";
                case DEAD -> "broker_lost";
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
    }

    private void onAlarm(BattleSimulation sim) {
        alarmApplied = true;
        int tick = sim.getSimTickIndex();
        raid.raiseAlarm(tick);
        sim.getUnitIndex().cover(null);
        beat(sim, progress.alarm() == DustLedgerProgress.Alarm.SHOTS ? "alarm_shot" : "alarm_" + lower(progress.alarm().name()));
        beat(sim, "ledger_burning");
        if (!progress.nestsPowered() && !nests.isEmpty()) beat(sim, "nests_dark");
        if (progress.drinkersJoin() && !drinkers.isEmpty()) {
            drinkerSquad = release(sim, drinkers, sites.drinkerCells(), Faction.DEFENDER);
            sim.assignSquadCommand(ObjectiveAssignment.attackMove(drinkerSquad, sites.burnBarrel().x(), sites.burnBarrel().y()),
                    CommandAuthority.SCRIPTED, ISSUER, "Cantina drinkers arm and make for the pump house");
            beat(sim, "cantina_joins");
        } else if (!progress.drinkersJoin()) beat(sim, "cantina_held");
        if (progress.salvageTeamJoins() && !salvageTeam.isEmpty()) {
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

    private void stepCrate(BattleSimulation sim) {
        boolean alive = crate.carrier != 0L && onField(sim, crate.carrier);
        long pickup = crate.carrier == 0L && !crate.delivered
                ? companyWithin(sim, crate.x + .5f, crate.y + .5f, CRATE_REACH_CELLS, true) : 0L;
        crate.step(alive, alive ? sim.world().cellX(crate.carrier) : crate.x, alive ? sim.world().cellY(crate.carrier) : crate.y,
                pickup, sites.harrowYard());
    }

    /** Whether a company combatant stands inside {@code area}. */
    private static boolean companyIn(BattleSimulation sim, Area area) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long id = sim.liveUnitAt(i);
            if (sim.identity().faction(id) != Faction.MARINE || !sim.identity().type(id).combatant
                    || !sim.world().hasPosition(id) || sim.isRiding(id)) continue;
            if (area.contains(sim.world().cellX(id), sim.world().cellY(id))) return true;
        }
        return false;
    }

    private boolean leaderAtWell(BattleSimulation sim) {
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.MARINE || squad.leaderId == 0L || !onField(sim, squad.leaderId)) continue;
            if (sites.wellHouse().contains(sim.world().cellX(squad.leaderId), sim.world().cellY(squad.leaderId))) return true;
        }
        return false;
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

    /** Fleeing, he runs for the pad; held, he keeps to the nearest of his captors; otherwise he stands. */
    private void walkBroker(BattleSimulation sim) {
        if (!onField(sim, broker)) return;
        int goalX, goalY;
        switch (progress.broker()) {
            case FLEEING -> { goalX = sites.skimmerPad().x(); goalY = sites.skimmerPad().y(); }
            case HELD -> {
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
        boolean up = progress.spottersUp();
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

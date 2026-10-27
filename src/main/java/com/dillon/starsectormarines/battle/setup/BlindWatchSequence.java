package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.command.objective.ChargeSiteObjective;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.fort.FortSensors;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.objective.side.SideObjective;
import com.dillon.starsectormarines.battle.objective.side.SideObjectiveService;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Area;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Cell;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.story.BlindWatchProgress;
import com.dillon.starsectormarines.battle.story.BlindWatchProgress.Facts;
import com.dillon.starsectormarines.battle.story.BlindWatchProgress.Warning;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleLeg;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.i18n.Strings;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Blind Watch at the Sounding ({@code blind-watch-sounding.md}): three charges on three installations that are not
 * interchangeable, so the order they fall in is the company's plan. This adapter reads the battle into
 * {@link BlindWatchProgress}, which owns the rules, and carries out what they decide: the alarm, the uplink's warning to
 * Garnir, each charge's effect (the ranging mast stops spotting for Sable, the uplink falls silent), the riggers'
 * walk out and the field calls. Its three side objectives are toolkit jobs ({@link BlindWatchSideObjectives}).
 *
 * <p>The charges themselves are the Sabotage plant ({@code sabotage-command.md}): the three
 * {@link ChargeSiteObjective}s the setup installs from {@link #siteLayout}, read here by their stable ids. This
 * objective completes with all three and never fails, so a warned Garnir still ends in victory and only changes the
 * campaign's marks and the debrief. Defeat is the default rule.
 *
 * <p>Everything it reads of the map comes through {@link Sites}, so it runs on any grid that provides them.
 */
public final class BlindWatchSequence implements Objective {
    /** The stable charge-site ids; their world markers read S1, S2 and S3. */
    public static final String MAST_SITE = "SAB-01", UPLINK_SITE = "SAB-02", DISH_SITE = "SAB-03";
    /** How far the standing mast sees for Sable: a fort's radar ({@link FortSensors#RADAR_RANGE_CELLS}). */
    public static final float MAST_RANGE_CELLS = FortSensors.RADAR_RANGE_CELLS;
    /** A company combatant this close to a rigger has them on the move. UNTUNED first pass. */
    public static final float RIGGER_ESCORT_CELLS = 8f;
    /** Sight assumed for a sentry without a vision component. */
    static final float DEFAULT_SIGHT_CELLS = 18f;
    /** Radius, in cells, each revealed bunker and stretch of the carrier's track is held open. */
    static final int REVEAL_RADIUS_CELLS = 3;
    /**
     * The most track cells the mortar carrier is sent along at once. Its track is a loop, and a hull arrives on a route
     * whose last point is beside it, so it drives the loop a leg at a time, each ending as far along as this reaches.
     */
    static final int MORTAR_LEG_CELLS = 48;
    /** How often, in ticks, a walking rigger re-plans. */
    private static final int RIGGER_REPLAN_TICKS = 30;
    /** Beat ids are the narration catalog's events ({@code corvus.blind_watch.*}). */
    private static final String BEAT = "corvus.blind_watch.";
    /**
     * Grants side objectives before play ({@code riggers}, {@code schedule}, {@code coldstore}, comma separated, or
     * {@code all}): a standing
     * evidence lever, so a headless run can play the prepared approach the autonomous company never chooses.
     */
    public static final String SIDE_OBJECTIVES_PROPERTY = "battle.blindWatch.sideObjectives";

    /**
     * The named places the mission reads, in map cells. The map's layout maps onto this; nothing else of the map is read.
     *
     * @param landingArea   the landing ground behind the ridge, all berths: where the riggers are walked out to
     * @param mast          the ranging mast's plant cell (S1); the mast watches from here
     * @param uplink        the uplink's plant cell (S2)
     * @param dishField     the dish field's plant cell (S3)
     * @param riggersModule the riggers' module in the hab cluster: a company combatant inside frees them
     * @param riggerCells   walkable cells in or beside the module, one per rigger
     * @param controlHut    the dish field's control-hut console and the ground a squad leader holds it from
     * @param bunkers       the post's bunkers, revealed when the riggers are out
     * @param mortarTrack   the mortar carrier's driven track, revealed with the bunkers
     * @param podRacks      the cold drop's cryo pod racks by the lander pad, and the ground a squad holds them from
     */
    public record Sites(Area landingArea, Cell mast, Cell uplink, Cell dishField, Area riggersModule,
                        List<Cell> riggerCells, Area controlHut, List<Cell> bunkers, List<Cell> mortarTrack, Area podRacks) {
        public Sites {
            for (Object site : new Object[]{landingArea, mast, uplink, dishField, riggersModule, riggerCells, controlHut,
                    bunkers, mortarTrack, podRacks})
                if (site == null) throw new IllegalArgumentException("every Blind Watch site is required");
            riggerCells = List.copyOf(riggerCells);
            bunkers = List.copyOf(bunkers);
            mortarTrack = List.copyOf(mortarTrack);
            if (riggerCells.isEmpty()) throw new IllegalArgumentException("the riggers need somewhere to stand");
            if (Set.of(mast, uplink, dishField).size() != 3) throw new IllegalArgumentException("three distinct plant cells");
        }
    }

    /** The three charge sites in marker order: Mast (S1), Uplink (S2), Dish field (S3). */
    public static SabotageSiteLayout siteLayout(Sites sites) {
        return SabotageSiteLayout.authored(List.of(
                new SabotageSiteLayout.Site(MAST_SITE, PointOfInterest.Kind.COMMS, sites.mast().x(), sites.mast().y(),
                        "blindWatchHudSiteMast"),
                new SabotageSiteLayout.Site(UPLINK_SITE, PointOfInterest.Kind.COMMS, sites.uplink().x(), sites.uplink().y(),
                        "blindWatchHudSiteUplink"),
                new SabotageSiteLayout.Site(DISH_SITE, PointOfInterest.Kind.COMMS, sites.dishField().x(),
                        sites.dishField().y(), "blindWatchHudSiteDishes")));
    }

    /** Whether a shot or round from this side breaks the quiet: anything but a civilian's. */
    static boolean breaksQuiet(Faction shooter) {
        return shooter != null && shooter != Faction.CIVILIAN;
    }

    private final Sites sites;
    private final ChargeSiteObjective mast, uplink, dishes;
    private final StoryBeatLog log;
    private final BlindWatchProgress progress;
    private final SideObjective riggersJob, scheduleJob, coldStoreJob;
    private final List<Long> riggers = new ArrayList<>();
    private int riggerGoalPlannedTick = -1;
    private boolean begun, riggersFreed, riggersEscorted, revealed;
    private boolean mastDown, uplinkDown, dishesDown;
    private long mortar;
    /** Where on its track the mortar carrier's current leg ends, as an index into the track. */
    private int mortarAt;
    private DustLedgerTechnical technical;
    private BattleView field;

    public BlindWatchSequence(Sites sites, List<ChargeSiteObjective> charges, boolean stoodTo, StoryBeatLog log) {
        this(sites, charges, log, new BlindWatchProgress(BlindWatchProgress.WARNING_SECONDS, stoodTo));
    }

    BlindWatchSequence(Sites sites, List<ChargeSiteObjective> charges, StoryBeatLog log, BlindWatchProgress progress) {
        if (sites == null || charges == null || log == null || progress == null)
            throw new IllegalArgumentException("Blind Watch needs its sites, its charges and a beat log");
        this.sites = sites; this.log = log; this.progress = progress;
        mast = charge(charges, MAST_SITE);
        uplink = charge(charges, UPLINK_SITE);
        dishes = charge(charges, DISH_SITE);
        riggersJob = BlindWatchSideObjectives.riggers(sites, progress, this::reveal, this::leadRigger,
                () -> riggersFreed && riggersEscorted);
        scheduleJob = BlindWatchSideObjectives.schedule(sites, progress);
        coldStoreJob = BlindWatchSideObjectives.coldStore(sites, progress);
    }

    private static ChargeSiteObjective charge(List<ChargeSiteObjective> charges, String id) {
        for (ChargeSiteObjective c : charges) if (id.equals(c.siteId())) return c;
        throw new IllegalArgumentException("Blind Watch needs the charge site " + id);
    }

    /** Offers the three side objectives on the battle's service. Once, at setup. */
    void offer(SideObjectiveService service) {
        for (SideObjective job : List.of(riggersJob, scheduleJob, coldStoreJob)) service.install(job);
    }

    /** Grants the side objectives a control run names; unknown names fail loudly rather than play the wrong run. */
    void grantForced(String names, SideObjectiveService service, BattleSimulation sim) {
        for (String name : names.split(",")) {
            String key = name.trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty()) continue;
            List<String> jobs = switch (key) {
                case "all" -> BlindWatchSideObjectives.JOBS;
                case "riggers" -> List.of(BlindWatchSideObjectives.RIGGERS);
                case "schedule" -> List.of(BlindWatchSideObjectives.SCHEDULE);
                case "coldstore" -> List.of(BlindWatchSideObjectives.COLD_STORE);
                default -> throw new IllegalArgumentException(SIDE_OBJECTIVES_PROPERTY + ": unknown side objective " + name);
            };
            for (String job : jobs) if (!service.job(job).done()) service.grant(job, sim);
        }
    }

    /**
     * Stands the riggers in their module, offers the side jobs and puts the standing mast's eyes on Sable's side.
     * Once, at setup.
     */
    public void begin(BattleSimulation sim, List<EntitySpec> riggerSpecs) {
        if (begun) throw new IllegalStateException("Blind Watch begins once");
        if (riggerSpecs.isEmpty() || riggerSpecs.size() > sites.riggerCells().size())
            throw new IllegalArgumentException("one spawn cell per rigger, and at least one rigger");
        begun = true;
        field = sim;
        for (int i = 0; i < riggerSpecs.size(); i++) {
            Cell cell = sites.riggerCells().get(i);
            riggers.add(sim.spawn(riggerSpecs.get(i).atPosition(cell.x() + .5f, cell.y() + .5f).role(UnitRole.VIP)));
        }
        sim.setMissionSight((side, x, y) -> mastSees(sim.getGrid(), side, x, y));
        offer(sim.getSideObjectives());
        grantForced(System.getProperty(SIDE_OBJECTIVES_PROPERTY, ""), sim.getSideObjectives(), sim);
        if (progress.stoodTo()) beat(sim, "stood_to");
    }

    /** The riggers the post pressed into staying: civilians in suits, never fighters. */
    public static List<EntitySpec> riggerSpecs(int count) {
        List<EntitySpec> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            out.add(new EntitySpec(Strings.get("blindWatchHudRiggerName"), Faction.CIVILIAN, UnitType.CIVILIAN, 0, 0));
        return out;
    }

    /**
     * Sable's mortar carrier, recorded at setup so its loss is called. While it stands it drives
     * {@link Sites#mortarTrack()} round, start to end and again, firing on what its side observes as it goes.
     */
    public void mortar(long id) {
        if (mortar != 0L) throw new IllegalStateException("record the mortar carrier once");
        mortar = id;
    }

    /**
     * Sable's rocket truck on the uplink knoll, recorded at setup with its stands, its park first: from the alarm
     * it works the knoll's corners against the company's armor ({@link DustLedgerTechnical}).
     */
    public void technical(BattleSimulation sim, long id, List<Cell> stands) {
        if (technical != null) throw new IllegalStateException("record the technical once");
        technical = new DustLedgerTechnical(sim, id, stands);
    }

    /** The truck's hull id, or 0 when the mission fields none. */
    public long technical() { return technical == null ? 0L : technical.id(); }

    /**
     * What the outcome carries: whether the warning reached Garnir (the campaign keeps
     * {@link BlindWatchProgress#GARNIR_WARNED}), how far it got and how it ended, each side job's state as the battle
     * ended (an unfinished one reads as it stood, before settlement lapses it), and whether the post was stood-to.
     */
    public record Report(boolean garnirWarned, Warning warning, float warningSent, SideObjective.State riggers,
                         SideObjective.State schedule, SideObjective.State coldStore, boolean stoodTo) {
        public boolean riggersFreed() { return riggers == SideObjective.State.DONE; }
        public boolean falseSchedule() { return schedule == SideObjective.State.DONE; }
        public boolean manifestLogged() { return coldStore == SideObjective.State.DONE; }
    }

    public Report report() {
        return new Report(progress.garnirWarned(), progress.warning(), progress.sentShare(), riggersJob.state(),
                scheduleJob.state(), coldStoreJob.state(), progress.stoodTo());
    }

    public Sites sites() { return sites; }
    public BlindWatchProgress progress() { return progress; }
    public List<Long> riggers() { return List.copyOf(riggers); }
    public long mortar() { return mortar; }
    /** The mast is up while its charge has not blown: until then it spots for Sable. */
    public boolean mastUp() { return !mast.isComplete(); }
    public ChargeSiteObjective mastCharge() { return mast; }
    public ChargeSiteObjective uplinkCharge() { return uplink; }
    public ChargeSiteObjective dishCharge() { return dishes; }

    /**
     * Whether the standing mast sees the point for {@code side}: Sable's side only, inside its reach, along a line
     * no structure blocks, as a fort's radar does ({@link FortSensors#sees}). The mortar carrier fires on what this sees.
     */
    public boolean mastSees(NavigationGrid grid, Faction side, float x, float y) {
        return mastUp() && side != null && side.friendlyTo(Faction.DEFENDER) && mastSees(grid, x, y, MAST_RANGE_CELLS);
    }

    private boolean mastSees(NavigationGrid grid, float x, float y, float range) {
        Cell at = sites.mast();
        float dx = x - (at.x() + .5f), dy = y - (at.y() + .5f);
        if (dx * dx + dy * dy > range * range) return false;
        int tx = (int) Math.floor(x), ty = (int) Math.floor(y);
        return grid.inBounds(tx, ty) && FortSensors.lineOpen(grid, at.x(), at.y(), tx, ty, null);
    }

    public void advance(BattleSimulation sim) {
        if (!begun || sim.isComplete()) return;
        boolean wasQuiet = progress.quiet();
        Warning warningBefore = progress.warning();
        float sentBefore = progress.sentShare();
        boolean shot = wasQuiet && shotFired(sim);
        List<float[]> company = wasQuiet && !shot ? companyBodies(sim) : List.of();
        boolean sighted = !company.isEmpty() && sentrySighting(sim, company);
        boolean mastSighted = !company.isEmpty() && !sighted && mastUp() && mastSighting(sim, company);
        Facts facts = new Facts(shot, sighted, mastSighted, mast.isComplete(), uplink.isComplete(), dishes.isComplete());
        progress.advance(BattleSimulation.TICK_DT, facts);

        if (wasQuiet && !progress.quiet()) beat(sim, "alarm_" + progress.alarm().name().toLowerCase(Locale.ROOT));
        if (!mastDown && mast.isComplete()) { mastDown = true; beat(sim, "mast_down"); }
        if (!uplinkDown && uplink.isComplete()) { uplinkDown = true; beat(sim, "uplink_down"); }
        if (!dishesDown && dishes.isComplete()) { dishesDown = true; beat(sim, "dishes_down"); }
        Warning warning = progress.warning();
        if (warningBefore == Warning.WAITING && warning != Warning.WAITING && warning != Warning.STOPPED)
            beat(sim, "warning_started");
        if (sentBefore < BlindWatchProgress.WARNING_HALF && progress.sentShare() >= BlindWatchProgress.WARNING_HALF
                && warning != Warning.SENT) beat(sim, "warning_half");
        if (warningBefore != warning) {
            // A warning stopped before it started has nothing to cut off; the false schedule is its job's own line.
            if (warning == Warning.SENT) beat(sim, "warning_sent");
            else if (warning == Warning.STOPPED && warningBefore == Warning.SENDING) beat(sim, "warning_stopped");
        }
        if (mortar != 0L && !mortarPresent(sim)) beat(sim, "mortar_destroyed");
        else if (mortar != 0L) driveMortar(sim);
        if (technical != null && !progress.quiet()) technical.tick(sim);
        tendRiggers(sim);
    }

    /** Sends the carrier on along its track, a leg at a time, round again from the start once it is home. */
    private void driveMortar(BattleSimulation sim) {
        List<Cell> track = sites.mortarTrack();
        VehicleMission mission = sim.convoy().mission(mortar);
        if (track.size() < 2 || mission == null || mission.state != VehicleState.DEPLOYED
                || mission.authoredRouteX != null) return;
        int from = mortarAt, to = mortarLegEnd(track, from);
        mortarAt = to == track.size() - 1 ? 0 : to;
        if (to == from) return;
        float[] routeX = new float[to - from + 1], routeY = new float[to - from + 1];
        for (int i = from; i <= to; i++) {
            routeX[i - from] = track.get(i).x() + .5f;
            routeY[i - from] = track.get(i).y() + .5f;
        }
        mission.authoredRouteX = routeX;
        mission.authoredRouteY = routeY;
        mission.authoredLeg = VehicleLeg.MOVE_ORDER;
        mission.authoredRoutePaused = false;
    }

    /**
     * Where a leg from {@code from} ends: of the next {@link #MORTAR_LEG_CELLS} track cells, the one farthest from where
     * it starts, so the leg never ends where it began. Pure.
     */
    static int mortarLegEnd(List<Cell> track, int from) {
        int best = from;
        long farthest = 0;
        for (int i = from + 1; i < track.size() && i <= from + MORTAR_LEG_CELLS; i++) {
            long dx = track.get(i).x() - track.get(from).x(), dy = track.get(i).y() - track.get(from).y();
            if (dx * dx + dy * dy > farthest) { farthest = dx * dx + dy * dy; best = i; }
        }
        return best;
    }

    private static boolean shotFired(BattleSimulation sim) {
        for (ShotEvent shot : sim.getShotsThisFrame()) if (breaksQuiet(shot.shooterFaction)) return true;
        for (Projectile round : sim.getActiveProjectiles()) if (breaksQuiet(round.shooterFaction)) return true;
        return false;
    }

    /** Every company body on the ground, soldiers and hulls, as {@code {x, y}}. */
    private static List<float[]> companyBodies(BattleSimulation sim) {
        List<float[]> out = new ArrayList<>();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long id = sim.liveUnitAt(i);
            if (sim.identity().faction(id) != Faction.MARINE || !sim.world().hasPosition(id) || sim.isRiding(id)) continue;
            out.add(new float[]{sim.world().x(id), sim.world().y(id)});
        }
        ConvoyService convoy = sim.convoy();
        for (int i = 0, n = convoy.vehicleCount(); i < n; i++) {
            long hull = convoy.vehicleAt(i);
            if (convoy.faction(hull) != Faction.MARINE || !convoy.isPresent(hull)) continue;
            out.add(new float[]{convoy.body(hull).x, convoy.body(hull).y});
        }
        return out;
    }

    /** Whether a Sable combatant sees any company body, within its sight scaled for a stood-to post. */
    private boolean sentrySighting(BattleSimulation sim, List<float[]> company) {
        float scale = progress.sightScale();
        NavigationGrid grid = sim.getGrid();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long id = sim.liveUnitAt(i);
            if (sim.identity().faction(id) != Faction.DEFENDER || !sim.identity().type(id).combatant
                    || !sim.world().hasPosition(id) || sim.isRiding(id)) continue;
            int x = sim.world().cellX(id), y = sim.world().cellY(id);
            float sight = (sim.vision().has(id) ? sim.vision().visionRange(id) : DEFAULT_SIGHT_CELLS) * scale;
            for (float[] c : company)
                if (grid.hasLineOfSightWithin(x, y, (int) Math.floor(c[0]), (int) Math.floor(c[1]), sight)) return true;
        }
        return false;
    }

    private boolean mastSighting(BattleSimulation sim, List<float[]> company) {
        float range = MAST_RANGE_CELLS * progress.sightScale();
        for (float[] c : company) if (mastSees(sim.getGrid(), c[0], c[1], range)) return true;
        return false;
    }

    private boolean mortarPresent(BattleSimulation sim) {
        ConvoyService convoy = sim.convoy();
        return convoy.isVehicle(mortar) && convoy.isPresent(mortar);
    }

    /** The first rigger still on the field, or 0 once none is: the escort follows them. */
    private long leadRigger() {
        if (field == null) return 0L;
        for (long r : riggers) if (onField(field, r)) return r;
        return 0L;
    }

    /**
     * Freed by a company combatant reaching their module, the riggers make for the landing ground while one is near
     * them, and stand where they are otherwise. Out, they board and leave the field.
     */
    private void tendRiggers(BattleSimulation sim) {
        long lead = leadRigger();
        if (lead == 0L) return;
        if (riggersJob.state().isFinal()) {
            for (long r : riggers) {
                if (!onField(sim, r)) continue;
                if (riggersJob.done()) sim.takeOffTheField(r);
                else sim.clearPath(r);
            }
            return;
        }
        if (!riggersFreed && companyIn(sim, sites.riggersModule())) {
            riggersFreed = true;
            beat(sim, "riggers_found");
        }
        if (!riggersFreed) return;
        riggersEscorted = companyWithin(sim, sim.world().x(lead), sim.world().y(lead), RIGGER_ESCORT_CELLS);
        Cell goal = sites.landingArea().centre();
        int tick = sim.getSimTickIndex();
        boolean replan = tick - riggerGoalPlannedTick >= RIGGER_REPLAN_TICKS;
        for (long r : riggers) {
            if (!onField(sim, r)) continue;
            if (!riggersEscorted) { sim.clearPath(r); continue; }
            int x = sim.world().cellX(r), y = sim.world().cellY(r);
            int[] path = sim.movement().path(r);
            boolean spent = Paths.isEmpty(path) || sim.movement().pathIdx(r) >= Paths.cellCount(path);
            if ((spent || replan) && sim.movement().mayRepath(r) && sim.getGrid().inBounds(x, y))
                sim.setPath(r, sim.findPath(x, y, goal.x(), goal.y()));
            sim.advanceMovement(r);
        }
        if (replan) riggerGoalPlannedTick = tick;
    }

    /** Holds the bunkers and the carrier's track open in the player's view: what the riggers told. Once. */
    void reveal(BattleSimulation sim) {
        if (revealed || sim == null) return;
        revealed = true;
        FogOfWarService fog = sim.getFogOfWar();
        for (Cell bunker : sites.bunkers()) fog.addStandingSource(bunker.x(), bunker.y(), REVEAL_RADIUS_CELLS, 0f);
        Set<Cell> marked = new HashSet<>();
        // One source every few cells along the track covers it without one per cell.
        for (int i = 0; i < sites.mortarTrack().size(); i += REVEAL_RADIUS_CELLS) marked.add(sites.mortarTrack().get(i));
        if (!sites.mortarTrack().isEmpty()) marked.add(sites.mortarTrack().get(sites.mortarTrack().size() - 1));
        for (Cell c : marked) fog.addStandingSource(c.x(), c.y(), REVEAL_RADIUS_CELLS, 0f);
    }

    public boolean revealed() { return revealed; }

    private static boolean companyIn(BattleSimulation sim, Area area) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long id = sim.liveUnitAt(i);
            if (sim.identity().faction(id) != Faction.MARINE || !sim.identity().type(id).combatant
                    || !sim.world().hasPosition(id) || sim.isRiding(id)) continue;
            if (area.contains(sim.world().cellX(id), sim.world().cellY(id))) return true;
        }
        return false;
    }

    private static boolean companyWithin(BattleSimulation sim, float x, float y, float reach) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long id = sim.liveUnitAt(i);
            if (sim.identity().faction(id) != Faction.MARINE || !sim.identity().type(id).combatant
                    || !sim.world().hasPosition(id) || sim.isRiding(id)) continue;
            if (Math.hypot(sim.world().x(id) - x, sim.world().y(id) - y) <= reach) return true;
        }
        return false;
    }

    private static boolean onField(BattleView sim, long id) {
        return id != 0L && sim.resolveUnit(id) != 0L && sim.world().hasPosition(id) && sim.world().isAlive(id);
    }

    private void beat(BattleSimulation sim, String key) {
        log.emitOnce(sim.getSimTickIndex(), BEAT + key, StoryBeatLog.Audience.PLAYER);
    }

    @Override public Faction owningFaction() { return Faction.MARINE; }
    @Override public void tick(BattleView sim) { }
    /** Complete with the three charges: the warning and the side jobs change only the marks and the debrief. */
    @Override public boolean isComplete() { return mast.isComplete() && uplink.isComplete() && dishes.isComplete(); }
    @Override public boolean isFailed() { return false; }
    @Override public String displayName() { return Strings.get("blindWatchHudWarningName"); }
}

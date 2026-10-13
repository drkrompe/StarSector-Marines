package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.drone.DroneAnchor;
import com.dillon.starsectormarines.battle.drone.SortieFlight;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.VanillaData;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipSlot;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A drone tender: the vanilla Shepherd's hull as a mobile construction ship.
 *
 * <p>It is not a second ship model. The body is an ordinary {@link Warship} that
 * flies, hovers, casts a shadow and takes ordnance like any other overhead hull;
 * this class adds what makes it a tender and nothing else: a complement of drones
 * (half defend it, half build), an optional cap on build charges, and a line of
 * {@link ConstructionOrder}s. {@link TenderSystem} runs it.
 *
 * <p>The complement is the hull's own: the Shepherd's built-in {@code borer_wing}
 * names how many drones the bay holds. Every drone lost stays lost for the rest
 * of the battle. Building is free for now: the stock of build charges is an
 * optional cap that defaults to unlimited ({@link #UNLIMITED}), kept so a cost
 * can be added later by changing {@link #DEFAULT_STOCK} (or a mission's
 * {@link #stock(int)}) and nothing else.
 *
 * <p>It is a weak ship, pressed into the role: the hull's own hit points, armour
 * and omni shield from the hull table, on the hull's own flux, with no weapons
 * and no defensive buff beyond that. It is easy to kill by a determined raid.
 */
public final class Tender implements DroneAnchor {

    /** The hull a tender is fitted from. */
    public static final String HULL_ID = "shepherd";
    /** The wing the hull carries built in; its size is the tender's drone complement. */
    public static final String WING_ID = "borer_wing";
    /** Complement used when the game's wing table cannot be read. */
    static final int FALLBACK_COMPLEMENT = 6;
    /** The stock of a tender whose building is not metered. */
    public static final int UNLIMITED = Integer.MAX_VALUE;
    /**
     * Build charges a fresh tender carries. Building is free for now, so this is
     * {@link #UNLIMITED}; give it a number and every option's {@code cost} begins
     * to bite.
     */
    public static final int DEFAULT_STOCK = UNLIMITED;
    /** Spacing, in cells, two sites must keep. */
    public static final float SITE_SPACING = 2f;

    private final Warship ship;
    private boolean grounded;
    private boolean lowHover;
    /** Authored static-host footprint, independent of the metadata airframe's scale. */
    public record GroundDisplay(float centerX, float centerY, float width, float height) {
        public GroundDisplay {
            if (!Float.isFinite(centerX) || !Float.isFinite(centerY)
                    || !Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0)
                throw new IllegalArgumentException("Ground display needs a finite positive footprint");
        }
        public float enclosingRadius() { return (float) Math.hypot(width, height) * 0.5f; }
    }
    private GroundDisplay groundDisplay;
    public GroundDisplay groundDisplay() {
        return lowHover && groundDisplay != null
                ? new GroundDisplay(ship.x, ship.y, groundDisplay.width(), groundDisplay.height()) : groundDisplay;
    }
    public void groundDisplay(GroundDisplay display) {
        if (!grounded) throw new IllegalStateException("Only a grounded host has a static display footprint");
        groundDisplay = display;
    }
    private float groundBayX;
    private float groundBayY;
    private float groundMouthDegrees;
    private boolean groundHostAlive = true;

    /** A mission-owned grounded host; its metadata hull is never registered in the air layer. */
    public static Tender grounded(Faction faction, float x, float y, float bayX, float bayY,
                                  float mouthDegrees) throws Exception {
        Tender tender = fit(faction, x, y, mouthDegrees);
        tender.grounded = true;
        tender.groundBayX = bayX;
        tender.groundBayY = bayY;
        tender.groundMouthDegrees = mouthDegrees;
        tender.defenceOut(false);
        return tender;
    }

    public boolean grounded() { return grounded; }

    /** Live recovering hull: ordinary tender durability and drones, with a full low-hover footprint. */
    public static Tender lowHover(Faction faction, float x, float y, float width, float height) throws Exception {
        Tender tender = fit(faction, x, y, -90f);
        tender.lowHover = true;
        tender.groundDisplay = new GroundDisplay(x, y, width, height);
        tender.ship.lowHoverFootprint(width, height);
        return tender;
    }

    public boolean lowHover() { return lowHover && !ship.departing(); }

    /** Starts the ordinary physical climb-out; its completion witness excludes departure timeouts. */
    public void requestDeparture(float mapWidth, float mapHeight) {
        cancelAll();
        defenceOut(false);
        ship.departForward(mapWidth, mapHeight);
    }

    public boolean departed() { return ship.physicallyDeparted(); }
    public boolean disabled() { return ship.destroyed() || ship.departed() && !ship.physicallyDeparted(); }
    public List<ConstructionOrder> completedSites() { return Collections.unmodifiableList(completedSites); }

    /** Loss of a static host fails its construction and loses its deployed wing on the next tick. */
    public void loseGroundHost() {
        if (grounded) groundHostAlive = false;
    }

    /** Grounded construction uses a straight, clear sortie corridor from the exterior bay. */
    public boolean groundSiteReachable(float x, float y, NavigationGrid grid) {
        if (lowHover) return !ship.departing() && Math.hypot(x - ship.x, y - ship.y) <= TenderSystem.PILOT_REACH_CELLS
                && !(Math.abs(x - ship.x) < ship.lowHoverWidth() / 2f
                && Math.abs(y - ship.y) < ship.lowHoverHeight() / 2f);
        if (!grounded) return true;
        float dx = x - bayX();
        float dy = y - bayY();
        float distance = (float) Math.hypot(dx, dy);
        if (distance > TenderSystem.PILOT_REACH_CELLS) return false;
        float[] mouth = SortieFlight.direction(bayMouthDegrees());
        if (dx * mouth[0] + dy * mouth[1] < 3.5f) return false;
        int steps = Math.max(1, (int) Math.ceil(distance * 4));
        for (int i = 0; i <= steps; i++) {
            int cx = (int) Math.floor(bayX() + dx * i / steps);
            int cy = (int) Math.floor(bayY() + dy * i / steps);
            if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) return false;
        }
        // Every builder's work ring must stay clear of the grounded hull as well.
        for (int cx = (int) Math.floor(x - TenderSystem.WORK_RADIUS); cx <= x + TenderSystem.WORK_RADIUS; cx++) {
            for (int cy = (int) Math.floor(y - TenderSystem.WORK_RADIUS); cy <= y + TenderSystem.WORK_RADIUS; cy++) {
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) return false;
            }
        }
        return true;
    }
    private final List<WarshipSlot> bays;
    private final int builderComplement;
    private final int defenceComplement;
    private final List<ConstructionOrder> orders = new ArrayList<>();
    private final List<ConstructionOrder> completedSites = new ArrayList<>();
    final List<Long> builders = new ArrayList<>();
    final List<Long> defenders = new ArrayList<>();
    private int stock = DEFAULT_STOCK;
    private int builderReserve;
    private int defenceReserve;
    private boolean defenceOut = true;
    private int built;
    private int defenceLost;
    private int builderLost;
    private final TenderPointDefence pointDefence = new TenderPointDefence();
    int defenceSquadId = -1;
    float launchClock;
    float builderClock;
    float directorClock;
    /** Builder hit points last tick, to tell when one is shot. */
    final Map<Long, Float> builderHp = new HashMap<>();
    /** Seconds the current site stays blocked after a builder was hit. */
    float blockedSeconds;
    private int buildersLostSeen;
    final TenderFlight flight = new TenderFlight();
    private boolean helmed;
    private List<ConstructionOrder> ordersBeforeHelm = List.of();
    private boolean defenceOutBeforeHelm = true;

    private Tender(Warship ship, List<WarshipSlot> bays, int complement) {
        this.ship = ship;
        this.bays = bays;
        this.builderComplement = complement / 2;
        this.defenceComplement = complement - builderComplement;
        this.builderReserve = builderComplement;
        this.defenceReserve = defenceComplement;
    }

    /**
     * Fits the Shepherd over {@code (x, y)} pointing {@code facingDegrees}. The
     * hull, its launch bays and its complement are read through
     * {@link VanillaData}, so a mod's re-tuned Shepherd is a re-tuned tender.
     */
    public static Tender fit(Faction faction, float x, float y, float facingDegrees) throws Exception {
        return fit(TenderLoadout.stock(), faction, x, y, facingDegrees);
    }

    /**
     * Fits a campaign Shepherd as a tender, the way {@code Warship.fit(FleetMemberAPI, ...)}
     * fits a campaign warship: the hull is the member's own. A member that is
     * absent, or whose hull is not a Shepherd, falls back to the stock Shepherd.
     */
    public static Tender fit(FleetMemberAPI member, Faction faction, float x, float y, float facingDegrees)
            throws Exception {
        return fit(TenderLoadout.of(member), faction, x, y, facingDegrees);
    }

    private static Tender fit(TenderLoadout loadout, Faction faction, float x, float y, float facingDegrees)
            throws Exception {
        Warship ship = Warship.fit(loadout, faction, x, y, facingDegrees);
        // The shield runs on the hull's own flux at the hull table's own figures; with no
        // guns, the only thing that ever puts flux on it is a hit the shield absorbs.
        ship.limitFlux();
        List<WarshipSlot> bays = new ArrayList<>();
        for (WarshipSlot slot : WarshipSlot.parse(VanillaData.json("data/hulls/" + HULL_ID + ".ship"))) {
            if ("LAUNCH_BAY".equals(slot.type())) bays.add(slot);
        }
        return new Tender(ship, bays, complement());
    }

    /** The built-in wing's size from the wing table, or {@link #FALLBACK_COMPLEMENT}. */
    static int complement() {
        try {
            for (Map<String, String> row : VanillaData.csv("data/hulls/wing_data.csv")) {
                if (!WING_ID.equals(row.get("id"))) continue;
                int n = (int) Float.parseFloat(row.get("num").trim());
                return Math.max(2, n);
            }
        } catch (Exception e) {
            // A wing table that cannot be read leaves the hull its usual six.
        }
        return FALLBACK_COMPLEMENT;
    }

    /** The hull. It is also registered with the simulation's warships, which fly it. */
    public Warship ship() {
        return ship;
    }

    public Faction faction() {
        return ship.faction;
    }

    @Override public float anchorX() {
        return ship.x;
    }

    @Override public float anchorY() {
        return ship.y;
    }

    @Override public boolean anchorAlive() {
        return grounded ? groundHostAlive : !ship.destroyed() && !ship.departed();
    }

    @Override public float anchorHeadingDegrees() {
        return ship.facingDegrees;
    }

    @Override public boolean escort() {
        return true;
    }

    @Override public float leashCells() {
        return TenderFlight.LEASH_CELLS;
    }

    @Override public boolean threatened() {
        return flight.threatened();
    }

    @Override public long focusTarget() {
        return flight.focus();
    }

    @Override public long[] escortIds() {
        return flight.escortIds();
    }

    @Override public float[] escortPositions() {
        return flight.escortPositions();
    }

    @Override public float[] sortieGoal(long member) {
        return flight.goalOf(member);
    }

    /** The bearing the launch bay opens on: the hull's heading turned by the bay's own angle. */
    public float bayMouthDegrees() {
        if (grounded) return groundMouthDegrees;
        return ship.facingDegrees + (bays.isEmpty() ? 0f : bays.get(0).angleDegrees());
    }

    /**
     * Whether builder {@code id} is at its station with its torch on the site: the
     * order is being worked, the ground is clear, and the drone is on task close
     * to the site. For presentation and evidence.
     */
    public boolean welding(long id, ConstructionOrder order, float x, float y) {
        TenderFlight.Sortie s = flight.of(id);
        return s != null && s.leg == TenderFlight.Leg.TASK && order != null
                && order.phase() == ConstructionOrder.Phase.WORKING && !order.contested()
                && Math.hypot(x - order.siteX(), y - order.siteY()) <= TenderSystem.WORK_RADIUS;
    }

    /** The defence drones' point defence: their mining lasers against hostile ordnance. */
    public TenderPointDefence pointDefence() {
        return pointDefence;
    }

    /** World x of the launch bay drones leave and return through; the hull's centre when it lists none. */
    public float bayX() {
        return grounded ? groundBayX : bays.isEmpty() ? ship.x : ship.slotX(bays.get(0));
    }

    /** World y of the launch bay. */
    public float bayY() {
        return grounded ? groundBayY : bays.isEmpty() ? ship.y : ship.slotY(bays.get(0));
    }

    /** Whether building is metered: false while the stock is {@link #UNLIMITED}, which is the default. */
    public boolean metered() {
        return stock != UNLIMITED;
    }

    /** Build charges left, or {@link #UNLIMITED}. A laid order on a metered tender has already drawn its charge. */
    public int stock() {
        return stock;
    }

    /** Sets a finite stock, for a mission that sends a metered tender; {@link #UNLIMITED} lifts the cap. */
    public void stock(int charges) {
        this.stock = charges == UNLIMITED ? UNLIMITED : Math.max(0, charges);
    }

    public int builderComplement() {
        return builderComplement;
    }

    public int defenceComplement() {
        return defenceComplement;
    }

    /** Builders still in the bay. */
    public int builderReserve() {
        return builderReserve;
    }

    /** Defence drones still in the bay. */
    public int defenceReserve() {
        return defenceReserve;
    }

    /** Builders and defenders that have been shot down. */
    public int dronesLost() {
        return builderLost + defenceLost;
    }

    /**
     * Whether a builder was hit (or shot down) within {@link TenderThreat#BLOCK_SECONDS}: the site
     * is blocked and its builders are home.
     */
    public boolean underFire() {
        return blockedSeconds > 0f;
    }

    /** Whether a builder has been shot down since the last call. */
    boolean builderLostSinceLastLook() {
        boolean lost = builderLost > buildersLostSeen;
        buildersLostSeen = builderLost;
        return lost;
    }

    /** Builders shot down. */
    public int buildersLost() {
        return builderLost;
    }

    /** Defence drones shot down. */
    public int defendersLost() {
        return defenceLost;
    }

    /** Ids of the builders out of the bay, in launch order, for presentation and evidence. */
    public List<Long> builderIds() {
        return Collections.unmodifiableList(builders);
    }

    public int defendersOut() {
        return defenders.size();
    }

    public int buildersOut() {
        return builders.size();
    }

    /** Sites finished. */
    public int built() {
        return built;
    }

    /** Whether the defence drones are wanted out; false recalls them into the bay. */
    public boolean defenceOut() {
        return defenceOut;
    }

    public void defenceOut(boolean out) {
        this.defenceOut = out;
    }

    /** Whether a pilot has the helm. A piloted tender stays where it is flown and does not station itself at a site. */
    public boolean piloted() {
        return helmed;
    }

    /**
     * Hands the tender to a pilot: the hull's own helm is taken and what the defence drones were
     * told is remembered. The hull's autonomy is given back by {@link #releaseHelm}.
     */
    public void takeHelm() {
        if (grounded || helmed) return;
        helmed = true;
        ordersBeforeHelm = new ArrayList<>(orders);
        defenceOutBeforeHelm = defenceOut;
        ship.takeHelm();
    }

    /**
     * Gives the tender back as the pilot found it: every build the pilot laid is withdrawn (a
     * hand order does not outlive the hand), the defence drones go back to what they were doing,
     * and the hull returns to its station or route.
     */
    public void releaseHelm() {
        if (!helmed) return;
        helmed = false;
        for (ConstructionOrder order : orders) if (!ordersBeforeHelm.contains(order)) cancel(order);
        ordersBeforeHelm = List.of();
        defenceOut = defenceOutBeforeHelm;
        ship.releaseHelm();
    }

    /** The orders still live, in the order they will be worked. */
    public List<ConstructionOrder> orders() {
        return Collections.unmodifiableList(orders);
    }

    /** Completed grounded structures keep their plot reserved for the mission. */
    public boolean groundPlotReserved(float x, float y) {
        if (!grounded && !lowHover) return false;
        for (ConstructionOrder order : orders) {
            if (order.phase() == ConstructionOrder.Phase.COMPLETE
                    && Math.hypot(order.siteX() - x, order.siteY() - y) < SITE_SPACING) return true;
        }
        for (ConstructionOrder order : completedSites) {
            if (Math.hypot(order.siteX() - x, order.siteY() - y) < SITE_SPACING) return true;
        }
        return false;
    }

    /** The order being worked, or {@code null} when the line is empty. */
    public ConstructionOrder current() {
        return orders.isEmpty() ? null : orders.get(0);
    }

    /**
     * Lays a site: joins the line, drawing the option's charge when the tender is
     * metered. Refused (null) when a metered stock cannot pay, the cell is not open ground, or another site is
     * already there. Whether the player may see the ground is the caller's rule.
     */
    public ConstructionOrder lay(BuildOption option, float x, float y, NavigationGrid grid) {
        if (!anchorAlive() || ship.departing() || option == null || (metered() && option.cost() > stock)) return null;
        int cx = (int) Math.floor(x);
        int cy = (int) Math.floor(y);
        if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) return null;
        if (!groundSiteReachable(cx + 0.5f, cy + 0.5f, grid)) return null;
        if (groundPlotReserved(cx + 0.5f, cy + 0.5f)) return null;
        for (ConstructionOrder other : orders) {
            if (other.phase().terminal()) continue;
            if (Math.hypot(other.siteX() - (cx + 0.5f), other.siteY() - (cy + 0.5f)) < SITE_SPACING) return null;
        }
        if (metered()) stock -= option.cost();
        ConstructionOrder order = new ConstructionOrder(option, cx + 0.5f, cy + 0.5f);
        orders.add(order);
        return order;
    }

    /** Withdraws every order that has not finished; their charges come back. */
    public void cancelAll() {
        for (ConstructionOrder order : orders) retire(order, null);
    }

    /** Withdraws one order; its charge comes back. */
    public void cancel(ConstructionOrder order) {
        retire(order, null);
    }

    /** Ends {@code order} as cancelled, or failed with {@code failure}, and returns its charge. */
    void retire(ConstructionOrder order, String failure) {
        if (order.phase().terminal()) return;
        if (failure == null) order.cancel();
        else order.fail(failure);
        if (metered()) stock += order.option().cost();
    }

    /** Takes terminal orders off the line. */
    void sweep() {
        for (int i = orders.size() - 1; i >= 0; i--) {
            ConstructionOrder order = orders.get(i);
            if (!order.phase().terminal()) continue;
            if (order.phase() == ConstructionOrder.Phase.COMPLETE) {
                built++;
                if (grounded || lowHover) completedSites.add(order);
            }
            orders.remove(i);
        }
    }

    void launchedBuilder(long id) {
        builders.add(id);
        builderReserve--;
    }

    void launchedDefender(long id) {
        defenders.add(id);
        defenceReserve--;
    }

    void dockedBuilder(long id) {
        flight.forget(id);
        builders.remove(id);
        builderReserve++;
    }

    void dockedDefender(long id) {
        flight.forget(id);
        defenders.remove(id);
        defenceReserve++;
    }

    void lostBuilder(long id) {
        flight.forget(id);
        if (builders.remove(id)) builderLost++;
    }

    void lostDefender(long id) {
        flight.forget(id);
        if (defenders.remove(id)) defenceLost++;
    }

    /** One line for the debug readout. */
    public String statusLine() {
        ConstructionOrder o = current();
        String work = o == null ? "idle" : String.format(Locale.ROOT, "%s %s %d%%%s",
                o.option().label(), o.phase(), Math.round(o.fraction() * 100f), o.underFire() ? " UNDER FIRE" : o.contested() ? " CONTESTED" : "");
        return String.format(Locale.ROOT,
                "TENDER %s  %s  hull %d%%  shield %s  %s  builders %d/%d  defence %d/%d%s  lost %d  built %d  %s",
                ship.faction == Faction.MARINE ? "[friendly]" : "[hostile]", ship.stance(),
                Math.round(ship.hullFraction() * 100f), ship.shieldState(),
                metered() ? "charges " + stock : "builds free",
                builders.size() + builderReserve, builderComplement,
                defenders.size() + defenceReserve, defenceComplement, defenceOut ? "" : " (recalled)",
                dronesLost(), built, work);
    }
}

package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.MissionStance;
import com.dillon.starsectormarines.battle.warship.ShipStationing;
import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Calls a committed ship over the battle: it comes in from the company's own map edge,
 * flies to a station over the targeted cell, works that ground for a bounded time under
 * the company's {@link com.dillon.starsectormarines.battle.warship.MissionStance}, and
 * departs.
 *
 * <p>One mechanism for every source. The power holds the frozen {@link ShipSupportSource}
 * list (a committed fleet member, a debug stock hull, an employer's offered hull) and
 * nothing from the campaign; each source is one charge, spent in order, so two committed
 * capitals are two activations rather than one doubled. A tender is added through the
 * tender system and builds by its stance director.
 */
public final class ShipOnStation extends CommandPower {

    private static final Logger LOG = Global.getLogger(ShipOnStation.class);

    public static final String ID = "ship_on_station";
    /** Employer offers name a stock hull after this prefix: {@code ship_on_station:onslaught}. */
    public static final String EMPLOYER_PREFIX = ID + ":";
    /** Time on station for every hull, counted from arrival; a bounded visit, then it departs. */
    public static final float STATION_SECONDS = 90f;
    /** Ground the director works around, in cells; also the targeting preview ring. */
    public static final float AREA_RADIUS_CELLS = 10f;
    public static final float CP_COST = 4f;
    public static final float COOLDOWN_SECONDS = 20f;
    public static final int SUPPLY_COST = 8;
    public static final int SLOT_WEIGHT = 3;

    private final List<ShipSupportSource> sources;

    public ShipOnStation(List<ShipSupportSource> sources) {
        super(ID, "Ship on Station", CP_COST, COOLDOWN_SECONDS, requireSources(sources).size(),
                SUPPLY_COST, SLOT_WEIGHT);
        this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
    }

    private static List<ShipSupportSource> requireSources(List<ShipSupportSource> sources) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("Ship on Station needs at least one source ship");
        }
        return sources;
    }

    /** The frozen ships this power can call, in the order they are called. */
    public List<ShipSupportSource> sources() { return sources; }

    /** The ship the next activation fits, given the charges left; the last one once all are spent. */
    public ShipSupportSource sourceFor(int chargesRemaining) {
        int used = maxCharges - Math.max(0, Math.min(maxCharges, chargesRemaining));
        return sources.get(Math.min(used, sources.size() - 1));
    }

    @Override
    public String cardLabel(int chargesRemaining) {
        return sourceFor(chargesRemaining).shipName() + " " + Math.max(0, chargesRemaining) + "/" + maxCharges;
    }

    @Override public float previewRadiusCells() { return AREA_RADIUS_CELLS; }

    @Override
    public boolean canTarget(int cellX, int cellY, BattleView battle) {
        return super.canTarget(cellX, cellY, battle) && battle.playerSeesCell(cellX, cellY);
    }

    @Override
    public void resolve(int cellX, int cellY, CommandPowerService service, BattleControl battle) {
        // The charge is already spent, so the ship fitted is the one that charge stood for.
        ShipSupportSource source = sourceFor(service.getChargesRemaining(ID) + 1);
        try {
            call(source, cellX + 0.5f, cellY + 0.5f, battle);
        } catch (Exception e) {
            LOG.warn("ShipOnStation: " + source.shipName() + " (" + source.hullId() + ") could not be fitted", e);
        }
    }

    /** Fits, places and orders one source over the battle for the company. Shared by the power and the scenes. */
    public static Warship call(ShipSupportSource source, float stationX, float stationY,
                               BattleControl battle) throws Exception {
        return call(source, Faction.MARINE, stationX, stationY, battle);
    }

    /**
     * The one call path for either side. The company's ship takes the company's mission stance;
     * the defender's ship always holds ground ({@link MissionStance#DEFEND}), descending on the
     * defender's side of the station.
     */
    public static Warship call(ShipSupportSource source, Faction side, float stationX, float stationY,
                               BattleControl battle) throws Exception {
        int w = battle.getGrid().getWidth();
        int h = battle.getGrid().getHeight();
        Tender tender = null;
        Warship ship;
        ShipStationing.Placement p;
        if (source.tender()) {
            Tender probe = Tender.fit(side, 0f, 0f, 0f);
            p = ShipStationing.callIn(side, battle.shipAxis(), w, h, probe.ship().lengthCells, stationX, stationY);
            tender = Tender.fit(side, p.x(), p.y(), p.facingDegrees());
            ship = tender.ship();
        } else {
            Warship probe = Warship.fit(source, side, 0f, 0f, 0f);
            p = ShipStationing.callIn(side, battle.shipAxis(), w, h, probe.lengthCells, stationX, stationY);
            ship = Warship.fit(source, side, p.x(), p.y(), p.facingDegrees());
        }
        ship.stationTo(stationX, stationY);
        ship.arriveFromAbove();
        ship.stance(side == Faction.DEFENDER ? MissionStance.DEFEND : battle.companyStance());
        ship.stayFor(STATION_SECONDS);
        if (tender != null) {
            battle.addTender(tender);
        } else {
            ship.direct(AREA_RADIUS_CELLS, true);
            ship.useDoctrine();
            ship.fluxScale(Warship.BRAWLER_FLUX_SCALE);
            ship.limitFlux();
            battle.addWarship(ship);
        }
        return ship;
    }
}

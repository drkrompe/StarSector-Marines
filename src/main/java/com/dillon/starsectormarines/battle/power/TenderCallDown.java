package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.tender.TenderLanding;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.ShipStationing;
import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Calls a committed tender down to a patch of ground the player picks. The hull comes in from the company's own
 * edge through the same call-in path as {@link ShipOnStation}, lands and grounds there, and the existing
 * {@link YardConstruction} is registered for it, so the player builds Vulcans beside the site the way they would
 * from a tender a mission grounded at setup.
 *
 * <p>Offered only when the fleet brought a tender: the power exists because its frozen sources do, one per charge.
 * Mission-agnostic; the generic {@value TenderLanding#BEAT} beat is the only thing it announces.
 */
public final class TenderCallDown extends CommandPower {

    private static final Logger LOG = Global.getLogger(TenderCallDown.class);

    public static final String ID = "tender_call_down";
    public static final float CP_COST = 2f;
    public static final float COOLDOWN_SECONDS = 20f;
    public static final int SUPPLY_COST = 4;
    public static final int SLOT_WEIGHT = 2;

    private final List<ShipSupportSource> sources;
    /** The tender's landed length and width in cells, measured on first use. */
    private float[] hull;

    public TenderCallDown(List<ShipSupportSource> sources) {
        super(ID, "Land Tender", CP_COST, COOLDOWN_SECONDS, requireSources(sources).size(), SUPPLY_COST, SLOT_WEIGHT);
        this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
    }

    private static List<ShipSupportSource> requireSources(List<ShipSupportSource> sources) {
        if (sources == null || sources.isEmpty()) throw new IllegalArgumentException("Land Tender needs a tender");
        for (ShipSupportSource source : sources) {
            if (source == null || !source.tender()) throw new IllegalArgumentException("Land Tender calls tenders only");
        }
        return sources;
    }

    /** The frozen tenders this power can land, in the order they are called. */
    public List<ShipSupportSource> sources() { return sources; }

    public ShipSupportSource sourceFor(int chargesRemaining) {
        int used = maxCharges - Math.max(0, Math.min(maxCharges, chargesRemaining));
        return sources.get(Math.min(used, sources.size() - 1));
    }

    @Override public String cardLabel(int chargesRemaining) {
        return "Land " + sourceFor(chargesRemaining).shipName() + " " + Math.max(0, chargesRemaining) + "/" + maxCharges;
    }

    @Override
    public boolean canTarget(int cellX, int cellY, BattleView battle) {
        if (!super.canTarget(cellX, cellY, battle) || !battle.playerSeesCell(cellX, cellY)) return false;
        float[] dims = hullSize();
        if (dims == null) return false;
        float siteX = cellX + 0.5f;
        float siteY = cellY + 0.5f;
        var grid = battle.getGrid();
        var in = ShipStationing.callIn(Faction.MARINE, battle.shipAxis(), grid.getWidth(), grid.getHeight(),
                dims[2], siteX, siteY);
        return TenderLanding.clear(TenderLanding.footprint(siteX, siteY, in.facingDegrees(), dims[0], dims[1]), grid);
    }

    @Override
    public void resolve(int cellX, int cellY, CommandPowerService service, BattleControl battle) {
        ShipSupportSource source = sourceFor(service.getChargesRemaining(ID) + 1);
        try {
            float siteX = cellX + 0.5f;
            float siteY = cellY + 0.5f;
            Tender probe = Tender.fit(Faction.MARINE, 0f, 0f, 0f);
            ShipStationing.Placement p = ShipStationing.callIn(Faction.MARINE, battle.shipAxis(),
                    battle.getGrid().getWidth(), battle.getGrid().getHeight(), probe.ship().lengthCells, siteX, siteY);
            Tender tender = Tender.fit(Faction.MARINE, p.x(), p.y(), p.facingDegrees());
            Warship ship = tender.ship();
            ship.calledFrom(source);
            ship.stationTo(siteX, siteY);
            ship.arriveFromAbove(siteX, siteY);
            battle.landTender(tender, siteX, siteY);
        } catch (Exception e) {
            LOG.warn("TenderCallDown: " + source.shipName() + " could not be fitted", e);
        }
    }

    /** {landed length, landed width, flight length}, or null when the hull cannot be fitted. */
    private float[] hullSize() {
        if (hull == null) {
            try {
                Warship ship = Tender.fit(Faction.MARINE, 0f, 0f, 0f).ship();
                hull = new float[] {TenderLanding.landedLength(ship), TenderLanding.landedWidth(ship), ship.lengthCells};
            } catch (Exception e) {
                LOG.warn("TenderCallDown: the tender hull could not be measured", e);
            }
        }
        return hull;
    }
}

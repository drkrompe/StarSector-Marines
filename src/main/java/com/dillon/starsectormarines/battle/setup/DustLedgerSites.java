package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Area;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Cell;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Sites;
import com.dillon.starsectormarines.battle.world.gen.DustLedgerCrossing;
import com.dillon.starsectormarines.battle.world.gen.DustLedgerCrossing.Layout;
import com.dillon.starsectormarines.battle.world.gen.DustLedgerCrossing.PostRole;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reads the mission's {@link Sites} off Saltwell Cross's authored {@link Layout}; the map owns where, the sequence what. */
public final class DustLedgerSites {
    private DustLedgerSites() {}

    public static Sites from(Layout layout) {
        return new Sites(area(layout.landing()), cell(layout.couplingCrate()), area(layout.ledgerRoom()),
                cell(layout.burnBarrel()), cell(layout.brokerOffice()), cell(layout.skimmerBoarding()),
                area(layout.exclusionRing()), area(layout.compound()), cells(layout.spotterPosts()),
                area(layout.cantina()), cells(layout.posts().get(PostRole.CANTINA)), area(layout.wellHouseArea()),
                area(layout.harrowYard()), cells(layout.salvageTeam()), cell(layout.gate()),
                area(layout.feedJunctionArea()), cells(layout.reinforcementCells()));
    }

    static Cell cell(DustLedgerCrossing.Cell cell) { return new Cell(cell.x(), cell.y()); }

    static List<Cell> cells(List<DustLedgerCrossing.Cell> cells) {
        List<Cell> out = new ArrayList<>(cells.size());
        for (DustLedgerCrossing.Cell cell : cells) out.add(cell(cell));
        return out;
    }

    static Area area(DustLedgerCrossing.Area area) {
        Set<Cell> out = new HashSet<>();
        for (DustLedgerCrossing.Cell cell : area.cells()) out.add(cell(cell));
        return new Area(out);
    }

    /** A building's whole footprint, walls included. */
    static Area area(PointOfInterest building) {
        Set<Cell> out = new HashSet<>();
        int bottom = Math.min(building.top, building.bottom), top = Math.max(building.top, building.bottom);
        for (int x = building.left; x <= building.right; x++)
            for (int y = bottom; y <= top; y++) out.add(new Cell(x, y));
        return new Area(out);
    }
}

package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.drone.SortieFlight;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.power.YardConstruction;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.warship.LandedScale;
import com.dillon.starsectormarines.battle.warship.Warship;

/**
 * A flown tender on its way to a chosen patch of ground, and what happens when it gets there: the hull is
 * berthed at landed scale, the tender sets down as a grounded host, and the existing {@link YardConstruction}
 * is registered for it so the player lays sites exactly as with a tender a mission grounded at setup.
 *
 * <p>Mission-agnostic. The only thing it tells the rest of the battle is the generic
 * {@value #BEAT} story beat, which a mission's own sequence may re-emit under its own name.
 */
public final class TenderLanding {

    /** The generic story beat for a called-down tender that has set down. */
    public static final String BEAT = "tender_landed";
    /** Cells of clear ground wanted past the landed hull on every side. */
    static final float MARGIN_CELLS = 1f;
    /** How far past the hull's nose the bay opens. */
    static final float BAY_GAP_CELLS = 1.5f;
    /** Within this many cells of the site, and slower than {@link #ARRIVE_SPEED}, the hull is down. */
    static final float ARRIVE_CELLS = 2f;
    static final float ARRIVE_SPEED = 1.5f;
    /** A hull that has not arrived by now is set down where it stands rather than hang over the battle. */
    static final float LIMIT_SECONDS = 150f;

    /** The ground a landed hull takes and the bay it opens, in cells. */
    public record Footprint(float centreX, float centreY, float halfX, float halfY, float bayX, float bayY) {}

    /** The ground a hull {@code length} by {@code width} cells (landed scale), nose on {@code facingDegrees}, takes at the site. */
    public static Footprint footprint(float siteX, float siteY, float facingDegrees, float length, float width) {
        float[] nose = SortieFlight.direction(facingDegrees);
        float halfX = Math.abs(nose[0]) * length / 2f + Math.abs(nose[1]) * width / 2f;
        float halfY = Math.abs(nose[1]) * length / 2f + Math.abs(nose[0]) * width / 2f;
        float reach = length / 2f + BAY_GAP_CELLS;
        return new Footprint(siteX, siteY, halfX, halfY, siteX + nose[0] * reach, siteY + nose[1] * reach);
    }

    /** Whether every cell under the footprint (and a margin round it), and the bay's cell, is open ground. */
    public static boolean clear(Footprint f, NavigationGrid grid) {
        int x0 = (int) Math.floor(f.centreX() - f.halfX() - MARGIN_CELLS);
        int x1 = (int) Math.floor(f.centreX() + f.halfX() + MARGIN_CELLS);
        int y0 = (int) Math.floor(f.centreY() - f.halfY() - MARGIN_CELLS);
        int y1 = (int) Math.floor(f.centreY() + f.halfY() + MARGIN_CELLS);
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) {
            if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) return false;
        }
        int bx = (int) Math.floor(f.bayX());
        int by = (int) Math.floor(f.bayY());
        return grid.inBounds(bx, by) && grid.isWalkable(bx, by);
    }

    /** The hull's length when landed, in cells. */
    public static float landedLength(Warship ship) { return ship.lengthCells * LandedScale.berthFactor(); }

    /** The hull's width when landed, in cells. */
    public static float landedWidth(Warship ship) { return ship.widthCells * LandedScale.berthFactor(); }

    private final Tender tender;
    private final float siteX;
    private final float siteY;
    private float seconds;
    private boolean down;

    public TenderLanding(Tender tender, float siteX, float siteY) {
        this.tender = tender;
        this.siteX = siteX;
        this.siteY = siteY;
    }

    public Tender tender() { return tender; }

    public boolean down() { return down; }

    /** Advances the approach; returns true once the tender has set down (or been lost on the way). */
    boolean tick(float dt, BattleSimulation sim) {
        Warship ship = tender.ship();
        if (ship.destroyed() || ship.departed()) return true;
        seconds += dt;
        boolean arrived = Math.hypot(ship.x - siteX, ship.y - siteY) <= ARRIVE_CELLS && ship.speed() <= ARRIVE_SPEED;
        if (!arrived && seconds < LIMIT_SECONDS) return false;
        ship.berth(siteX, siteY);
        Footprint f = footprint(siteX, siteY, ship.facingDegrees, landedLength(ship), landedWidth(ship));
        tender.touchDown(f.bayX(), f.bayY(), ship.facingDegrees,
                new Tender.GroundDisplay(siteX, siteY, 2f * f.halfX(), 2f * f.halfY()));
        sim.tenders().track(tender);
        sim.getCommandPowerService().register(new YardConstruction(tender));
        sim.emitStoryBeat(BEAT);
        down = true;
        return true;
    }
}

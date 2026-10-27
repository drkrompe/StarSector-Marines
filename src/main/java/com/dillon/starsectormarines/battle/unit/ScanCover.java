package com.dillon.starsectormarines.battle.unit;

/**
 * Who a hostile scan may not find this tick, for a mission whose sides are not yet fighting: the Dust Ledger's quiet
 * phase, when Sable does not treat the company as hostile outside the exclusion ring and the company holds its
 * own fire. A covered body is still seen, hit and gathered; it is only left out of the search for something to shoot,
 * as a restrained fort core is ({@link com.dillon.starsectormarines.battle.fort.FortRestraint}).
 *
 * <p>Asked once per unit at each index rebuild, from the unit's faction and position. {@link #NONE} covers nobody
 * and is what every battle without a quiet phase carries.
 */
@FunctionalInterface
public interface ScanCover {
    ScanCover NONE = (faction, x, y) -> false;

    boolean covers(Faction faction, float x, float y);
}

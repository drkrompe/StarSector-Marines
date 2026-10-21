package com.dillon.starsectormarines.battle.creep;

import java.util.List;

/**
 * What the creep field holds at one tick: living creeped cells, cells dying
 * back, and each living source's reach and claim.
 *
 * @param coveredCells cells holding living creep (coverage and vitality at least {@link CreepField#CREEPED})
 * @param dyingCells   cells still covered whose growth has died
 */
public record CreepSnapshot(int tick, int coveredCells, int dyingCells, List<SourceView> sources) {

    /**
     * One living source.
     *
     * @param reachCells    the path-cost reach it has grown to, in cells
     * @param claimedCells  cells it has reached, walls included
     * @param fed           whether a feed boost is running
     */
    public record SourceView(long id, int x, int y, float reachCells, float maxReachCells, int claimedCells, boolean fed) {}
}

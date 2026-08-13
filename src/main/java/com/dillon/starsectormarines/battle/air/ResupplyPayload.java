package com.dillon.starsectormarines.battle.air;

/** Drops one persistent battlefield resupply cache. */
public enum ResupplyPayload implements AirDeliveryPayload {
    INSTANCE;

    @Override
    public int unitsPerSortie(ShuttleType carrier) { return 1; }

    @Override
    public boolean tryDeploy(AirDeliveryContext context) {
        int[] cell = context.findOpenDeboardCell();
        if (cell == null) return false;
        context.deployResupplyCache(cell[0], cell[1]);
        return true;
    }
}

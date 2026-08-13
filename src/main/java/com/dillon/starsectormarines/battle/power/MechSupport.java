package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.MechSupportPayload;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.reinforcement.LandingZoneScorer;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Calls a shootable heavy transport that physically unloads one marine mech. */
public final class MechSupport extends CommandPower {

    public static final String ID = "mech_support";

    private static final int LZ_SEARCH_RADIUS = 4;
    private static final int MIN_CLEARANCE = 5;
    private static final float OFFMAP_PAD = 8f;

    public MechSupport() {
        super(ID, "Mech Support", 4f, 0f, 1);
    }

    @Override
    public float previewRadiusCells() { return 1.5f; }

    @Override
    public boolean canTarget(int cellX, int cellY, BattleView battle) {
        return landingZoneNear(cellX, cellY, battle) != null;
    }

    @Override
    public void resolve(int cellX, int cellY, CommandPowerService service, BattleControl battle) {
        int[] lz = landingZoneNear(cellX, cellY, battle);
        if (lz == null) return;
        float lzX = lz[0] + 0.5f;
        float lzY = lz[1] + 0.5f;
        float[] route = routeFromNearestEdge(lzX, lzY, battle.getGrid());
        long shuttle = battle.spawnShuttle(ShuttleType.VALKYRIE, Faction.MARINE,
                lzX, lzY, route[0], route[1], route[2], route[3], 0f);
        ShuttleMission mission = battle.world().mission(shuttle);
        mission.payload = MechSupportPayload.INSTANCE;
        mission.marinesRemaining = mission.payload.unitsPerSortie(ShuttleType.VALKYRIE);
        mission.totalCycles = 1;
    }

    private static int[] landingZoneNear(int cellX, int cellY, BattleView battle) {
        if (battle == null || !battle.getGrid().inBounds(cellX, cellY)) return null;
        return new LandingZoneScorer(battle.getGrid(), battle.getTopology())
                .bestNear(cellX, cellY, LZ_SEARCH_RADIUS, MIN_CLEARANCE);
    }

    private static float[] routeFromNearestEdge(float lzX, float lzY, NavigationGrid grid) {
        float left = lzX;
        float right = grid.getWidth() - lzX;
        float bottom = lzY;
        float top = grid.getHeight() - lzY;
        float nearest = Math.min(Math.min(left, right), Math.min(bottom, top));
        if (nearest == left) return new float[]{-OFFMAP_PAD, lzY, -OFFMAP_PAD - 4f, lzY};
        if (nearest == right) return new float[]{grid.getWidth() + OFFMAP_PAD, lzY,
                grid.getWidth() + OFFMAP_PAD + 4f, lzY};
        if (nearest == bottom) return new float[]{lzX, -OFFMAP_PAD, lzX, -OFFMAP_PAD - 4f};
        return new float[]{lzX, grid.getHeight() + OFFMAP_PAD, lzX,
                grid.getHeight() + OFFMAP_PAD + 4f};
    }
}

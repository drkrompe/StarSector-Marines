package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.logistics.ResupplyCache;
import com.dillon.starsectormarines.battle.logistics.ResupplyService;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;

/** Narrow deployment API handed to an {@link AirDeliveryPayload}. */
public final class AirDeliveryContext {

    private static final int DEBOARD_SCAN_RADIUS = 5;

    public final ShuttleMission mission;
    public final ShuttleType carrier;
    public final Faction faction;

    private final NavigationService navigation;
    private final UnitRosterService roster;
    private final Function<EntitySpec, Long> spawnSink;
    private final ResupplyService resupply;

    AirDeliveryContext(ShuttleMission mission, ShuttleType carrier, Faction faction,
                       NavigationService navigation, UnitRosterService roster,
                       Function<EntitySpec, Long> spawnSink, ResupplyService resupply) {
        this.mission = mission;
        this.carrier = carrier;
        this.faction = faction;
        this.navigation = navigation;
        this.roster = roster;
        this.spawnSink = spawnSink;
        this.resupply = resupply;
    }

    public int[] findOpenDeboardCell() {
        int lzX = (int) Math.floor(mission.lzX);
        int lzY = (int) Math.floor(mission.lzY);
        Set<Long> seen = new HashSet<>();
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{lzX, lzY, 0});
        seen.add(key(lzX, lzY));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (p[2] > DEBOARD_SCAN_RADIUS) continue;
            if (p[2] > 0
                    && navigation.getGrid().inBounds(p[0], p[1])
                    && navigation.getGrid().isWalkable(p[0], p[1])
                    && !navigation.isCellOccupied(p[0], p[1])) {
                return new int[]{p[0], p[1]};
            }
            for (int[] d : dirs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (!navigation.getGrid().inBounds(nx, ny) || !seen.add(key(nx, ny))) continue;
                q.add(new int[]{nx, ny, p[2] + 1});
            }
        }
        return null;
    }

    public int mintSquad(UnitType type) {
        return roster.mintSquad(faction, type);
    }

    public Squad squad(int squadId) {
        return roster.getSquad(squadId);
    }

    public String nextUnitName() {
        return roster.nextMarineId();
    }

    public long spawn(EntitySpec spec) {
        return spawnSink.apply(spec);
    }

    public void attachMechLoadout(long unit, MechRole role) {
        roster.world().attachMechLoadout(unit, MechLoadoutComponent.defaultLoadout(role));
    }

    public void deployResupplyCache(int cellX, int cellY) {
        resupply.add(new ResupplyCache(cellX, cellY, faction));
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}

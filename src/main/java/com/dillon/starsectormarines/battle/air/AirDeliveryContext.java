package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.command.SquadDirectiveControl;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.logistics.ResupplyCache;
import com.dillon.starsectormarines.battle.logistics.ResupplyService;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.squad.CampaignSquadTag;
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

    /**
     * How far from the LZ a passenger may be set down.
     *
     * <p>The search is nearest-first, so this is a bound on the bad case rather
     * than a target: with a free cell beside the ramp nothing reaches past one
     * ring. It used to be five, which is a sensible distance for "don't scatter
     * a squad across the map" and a catastrophic one for "there is nowhere to
     * stand" — a squad that lands and holds around its own LZ can fill every
     * cell within five, and the craft then retries forever and never departs.
     * Twice the reach costs a slightly wider spill in the crowded case and
     * removes the deadlock entirely; {@code UNLOAD_PATIENCE_SEC} in
     * {@code AirSystem} covers the case where even this finds nothing.
     */
    private static final int DEBOARD_SCAN_RADIUS = 10;

    public final ShuttleMission mission;
    public final ShuttleType carrier;
    public final Faction faction;

    private final NavigationService navigation;
    private final UnitRosterService roster;
    private final Function<EntitySpec, Long> spawnSink;
    private final ResupplyService resupply;
    private final SquadDirectiveControl commandControl;

    AirDeliveryContext(ShuttleMission mission, ShuttleType carrier, Faction faction,
                       NavigationService navigation, UnitRosterService roster,
                       Function<EntitySpec, Long> spawnSink, ResupplyService resupply,
                       SquadDirectiveControl commandControl) {
        this.mission = mission;
        this.carrier = carrier;
        this.faction = faction;
        this.navigation = navigation;
        this.roster = roster;
        this.spawnSink = spawnSink;
        this.resupply = resupply;
        this.commandControl = commandControl;
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

    /** Applies optional spawn-time ownership before the squad's first unit appears. */
    public void claimSquadCommand(int squadId, SquadCommandClaim claim) {
        if (claim != null) claim.apply(commandControl, squadId);
    }

    /** Applies optional spawn-time ownership together with the squad's first task, atomically by issuer. */
    public void claimSquadCommand(SquadCommandClaim claim,
                                  ObjectiveAssignment assignment) {
        if (claim != null) claim.apply(commandControl, assignment);
    }

    public void assignSquadCommand(ObjectiveAssignment assignment,
                                   CommandAuthority authority,
                                   String issuer, String reason) {
        commandControl.assignSquadCommand(assignment, authority, issuer, reason);
    }

    /** Mint-or-join the battle squad for a tagged campaign marine at this mission's LZ. */
    public int squadForCampaign(UnitType type, CampaignSquadTag tag) {
        if (mission.landingAreaId >= 0) {
            return roster.squadForCampaign(faction, type, tag,
                    mission.landingAreaId);
        }
        return roster.squadForCampaign(faction, type, tag,
                (int) Math.floor(mission.lzX), (int) Math.floor(mission.lzY));
    }

    public int squadForArrivalGroup(UnitType type) {
        return roster.squadForArrivalGroup(faction, type,
                mission.arrivalGroupId, mission.currentCycle,
                mission.expectedArrivalStrength);
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

    public void attachMechLoadout(long unit, MechLoadoutComponent loadout) {
        roster.world().attachMechLoadout(unit, loadout);
    }

    public void deployResupplyCache(int cellX, int cellY) {
        resupply.add(new ResupplyCache(cellX, cellY, faction));
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}

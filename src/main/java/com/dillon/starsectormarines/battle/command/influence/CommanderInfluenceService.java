package com.dillon.starsectormarines.battle.command.influence;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.IdentityService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.BelievedContact;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Owns the independently aggregated Marine and Defender influence snapshots. */
public final class CommanderInfluenceService {

    public static final int BLOCK_SIZE = 8;
    public static final int UPDATE_INTERVAL_TICKS = 15;

    private final NavigationGrid grid;
    private final UnitRosterService roster;
    private volatile CommanderInfluenceSnapshot marineSnapshot;
    private volatile CommanderInfluenceSnapshot defenderSnapshot;
    private int lastUpdateTick = Integer.MIN_VALUE;

    public CommanderInfluenceService(NavigationGrid grid, UnitRosterService roster) {
        this.grid = grid;
        this.roster = roster;
        int width = (grid.getWidth() + BLOCK_SIZE - 1) / BLOCK_SIZE;
        int height = (grid.getHeight() + BLOCK_SIZE - 1) / BLOCK_SIZE;
        marineSnapshot = emptySnapshot(Faction.MARINE, width, height);
        defenderSnapshot = emptySnapshot(Faction.DEFENDER, width, height);
    }

    public void tick(int simTick) {
        if (lastUpdateTick != Integer.MIN_VALUE
                && simTick - lastUpdateTick < UPDATE_INTERVAL_TICKS) return;
        refresh(simTick);
    }

    /** Immediate deterministic rebuild used by the fixed cadence and focused tests. */
    public void refresh(int simTick) {
        InfluenceTopology topology = new InfluenceTopology(grid, BLOCK_SIZE);
        CommanderInfluenceSnapshot marine = buildSnapshot(Faction.MARINE, simTick, topology);
        CommanderInfluenceSnapshot defender = buildSnapshot(Faction.DEFENDER, simTick, topology);
        marineSnapshot = marine;
        defenderSnapshot = defender;
        lastUpdateTick = simTick;
    }

    public CommanderInfluenceSnapshot snapshot(Faction faction) {
        if (faction == Faction.MARINE) return marineSnapshot;
        if (faction == Faction.DEFENDER) return defenderSnapshot;
        return null;
    }

    private CommanderInfluenceSnapshot emptySnapshot(Faction faction, int width, int height) {
        return new CommanderInfluenceSnapshot(faction, -1, BLOCK_SIZE,
                width, height, grid.getWidth(), grid.getHeight(),
                new float[width * height], new float[width * height], List.of());
    }

    private CommanderInfluenceSnapshot buildSnapshot(Faction faction, int simTick,
                                                       InfluenceTopology topology) {
        List<InfluenceSource> friendlySources = friendlySources(faction);
        List<CommanderContact> contacts = aggregateContacts(faction);
        List<InfluenceSource> hostileSources = new ArrayList<>(contacts.size());
        for (CommanderContact contact : contacts) {
            hostileSources.add(new InfluenceSource(contact.cellX(), contact.cellY(),
                    contact.confidence()));
        }
        return new CommanderInfluenceSnapshot(faction, simTick, BLOCK_SIZE,
                topology.blockWidth(), topology.blockHeight(),
                grid.getWidth(), grid.getHeight(),
                InfluenceFieldBuilder.propagate(topology, friendlySources),
                InfluenceFieldBuilder.propagate(topology, hostileSources), contacts);
    }

    private List<InfluenceSource> friendlySources(Faction faction) {
        List<InfluenceSource> sources = new ArrayList<>();
        World world = roster.world();
        IdentityService identity = roster.identity();
        long[] dense = roster.denseArray();
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long unit = dense[i];
            if (identity.faction(unit) != faction || !identity.type(unit).combatant) continue;
            sources.add(new InfluenceSource(world.cellX(unit), world.cellY(unit), 1f));
        }
        return sources;
    }

    private List<CommanderContact> aggregateContacts(Faction faction) {
        Map<Long, CommanderContact> merged = new LinkedHashMap<>();
        IdentityService identity = roster.identity();
        for (Squad squad : roster.getSquads()) {
            if (squad.faction != faction || squad.aliveMembers <= 0) continue;
            for (BelievedContact belief : squad.believedContacts()) {
                if (!roster.isLive(belief.unitId())
                        || identity.faction(belief.unitId()) == faction
                        || !identity.type(belief.unitId()).combatant) {
                    continue;
                }
                CommanderContact candidate = new CommanderContact(
                        belief.unitId(), belief.lastSeenCellX(), belief.lastSeenCellY(),
                        belief.lastSeenTick(), belief.confidence(), belief.source(), squad.id);
                CommanderContact old = merged.get(candidate.unitId());
                if (old == null || prefer(candidate, old)) {
                    merged.put(candidate.unitId(), candidate);
                }
            }
        }
        List<CommanderContact> contacts = new ArrayList<>(merged.values());
        contacts.sort(Comparator.comparingLong(CommanderContact::unitId));
        return contacts;
    }

    private static boolean prefer(CommanderContact candidate, CommanderContact old) {
        int confidence = Float.compare(candidate.confidence(), old.confidence());
        if (confidence != 0) return confidence > 0;
        if (candidate.observedTick() != old.observedTick()) {
            return candidate.observedTick() > old.observedTick();
        }
        return candidate.reporterSquadId() < old.reporterSquadId();
    }
}

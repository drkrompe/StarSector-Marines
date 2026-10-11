package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.RelayNineCommand;
import com.dillon.starsectormarines.battle.command.RelayNineTactics;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import java.util.List;

/** One eight-person road reserve, committed at setup and released by public territory loss. */
public final class RelayNineSequence {
    private final List<EntitySpec> reserve;
    private final RelayNineTactics territory = new RelayNineTactics();
    private int releaseTick = -1, squadId = -1;
    private int approachX, approachY;
    private boolean handedOff;
    public RelayNineSequence(List<EntitySpec> reserve) {
        if (reserve.size() != 8) throw new IllegalArgumentException("Relay Nine has exactly eight road reserves");
        this.reserve = List.copyOf(reserve);
    }
    public int pendingMembers() { return arrived() ? 0 : reserve.size(); }
    public boolean arrived() { return squadId >= 0; }
    public void tick(BattleSimulation sim) {
        territory.observe(sim.getCompoundService().getRecords().stream().map(r -> r.state).toList());
        int tick = sim.getSimTickIndex();
        if (releaseTick < 0 && territory.firstRoadLoss() >= 0) releaseTick = tick + 8 * 30;
        if (!arrived() && releaseTick >= 0 && tick >= releaseTick) {
            boolean gateFirst = territory.firstRoadLoss() == 2;
            approachX = gateFirst ? 130 : 60; approachY = gateFirst ? 103 : 23;
            squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
            for (int i = 0; i < reserve.size(); i++) {
                EntitySpec spec = reserve.get(i);
                if (gateFirst) spec.atPosition(129.5f + i % 3, 124.5f + i / 3);
                if (!sim.getGrid().isWalkable(spec.cellX, spec.cellY)) throw new IllegalStateException("Reserve road obstructed");
                sim.spawn(spec.squad(squadId));
            }
            sim.getSquad(squadId).originalSize = reserve.size();
            sim.assignSquadCommand(ObjectiveAssignment.attackMove(squadId, approachX, approachY),
                    CommandAuthority.SCRIPTED, "relay-nine-road", "advance along the service road");
        }
        if (!arrived() || handedOff) return;
        int n = sim.squadMemberCount(squadId);
        if (n == 0) { handedOff = true; return; }
        float x = 0, y = 0;
        for (int i = 0; i < n; i++) {
            long unit = sim.squadMemberAt(squadId, i); x += sim.world().x(unit); y += sim.world().y(unit);
        }
        if (Math.hypot(x / n - approachX, y / n - approachY) <= 5 || sim.getSquad(squadId).hasDirectContactThisTick())
            handedOff = sim.handoffSquadCommand(squadId, "relay-nine-road", CommandAuthority.MISSION_COMMAND,
                    RelayNineCommand.issuer(Faction.DEFENDER), null, "finite road reserve retakes lost ground");
    }
}

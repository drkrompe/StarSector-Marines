package com.dillon.starsectormarines.battle.command.allied;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * What brings a mission's allied tools onto the field (`allied-tools.md`, Code seam). Its pace or table is its
 * stock; a commander draws on that stock by need through {@link #request}, rather than every machine being sent on a
 * timer. Sends from its {@link AlliedDepot}, so a request while the depot cannot send is refused, not sent from
 * elsewhere; a sortie stays behind the mission's air gate inside the producer.
 */
public interface AlliedProducer extends AlliedDepot {

    /** Sends one of {@code request.tool()} to its place, or {@code false} when the depot cannot send or none is free. */
    boolean request(BattleSimulation sim, AlliedRequest request);

    /** Tools of this kind on their way to {@code toPlace} and not yet there, for a commander's one-at-a-time rules. */
    int onRoad(AlliedTool tool, String toPlace);
}

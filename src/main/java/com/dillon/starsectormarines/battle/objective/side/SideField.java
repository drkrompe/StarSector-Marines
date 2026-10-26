package com.dillon.starsectormarines.battle.objective.side;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * What a trigger may read of the battle: where the company stands and whether a tracked unit is still on the field.
 * Small on purpose, so a trigger is proved on a hand-built field rather than a played battle.
 */
public interface SideField {
    /** Which company units count for a trigger. */
    enum Who {
        /** A company combatant on foot. */
        COMBATANT,
        /** A company squad's leader. */
        LEADER
    }

    /** Whether a company unit of this kind stands in {@code ground}. */
    boolean anyIn(Who who, Ground ground);

    /** The nearest company unit of this kind within {@code reach} of a point, or 0 for none. */
    long nearest(Who who, float x, float y, float reach);

    /** Whether {@code id} is a live unit on the field. */
    boolean onField(long id);

    float x(long id);

    float y(long id);

    /**
     * Whether a company unit of this kind has a line of sight, within {@code range} cells, to any cell of {@code ground}
     * (its anchor or outline). A hand-built field sees nothing unless it says so.
     */
    default boolean sees(Who who, Ground ground, float range) { return false; }

    /** The field over a live battle. */
    static SideField of(BattleSimulation sim) {
        return new SideField() {
            @Override public boolean anyIn(Who who, Ground ground) {
                if (who == Who.LEADER) {
                    for (Squad squad : sim.getSquads())
                        if (squad.faction == Faction.MARINE && onField(squad.leaderId)
                                && ground.contains(sim.world().cellX(squad.leaderId), sim.world().cellY(squad.leaderId))) return true;
                    return false;
                }
                for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
                    long id = sim.liveUnitAt(i);
                    if (combatant(id) && ground.contains(sim.world().cellX(id), sim.world().cellY(id))) return true;
                }
                return false;
            }

            @Override public long nearest(Who who, float x, float y, float reach) {
                long best = 0L;
                double bestD = reach;
                if (who == Who.LEADER) {
                    for (Squad squad : sim.getSquads()) {
                        if (squad.faction != Faction.MARINE || !onField(squad.leaderId)) continue;
                        double d = Math.hypot(sim.world().x(squad.leaderId) - x, sim.world().y(squad.leaderId) - y);
                        if (d <= bestD) { bestD = d; best = squad.leaderId; }
                    }
                    return best;
                }
                for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
                    long id = sim.liveUnitAt(i);
                    if (!combatant(id)) continue;
                    double d = Math.hypot(sim.world().x(id) - x, sim.world().y(id) - y);
                    if (d <= bestD) { bestD = d; best = id; }
                }
                return best;
            }

            @Override public boolean onField(long id) {
                return id != 0L && sim.resolveUnit(id) != 0L && sim.world().hasPosition(id) && sim.world().isAlive(id);
            }

            @Override public float x(long id) { return sim.world().x(id); }

            @Override public float y(long id) { return sim.world().y(id); }

            @Override public boolean sees(Who who, Ground ground, float range) {
                if (who == Who.LEADER) {
                    for (Squad squad : sim.getSquads())
                        if (squad.faction == Faction.MARINE && onField(squad.leaderId) && sees(squad.leaderId, ground, range)) return true;
                    return false;
                }
                for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
                    long id = sim.liveUnitAt(i);
                    if (combatant(id) && sees(id, ground, range)) return true;
                }
                return false;
            }

            private boolean sees(long id, Ground ground, float range) {
                int ux = sim.world().cellX(id), uy = sim.world().cellY(id);
                var anchor = ground.anchor();
                // Far beyond range of the whole place: no ray is cast.
                if (Math.hypot(anchor.x() - ux, anchor.y() - uy) > range + 8) return false;
                if (sim.getGrid().hasLineOfSightWithin(ux, uy, anchor.x(), anchor.y(), range)) return true;
                for (var cell : ground.outline())
                    if (sim.getGrid().hasLineOfSightWithin(ux, uy, cell.x(), cell.y(), range)) return true;
                return false;
            }

            private boolean combatant(long id) {
                return sim.identity().faction(id) == Faction.MARINE && sim.identity().type(id).combatant
                        && sim.world().hasPosition(id) && !sim.isRiding(id);
            }
        };
    }
}

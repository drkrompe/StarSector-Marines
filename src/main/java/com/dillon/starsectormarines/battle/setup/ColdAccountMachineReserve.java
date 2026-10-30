package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.mech.FactionMechLoadouts;
import com.dillon.starsectormarines.battle.mech.MechSpawnPlacement;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import java.util.ArrayList;
import java.util.List;

/**
 * Sable's machines in the outpost's vehicle shed. They stand down until the company is inside the walls: the first
 * company soldier on foot within the compound starts a short clock, and when it runs out the machines come out of the
 * shed as one squad to meet the company inside. Until then they are pending defenders, so the outpost is not taken
 * while its machines are still in the shed.
 */
public final class ColdAccountMachineReserve {
    /** Seconds from the company entering the compound to the machines coming out. UNTUNED first pass. */
    static final float WAKE_SECONDS = 6f;

    private final List<MechVariant> machines;
    private final int left, top, right, bottom;
    private final int bayX, bayY;
    private final String factionId;
    private float clock = -1f;
    private boolean awake;
    private int squad = -1;

    /**
     * @param machines  the chassis in the shed, in squad order
     * @param footprint the compound, {@code {left, top, right, bottom}} inclusive
     * @param bay       the shed's floor cell the machines come out from
     * @param factionId the defending faction, for the machines' fits
     */
    ColdAccountMachineReserve(List<MechVariant> machines, int[] footprint, int[] bay, String factionId) {
        this.machines = List.copyOf(machines);
        left = footprint[0]; top = footprint[1]; right = footprint[2]; bottom = footprint[3];
        bayX = bay[0]; bayY = bay[1];
        this.factionId = factionId;
    }

    /** Machines still in the shed. */
    public int pendingMembers() { return awake ? 0 : machines.size(); }
    public boolean awake() { return awake; }
    public int squad() { return squad; }

    /** One tick: the company inside the compound starts the clock, and its running out brings the machines out. */
    public void advance(BattleSimulation sim) {
        if (awake) return;
        if (clock < 0f) {
            if (companyInside(sim)) clock = 0f;
            return;
        }
        clock += BattleSimulation.TICK_DT;
        if (clock < WAKE_SECONDS) return;
        awake = true;
        rollOut(sim);
    }

    private void rollOut(BattleSimulation sim) {
        List<Long> out = new ArrayList<>();
        for (MechVariant variant : machines) {
            MechSpawnPlacement.Point at = standable(sim, variant);
            if (at == null) continue;
            if (squad < 0) squad = sim.mintSquad(Faction.DEFENDER, UnitType.HEAVY_MECH);
            long id = sim.spawn(variant.applyTo(new EntitySpec("Sable shed " + variant.name().toLowerCase(),
                    Faction.DEFENDER, UnitType.HEAVY_MECH, at.cellX(), at.cellY()))
                    .atPosition(at.x(), at.y()).squad(squad));
            sim.world().attachMechLoadout(id, FactionMechLoadouts.create(variant, variant.defaultRole, factionId));
            out.add(id);
        }
        if (squad < 0) return;
        Squad machines = sim.getSquad(squad);
        machines.leaderId = out.get(0);
        machines.originalSize = out.size();
    }

    /** The nearest point to the shed floor a machine of {@code variant}'s size stands clear of everyone. */
    private MechSpawnPlacement.Point standable(BattleSimulation sim, MechVariant variant) {
        for (int r = 0; r <= 8; r++)
            for (int dy = -r; dy <= r; dy++)
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    MechSpawnPlacement.Point at = MechSpawnPlacement.nearCell(sim.getGrid(), variant.radius,
                            bayX + dx, bayY + dy, (px, py) -> true,
                            (px, py) -> MechSpawnPlacement.unoccupied(sim.getRoster(), px, py, variant.radius, 0L));
                    if (at != null) return at;
                }
        return null;
    }

    private boolean companyInside(BattleSimulation sim) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long id = sim.liveUnitAt(i);
            if (sim.identity().faction(id) != Faction.MARINE || !sim.world().hasPosition(id) || sim.isRiding(id)) continue;
            int x = sim.world().cellX(id), y = sim.world().cellY(id);
            if (x >= left && x <= right && y >= top && y <= bottom) return true;
        }
        return false;
    }
}

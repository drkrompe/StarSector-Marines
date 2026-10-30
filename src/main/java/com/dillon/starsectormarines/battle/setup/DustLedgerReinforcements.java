package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechSpawnPlacement;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Area;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Cell;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import java.util.List;

/**
 * Sable's road reserve ({@code dust-ledger-crossing.md}, Reinforcements): the first company unit to step into the
 * exclusion ring starts a short clock, and when it runs out two squads of infantry and a Hound muster at the crawler
 * road's far end and go in to hold the pump house. The Hound carries a light machine gun in place of its chaingun and no rocket pod,
 * and follows the second squad ({@code MechAttachmentService}) so the pair stay together; the first squad holds the
 * ledger room itself. It comes once, whether or not the alarm has sounded.
 */
final class DustLedgerReinforcements {
    /** Seconds from a company unit entering the ring to the reserve mustering. UNTUNED first pass. */
    static final float DELAY_SECONDS = 25f;
    /** Squads in the reserve, and their size, so the muster cells line up: two of six, then the Hound's cell. */
    static final int SQUADS = 2, SQUAD_SIZE = 6;
    /** Radius, in cells, of the ground each squad holds round the ledger room. UNTUNED first pass. */
    static final int ROOM_HOLD_CELLS = 6, YARD_HOLD_CELLS = 12;
    private static final String ISSUER = "dust-ledger-reserve";

    private final List<List<EntitySpec>> squads;
    private final List<Cell> musterCells;
    private final Cell holdCentre;
    private float clock = -1f;
    private boolean arrived;
    private final int[] squadIds = {-1, -1};
    private int houndSquad = -1;
    private long hound;

    /**
     * @param squads      the two infantry squads, unplaced; each {@link #SQUAD_SIZE} strong
     * @param musterCells one cell per infantry member in squad order, then the Hound's
     * @param holdCentre  the facility the reserve goes in to hold: the ledger room
     */
    DustLedgerReinforcements(List<List<EntitySpec>> squads, List<Cell> musterCells, Cell holdCentre) {
        if (squads.size() != SQUADS || squads.stream().anyMatch(squad -> squad.size() != SQUAD_SIZE))
            throw new IllegalArgumentException("the road reserve is two squads of " + SQUAD_SIZE);
        if (musterCells.size() < SQUADS * SQUAD_SIZE + 1)
            throw new IllegalArgumentException("one muster cell per member, and one for the Hound");
        this.squads = squads.stream().map(List::copyOf).toList();
        this.musterCells = List.copyOf(musterCells);
        this.holdCentre = holdCentre;
    }

    /** The reserve still to arrive, once it has been called: the infantry and the Hound. */
    int pendingMembers() { return clock >= 0f && !arrived ? SQUADS * SQUAD_SIZE + 1 : 0; }

    boolean called() { return clock >= 0f; }
    boolean arrived() { return arrived; }
    int squad(int index) { return squadIds[index]; }
    int houndSquad() { return houndSquad; }
    long hound() { return hound; }

    /** The Hound's loadout: a light machine gun on its nose mount and no shoulder pod. */
    static MechLoadoutComponent houndLoadout() {
        return new MechLoadoutComponent(MechVariant.HOUND, MechWeaponComponent.LIGHT_MACHINE_GUN, null, null, MechRole.ASSAULT);
    }

    /**
     * One tick: a company unit in the ring starts the clock, and its running out musters the reserve.
     *
     * @param companyInRing whether a company unit stands inside the exclusion ring this tick
     * @return whether the reserve mustered on this tick
     */
    boolean advance(BattleSimulation sim, boolean companyInRing) {
        if (arrived) return false;
        if (clock < 0f) {
            if (companyInRing) clock = 0f;
            return false;
        }
        clock += BattleSimulation.TICK_DT;
        if (clock < DELAY_SECONDS) return false;
        arrived = true;
        muster(sim);
        return true;
    }

    private void muster(BattleSimulation sim) {
        for (int index = 0; index < SQUADS; index++) {
            int squad = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
            List<EntitySpec> members = squads.get(index);
            for (int i = 0; i < members.size(); i++) {
                Cell cell = musterCells.get(index * SQUAD_SIZE + i);
                sim.spawn(members.get(i).atPosition(cell.x() + .5f, cell.y() + .5f).squad(squad));
            }
            sim.getSquad(squad).originalSize = members.size();
            squadIds[index] = squad;
        }
        spawnHound(sim);
        sim.assignSquadCommand(ObjectiveAssignment.defendArea(squadIds[0], holdCentre.x(), holdCentre.y(), ROOM_HOLD_CELLS),
                CommandAuthority.SCRIPTED, ISSUER, "Road reserve goes in to hold the ledger room");
        sim.assignSquadCommand(ObjectiveAssignment.defendArea(squadIds[1], holdCentre.x(), holdCentre.y(), YARD_HOLD_CELLS),
                CommandAuthority.SCRIPTED, ISSUER, "Road reserve holds the yard round the pump house");
        if (hound != 0L) sim.getMechAttachmentService().requestAttach(houndSquad, squadIds[1]);
    }

    private void spawnHound(BattleSimulation sim) {
        Cell cell = musterCells.get(SQUADS * SQUAD_SIZE);
        MechSpawnPlacement.Point at = standable(sim, cell);
        if (at == null) return;
        houndSquad = sim.mintSquad(Faction.DEFENDER, UnitType.HEAVY_MECH);
        hound = sim.spawn(new EntitySpec("Sable road Hound", Faction.DEFENDER, UnitType.HEAVY_MECH, at.cellX(), at.cellY())
                .atPosition(at.x(), at.y()).mechVariant(MechVariant.HOUND).squad(houndSquad));
        sim.world().attachMechLoadout(hound, houndLoadout());
        Squad squad = sim.getSquad(houndSquad);
        squad.leaderId = hound;
        squad.originalSize = 1;
    }

    private static MechSpawnPlacement.Point standable(BattleSimulation sim, Cell cell) {
        for (int r = 0; r <= 4; r++)
            for (int dy = -r; dy <= r; dy++)
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    MechSpawnPlacement.Point at = MechSpawnPlacement.nearCell(sim.getGrid(), MechVariant.HOUND.radius,
                            cell.x() + dx, cell.y() + dy, (px, py) -> true, (px, py) -> true);
                    if (at != null) return at;
                }
        return null;
    }

    /** Whether any company unit on the ground stands inside {@code ring}. */
    static boolean companyIn(BattleSimulation sim, Area ring) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long id = sim.liveUnitAt(i);
            if (sim.identity().faction(id) != Faction.MARINE || !sim.world().hasPosition(id) || sim.isRiding(id)) continue;
            if (ring.contains(sim.world().cellX(id), sim.world().cellY(id))) return true;
        }
        return false;
    }
}

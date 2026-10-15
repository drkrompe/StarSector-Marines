package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.infantry.SquadSupportKit;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

/**
 * Issues the mission's declared support kit to the defender infantry squads as they are made: every {@code observerEvery}th
 * infantry squad has a comms radio on its first member, which is what lets the defending side's own air director task
 * the pool the mission gave it.
 *
 * <p>The mirror of {@link SupportKitIssue}, which does the same for the marines' lift. A side's pool is directed through
 * living operators on the ground, whoever the side is: a defender pool declared with no one to call it would be inert,
 * and declaring where the radios are is the mission's job, as it is for the marines. Mechs and anything not drawn as
 * infantry are never issued one, and a declaration that states no kit for the defenders (every production mission)
 * changes nothing.
 */
final class DefenderObserverIssue {

    private final SquadSupportKit kit;
    private final SpecialEquipmentDef radio;
    private int squads;

    private DefenderObserverIssue(SquadSupportKit kit) {
        this.kit = kit;
        this.radio = kit.observerEvery() > 0 ? SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.COMMS_RADIO_ID) : null;
    }

    static DefenderObserverIssue of(BattleSimulation sim) {
        return of(sim.squadSupport().forSide(Faction.DEFENDER));
    }

    static DefenderObserverIssue of(SquadSupportKit kit) {
        return new DefenderObserverIssue(kit);
    }

    /**
     * Called for each member as it is made. The first member of an infantry squad counts the squad and, when the kit names
     * it, carries the radio.
     *
     * @param infantry whether the unit is a foot soldier rather than a mech
     * @param first    whether it is the first member of its squad
     */
    void issue(EntitySpec unit, boolean infantry, boolean first) {
        if (radio == null || !infantry || !first) return;
        boolean carries = kit.observer(squads++);
        if (carries && unit.specialEquipment == null) unit.specialEquipment(radio, radio.startingAmmo());
    }
}

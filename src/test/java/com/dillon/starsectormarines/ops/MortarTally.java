package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.deployable.FieldMortarService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.BelievedContact;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Playback-side tally of field-mortar use: how many living marines carried a tube, set-ups, the rounds each standing
 * tube fired, and the first tick one did.
 */
final class MortarTally {
    private final Map<FieldMortarService.Tube, Integer> fired = new IdentityHashMap<>();
    private final Map<String, Integer> gates = new TreeMap<>();
    private int maxCarriers;
    private int firstCarrierTick = -1;
    private int firstSetUpTick = -1;
    private int firstRoundTick = -1;

    void observe(BattleSimulation sim) {
        int carriers = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (!sim.world().hasSecondaryWeapon(unit)) continue;
            SpecialEquipmentDef special = sim.world().specialEquipment(unit);
            if (special == null || !SpecialEquipmentRegistry.FIELD_MORTAR_ID.equals(special.id())) continue;
            carriers++;
            if (sim.fieldMortars().tubeOfCarrier(unit) == null) gates.merge(blockingGate(unit, sim), 1, Integer::sum);
        }
        if (carriers > 0 && firstCarrierTick < 0) firstCarrierTick = sim.getSimTickIndex();
        maxCarriers = Math.max(maxCarriers, carriers);
        for (FieldMortarService.Tube tube : sim.fieldMortars().tubes()) {
            if (firstSetUpTick < 0) firstSetUpTick = sim.getSimTickIndex();
            if (tube.roundsFired() > 0 && firstRoundTick < 0) firstRoundTick = sim.getSimTickIndex();
            fired.merge(tube, tube.roundsFired(), Math::max);
        }
    }

    /** The first set-up condition {@code MortarTactics.tryCommitDeploy} would refuse on, approximated from outside. */
    private static String blockingGate(long unit, BattleSimulation sim) {
        Squad squad = sim.squadOf(unit);
        if (squad == null) return "noSquad";
        float nearest = Float.MAX_VALUE;
        int inBand = 0;
        World world = sim.world();
        Faction faction = sim.identity().faction(unit);
        for (BelievedContact contact : squad.believedContacts()) {
            long id = sim.resolveUnit(contact.unitId());
            if (id == 0L || !faction.hostileTo(sim.identity().faction(id))) continue;
            float d = (float) Math.hypot(world.x(id) - world.x(unit), world.y(id) - world.y(unit));
            nearest = Math.min(nearest, d);
            if (contact.confidence() >= 0.3f && d >= 17f && d <= 50f) inBand++;
        }
        String band = inBand == 0 ? (nearest == Float.MAX_VALUE ? "noContact" : nearest < 17f ? "contactTooClose" : "contactOutOfBand")
                : "inBand";
        if (squad.timeSinceUnderFire < 3f) return "underFire/" + band;
        if (!sim.movement().settled(unit)) return "moving/" + band;
        return "quietSettled/" + band;
    }

    int rounds() {
        return fired.values().stream().mapToInt(Integer::intValue).sum();
    }

    String describe(BattleSimulation sim) {
        FieldMortarService mortars = sim.fieldMortars();
        return "mortar carriers=" + maxCarriers + " firstCarrierTick=" + firstCarrierTick
                + " setUps=" + mortars.setUps() + " packs=" + mortars.packs() + " rounds=" + rounds()
                + " firstSetUpTick=" + firstSetUpTick + " firstRoundTick=" + firstRoundTick
                + " lostWithGunner=" + mortars.lostWithGunner() + " recrewed=" + mortars.recrewed()
                + " destroyedStanding=" + mortars.destroyedStanding() + " gateTicks=" + gates;
    }
}

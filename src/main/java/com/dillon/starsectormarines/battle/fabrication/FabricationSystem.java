package com.dillon.starsectormarines.battle.fabrication;

import com.dillon.starsectormarines.battle.ambient.AmbientTaskService;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.EnumSet;
import java.util.List;

/**
 * Turns the hands actually at work in a vehicle bay into machines coming out of
 * it.
 *
 * <p>Production is <b>counted off the workers, not off the clock</b>, and that
 * is the whole design. A shed that made a machine every so many seconds would be
 * a timer wearing a building's clothes: an attacker could stand in the doorway
 * killing technicians and the line would run exactly as fast. Counting hands
 * instead makes every consequence fall out on its own — kill the technicians and
 * the stocks stop; take the shed and they stop; leave it alone and a garrison
 * that started with three machines finishes with five.
 *
 * <p>What counts is a technician <em>at</em> a servicing point. Not standing in
 * the bay, not walking across it, and not at the stores or the terminal, which
 * are jobs the same person does in the same room on the same rotation. The
 * ambient service says which job somebody has in hand and the fitting says which
 * berth each servicing cell works, so the two together say who is welding and
 * where — and a shed with three technicians in it is welding for rather less
 * than three hand-seconds a second, because most of a shift is not welding.
 *
 * <p>Follows the {@code *System} convention: stateless, reads
 * {@link FabricationService} and the battle, writes through ordinary spawning.
 */
public final class FabricationSystem {

    private static final Logger LOG = Global.getLogger(FabricationSystem.class);

    /**
     * How far from a finished berth a node may be and still be the place the
     * machine is for.
     *
     * <p>Generous, because the question is only which installation a shed
     * belongs to and a ward is a few dozen cells across. A machine that finds no
     * node walks out as a free agent and falls through to ordinary engagement,
     * which is a worse machine rather than a broken one.
     */
    private static final int HOME_NODE_RADIUS = 60;

    private static final EnumSet<TacticalNode.Kind> INSTALLATIONS = EnumSet.of(
            TacticalNode.Kind.ARMORY, TacticalNode.Kind.BARRACKS,
            TacticalNode.Kind.COMMAND_POST);

    private int nextMachine;

    public void tick(float dt, BattleSimulation sim, FabricationService works) {
        if (works == null || works.isEmpty() || dt <= 0f) return;

        creditTheHandsAtWork(dt, sim, works);
        rollOutWhatIsFinished(sim, works);
    }

    /**
     * Give every bay the work its own technicians did this tick.
     *
     * <p>Walked over the actors the ambient service is controlling rather than
     * over the bays, because the question is about people. Anybody it is not
     * controlling is not doing authored work at all, and the roster of a
     * conquest map is mostly soldiers.
     */
    private void creditTheHandsAtWork(float dt, BattleSimulation sim, FabricationService works) {
        AmbientTaskService tasks = sim.ambientTasks();
        for (long actor : tasks.assigned()) {
            if (tasks.jobInHand(actor) == null) continue;
            int bay = works.bayServicedFrom(
                    sim.world().cellX(actor), sim.world().cellY(actor));
            if (bay < 0) continue;
            works.work(bay, dt);
            works.setLastBuilder(sim.identity().faction(actor));
        }
    }

    /** Roll out every machine that is done, and lay the next one down. */
    private void rollOutWhatIsFinished(BattleSimulation sim, FabricationService works) {
        for (FabricationService.Works bay : works.bays()) {
            if (bay.progress() < 1f || bay.chassis == null) continue;
            if (driveOut(sim, works, bay)) works.rollOut(bay.siteId);
        }
    }

    /**
     * Put a finished machine on the deck of its own berth, crewed and tasked.
     *
     * <p>An ordinary unit in an ordinary squad, which is the contract every
     * producer of battle actors owes: nothing about a machine that was built
     * here rather than delivered here changes how it fights, who may command it,
     * or how it dies.
     *
     * <p>Left <b>unclaimed</b> on purpose. A squad minted with a delivery arm's
     * claim outranks mission command and is visible to it but permanently
     * immovable by it, which is how a reinforcement that lands in the wrong
     * place stays there for the rest of the battle. A machine that walks out of
     * a shed under its own power is simply part of the garrison, and the side's
     * commander takes it like any other.
     *
     * @return whether it went; a berth with no clear cell keeps its machine and
     *     tries again, rather than losing a whole build to a crowded lane
     */
    private boolean driveOut(BattleSimulation sim, FabricationService works,
                             FabricationService.Works bay) {
        Gantry berth = clearBerth(sim, works, bay);
        if (berth == null) return false;

        MechVariant chassis = bay.chassis;
        Faction builder = works.lastBuilder();
        EntitySpec machine = new EntitySpec("f" + (nextMachine++), builder,
                UnitType.HEAVY_MECH, berth.centerX, berth.centerY);
        machine.mechVariant(chassis);
        machine.role(UnitRole.PATROL);
        machine.home(berth.centerX, berth.centerY);

        int squadId = sim.mintSquad(builder, UnitType.HEAVY_MECH);
        Squad squad = sim.getSquad(squadId);
        if (squad != null) {
            squad.assignedNode = homeNode(sim, berth);
            squad.originalSize = 1;
            machine.squad(squad.id);
        }

        long unit = sim.spawn(machine);
        sim.world().attachMechLoadout(unit, chassis.createLoadout(chassis.defaultRole));
        LOG.info("FabricationSystem: " + builder + " " + chassis.displayName
                + " rolled out of the bay at " + berth.centerX + "," + berth.centerY);
        return true;
    }

    /**
     * A berth of this bay a machine can be stood in, or null while every one of
     * them is blocked.
     *
     * <p>A bay has a rank of them and the machine comes out of whichever is
     * clear, which is what makes a crowded lane a delay rather than a lost
     * build. Refusing outright and keeping the finished work is the point: a
     * shed that discarded a machine because somebody was standing in the way
     * would lose four minutes of a crew's work to a pathing accident.
     */
    private static Gantry clearBerth(BattleSimulation sim, FabricationService works,
                                     FabricationService.Works bay) {
        for (int berth : bay.berths) {
            Gantry gantry = works.gantry(berth);
            if (sim.getGrid().isWalkable(gantry.centerX, gantry.centerY)) return gantry;
        }
        return null;
    }

    /** The installation this shed belongs to, or null where none is near. */
    private static TacticalNode homeNode(BattleSimulation sim, Gantry berth) {
        TacticalMap map = sim.getTacticalMap();
        if (map == null) return null;
        List<TacticalNode> near = map.nearest(berth.centerX, berth.centerY, 1, INSTALLATIONS);
        if (near.isEmpty()) return null;
        TacticalNode candidate = near.get(0);
        int distance = Math.abs(candidate.anchorX - berth.centerX)
                + Math.abs(candidate.anchorY - berth.centerY);
        return distance <= HOME_NODE_RADIUS ? candidate : null;
    }
}

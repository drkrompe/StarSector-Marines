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
 * it, one weld at a time on a body anybody can shoot.
 *
 * <p>Production is <b>counted off the workers, not off the clock</b>, and that
 * is the whole design. A shed that made a machine every so many seconds would be
 * a timer wearing a building's clothes: an attacker could stand in the doorway
 * killing technicians and the line would run at exactly the same rate. Counting
 * hands instead makes every consequence fall out on its own — kill the
 * technicians and the stocks stop; take the shed and they stop; leave it alone
 * and a garrison that started with three machines finishes with five.
 *
 * <p>What counts is a technician <em>at</em> a servicing point. Not standing in
 * the bay, not walking across it, and not at its stores or its terminals, which
 * are jobs the same person does in the same room on the same rotation. The
 * ambient service says which job somebody has in hand and the fitting says which
 * berth each servicing cell works, so the two together say who is welding and
 * where — and a shed with three technicians in it welds for rather less than
 * three hand-seconds a second, because most of a shift is not welding.
 *
 * <p><b>The weld goes into a body.</b> A machine on the stocks is a unit
 * standing in its gantry whose structure is how built it is, so welding is
 * literally putting it together and a marine's fire is literally taking it
 * apart. Nothing here has a rule for what damage does to progress, because
 * damage <em>is</em> what it does to progress.
 *
 * <p><b>The crew lays the keel.</b> A bay with nothing on its stocks starts the
 * next machine on the first tick somebody is at the gantry to start it — so a
 * crew that has stood down under fire lays nothing, a crew that is dead lays
 * nothing ever again, and there is no separate rule saying so.
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
    private int nextFrame;

    public void tick(float dt, BattleSimulation sim, FabricationService works) {
        if (works == null || works.isEmpty() || dt <= 0f) return;

        forgetWhatWasDestroyed(sim, works);
        weld(dt, sim, works);
        rollOutWhatIsFinished(sim, works);
    }

    /**
     * Take a machine that was shot in its gantry off the bay's books.
     *
     * <p>Nothing is refunded and nothing is remembered. The work went into that
     * body and the body is wreckage; the next keel starts from a keel, which is
     * what makes destroying one worth an attacker's rounds.
     */
    private static void forgetWhatWasDestroyed(BattleSimulation sim, FabricationService works) {
        for (FabricationService.Works bay : works.bays()) {
            if (!bay.hasFrame() || sim.world().isAlive(bay.frameId)) continue;
            LOG.info("FabricationSystem: the machine on the stocks in bay "
                    + bay.siteId + " was destroyed before it was finished");
            works.clearStocks(bay.siteId);
        }
    }

    /**
     * Give every bay the work its own technicians did this tick, and let them
     * lay a keel where there is nothing to work on.
     *
     * <p>Walked over the actors the ambient service is controlling rather than
     * over the bays, because the question is about people. Anybody it is not
     * controlling is not doing authored work at all, and the roster of a
     * conquest map is mostly soldiers.
     */
    private void weld(float dt, BattleSimulation sim, FabricationService works) {
        AmbientTaskService tasks = sim.ambientTasks();
        for (long actor : tasks.assigned()) {
            if (tasks.jobInHand(actor) == null) continue;
            int siteId = works.bayServicedFrom(
                    sim.world().cellX(actor), sim.world().cellY(actor));
            if (siteId < 0) continue;
            FabricationService.Works bay = works.bay(siteId);
            if (bay == null || bay.chassis == null) continue;

            works.setLastBuilder(sim.identity().faction(actor));
            if (!bay.hasFrame()) {
                layKeel(sim, works, bay);
                continue;
            }
            put(sim, bay.frameId, dt * FabricationService.STRUCTURE_PER_HAND_SECOND);
        }
    }

    /** Stand a new machine on a bay's stocks, as far along as a keel is. */
    private void layKeel(BattleSimulation sim, FabricationService works,
                         FabricationService.Works bay) {
        Gantry stocks = works.gantry(bay.stocks);
        MechVariant chassis = bay.chassis;
        EntitySpec frame = new EntitySpec("mf" + (nextFrame++), works.lastBuilder(),
                UnitType.MACHINE_FRAME, stocks.centerX, stocks.centerY);
        // Unarmoured on purpose: plate is the last thing that goes on, and a
        // frame that arrived already proof against small arms would be a
        // production line an infantry assault could not touch.
        frame.maxHp(chassis.maxStructure);
        frame.hp(Math.max(1f, chassis.maxStructure * FabricationService.KEEL_FRACTION));
        frame.role(UnitRole.STRUCTURE);
        // No mech variant on the spec. A variant is a mech's installed hardware
        // and only a mech type accepts one; what this body needs from the
        // chassis is how much structure a finished one has, which is already in
        // its maximum. The bay holds the chassis until there is a machine to
        // give it to.
        works.lay(bay.siteId, sim.spawn(frame));
    }

    /** Put structure on a machine, up to what a finished one has. */
    private static void put(BattleSimulation sim, long frameId, float structure) {
        float built = Math.min(sim.world().maxHp(frameId),
                sim.world().hp(frameId) + structure);
        sim.world().setHp(frameId, built);
    }

    /** Roll out every machine that is whole, and clear its gantry. */
    private void rollOutWhatIsFinished(BattleSimulation sim, FabricationService works) {
        for (FabricationService.Works bay : works.bays()) {
            if (!bay.hasFrame() || bay.chassis == null) continue;
            if (sim.world().hp(bay.frameId) < sim.world().maxHp(bay.frameId)) continue;
            driveOut(sim, works, bay);
            works.rollOut(bay.siteId);
        }
    }

    /**
     * Finish the machine: the frame comes off the map and the chassis it was
     * stands where it stood, crewed and tasked.
     *
     * <p>Taken off the field rather than killed. A death here would owe a wreck
     * and a casualty nobody took, and would tell every system that counts losses
     * that the defender had just lost a machine at the moment they gained one.
     *
     * <p>What replaces it is an ordinary unit in an ordinary squad, which is the
     * contract every producer of battle actors owes: nothing about a machine
     * that was built here rather than delivered here changes how it fights, who
     * may command it, or how it dies.
     *
     * <p>Left <b>unclaimed</b> on purpose. A squad minted with a delivery arm's
     * claim outranks mission command and is visible to it but permanently
     * immovable by it, which is how a reinforcement that lands in the wrong
     * place stays there for the rest of the battle. A machine that walks out of
     * a shed under its own power is simply part of the garrison, and the side's
     * commander takes it like any other.
     */
    private void driveOut(BattleSimulation sim, FabricationService works,
                          FabricationService.Works bay) {
        Gantry stocks = works.gantry(bay.stocks);
        MechVariant chassis = bay.chassis;
        Faction builder = sim.identity().faction(bay.frameId);
        sim.takeOffTheField(bay.frameId);

        EntitySpec machine = new EntitySpec("f" + (nextMachine++), builder,
                UnitType.HEAVY_MECH, stocks.centerX, stocks.centerY);
        machine.mechVariant(chassis);
        machine.role(UnitRole.PATROL);
        machine.home(stocks.centerX, stocks.centerY);

        int squadId = sim.mintSquad(builder, UnitType.HEAVY_MECH);
        Squad squad = sim.getSquad(squadId);
        if (squad != null) {
            squad.assignedNode = homeNode(sim, stocks);
            squad.originalSize = 1;
            machine.squad(squad.id);
        }

        long unit = sim.spawn(machine);
        sim.world().attachMechLoadout(unit, chassis.createLoadout(chassis.defaultRole));
        LOG.info("FabricationSystem: " + builder + " " + chassis.displayName
                + " rolled out of the bay at " + stocks.centerX + "," + stocks.centerY);
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

package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.List;

/**
 * The company holding the compound's gate while a convoy loads there ({@code cordon-allied-command.md}, rule 2), as
 * the owner would see it: the town Road the probe plays, played once unrecorded to find the tick the first convoy
 * starts loading, then played again to just before it and recorded through the look preset following the leader of a
 * squad holding the gate, as the trucks pull in, load and turn for home.
 *
 * <p>{@code -Dsnapshot.cordonGate.seed=8} and {@code .ticks=900} choose the battle and the length; the shot opens
 * {@link #LEAD} ticks before loading starts. Opt-in: it plays a battle twice before it draws.
 */
public final class CordonGateLookSuite implements SnapshotSuite {

    /** The latest tick loading is waited for. */
    static final int BY = 9000;
    /** Ticks of the trucks' arrival the shot keeps before loading starts. */
    static final int LEAD = 150;
    /** Ticks between recorded frames. */
    static final int EVERY = 2;

    @Override public String id() { return "cordon-gate-look"; }

    @Override public String label() { return "Cordon: the company holding the compound's gate while a convoy loads"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        long seed = Long.parseLong(System.getProperty("snapshot.cordonGate.seed", "8").trim());
        int ticks = Integer.parseInt(System.getProperty("snapshot.cordonGate.ticks", "900").trim());
        BattleSimulation probe = battle(seed);
        while (!loading(probe)) {
            if (probe.getSimTickIndex() >= BY) throw new IllegalStateException("no convoy loading by tick " + BY);
            probe.advance(BattleSimulation.TICK_DT);
        }
        int from = Math.max(1, probe.getSimTickIndex() - LEAD);
        // Stills are counted in recorded frames, one every EVERY ticks.
        int frames = ticks / EVERY;
        return LookReview.record(context.modRoot(), new LookReview.Shot("gate", LookGround.of(SurfacePalette.ARID, seed),
                40, 30, placement -> {
                    BattleSimulation sim = battle(seed);
                    while (sim.getSimTickIndex() < from) sim.advance(BattleSimulation.TICK_DT);
                    Squad holder = gateHolder(sim);
                    placement.subject("gate", holder.leaderId);
                    System.out.printf("    [look/gate] seed %d: squad %d holds the gate, convoys %s, recorded from tick %d%n",
                            seed, holder.id, sim.getCordon().convoys().describe(sim), sim.getSimTickIndex());
                    return sim;
                }, placement -> List.of(
                        LookReview.View.follow("play", placement, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS, "gate"),
                        LookReview.View.follow("close", placement, 32f, "gate")), ticks, EVERY, 3)
                .withStills(0, frames / 4, frames / 2, 3 * frames / 4, frames - 1));
    }

    /** The battle the probe plays: the Road fixture's company on this seed's town map. */
    private static BattleSimulation battle(long seed) {
        try {
            return HeadlessBattles.build("cordon:variant=road,density=town,biome=arid,seed=" + seed);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean loading(BattleSimulation sim) {
        return sim.getCordon().convoys() != null && sim.getCordon().convoys().describe(sim).contains("loading");
    }

    /** The Marine squad on the gate whose leader stands nearest it. */
    private static Squad gateHolder(BattleSimulation sim) {
        List<int[]> gates = sim.getCordon().convoys().gates();
        if (gates.isEmpty()) throw new IllegalStateException("no gate held at tick " + sim.getSimTickIndex());
        int[] gate = gates.get(0);
        Squad best = null;
        double bestD = Double.MAX_VALUE;
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.MARINE || squad.aliveMembers <= 0 || squad.leaderId == 0L) continue;
            CommandDirective directive = sim.getCommanderService().activeDirective(squad.id);
            if (directive == null || !directive.reason().startsWith("CORDON_GATE")) continue;
            double d = Math.hypot(sim.world().x(squad.leaderId) - gate[0], sim.world().y(squad.leaderId) - gate[1]);
            if (d < bestD) {
                bestD = d;
                best = squad;
            }
        }
        if (best == null) throw new IllegalStateException("no squad holds the gate at tick " + sim.getSimTickIndex());
        return best;
    }
}

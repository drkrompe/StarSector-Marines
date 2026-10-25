package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.evacuation.SwarmBores;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.List;

/**
 * A forward observer calls the allied air wing onto a swarm mass (`air-nouns.md`, Forward observer), in the look
 * preset: an allied garrison team holding in the west with its observer, the wing on call flying in over the west
 * edge, and a Rav mass of runners and Brutes gathered in front of it, short of a spore colony nobody on the line has
 * seen. Nothing is scripted: the wing's own officer strikes the mass the observer has in sight, and the colony shoots
 * at whatever passes over it. Play zoom over the whole fight, and close on the mass. Logs every call and loss.
 */
public class ForwardObserverLookSuite implements SnapshotSuite {

    private static final int W = 84, H = 44;
    private static final int SECONDS = 40, EVERY_TICKS = 3;

    @Override public String id() { return "forward-observer-look"; }

    @Override public String label() { return "Look: an allied forward observer calls the air wing onto a Rav mass"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        LookReview.Shot shot = new LookReview.Shot("observer-strike", LookGround.verdant(), W, H, placement -> {
            SceneBuilder builder = placement.builder();
            int[] post = cell(placement, 10f, 22f), mass = cell(placement, 38f, 22f), colony = cell(placement, 62f, 15f);
            int x0 = placement.site().x0(), y0 = placement.site().y0();
            builder.squad("garrison").faction(Faction.ALLY).type(UnitType.MILITIA).size(4).at(post[0] - x0, post[1] - y0)
                    .primary("weapon.field-rifle").stationary()
                    .assigned(id -> ObjectiveAssignment.defendTrack(id, post[0], post[1])).done();
            builder.squad("swarm").faction(Faction.DEFENDER).type(UnitType.SWARM_RUNNER).size(16)
                    .at(mass[0] - x0, mass[1] - y0).unarmed().stationary()
                    .assigned(id -> ObjectiveAssignment.defendTrack(id, mass[0], mass[1])).done();
            builder.squad("brutes").faction(Faction.DEFENDER).type(UnitType.SWARM_BRUTE).size(3)
                    .at(mass[0] + 4 - x0, mass[1] - y0).unarmed().stationary()
                    .assigned(id -> ObjectiveAssignment.defendTrack(id, mass[0] + 4, mass[1])).done();
            SceneWorld world = builder.build();
            BattleSimulation sim = world.sim();
            sim.spawn(SwarmBores.bodySpec("spore colony", "structure.spore-colony-bore", colony[0], colony[1]));
            int garrison = world.squadId("garrison");
            ForwardObservers posts = AlliedAirWing.install(sim, placement.site().x(-40f), placement.site().y(22f),
                    List.of(s -> List.of(garrison)));
            placement.subject("swarm", world.members("swarm")[0]);
            int[] seen = {0};
            placement.beforeTick(s -> log(s, posts, seen));
            return sim;
        }, placement -> List.of(
                LookReview.View.play(placement.site().x(32f), placement.site().y(22f)),
                LookReview.View.follow("mass", placement, 30f, "swarm")),
                30 * SECONDS, EVERY_TICKS, 4);
        return LookReview.record(context.modRoot(), shot);
    }

    /** Each new line of the wing's ledger, as it happens. */
    private static void log(BattleSimulation sim, ForwardObservers posts, int[] seen) {
        List<AirPoolService.Event> events = sim.airPool().events();
        for (; seen[0] < events.size(); seen[0]++) {
            System.out.printf("    [forward-observer] t=%.1fs %s (observer %s)%n", sim.getSimTickIndex() / 30f,
                    events.get(seen[0]), observerState(sim, posts.observer(0)));
        }
    }

    /** Whether the observer is alive, still carries the radio, and how much fire is on it. */
    private static String observerState(BattleSimulation sim, long observer) {
        if (observer == 0L || !sim.world().isAlive(observer)) return "down";
        return String.format("alive, radio %s, pressure %.2f", CommsKit.carried(sim.world(), observer),
                sim.getRoster().combat().incomingPressure(observer, sim.getSimTickIndex()));
    }

    private static int[] cell(LookReview.Placement p, float x, float y) {
        float[] at = p.openNear(x, y);
        return new int[]{(int) at[0], (int) at[1]};
    }
}

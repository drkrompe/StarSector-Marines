package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
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
 * A roving observer team relocating and calling the allied air wing (`air-nouns.md`, Forward observer), in the look
 * preset: the team walks up from the west edge to its best vantage, where a Rav mass is gathered in sight, and the wing
 * strikes it; then a pack rushes the vantage, the team falls back to the next one, and calls the wing onto a second
 * mass it sees from there, out of sight of the first. Nothing is scripted but the pack's rush: the team's moves are {@link RovingObservers}' own and the strikes
 * the wing's officer's. The camera rides the observer at play zoom, with the whole ground beside it.
 */
public class RovingObserverLookSuite implements SnapshotSuite {

    private static final int W = 90, H = 50;
    private static final int SECONDS = 85, EVERY_TICKS = 3;
    /** When the pack is let go at the first vantage. */
    private static final int RUSH_TICK = 30 * 22;

    @Override public String id() { return "roving-observer-look"; }

    @Override public String label() { return "Look: a roving observer team relocates and calls the air wing onto a Rav mass"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        LookReview.Shot shot = new LookReview.Shot("roving-observer", LookGround.verdant(), W, H, placement -> {
            SceneBuilder builder = placement.builder();
            int[] home = cell(placement, 4f, 30f), first = cell(placement, 34f, 16f), second = cell(placement, 24f, 38f);
            int[] mass = cell(placement, 58f, 14f), drift = cell(placement, 52f, 44f), pack = cell(placement, 74f, 22f);
            int x0 = placement.site().x0(), y0 = placement.site().y0();
            builder.squad("mass").faction(Faction.DEFENDER).type(UnitType.SWARM_RUNNER).size(14)
                    .at(mass[0] - x0, mass[1] - y0).unarmed().stationary()
                    .assigned(id -> ObjectiveAssignment.defendTrack(id, mass[0], mass[1])).done();
            builder.squad("drift").faction(Faction.DEFENDER).type(UnitType.SWARM_RUNNER).size(12)
                    .at(drift[0] - x0, drift[1] - y0).unarmed().stationary()
                    .assigned(id -> ObjectiveAssignment.defendTrack(id, drift[0], drift[1])).done();
            builder.squad("pack").faction(Faction.DEFENDER).type(UnitType.SWARM_RUNNER).size(10)
                    .at(pack[0] - x0, pack[1] - y0).unarmed()
                    .assigned(id -> ObjectiveAssignment.defendTrack(id, pack[0], pack[1])).done();
            SceneWorld world = builder.build();
            BattleSimulation sim = world.sim();
            AlliedAirWing.install(sim, home[0], home[1], List.of());
            RovingObservers teams = new RovingObservers(Faction.ALLY, home[0], home[1],
                    s -> List.of(first, second), 7L, 0);
            sim.airPool().rovingFrom(teams);
            teams.tick(sim);
            placement.subject("observer", teams.observers().get(0));
            int packSquad = world.squadId("pack");
            int[] seen = {0};
            boolean[] rushed = {false};
            placement.beforeTick(s -> {
                if (!rushed[0] && s.getSimTickIndex() >= RUSH_TICK) {
                    rushed[0] = true;
                    s.assignSquadCommand(ObjectiveAssignment.attackMove(packSquad, first[0], first[1]),
                            CommandAuthority.SCRIPTED, "look", "the pack rushes the vantage");
                }
                log(s, teams, seen);
            });
            return sim;
        }, placement -> List.of(
                LookReview.View.followAtPlay(placement, "observer"),
                LookReview.View.close("whole", placement.site().x(W / 2f), placement.site().y(H / 2f), W)),
                30 * SECONDS, EVERY_TICKS, 4);
        return LookReview.record(context.modRoot(), shot);
    }

    /** Each new line of the wing's ledger and each move of the team, as it happens. */
    private static void log(BattleSimulation sim, RovingObservers teams, int[] seen) {
        List<AirPoolService.Event> events = sim.airPool().events();
        for (; seen[0] < events.size(); seen[0]++) {
            System.out.printf("    [roving-observer] t=%.1fs %s, team moves %d%n", sim.getSimTickIndex() / 30f,
                    events.get(seen[0]), teams.moves());
        }
    }

    private static int[] cell(LookReview.Placement p, float x, float y) {
        float[] at = p.openNear(x, y);
        return new int[]{(int) at[0], (int) at[1]};
    }
}

package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.vehicle.VehicleStockFit;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.allied.AlliedCommand;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The heart's allied tools as the owner would see them (`heart-allied-forces.md`), one look recording each on real
 * generated ground: the tools a logistics base could send up against a swarm pack coming in from the east. {@code
 * allies-armour}: a tank pair and an APC driven up from the west. {@code allies-gunship}: an allied gunship on station
 * over the pack. {@code allies-ammo}: two supply trucks running up to a dug-in Locust battery. {@code allies-mechs}: a
 * pair of Hounds. Each at play zoom on the fight. Opt-in: a design question, not part of {@code all}.
 */
public final class HeartAlliesLookSuite implements SnapshotSuite {

    static final int W = 80, H = 40, TICKS = 600;
    /** Frames (every 6 ticks) kept as stills. */
    static final int[] STILLS = {15, 35, 60};

    @Override public String id() { return "heart-allies-look"; }

    @Override public String label() { return "Heart allied tools: armour, gunship, ammunition trucks and mechs on real ground"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        out.addAll(LookReview.record(context.modRoot(), shot("allies-armour", (sim, p) -> {
            AlliedCommand command = new AlliedCommand("look");
            int[] a = cell(p, 14f, 16f), b = cell(p, 14f, 24f), c = cell(p, 10f, 20f), goal = cell(p, 36f, 20f);
            p.subject("allies", command.deploy(sim, VehicleType.BREAKTHROUGH_TANK, a[0], a[1], 0f));
            long tank = command.deploy(sim, VehicleType.BREAKTHROUGH_TANK, b[0], b[1], 0f);
            long apc = command.deploy(sim, VehicleStockFit.CONVOY_ESCORT, c[0], c[1], 0f);
            command.station(sim, p.subjects().get("allies"), goal[0], goal[1] - 4);
            command.station(sim, tank, goal[0], goal[1] + 4);
            command.station(sim, apc, goal[0] - 8, goal[1]);
        })));
        out.addAll(LookReview.record(context.modRoot(), shot("allies-gunship", (sim, p) -> {
            float[] over = p.openNear(46f, 20f);
            p.subject("allies", sim.spawnGunship(ShuttleType.MUDSKIPPER, Faction.ALLY, over[0], over[1],
                    p.site().x(-12f), over[1], p.site().x(-12f), over[1]));
        })));
        out.addAll(LookReview.record(context.modRoot(), shot("allies-ammo", (sim, p) -> {
            AlliedCommand command = new AlliedCommand("look");
            int[] gun = cell(p, 30f, 20f), t1 = cell(p, 8f, 18f), t2 = cell(p, 6f, 23f);
            p.subject("allies", sim.spawn(MapTurret.create("battery", Faction.MARINE, "structure.battery-locust",
                    gun[0], gun[1])));
            long a = command.deploy(sim, VehicleType.SUPPLY_TRUCK, t1[0], t1[1], 0f);
            long b = command.deploy(sim, VehicleType.SUPPLY_TRUCK, t2[0], t2[1], 0f);
            command.station(sim, a, gun[0] - 3, gun[1] - 2);
            command.station(sim, b, gun[0] - 3, gun[1] + 2);
        })));
        out.addAll(LookReview.record(context.modRoot(), shot("allies-mechs", (sim, p) -> { })));
        return out;
    }

    /** A shot: the swarm pack in the east, the option's tool staged by {@code stage}. */
    static LookReview.Shot shot(String name, BiConsumer<BattleSimulation, LookReview.Placement> stage) {
        return LookReview.Shot.of(name, W, H, placement -> {
            SceneBuilder builder = placement.builder();
            int[] pack = cell(placement, 58f, 20f), toward = cell(placement, 14f, 20f);
            builder.squad("swarm").faction(Faction.DEFENDER).type(UnitType.SWARM_RUNNER).size(14)
                    .at(pack[0] - placement.site().x0(), pack[1] - placement.site().y0())
                    .assigned(squad -> ObjectiveAssignment.attackMove(squad, toward[0], toward[1])).done();
            builder.squad("brutes").faction(Faction.DEFENDER).type(UnitType.SWARM_BRUTE).size(3)
                    .at(pack[0] + 4 - placement.site().x0(), pack[1] - placement.site().y0())
                    .assigned(squad -> ObjectiveAssignment.attackMove(squad, toward[0], toward[1])).done();
            if (name.equals("allies-mechs")) {
                int[] m = cell(placement, 22f, 20f);
                builder.squad("hounds").faction(Faction.ALLY).size(2).mech(MechVariant.HOUND, MechRole.BALANCED)
                        .at(m[0] - placement.site().x0(), m[1] - placement.site().y0()).done();
            }
            BattleSimulation sim = builder.build().sim();
            stage.accept(sim, placement);
            return sim;
        }, placement -> List.of(LookReview.View.play(placement.site().x(40f), placement.site().y(20f))), TICKS)
                .withStills(STILLS);
    }

    private static int[] cell(LookReview.Placement p, float x, float y) {
        float[] at = p.openNear(x, y);
        return new int[]{(int) at[0], (int) at[1]};
    }
}

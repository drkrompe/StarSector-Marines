package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Onager's modular turret heads ({@code vehicle-card-sizes.md}, {@code convoy-nouns.md}): every ballistic part its
 * gun card takes, fitted, on real ground at three poses, with and without the active-protection kit; then worn turret
 * plate, the bare ring and wrecks. Owner-facing look frames only; there is no verdict.
 */
public final class TankHeadsSnapshotSuite implements SnapshotSuite {

    /** A row of the lineup: what is in the gun card, and whether the rack carries active protection. */
    record Row(String key, MechWeaponComponent gun, boolean activeProtection) { }

    /** Every ballistic part the Onager's card takes, in the close views' groups; null is the empty card. */
    static final List<Row> ROWS = List.of(
            new Row("empty", null, false),
            new Row("heavy-cannon", MechWeaponComponent.SINGLE_HEAVY_CANNON, false),
            new Row("heavy-cannon-aps", MechWeaponComponent.SINGLE_HEAVY_CANNON, true),
            new Row("bastion", MechWeaponComponent.BASTION_AUTOCANNON, false),
            new Row("muster", MechWeaponComponent.MUSTER_AUTOGUN, false),
            new Row("demolition", MechWeaponComponent.DEMOLITION_CANNON, false),
            new Row("quarry", MechWeaponComponent.QUARRY_BREAKER_CANNON, false),
            new Row("bastion-aps", MechWeaponComponent.BASTION_AUTOCANNON, true),
            new Row("linear", MechWeaponComponent.LINEAR_CANNON, false),
            new Row("chaingun", MechWeaponComponent.CHAINGUN, false),
            new Row("chaingun-hf", MechWeaponComponent.CHAINGUN_HF, false),
            new Row("lmg", MechWeaponComponent.LIGHT_MACHINE_GUN, false));

    /** The three poses, as hull heading and turret bearing: turret ahead, turret across, hull and turret turned away. */
    static final float[][] POSES = {{30f, 30f}, {30f, 120f}, {250f, 200f}};

    /** The close views: rows shown together, by head family. */
    private static final List<List<String>> GROUPS = List.of(
            List.of("empty", "heavy-cannon", "heavy-cannon-aps"),
            List.of("bastion", "muster", "demolition"),
            List.of("quarry", "bastion-aps", "linear"),
            List.of("chaingun", "chaingun-hf", "lmg"));
    private static final List<String> GROUP_NAMES = List.of("cannon", "autocannon", "breaker-aps-rail", "rotary");

    private static final float COLUMN = 8f;
    private static final float ROW = 7.5f;

    @Override public String id() { return "tank-heads"; }

    @Override public String label() { return "Onager modular turret heads, worn plate and wrecks"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> artifacts = new ArrayList<>(LookReview.record(context.modRoot(), lineup()));
        artifacts.addAll(LookReview.record(context.modRoot(), wear()));
        return List.copyOf(artifacts);
    }

    /** Each row at the three poses, held still, every gun silent. */
    static LookReview.Shot lineup() {
        int width = (int) (COLUMN * POSES.length + 8);
        int height = (int) (ROW * ROWS.size() + 6);
        return new LookReview.Shot("onager-heads", LookGround.verdant(), width, height, placement -> {
            BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
            List<Consumer<BattleSimulation>> holds = new ArrayList<>();
            for (int r = 0; r < ROWS.size(); r++) {
                Row row = ROWS.get(r);
                for (int c = 0; c < POSES.length; c++) {
                    float[] at = placement.openNear(4f + COLUMN * (c + .5f), 3f + ROW * (r + .5f));
                    long tank = spawn(sim, row.gun(), row.activeProtection(), at);
                    placement.subject(row.key() + c, tank);
                    holds.add(hold(tank, at, POSES[c][0], POSES[c][1]));
                }
            }
            placement.beforeTick(s -> holds.forEach(h -> h.accept(s)));
            return sim;
        }, placement -> {
            List<LookReview.View> views = new ArrayList<>();
            List<String> all = new ArrayList<>();
            for (Row row : ROWS) for (int c = 0; c < POSES.length; c++) all.add(row.key() + c);
            views.add(LookReview.View.followAtPlay(placement, all.toArray(new String[0])));
            for (int g = 0; g < GROUPS.size(); g++) {
                List<String> keys = new ArrayList<>();
                for (String key : GROUPS.get(g)) for (int c = 0; c < POSES.length; c++) keys.add(key + c);
                views.add(LookReview.View.follow(GROUP_NAMES.get(g), placement, 28f, keys.toArray(new String[0])));
            }
            return views;
        }, 12, 6, 1, new int[] {1});
    }

    /** What a worn turret, a bare ring and a wreck look like with a head and with a headless part. */
    static LookReview.Shot wear() {
        record Tank(String key, MechWeaponComponent gun, float armorShare, boolean wrecked) { }
        List<Tank> tanks = Arrays.asList(
                new Tank("hc-fresh", MechWeaponComponent.SINGLE_HEAVY_CANNON, 1f, false),
                new Tank("hc-worn", MechWeaponComponent.SINGLE_HEAVY_CANNON, .2f, false),
                new Tank("bastion-worn", MechWeaponComponent.BASTION_AUTOCANNON, .2f, false),
                new Tank("bare-worn", null, .2f, false),
                new Tank("hc-wreck", MechWeaponComponent.SINGLE_HEAVY_CANNON, 1f, true),
                new Tank("bastion-wreck", MechWeaponComponent.BASTION_AUTOCANNON, 1f, true));
        return new LookReview.Shot("onager-wear", LookGround.verdant(), 30, 16, placement -> {
            BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
            List<Consumer<BattleSimulation>> holds = new ArrayList<>();
            for (int i = 0; i < tanks.size(); i++) {
                Tank t = tanks.get(i);
                float[] at = placement.openNear(5f + 7f * (i % 3), 4f + 7f * (i / 3));
                long tank = spawn(sim, t.gun(), false, at);
                sim.convoy().locations(tank).stageArmorShare(t.armorShare());
                placement.subject(t.key(), tank);
                if (t.wrecked()) {
                    sim.convoy().body(tank).teleport(at[0], at[1], 30f);
                    if (sim.convoy().turret(tank) != null) sim.convoy().turret(tank).facingDeg = 75f;
                    sim.applyDamage(tank, 100000, 100000);
                } else {
                    holds.add(hold(tank, at, 30f, 75f));
                }
            }
            placement.beforeTick(s -> holds.forEach(h -> h.accept(s)));
            return sim;
        }, placement -> {
            List<String> keys = new ArrayList<>();
            for (Tank t : tanks) keys.add(t.key());
            return List.of(LookReview.View.followAtPlay(placement, keys.toArray(new String[0])),
                    LookReview.View.follow("close", placement, 24f, keys.toArray(new String[0])));
        }, 60, 6, 1, new int[] {9});
    }

    private static long spawn(BattleSimulation sim, MechWeaponComponent gun, boolean activeProtection, float[] at) {
        List<VehicleKit> kits = activeProtection ? List.of(VehicleKit.ACTIVE_PROTECTION) : List.of();
        long tank = sim.convoy().spawn(new VehicleDeploymentSpec(null, null, VehicleType.BREAKTHROUGH_TANK, kits,
                VehiclePlating.FACTORY, VehicleEngine.STOCK, gun), Faction.MARINE, VehicleMission.deployed(at[0], at[1]));
        GroundTurret turret = sim.convoy().turret(tank);
        if (turret != null) {
            // Silent: these frames are about how the head looks, not what it hits.
            turret.ammo = 0;
            turret.unlimited = false;
            turret.loaderSeconds = Float.POSITIVE_INFINITY;
        }
        return tank;
    }

    /** Pins a hull and its turret to a pose every tick: a deployed hull drifts and an idle turret settles. */
    private static Consumer<BattleSimulation> hold(long tank, float[] at, float hull, float turret) {
        return sim -> {
            sim.convoy().body(tank).teleport(at[0], at[1], hull);
            GroundTurret t = sim.convoy().turret(tank);
            if (t != null) t.facingDeg = turret;
        };
    }
}

package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * The blast impulse loops, from the same run that produced their verdicts, and two look shots on real ground: an
 * SRM-15 (Frag) salvo on a marine squad and then an SRM-15's anti-armor salvo on the scout car beside it; and a heavy
 * mortar round on the squad and then a DC-5 shell between the squad and the car. The rounds come in from off the
 * west edge, so what is seen is the blasts and what they shove. Under {@code -Dbattle.blastImpulse=false} the same
 * rounds land and nothing moves.
 */
public final class BlastImpulseSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public BlastImpulseSceneSnapshotSuite() {
        super(new BlastImpulseScene(), 1920, 1080);
    }

    static final int LOOK_W = 40, LOOK_H = 24, LOOK_TICKS = 240;
    private static final float DEEP = 50_000f;
    private static final float[][] SQUAD = {{0f, 0f}, {-.75f, .3f}, {.7f, .35f}, {-.6f, -.55f}, {.55f, -.6f}};
    private static final float[][] SCATTER = {{0f, 0f}, {.3f, .15f}, {-.25f, .25f}, {.15f, -.3f}, {-.2f, -.15f}};

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }

    @Override protected List<LookReview.Shot> lookShots() {
        return List.of(
                LookReview.Shot.of("srm-salvo-squad-and-car", LOOK_W, LOOK_H, p -> stage(p, true),
                        BlastImpulseSceneSnapshotSuite::views, LOOK_TICKS).withStills(3, 7, 9, 18, 22, 26),
                LookReview.Shot.of("mortar-and-dc5-squad-and-car", LOOK_W, LOOK_H, p -> stage(p, false),
                        BlastImpulseSceneSnapshotSuite::views, LOOK_TICKS).withStills(3, 7, 9, 22, 26, 30));
    }

    private static List<LookReview.View> views(LookReview.Placement placement) {
        return List.of(LookReview.View.followAtPlay(placement, "m0", "car"),
                LookReview.View.follow("close", placement, 14f, "m0", "car"));
    }

    /** One round: when it is fired, from where, to where. */
    private record Round(int tick, WeaponDef weapon, float fromX, float fromY, float toX, float toY) {}

    /**
     * A squad of five standing close in the open, a scout car three cells east of it, and the rounds: missiles,
     * or a mortar round and a DC-5 shell.
     */
    private static BattleSimulation stage(LookReview.Placement placement, boolean missiles) {
        float[] at = placement.openNear(LOOK_W / 2f - 2f, LOOK_H / 2f);
        SceneBuilder builder = placement.holdouts(placement.builder());
        int lx = (int) at[0] - placement.site().x0(), ly = (int) at[1] - placement.site().y0();
        for (int i = 0; i < SQUAD.length; i++) {
            builder.unit("m" + i, Faction.MARINE, UnitType.MARINE, lx, ly, spec -> spec.health(DEEP).moveSpeed(0f));
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        float sx = at[0], sy = at[1];
        for (int i = 0; i < SQUAD.length; i++) {
            long id = world.unit("m" + i);
            sim.world().setPos(id, sx + SQUAD[i][0], sy + SQUAD[i][1]);
            placement.subject("m" + i, id);
        }
        float[] carAt = placement.openNear(lx + 3.5f, ly);
        long car = sim.convoy().spawn(VehicleType.SCOUT_CAR, Faction.MARINE, VehicleMission.deployed(carAt[0], carAt[1]));
        sim.convoy().body(car).teleport(carAt[0], carAt[1], 0f);
        sim.world().setMaxHp(car, DEEP);
        sim.world().setHp(car, DEEP);
        placement.subject("car", car);

        float fromX = sx - 26f, fromY = sy + 4f;
        List<Round> rounds = new ArrayList<>();
        if (missiles) {
            WeaponDef frag = WeaponRegistry.require("weapon.mech-srm-frag");
            WeaponDef at15 = WeaponRegistry.require("weapon.mech-srm");
            for (int i = 0; i < 15; i++) {
                float[] off = SCATTER[i % SCATTER.length];
                rounds.add(new Round(20 + (i / 5) * 18 + (i % 5) * 3, frag, fromX, fromY, sx + off[0], sy + off[1]));
            }
            for (int i = 0; i < 15; i++) {
                float[] off = SCATTER[i % SCATTER.length];
                rounds.add(new Round(130 + (i / 5) * 18 + (i % 5) * 3, at15, fromX, fromY,
                        carAt[0] - .9f + off[0] * .5f, carAt[1] + off[1]));
            }
        } else {
            rounds.add(new Round(20, WeaponRegistry.require("weapon.vehicle-mortar"), fromX, fromY, sx + .3f, sy + .2f));
            rounds.add(new Round(120, WeaponRegistry.require("weapon.mech-dc-5"), fromX, fromY,
                    (sx + carAt[0]) / 2f, sy));
        }
        placement.beforeTick(s -> {
            int tick = s.getSimTickIndex();
            for (Round r : rounds) {
                if (r.tick() == tick) fire(s, r);
            }
        });
        return sim;
    }

    /** Fires a round as a mech or turret would: a travelling round whose burst lands with it. */
    private static void fire(BattleSimulation sim, Round r) {
        WeaponDef w = r.weapon();
        float distance = (float) Math.hypot(r.toX() - r.fromX(), r.toY() - r.fromY());
        float speed = w.roundVelocity > 0f ? w.roundVelocity : w.directRoundVelocity();
        float flight = Math.max(.1f, distance / speed);
        PendingDetonation det = new PendingDetonation(CombatTelemetryService.NO_ATTACKER, r.toX(), r.toY(), flight,
                w.aoeRadius, w.damage, w.penetration, w.wallDamage, Faction.DEFENDER, w.arcHeight > 0f,
                w.wallDamageRadius, true, false).withBlast(w.blast());
        sim.getShots().queueProjectile(new Projectile(r.fromX(), r.fromY(), r.toX(), r.toY(), w.boostRamp,
                w.arcHeight, Faction.DEFENDER, w.arcHeight > 0f, flight, det, w.id, w.pointDefenseTarget));
        sim.getShots().postShot(new ShotEvent(CombatTelemetryService.NO_ATTACKER, r.fromX(), r.fromY(), r.toX(),
                r.toY(), true, Faction.DEFENDER, flight, null, null, null, w, 1f));
    }
}

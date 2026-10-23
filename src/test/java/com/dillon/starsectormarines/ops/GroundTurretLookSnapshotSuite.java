package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.EmplacementStyle;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Opt-in look study for the ground turrets: the point emplacements the generator stands (the Vulcan, Arbalest and
 * grenade-launcher structures, which carry the autogun, cannon and grenade art), the carried point-defence pod, and the
 * hardpoints ({@code structure.hardpoint-cannon}, {@code -locust}) on real generated ground through {@link LookReview},
 * with a marine squad beside them for scale, once per {@link EmplacementStyle}. Each turret is given the style the
 * way a generated post's is, so the frame shows what the renderer resolves from the structure's {@code looks}.
 * {@code -Dsnapshot.groundTurrets.looks=} and {@code -Dsnapshot.groundTurrets.biomes=} narrow the run.
 *
 * <p>A {@code firing-<look>} shot per look plays the guns against targets for six seconds, so barrel recoil and
 * muzzle effects can be judged in motion; {@code -Dsnapshot.groundTurrets.firing=false} leaves it out.
 */
public final class GroundTurretLookSnapshotSuite implements SnapshotSuite {

    /** Role in the scene's layout to the catalog structure that fills it. */
    private static final Map<String, String> ROLES = Map.of(
            "heavy", "structure.hardpoint-cannon",
            "locust", "structure.hardpoint-locust",
            "autogun", "structure.turret-vulcan",
            "cannon", "structure.turret-arbalest",
            "grenade", "structure.turret-grenade-launcher",
            "pd", "structure.deployable-point-defence");

    @Override public String id() { return "ground-turret-look"; }

    @Override public String label() { return "Ground turret looks (look study)"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<String> looks = new ArrayList<>();
        for (String look : System.getProperty("snapshot.groundTurrets.looks", "brutalist,earthwork,derelict").split(",")) {
            looks.add(look.trim());
        }
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        for (String biome : System.getProperty("snapshot.groundTurrets.biomes", "verdant,arid,frozen").split(",")) {
            SurfacePalette surface = SurfacePalette.valueOf(biome.trim().toUpperCase());
            for (String look : looks) {
                artifacts.addAll(LookReview.record(context.modRoot(), shot(look, biome.trim(), surface)));
            }
        }
        if (Boolean.parseBoolean(System.getProperty("snapshot.groundTurrets.firing", "true"))) {
            for (String look : looks) artifacts.addAll(LookReview.record(context.modRoot(), firing(look)));
        }
        return artifacts;
    }

    /**
     * A 40x22 window: a heavy-cannon hardpoint and a Locust hardpoint side by side, four point emplacements (autogun,
     * light cannon, grenade launcher, carried point-defence pod) round them, and a six-marine squad standing between the
     * hardpoints. The hardpoints' guns are laid away from their stairs. Only tick zero is drawn, so the heads hold
     * the bearings set here and no launch smoke covers them.
     */
    private static LookReview.Shot shot(String look, String biome, SurfacePalette surface) {
        LookGround ground = LookGround.of(surface, 7L);
        return new LookReview.Shot(look + "-" + biome, ground, 40, 22, placement -> {
            SceneBuilder builder = placement.builder();
            float[] squad = placement.openNear(20f, 12f);
            builder.squad("squad").faction(Faction.MARINE).size(6)
                    .at((int) squad[0] - placement.site().x0(), (int) squad[1] - placement.site().y0())
                    .primary("weapon.field-rifle").stationary().done();
            placement.holdouts(builder);
            SceneWorld world = builder.build();
            BattleSimulation sim = world.sim();
            int i = 0;
            Object[][] layout = {
                    {"heavy", 12f, 11f, 25f}, {"locust", 28f, 11f, 330f},
                    {"autogun", 17f, 4f, 330f}, {"cannon", 23f, 18f, 120f},
                    {"grenade", 8f, 5f, 290f}, {"pd", 31f, 17f, 60f}};
            for (Object[] spot : layout) {
                float[] at = placement.openNear((Float) spot[1], (Float) spot[2]);
                int x = (int) at[0];
                int y = (int) at[1];
                long id = sim.spawn(MapTurret.create("gt" + i++, Faction.MARINE, ROLES.get((String) spot[0]), x, y));
                sim.setStructureStyle(id, EmplacementStyle.valueOf(look.toUpperCase()));
                sim.getGrid().setWalkable(x, y, false);
                sim.turretState().setFacingDegrees(id, (Float) spot[3]);
            }
            return sim;
        }, placement -> List.of(
                LookReview.View.play(placement.site().centreX(), placement.site().centreY()),
                LookReview.View.close("close", placement.site().centreX(), placement.site().centreY(), 30f)),
                1, 2, 1);
    }

    /** Seconds of fire a firing shot plays, at one frame a tick. */
    private static final int FIRING_TICKS = 180;

    /**
     * A 40x22 window on arid ground: the autogun, cannon and grenade emplacements in a column at the west with the
     * heavy-cannon hardpoint behind them, all defenders, and a line of marine targets sixteen cells east that outlast
     * the run (bodies, not squads, so only the guns fire). Played for {@link #FIRING_TICKS} ticks at one frame a tick
     * so a barrel's kick and a muzzle's flash land in the animation.
     */
    private static LookReview.Shot firing(String look) {
        LookGround ground = LookGround.of(SurfacePalette.ARID, 7L);
        return new LookReview.Shot("firing-" + look, ground, 40, 22, placement -> {
            SceneBuilder builder = placement.builder();
            placement.holdouts(builder);
            BattleSimulation sim = builder.build().sim();
            Object[][] guns = {{"autogun", 16f, 8f}, {"cannon", 16f, 11f}, {"grenade", 16f, 14f}, {"heavy", 11f, 11f}};
            int i = 0;
            for (Object[] gun : guns) {
                float[] at = placement.openNear((Float) gun[1], (Float) gun[2]);
                int x = (int) at[0];
                int y = (int) at[1];
                long id = sim.spawn(MapTurret.create("fire" + i++, Faction.DEFENDER, ROLES.get((String) gun[0]), x, y));
                sim.setStructureStyle(id, EmplacementStyle.valueOf(look.toUpperCase()));
                sim.getGrid().setWalkable(x, y, false);
                sim.turretState().setFacingDegrees(id, 90f);
            }
            for (int t = 0; t < 5; t++) {
                float[] at = placement.openNear(31f, 7f + t * 2f);
                EntitySpec target = new EntitySpec("target" + t, Faction.MARINE, UnitType.MARINE, (int) at[0], (int) at[1]);
                target.moveSpeed = 0f;
                target.hp = target.maxHp = 4000f;
                sim.spawn(target);
            }
            return sim;
        }, placement -> List.of(
                LookReview.View.close("close", placement.site().x(21f), placement.site().y(11f), 26f)),
                FIRING_TICKS, 1, 3);
    }
}

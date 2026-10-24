package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.EmplacementStyle;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.ShieldTint;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.turret.TurretShieldService;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
 * {@code -Dsnapshot.groundTurrets.firingSeconds=} plays it longer (the heavy cannon fires about once in five seconds,
 * so its muzzle scorch only builds up over half a minute), and {@code -Dsnapshot.groundTurrets.firingStills=}
 * keeps those recorded frames as its stills instead of the busiest moments.
 *
 * <p>A {@code shield-<look>} shot, for each look that fits the hardpoint's flux shield, puts a squad in front of the
 * cannon hardpoint that shoots back and a marine on its flank, for {@code -Dsnapshot.groundTurrets.shieldSeconds=}
 * (twenty by default): the arc rising to face the squad, its blocks, its break and its return, and the flank's rounds
 * going through. {@code -Dsnapshot.groundTurrets.shield=false} leaves it out.
 *
 * <p>{@code -Dsnapshot.groundTurrets.fluxHardware=brutalist,modular,composite} adds a {@code flux-<style>} shot per
 * style named: three cannon hardpoints with their flux hardware cold, at about 80% and just broken.
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
        StringBuilder every = new StringBuilder();
        for (EmplacementStyle style : EmplacementStyle.values()) every.append(every.length() > 0 ? "," : "").append(style.key);
        for (String look : System.getProperty("snapshot.groundTurrets.looks", every.toString()).split(",")) {
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
        if (Boolean.parseBoolean(System.getProperty("snapshot.groundTurrets.shield", "true"))) {
            StructureDef heavy = TurretCatalogRegistry.requireStructure(ROLES.get("heavy"));
            for (String look : looks) {
                if (heavy.fluxShieldIn(EmplacementStyle.valueOf(look.toUpperCase())) == null) continue;
                artifacts.addAll(LookReview.record(context.modRoot(), shieldUnderFire(look)));
            }
        }
        String hardware = System.getProperty("snapshot.groundTurrets.fluxHardware", "");
        if (!hardware.isBlank()) {
            for (String style : hardware.split(",")) {
                artifacts.addAll(LookReview.record(context.modRoot(), fluxHardware(style.trim())));
            }
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
                long id = sim.spawn(MapTurret.create("gt" + i++, Faction.MARINE, built((String) spot[0], look), x, y));
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

    /** Ticks of fire a firing shot plays, at one frame a tick: six seconds unless asked for longer. */
    private static int firingTicks() {
        float seconds = Float.parseFloat(System.getProperty("snapshot.groundTurrets.firingSeconds", "6"));
        return Math.round(seconds / BattleSimulation.TICK_DT);
    }

    /**
     * A 40x22 window on arid ground: the autogun, cannon and grenade emplacements in a column at the west with the
     * heavy-cannon hardpoint behind them, all defenders, and a line of marine targets sixteen cells east that outlast
     * the run (bodies, not squads, so only the guns fire). Two of the guns start hurt, for their damage smoke. Played for {@link #firingTicks()} ticks at one frame a tick
     * so a barrel's kick and a muzzle's flash land in the animation.
     */
    private static LookReview.Shot firing(String look) {
        LookGround ground = LookGround.of(SurfacePalette.ARID, 7L);
        return new LookReview.Shot("firing-" + look, ground, 40, 22, placement -> {
            SceneBuilder builder = placement.builder();
            placement.holdouts(builder);
            BattleSimulation sim = builder.build().sim();
            // Role, local x and y, and the share of its structure left: the grenade launcher is worn enough to smoke and
            // the hardpoint hurt enough to throw sparks as well.
            Object[][] guns = {{"autogun", 16f, 8f, 1f}, {"cannon", 16f, 11f, 1f}, {"grenade", 16f, 14f, .4f},
                    {"heavy", 11f, 11f, .2f}};
            int i = 0;
            for (Object[] gun : guns) {
                float[] at = placement.openNear((Float) gun[1], (Float) gun[2]);
                int x = (int) at[0];
                int y = (int) at[1];
                long id = sim.spawn(MapTurret.create("fire" + i++, Faction.DEFENDER, built((String) gun[0], look), x, y));
                sim.setStructureStyle(id, EmplacementStyle.valueOf(look.toUpperCase()));
                sim.getGrid().setWalkable(x, y, false);
                sim.turretState().setFacingDegrees(id, 90f);
                sim.world().setHp(id, sim.world().maxHp(id) * (Float) gun[3]);
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
                firingTicks(), 1, 3).withStills(firingStills());
    }

    /**
     * A 40x22 window on arid ground: the cannon hardpoint at the west, a six-marine squad with two rocket launchers
     * sixteen cells east of it, outside its dead zone, and one rifleman nine cells north on its flank, inside it. Both
     * sides fire, and every body, the gun's too, has the hit points to outlast the run. Played at a frame every two ticks.
     */
    private static LookReview.Shot shieldUnderFire(String look) {
        LookGround ground = LookGround.of(SurfacePalette.ARID, 7L);
        float seconds = Float.parseFloat(System.getProperty("snapshot.groundTurrets.shieldSeconds", "20"));
        return new LookReview.Shot("shield-" + look, ground, 40, 22, placement -> {
            SceneBuilder builder = placement.builder();
            float[] front = placement.openNear(28f, 11f);
            builder.squad("front").faction(Faction.MARINE).size(6)
                    .at((int) front[0] - placement.site().x0(), (int) front[1] - placement.site().y0())
                    .primary("weapon.field-rifle").special("special.rocket-launcher", 2, 6)
                    .health(4000f).stationary().done();
            float[] flank = placement.openNear(12f, 2f);
            builder.squad("flank").faction(Faction.MARINE).size(1)
                    .at((int) flank[0] - placement.site().x0(), (int) flank[1] - placement.site().y0())
                    .primary("weapon.field-rifle").health(4000f).stationary().done();
            placement.holdouts(builder);
            BattleSimulation sim = builder.build().sim();
            float[] at = placement.openNear(12f, 11f);
            int x = (int) at[0];
            int y = (int) at[1];
            long id = sim.spawn(MapTurret.create("shielded", Faction.DEFENDER, built("heavy", look), x, y));
            sim.setStructureStyle(id, EmplacementStyle.valueOf(look.toUpperCase()));
            sim.getGrid().setWalkable(x, y, false);
            sim.turretState().setFacingDegrees(id, 90f);
            // Hit points to outlast the run, so the shield's return is in the frame rather than the gun's wreck.
            sim.world().setMaxHp(id, 4000f);
            sim.world().setHp(id, 4000f);
            return sim;
        }, placement -> List.of(
                LookReview.View.close("close", placement.site().x(20f), placement.site().y(9f), 30f)),
                Math.round(seconds / BattleSimulation.TICK_DT), 2, 4)
                // Recorded frames, at two ticks each: blocking, the break, the gun silent, the shield back up. The
                // battle is deterministic, so these hold until the shield's numbers or the squad's kit change.
                .withStills(60, 177, 220, 290);
    }

    /**
     * Three cannon hardpoints built in {@code style} on arid ground, each with a flux shield fitted and its hardware
     * on the plinth: idle and cold, at about 80% charge, and just broken and venting. A body of their own side stands
     * to the east, which the guns will not fire on; rounds credited to it raise the shields toward it and charge the
     * capacitors, so nothing in the frame is firing.
     * Styles whose builders fit no shield yet (the low and midline ones) are fitted one in orange for the study.
     * Played a little over a second and stilled at one, so the break has played out and the third gun shows no shield,
     * only its radiator venting; play and close views.
     */
    private static LookReview.Shot fluxHardware(String style) {
        LookGround ground = LookGround.of(SurfacePalette.ARID, 7L);
        EmplacementStyle built = EmplacementStyle.valueOf(style.toUpperCase());
        return new LookReview.Shot("flux-" + style, ground, 40, 22, placement -> {
            SceneBuilder builder = placement.builder();
            placement.holdouts(builder);
            BattleSimulation sim = builder.build().sim();
            float[] there = placement.openNear(36f, 11f);
            EntitySpec source = new EntitySpec("source", Faction.DEFENDER, UnitType.MARINE, (int) there[0], (int) there[1]);
            source.moveSpeed = 0f;
            source.attackRange = 0f;
            long dummy = sim.spawn(source);
            StructureDef heavy = TurretCatalogRegistry.requireStructure(ROLES.get("heavy"));
            float[] charge = {0f, 0.8f, 1.2f};
            long[] guns = new long[charge.length];
            for (int i = 0; i < charge.length; i++) {
                float[] at = placement.openNear(12f + i * 6f, 11f);
                int x = (int) at[0];
                int y = (int) at[1];
                guns[i] = sim.spawn(MapTurret.create("gun" + i, Faction.DEFENDER, built("heavy", style), x, y));
                sim.setStructureStyle(guns[i], built);
                sim.getGrid().setWalkable(x, y, false);
                if (sim.turretShields().fitted(guns[i]) == null) {
                    sim.turretShields().fit(guns[i], heavy.fluxShield, ShieldTint.ORANGE, 0f);
                }
            }
            TurretShieldService.Positions positions = POSITIONS.apply(sim);
            for (int t = 0; t < 45; t++) {
                for (long gun : guns) sim.turretShields().absorbs(gun, dummy, 0f, positions);
                sim.advance(BattleSimulation.TICK_DT);
            }
            for (int i = 0; i < charge.length; i++) {
                TurretShieldService.Fitted fitted = sim.turretShields().fitted(guns[i]);
                float want = charge[i] * fitted.shield.spec().fluxCapacity() - fitted.shield.flux();
                if (want > 0f) sim.turretShields().absorbs(guns[i], dummy, want / fitted.shield.spec().fluxPerDamage(),
                        positions);
            }
            return sim;
        }, placement -> List.of(
                LookReview.View.play(placement.site().x(18f), placement.site().y(11f)),
                LookReview.View.close("close", placement.site().x(18f), placement.site().y(11f), 22f)),
                Math.round(1.2f / BattleSimulation.TICK_DT), 1, 1).withStills(Math.round(1f / BattleSimulation.TICK_DT));
    }

    /**
     * The structure a post built in {@code look} puts up for {@code role}, as a generated post would
     * ({@code DefensePost.withStyle}): a high-tech look's guns are lasers.
     */
    private static String built(String role, String look) {
        return TurretCatalogRegistry.requireStructure(ROLES.get(role)).builtIn(EmplacementStyle.valueOf(look.toUpperCase()));
    }

    /** A shield's view of where things are, read off {@code sim}'s world. */
    private static final Function<BattleSimulation, TurretShieldService.Positions> POSITIONS =
            sim -> new TurretShieldService.Positions() {
                @Override public float x(long id) { return sim.world().x(id); }
                @Override public float y(long id) { return sim.world().y(id); }
            };

    /** The recorded frames {@code -Dsnapshot.groundTurrets.firingStills} asks for; none keeps the busiest. */
    private static int[] firingStills() {
        String frames = System.getProperty("snapshot.groundTurrets.firingStills", "").trim();
        if (frames.isEmpty()) return new int[0];
        return Arrays.stream(frames.split(",")).mapToInt(frame -> Integer.parseInt(frame.trim())).toArray();
    }
}

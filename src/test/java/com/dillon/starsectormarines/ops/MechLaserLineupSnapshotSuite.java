package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketType;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * Mechs in labelled rows with each laser they can fit installed, the way the tank lineup shows the hulls: Bulwarks with
 * a 2x2 or 1x2 laser on both arms, and Wardens with each of the six lasers as the nose gun, the PD family above and the
 * burst family below. Only fits the Mech Lab would accept are drawn ({@link #require}): energy parts fit omni sockets,
 * which are the Bulwark's arms and the Warden's 2x3 nose; a Hound's nose is a two by two ballistic socket and takes none.
 */
public final class MechLaserLineupSnapshotSuite implements SnapshotSuite {
    private static final MechWeaponComponent[] ARM_LINE = {MechWeaponComponent.PD_LASER,
            MechWeaponComponent.LIGHT_PULSE_LASER, MechWeaponComponent.MEDIUM_BURST_LASER,
            MechWeaponComponent.LIGHT_BURST_LASER};
    private static final String[] ARM_NAMES = {"PD LASER 2x2", "LIGHT PD LASER 1x2", "BURST LASER 2x2",
            "LIGHT BURST LASER 1x2"};
    private static final MechWeaponComponent[] NOSE_LINE = {MechWeaponComponent.HEAVY_PD_LASER,
            MechWeaponComponent.PD_LASER, MechWeaponComponent.LIGHT_PULSE_LASER, MechWeaponComponent.BURST_LASER,
            MechWeaponComponent.MEDIUM_BURST_LASER, MechWeaponComponent.LIGHT_BURST_LASER};
    private static final String[] NOSE_NAMES = {"HEAVY PD LASER 3x2", "PD LASER 2x2", "LIGHT PD LASER 1x2",
            "HEAVY BURST LASER 3x2", "BURST LASER 2x2", "LIGHT BURST LASER 1x2"};

    @Override public String id() { return "mech-laser-lineup"; }
    @Override public String label() { return "Bulwarks and Wardens lined up with each fitting laser installed"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        lineup(context, MechVariant.BULWARK, ARM_LINE, ARM_NAMES, new SocketId[] {SocketId.LEFT_ARM, SocketId.RIGHT_ARM},
                2, 7, 5, new float[] {13.5f, 8.4f, 19f}, "both arms", artifacts);
        lineup(context, MechVariant.WARDEN, NOSE_LINE, NOSE_NAMES, new SocketId[] {SocketId.ARMS},
                3, 5, 5, new float[] {15f, 9.4f, 16.5f}, "the nose", artifacts);
        return List.copyOf(artifacts);
    }

    /** Fails on a part the Mech Lab would refuse on {@code socket}: the wrong socket type or too big for it. */
    private static void require(MechVariant variant, SocketId socket, MechWeaponComponent part) {
        SocketDef def = MechFittingLayout.forVariant(variant).socket(socket);
        boolean typeFits = def.type() == SocketType.OMNI || def.type().name().equals(part.hardpointType.name());
        if (!typeFits || !def.accommodates(part.footprintColumns, part.footprintRows)) {
            throw new IllegalStateException(part + " does not fit the " + variant + " " + socket);
        }
    }

    private static void lineup(SnapshotContext context, MechVariant variant, MechWeaponComponent[] line,
            String[] names, SocketId[] sockets, int columns, int stepX, int stepY, float[] camera, String where,
            List<SnapshotArtifact> artifacts) throws Exception {
        SceneBuilder builder = SceneBuilder.openGround(160, 60).missionCompletion(false);
        builder.unit("marine-holdout", Faction.MARINE, UnitType.MARINE, 1, 1, spec -> spec.moveSpeed(0f));
        builder.unit("defender-holdout", Faction.DEFENDER, UnitType.MARINE, 158, 58, spec -> spec.moveSpeed(0f));
        int rows = (line.length + columns - 1) / columns;
        for (int i = 0; i < line.length; i++) {
            for (SocketId socket : sockets) require(variant, socket, line[i]);
            require(variant, SocketId.LEFT_SHOULDER, MechWeaponComponent.SRM_5);
            require(variant, SocketId.RIGHT_SHOULDER, MechWeaponComponent.SRM_5);
            int x = 10 + stepX * (i % columns);
            int y = 6 + stepY * (rows - 1 - i / columns);
            MechDeploymentSpec spec = new MechDeploymentSpec(variant, MechRole.BALANCED,
                    MissileReplenisherComponent.STANDARD, line[i], MechWeaponComponent.SRM_5, MechWeaponComponent.SRM_5);
            builder.squad("mech" + i).faction(Faction.MARINE).size(1).at(x, y).mech(spec)
                    .assigned(id -> ObjectiveAssignment.defendArea(id, x, y)).done();
        }
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            // Long enough for the drop-in to finish; the only enemy is far outside sight, so no mech turns from the line.
            for (int tick = 0; tick < 300; tick++) sim.advance(BattleSimulation.TICK_DT);
            ReviewAnnotations.Builder labels = ReviewAnnotations.builder();
            for (int i = 0; i < line.length; i++) {
                long id = world.members("mech" + i)[0];
                labels.label(sim.world().renderX(id), sim.world().renderY(id) - 2.4f, names[i], ReviewStyle.NOTE);
            }
            BattleReviewFrameRenderer renderer = new BattleReviewFrameRenderer(
                    context.modRoot(), 1920, 1080, camera[0], camera[1], camera[2]);
            artifacts.add(new SnapshotArtifact(variant.name().toLowerCase() + "-lineup.png", renderer.render(sim,
                    variant.name() + " | each laser it can fit installed on " + where + " | 1920 x 1080, UI 100%",
                    labels.build())));
        }
    }
}

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
 * Bulwarks in labelled rows with each laser a mech can fit installed on both arms, the way the tank lineup shows the
 * hulls: the PD family above and the burst family below, the 2x2 part then the 1x2. Only fits the Mech Lab would accept
 * are drawn ({@link #require}): the arms are the one place an energy part fits a mech today (the nose sockets are
 * ballistic, and a Hound's is two by two), so the heavy 2x3 lasers have no mech here.
 */
public final class MechLaserLineupSnapshotSuite implements SnapshotSuite {
    private static final MechWeaponComponent[] LINE = {MechWeaponComponent.PD_LASER,
            MechWeaponComponent.LIGHT_PULSE_LASER, MechWeaponComponent.MEDIUM_BURST_LASER,
            MechWeaponComponent.LIGHT_BURST_LASER};
    private static final String[] NAMES = {"PD LASER 2x2", "LIGHT PD LASER 1x2", "BURST LASER 2x2",
            "LIGHT BURST LASER 1x2"};

    @Override public String id() { return "mech-laser-lineup"; }
    @Override public String label() { return "Bulwarks lined up with each fitting laser installed on both arms"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        lineup(context, MechVariant.BULWARK, artifacts);
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

    private static void lineup(SnapshotContext context, MechVariant variant, List<SnapshotArtifact> artifacts)
            throws Exception {
        SceneBuilder builder = SceneBuilder.openGround(160, 60).missionCompletion(false);
        builder.unit("marine-holdout", Faction.MARINE, UnitType.MARINE, 1, 1, spec -> spec.moveSpeed(0f));
        builder.unit("defender-holdout", Faction.DEFENDER, UnitType.MARINE, 158, 58, spec -> spec.moveSpeed(0f));
        for (int i = 0; i < LINE.length; i++) {
            require(variant, SocketId.LEFT_ARM, LINE[i]);
            require(variant, SocketId.RIGHT_ARM, LINE[i]);
            int x = 10 + 7 * (i % 2);
            int y = i < 2 ? 11 : 6;
            MechDeploymentSpec spec = new MechDeploymentSpec(variant, MechRole.BALANCED,
                    MissileReplenisherComponent.STANDARD, LINE[i], MechWeaponComponent.SRM_5, MechWeaponComponent.SRM_5);
            builder.squad("mech" + i).faction(Faction.MARINE).size(1).at(x, y).mech(spec)
                    .assigned(id -> ObjectiveAssignment.defendArea(id, x, y)).done();
        }
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            // Long enough for the drop-in to finish; the only enemy is far outside sight, so no mech turns from the line.
            for (int tick = 0; tick < 300; tick++) sim.advance(BattleSimulation.TICK_DT);
            ReviewAnnotations.Builder labels = ReviewAnnotations.builder();
            for (int i = 0; i < LINE.length; i++) {
                long id = world.members("mech" + i)[0];
                labels.label(sim.world().renderX(id), sim.world().renderY(id) - 2.4f, NAMES[i], ReviewStyle.NOTE);
            }
            BattleReviewFrameRenderer renderer = new BattleReviewFrameRenderer(
                    context.modRoot(), 1920, 1080, 13.5f, 8.4f, 19f);
            artifacts.add(new SnapshotArtifact(variant.name().toLowerCase() + "-lineup.png", renderer.render(sim,
                    variant.name() + " | each laser it can fit installed on both arms | 1920 x 1080, UI 100%",
                    labels.build())));
        }
    }
}

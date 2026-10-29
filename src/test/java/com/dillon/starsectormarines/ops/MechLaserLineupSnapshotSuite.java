package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
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
 * Mechs standing in labelled rows with each laser of the ladder installed as the nose gun, the way the tank lineup
 * shows the hulls: the PD family above and the burst family below, heaviest to lightest. Art review: the three
 * sizes and two families must read apart on a mech.
 */
public final class MechLaserLineupSnapshotSuite implements SnapshotSuite {
    private static final MechWeaponComponent[] LINE = {MechWeaponComponent.HEAVY_PD_LASER,
            MechWeaponComponent.PD_LASER, MechWeaponComponent.LIGHT_PULSE_LASER, MechWeaponComponent.BURST_LASER,
            MechWeaponComponent.MEDIUM_BURST_LASER, MechWeaponComponent.LIGHT_BURST_LASER};
    private static final String[] NAMES = {"HEAVY PD LASER 3x2", "PD LASER 2x2", "LIGHT PD LASER 1x2",
            "HEAVY BURST LASER 3x2", "BURST LASER 2x2", "LIGHT BURST LASER 1x2"};
    private static final MechVariant[] VARIANTS = {MechVariant.WARDEN, MechVariant.HOUND};

    @Override public String id() { return "mech-laser-lineup"; }
    @Override public String label() { return "Mechs lined up with each laser installed as the nose gun"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        for (MechVariant variant : VARIANTS) lineup(context, variant, artifacts);
        return List.copyOf(artifacts);
    }

    private static void lineup(SnapshotContext context, MechVariant variant, List<SnapshotArtifact> artifacts)
            throws Exception {
        String name = variant.name().toLowerCase();
        SceneBuilder builder = SceneBuilder.openGround(160, 60).missionCompletion(false);
        builder.unit("marine-holdout", Faction.MARINE, UnitType.MARINE, 1, 1, spec -> spec.moveSpeed(0f));
        builder.unit("defender-holdout", Faction.DEFENDER, UnitType.MARINE, 158, 58, spec -> spec.moveSpeed(0f));
        for (int i = 0; i < LINE.length; i++) {
            int x = 10 + 5 * (i % 3);
            int y = i < 3 ? 12 : 7;
            MechDeploymentSpec spec = new MechDeploymentSpec(variant, MechRole.BALANCED,
                    MissileReplenisherComponent.STANDARD, LINE[i], MechWeaponComponent.SRM_5, MechWeaponComponent.SRM_5);
            builder.squad("mech" + i).faction(Faction.MARINE).size(1).at(x, y).mech(spec)
                    .assigned(id -> ObjectiveAssignment.defendArea(id, x, y)).done();
        }
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            // Long enough for the drop-in to finish; the only enemy is far outside sight, so no mech turns from the line.
            for (int tick = 0; tick < 300; tick++) sim.advance(BattleSimulation.TICK_DT);
            ReviewAnnotations.Builder all = ReviewAnnotations.builder();
            for (int i = 0; i < LINE.length; i++) {
                long id = world.members("mech" + i)[0];
                float x = sim.world().renderX(id);
                float y = sim.world().renderY(id);
                all.label(x, y - 2.1f, NAMES[i], ReviewStyle.NOTE);
            }
            String what = variant.name() + " | each laser of the ladder installed as the nose gun";
            BattleReviewFrameRenderer renderer = new BattleReviewFrameRenderer(
                    context.modRoot(), 1920, 1080, 15f, 9.6f, 16.5f);
            artifacts.add(new SnapshotArtifact(name + "-lineup.png",
                    renderer.render(sim, what + " | 1920 x 1080, UI 100%", all.build())));
        }
    }
}

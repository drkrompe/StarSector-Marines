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
 * A third frame shows the older energy parts that still wear the olive and red mech art.
 */
public final class MechLaserLineupSnapshotSuite implements SnapshotSuite {
    private static final MechWeaponComponent[] ARM_LINE = {MechWeaponComponent.BL_2,
            MechWeaponComponent.BL_1, MechWeaponComponent.PL_2,
            MechWeaponComponent.PL_1};
    private static final String[] ARM_NAMES = {"BL-2 2x2", "BL-1 1x2", "PL-2 2x2",
            "PL-1 1x2"};
    private static final MechWeaponComponent[] NOSE_LINE = {MechWeaponComponent.PL_3_SWEEP,
            MechWeaponComponent.BL_2, MechWeaponComponent.BL_1, MechWeaponComponent.PL_3,
            MechWeaponComponent.PL_2, MechWeaponComponent.PL_1};
    private static final String[] NOSE_NAMES = {"PL-3 (SWEEP) 3x2", "BL-2 2x2", "BL-1 1x2",
            "PL-3 3x2", "PL-2 2x2", "PL-1 1x2"};

    private static final SocketId[] BULWARK_ARMS = {SocketId.LEFT_ARM, SocketId.RIGHT_ARM};
    private static final SocketId[] NOSE = {SocketId.ARMS};

    /** One mech in a lineup: its variant, the part in its arm sockets and in its right shoulder, and its label. */
    private record Entry(MechVariant variant, SocketId[] armSockets, MechWeaponComponent arms,
                         MechWeaponComponent rightShoulder, String name) { }

    @Override public String id() { return "mech-laser-lineup"; }
    @Override public String label() { return "Bulwarks and Wardens lined up with each fitting laser installed"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        List<Entry> bulwarks = new ArrayList<>();
        for (int i = 0; i < ARM_LINE.length; i++) {
            bulwarks.add(new Entry(MechVariant.BULWARK, BULWARK_ARMS, ARM_LINE[i], MechWeaponComponent.SRM_5, ARM_NAMES[i]));
        }
        artifacts.add(lineup(context, "bulwark-lineup.png", "BULWARK | each laser it can fit installed on both arms",
                bulwarks, 2, 7, 5, new float[] {13.5f, 8.4f, 19f}));
        List<Entry> wardens = new ArrayList<>();
        for (int i = 0; i < NOSE_LINE.length; i++) {
            wardens.add(new Entry(MechVariant.WARDEN, NOSE, NOSE_LINE[i], MechWeaponComponent.SRM_5, NOSE_NAMES[i]));
        }
        artifacts.add(lineup(context, "warden-lineup.png", "WARDEN | each laser it can fit installed on the nose",
                wardens, 3, 5, 5, new float[] {15f, 9.4f, 16.5f}));
        // The energy parts older than the ladder, still in the olive and red mech art.
        List<Entry> legacy = List.of(
                new Entry(MechVariant.BULWARK, BULWARK_ARMS, MechWeaponComponent.PL_2_AP, MechWeaponComponent.SRM_5,
                        "PL-2 (AP) 2x2 (old art)"),
                new Entry(MechVariant.WARDEN, NOSE, MechWeaponComponent.HC_10,
                        MechWeaponComponent.LL_3, "LL-3 3x2 (old art)"),
                new Entry(MechVariant.WARDEN, NOSE, MechWeaponComponent.HC_10, MechWeaponComponent.THERMAL_LANCE,
                        "THERMAL LANCE 1x2 (old art)"));
        artifacts.add(lineup(context, "legacy-lineup.png", "OLDER ENERGY PARTS | still the olive and red mech art",
                legacy, 3, 5, 5, new float[] {15f, 6.4f, 16.5f}));
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

    private static SnapshotArtifact lineup(SnapshotContext context, String file, String title, List<Entry> line,
            int columns, int stepX, int stepY, float[] camera) throws Exception {
        SceneBuilder builder = SceneBuilder.openGround(160, 60).missionCompletion(false);
        builder.unit("marine-holdout", Faction.MARINE, UnitType.MARINE, 1, 1, spec -> spec.moveSpeed(0f));
        builder.unit("defender-holdout", Faction.DEFENDER, UnitType.MARINE, 158, 58, spec -> spec.moveSpeed(0f));
        int rows = (line.size() + columns - 1) / columns;
        for (int i = 0; i < line.size(); i++) {
            Entry entry = line.get(i);
            for (SocketId socket : entry.armSockets()) require(entry.variant(), socket, entry.arms());
            require(entry.variant(), SocketId.LEFT_SHOULDER, MechWeaponComponent.SRM_5);
            require(entry.variant(), SocketId.RIGHT_SHOULDER, entry.rightShoulder());
            int x = 10 + stepX * (i % columns);
            int y = 6 + stepY * (rows - 1 - i / columns);
            MechDeploymentSpec spec = new MechDeploymentSpec(entry.variant(), MechRole.BALANCED,
                    MissileReplenisherComponent.STANDARD, entry.arms(), MechWeaponComponent.SRM_5,
                    entry.rightShoulder());
            builder.squad("mech" + i).faction(Faction.MARINE).size(1).at(x, y).mech(spec)
                    .assigned(id -> ObjectiveAssignment.defendArea(id, x, y)).done();
        }
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            // Long enough for the drop-in to finish; the only enemy is far outside sight, so no mech turns from the line.
            for (int tick = 0; tick < 300; tick++) sim.advance(BattleSimulation.TICK_DT);
            ReviewAnnotations.Builder labels = ReviewAnnotations.builder();
            for (int i = 0; i < line.size(); i++) {
                long id = world.members("mech" + i)[0];
                labels.label(sim.world().renderX(id), sim.world().renderY(id) - 2.4f, line.get(i).name(), ReviewStyle.NOTE);
            }
            BattleReviewFrameRenderer renderer = new BattleReviewFrameRenderer(
                    context.modRoot(), 1920, 1080, camera[0], camera[1], camera[2]);
            return new SnapshotArtifact(file, renderer.render(sim, title + " | 1920 x 1080, UI 100%", labels.build()));
        }
    }
}

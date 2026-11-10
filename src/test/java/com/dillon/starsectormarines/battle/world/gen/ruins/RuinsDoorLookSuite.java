package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.door.LockedDoor;
import com.dillon.starsectormarines.battle.fixture.RuinsBattleFixture;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.ruins.SealDoor;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import com.dillon.starsectormarines.campaign.ruins.RuinsOccupant;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Look shots of the ruins' leaf doors as built ({@code ruins-expedition.md}, The ruins' doors are leaf doors): the
 * plant's hall doors in each stance, a cargo door on a way in, and the vault's and a store's three-cell blast doors
 * sealed and opened. Each is a real render through the look preset at 1920x1080, roofs off. Opt-in:
 * {@code -Psnapshot=ruins-door-look}.
 */
public final class RuinsDoorLookSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;

    @Override public String id() { return "ruins-door-look"; }

    @Override public String label() { return "Ruins leaf doors as built"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        RuinsBattleFixture fixture = RuinsBattleFixture.fromFactoryInputs(7L, BattleSetup.defaultManifest(), false,
                OperationTier.VETERAN, RiskLevel.MEDIUM, RuinsGroundEvidence.profile(SurfacePalette.ARID),
                FlybyRoster.EMPTY, FlybyRoster.EMPTY, RuinsGrade.EXTENSIVE, RuinsOccupant.SCAVENGERS, List.of(), null);
        List<SnapshotArtifact> out = new ArrayList<>();
        try (BattleSimulation sim = fixture.build()) {
            RuinsDelve delve = sim.getRuinsWorks().delve();
            List<RuinsDelve.LeafDoor> doors = delve.interior().doors();
            for (RuinsDelve.DoorStance stance : RuinsDelve.DoorStance.values()) {
                for (RuinsDelve.LeafDoor d : doors) {
                    if (d.cargo() || d.stance() != stance) continue;
                    out.add(shot(context, sim, d.span().centreX() + .5f, d.span().centreY() + .5f, 16f,
                            "hall-door-" + stance.name().toLowerCase() + ".png", "Hall door, " + word(stance)));
                    break;
                }
            }
            for (RuinsDelve.LeafDoor d : doors) {
                if (!d.cargo()) continue;
                out.add(shot(context, sim, d.span().centreX() + .5f, d.span().centreY() + .5f, 24f,
                        "cargo-door.png", "Cargo door on a way in, " + word(d.stance())));
                break;
            }
            for (RuinsDelve.PlantHall h : delve.interior().halls()) {
                if (h.stage() != RuinsDelve.PlantStage.INTAKE) continue;
                out.add(shot(context, sim, h.footprint().centreX() + .5f, h.footprint().centreY() + .5f, 44f,
                        "plant-hall-doors.png", "The intake hall's joins behind their leaf doors"));
            }
            RuinsDelve.Vault vault = delve.vault();
            float vx = (vault.door().x() + vault.innerDoor().x()) / 2f + .5f;
            float vy = (vault.door().y() + vault.innerDoor().y()) / 2f + .5f;
            out.add(shot(context, sim, vx, vy, 24f, "vault-sealed.png", "The vault's three-cell blast door, sealed"));
            SealDoor store = null;
            for (SealDoor d : sim.getRuinsWorks().doors()) {
                if (d.store()) {
                    store = d;
                    break;
                }
            }
            if (store != null) {
                int[] m = store.middle();
                out.add(shot(context, sim, m[0] + .5f, m[1] + .5f, 20f, "store-sealed.png",
                        "A sealed store's three-cell blast door"));
            }
            // A two-cell hall door torn out to four, as a derelict hall frame leaves it: the drawing only (the cells
            // stay as they are; the frame's own opening is the derelict watch's).
            for (RuinsDelve.LeafDoor d : doors) {
                if (d.cargo() || d.length() != 2) continue;
                RuinsDelve.Area s = d.span();
                RuinsDelve.Area wide = d.alongX() ? new RuinsDelve.Area(s.left() - 1, s.bottom(), s.right() + 1, s.top())
                        : new RuinsDelve.Area(s.left(), s.bottom() - 1, s.right(), s.top() + 1);
                sim.getRuinsWorks().doorStates().tear(d, wide);
                out.add(shot(context, sim, s.centreX() + .5f, s.centreY() + .5f, 16f, "hall-door-torn-wide.png",
                        "Hall door, " + word(RuinsDelve.DoorStance.TORN)));
                break;
            }
            // Unlocked: the passage opens and the leaves stand withdrawn into the walls.
            for (LockedDoor door : sim.lockedDoors()) door.settle(sim, true);
            out.add(shot(context, sim, vx, vy, 24f, "vault-open.png", "The vault's blast door, opened by its power"));
        }
        return out;
    }

    private static String word(RuinsDelve.DoorStance s) {
        return switch (s) {
            case ANSWERS -> "still powered: it parts for whoever comes through (shown shut)";
            case DEAD -> "dead part-open: its leaves stand in its end cells";
            case FORCED -> "forced long ago: one leaf torn out onto the floor";
            case SEALED -> "sealed: shut across a way that is wall, its lamps dark";
            case TORN -> "torn out wide, jambs and all: rubble at both ends, one leaf on the floor";
        };
    }

    private static SnapshotArtifact shot(SnapshotContext context, BattleSimulation sim, float cx, float cy,
                                         float width, String file, String caption) throws Exception {
        BufferedImage image = BattleReviewFrameRenderer.look(context.modRoot(), W, H, cx, cy, width).withoutRoofs()
                .render(sim, "");
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        int tw = g.getFontMetrics().stringWidth(caption);
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRoundRect(24, 18, tw + 40, 52, 14, 14);
        g.setColor(Color.WHITE);
        g.drawString(caption, 44, 53);
        g.dispose();
        return new SnapshotArtifact(file, image);
    }
}

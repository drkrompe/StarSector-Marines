package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.power.CloseAirSupport;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.power.OrbitalBarrage;
import com.dillon.starsectormarines.battle.power.SpawnInOnslaught;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.panel.DebugHudLayout;
import com.dillon.starsectormarines.battle.ui.panel.DebugHudSwitchPanel;
import com.dillon.starsectormarines.battle.ui.panel.DebugTogglesPanel;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.HudCompositionSnapshotSuite.SurfaceBounds;
import com.dillon.starsectormarines.ops.battleview.GlSpriteTokens;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.HeadlessGl;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.lwjgl.opengl.GL11.*;

/**
 * The battle's CHEAT switch and panel as a player sees them: a real battle and its retained HUD at 1920x1080 and 100%
 * scale, with the legacy GL panels painted between the two on a real driver, in the order {@code BattleScreen} paints
 * them. The panels' bounds come from the same retained surfaces the screen measures. Frames: the player build's switch
 * off; on, with Win and Add Onslaught open beside it; Add Onslaught hovered; after it, the Onslaught on the power bar
 * and its button gone; and the developer build's panel for comparison. The switch label
 * follows this JVM's {@code marines.dev}, so the developer frame still reads CHEAT unless the run passes it.
 */
public final class BattleCheatPanelSnapshotSuite implements SnapshotSuite {
    private static final int W = 1920, H = 1080;
    /** The retained surfaces {@code BattleScreen.bottomHudTop} measures. */
    private static final Set<String> BOTTOM = Set.of("infantry", "mech", "fleet powers", "orders", "retreat",
            "ship command", "direct control");

    @Override public String id() { return "battle-cheat-panel"; }
    @Override public String label() { return "Battle CHEAT switch and panel over the real battle HUD"; }
    @Override public boolean inAll() { return false; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        InstalledHullSpecs.install(); InstalledVanillaData.install();
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessSvgAssets.install(context.modRoot());
        DiskRegistries.installMapGeneration(context.projectRoot());
        HeadlessGl gl = HeadlessGl.createOrNull(W, H);
        if (gl == null) throw new IllegalStateException("No GL context: " + HeadlessGl.unavailableReason());
        SettingsAPI original = Global.getSettings();
        List<SnapshotArtifact> frames = new ArrayList<>();
        try (gl; HeadlessStrings strings = new HeadlessStrings(context.modRoot());
             GlSpriteTokens tokens = new GlSpriteTokens()) {
            Global.setSettings(sprites(context, tokens, HudCompositionSnapshotSuite.settings(context, Global.getSettings())));
            var world = SceneBuilder.openGround(120, 68).missionCompletion(false)
                    .squad("infantry").size(12).inFile(28, 34, 1, 0).done()
                    .unit("enemy", Faction.DEFENDER, UnitType.MARINE, 116, 65).build();
            try (BattleSimulation sim = world.sim()) {
                for (int y = 0; y < 68; y++) for (int x = 0; x < 120; x++) {
                    sim.getTopology().setGroundKind(x, y, y >= 30 && y <= 38 ? GroundKind.STREET : GroundKind.DIRT);
                }
                sim.getCommandPowerService().setPowers(List.of(new OrbitalBarrage(), new MechSupport(),
                        new CloseAirSupport()));
                sim.tenders().add(sim, Tender.fit(Faction.MARINE, 26, 24, -90));
                sim.advance(BattleSimulation.TICK_DT);
                Selection selection = new Selection();
                frames.add(frame(context, sim, selection, "01-switch-off", false, false, false));
                frames.add(frame(context, sim, selection, "02-switch-on-cheats", true, false, false));
                frames.add(frame(context, sim, selection, "03-onslaught-hover", true, true, false));
                // Add Onslaught clicked: the power is on the bar and its button is gone.
                sim.getCommandPowerService().register(new SpawnInOnslaught());
                frames.add(frame(context, sim, selection, "04-onslaught-added", true, false, false));
                frames.add(frame(context, sim, selection, "05-dev-build-expanded", true, true, true));
            }
        } finally {
            Global.setSettings(original);
        }
        return frames;
    }

    private static SnapshotArtifact frame(SnapshotContext context, BattleSimulation sim, Selection selection,
                                          String name, boolean switchOn, boolean act, boolean devRows) throws Exception {
        BufferedImage image = HudCompositionSnapshotSuite.compose(context, sim, selection, name, false, null,
                (scene, retained) -> {
                    Context ui = new Context(sim, selection, retained);
                    // As BattleScreen builds them: a dev build's switch and panel, or a player's switch carrying Win.
                    DebugHudSwitchPanel hudSwitch = devRows ? new DebugHudSwitchPanel(ui)
                            : new DebugHudSwitchPanel(ui, List.of(
                                    new DebugHudSwitchPanel.Cheat(Strings.get("cheatsWin"), () -> { }, () -> true),
                                    new DebugHudSwitchPanel.Cheat(Strings.get("cheatsOnslaught"), () -> { },
                                            () -> sim.getCommandPowerService().getPower(SpawnInOnslaught.ID) == null)));
                    DebugTogglesPanel tools = null;
                    if (devRows) {
                        tools = new DebugTogglesPanel(ui);
                        ui.tools = tools;
                        tools.addAction(Strings.get("cheatsWin"), () -> { });
                        addDiagnosticRows(tools);
                    }
                    ui.debugHud = switchOn;
                    if (act && tools != null) expand(ui, tools);
                    if (act && tools == null) hoverCheat(ui, hudSwitch, 1);
                    return paint(scene, hudSwitch, tools);
                });
        return new SnapshotArtifact("cheat-panel/" + name + "-1920x1080-ui100.png", image);
    }

    /** {@code BattleScreen.addDiagnosticRows}' labels and kinds; the rows act on nothing here. */
    private static void addDiagnosticRows(DebugTogglesPanel tools) {
        for (String label : List.of("Auto spike dumps", "Docking paths", "Marine friendly field",
                "Marine hostile field", "Defender friendly field", "Defender hostile field",
                "Marine commander picture", "Defender commander picture")) {
            tools.addToggle(label, () -> false, () -> { });
        }
        for (String label : List.of("Structure relief", "Surface relief", "Water waves", "Bump lighting",
                "Sun shadows")) {
            tools.addDial(label, () -> 0.5, value -> { }, 0, 1, 1);
        }
        tools.addDial("Sun azimuth", () -> 135, value -> { }, 0, 360, 1);
        tools.addDial("Sun elevation", () -> 45, value -> { }, 0, 90, 1);
        tools.addAction("Force reinforcement", () -> { });
        tools.addToggle("Capture commander trace", () -> false, () -> { });
        tools.addAction("Dump commander trace", () -> { });
        tools.addAction("Spawn mech family", () -> { });
        tools.addToggle("Turret author", () -> false, () -> { });
    }

    /** Moves the pointer over the player's {@code index}th cheat button, counting leftward from the switch. */
    private static void hoverCheat(Context ui, DebugHudSwitchPanel hudSwitch, int index) {
        BattleLayout layout = ui.getLayout();
        int x = Math.round(hudSwitch.cheatX(layout, index) + 10f), y = Math.round(DebugHudSwitchPanel.y(layout) + 8f);
        hudSwitch.handleInput(List.of(move(x, y)));
    }

    /** Clicks the collapsed header, wherever it sits along its row. */
    private static void expand(Context ui, DebugTogglesPanel tools) {
        int y = Math.round(DebugHudLayout.top(ui) - 8f);
        for (int x = 0; x < W; x += 8) {
            AtomicBoolean consumed = new AtomicBoolean();
            tools.handleInput(List.of(click(x, y, consumed)));
            if (consumed.get()) return;
        }
        throw new IllegalStateException("No header under y=" + y + "; tools visible " + tools.isVisible());
    }

    /** The scene, then the legacy panels over it on the GPU, as the battle screen's hud pass draws them. */
    private static BufferedImage paint(BufferedImage scene, DebugHudSwitchPanel hudSwitch, DebugTogglesPanel tools) {
        glViewport(0, 0, W, H);
        glMatrixMode(GL_PROJECTION); glLoadIdentity(); glOrtho(0, W, 0, H, -1, 1);
        glMatrixMode(GL_MODELVIEW); glLoadIdentity();
        glClearColor(0f, 0f, 0f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);
        int texture = upload(scene);
        glDisable(GL_BLEND);
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, texture);
        glColor4f(1f, 1f, 1f, 1f);
        glBegin(GL_QUADS);
        glTexCoord2f(0f, 0f); glVertex2f(0f, 0f);
        glTexCoord2f(1f, 0f); glVertex2f(W, 0f);
        glTexCoord2f(1f, 1f); glVertex2f(W, H);
        glTexCoord2f(0f, 1f); glVertex2f(0f, H);
        glEnd();
        glDeleteTextures(texture);
        if (hudSwitch.isVisible()) hudSwitch.render(1f);
        if (tools != null && tools.isVisible()) tools.render(1f);
        glFinish();
        ByteBuffer pixels = BufferUtils.createByteBuffer(W * H * 4);
        glReadPixels(0, 0, W, H, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int i = (y * W + x) * 4;
            out.setRGB(x, H - 1 - y, 0xFF000000 | (pixels.get(i) & 0xFF) << 16 | (pixels.get(i + 1) & 0xFF) << 8
                    | pixels.get(i + 2) & 0xFF);
        }
        return out;
    }

    private static int upload(BufferedImage image) {
        int[] argb = image.getRGB(0, 0, W, H, null, 0, W);
        ByteBuffer pixels = BufferUtils.createByteBuffer(W * H * 4);
        for (int y = H - 1; y >= 0; y--) for (int x = 0; x < W; x++) {
            int p = argb[y * W + x];
            pixels.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) 0xFF);
        }
        pixels.flip();
        int name = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, name);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, W, H, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return name;
    }

    /** Hands the font pages out as GL textures, the way the game's {@code getSprite} does. */
    private static SettingsAPI sprites(SnapshotContext context, GlSpriteTokens tokens, SettingsAPI previous) {
        return (SettingsAPI) Proxy.newProxyInstance(SettingsAPI.class.getClassLoader(), new Class<?>[]{SettingsAPI.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getSprite") && args != null && args.length == 1) {
                        String path = (String) args[0];
                        Path file = context.modRoot().resolve(path);
                        if (!Files.exists(file)) file = context.starsectorCore().resolve(path);
                        if (!Files.exists(file)) return null;
                        BufferedImage image = ImageIO.read(file.toFile());
                        return tokens.token(path, file, image.getWidth(), image.getHeight());
                    }
                    return method.invoke(previous, args);
                });
    }

    private static InputEventAPI move(int x, int y) {
        return (InputEventAPI) Proxy.newProxyInstance(InputEventAPI.class.getClassLoader(), new Class<?>[]{InputEventAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getX" -> x;
                    case "getY" -> y;
                    default -> method.getReturnType() == boolean.class ? method.getName().equals("isMouseMoveEvent") : null;
                });
    }

    private static InputEventAPI click(int x, int y, AtomicBoolean consumed) {
        return (InputEventAPI) Proxy.newProxyInstance(InputEventAPI.class.getClassLoader(), new Class<?>[]{InputEventAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "consume" -> { consumed.set(true); yield null; }
                    case "isConsumed" -> consumed.get();
                    case "getX" -> x;
                    case "getY" -> y;
                    default -> method.getReturnType() == boolean.class ? method.getName().equals("isLMBDownEvent") : null;
                });
    }

    /** {@code BattleScreen}'s bounds for the legacy HUD, read from the retained surfaces this frame paints. */
    private static final class Context implements BattleUiContext {
        final BattleSimulation sim;
        final Selection selection;
        final List<SurfaceBounds> retained;
        final BattleLayout layout;
        DebugTogglesPanel tools;
        boolean debugHud;

        Context(BattleSimulation sim, Selection selection, List<SurfaceBounds> retained) {
            this.sim = sim;
            this.selection = selection;
            this.retained = retained;
            this.layout = new BattleLayout(position(), sim.getGrid().getWidth(), sim.getGrid().getHeight());
        }

        @Override public BattleSimulation getSim() { return sim; }
        @Override public BattleFixture getBattleFixture() { return null; }
        @Override public BattleCamera getCamera() { return null; }
        @Override public BattleLayout getLayout() { return layout; }
        @Override public Selection getSelection() { return selection; }
        @Override public HighlightOverlay getHighlights() { return new HighlightOverlay(); }
        @Override public boolean debugHudVisible() { return debugHud; }
        @Override public void setDebugHudVisible(boolean visible) { debugHud = visible; }

        @Override public float topCenterContentBottom() {
            for (SurfaceBounds b : retained) if (b.name().equals("subtitle")) return b.y();
            return H - BattleLayout.PAD;
        }

        @Override public float developerHudBottom() { return DebugHudLayout.clusterBottom(this, null, tools); }

        @Override public float bottomHudTop() { return bottomHudTop(0f, W); }

        @Override public float bottomHudTop(float x, float width) {
            float top = BattleLayout.PAD;
            if (x < layout.backX + BattleLayout.BACK_W && x + width > layout.backX && !selection.hasSquadSelection()) {
                top = layout.backY + BattleLayout.BACK_H + BattleLayout.CONTROLS_GAP;
            }
            for (SurfaceBounds b : retained) {
                if (BOTTOM.contains(b.name()) && x < b.x() + b.width() && x + width > b.x()) {
                    top = Math.max(top, b.y() + b.height());
                }
            }
            return top;
        }
    }

    private static PositionAPI position() {
        return (PositionAPI) Proxy.newProxyInstance(PositionAPI.class.getClassLoader(), new Class<?>[]{PositionAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getX", "getY" -> 0f; case "getWidth" -> (float) W; case "getHeight" -> (float) H;
                    default -> throw new AssertionError("Unexpected position query " + method.getName());
                });
    }
}

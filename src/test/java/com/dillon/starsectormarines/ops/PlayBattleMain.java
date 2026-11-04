package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.audio.OperationRadio;
import com.dillon.starsectormarines.battle.audio.WaylineNarration;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.InstalledHullEngines;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.InstalledWeaponSlots;
import com.dillon.starsectormarines.render2d.texture.TextureLease;
import com.dillon.starsectormarines.render2d.texture.TextureLedger;
import com.dillon.starsectormarines.ui.retained.UiSounds;
import com.dillon.starsectormarines.ui.retained.UiSpriteCache;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.PixelFormat;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_PACK_ALIGNMENT;
import static org.lwjgl.opengl.GL11.GL_PROJECTION;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_STENCIL_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glLoadIdentity;
import static org.lwjgl.opengl.GL11.glMatrixMode;
import static org.lwjgl.opengl.GL11.glOrtho;
import static org.lwjgl.opengl.GL11.glPixelStorei;
import static org.lwjgl.opengl.GL11.glReadPixels;
import static org.lwjgl.opengl.GL11.glViewport;

/**
 * {@code gradlew playBattle}: the mod's real screens ({@link MarineOpsPanelPlugin}) in a window of their own, with no
 * game running. It opens
 * on the mission board at a stand-in Jangala ({@link PlayCompany}), and Back there opens {@link PlayWorldMenu} to
 * move between worlds. A mission runs through its real briefing, battle and results, or
 * {@code -Pbattle=<source>} jumps straight into one battle. It gives the screens what the game would: a window, a frame
 * loop, input events, settings, sound and saved data ({@code CampaignBackend}).
 *
 * <p>Sources are {@link HeadlessBattles#build}'s, or the name of a checked-in fixture. The simulation keeps the live
 * game's asynchronous defaults, unlike the evidence tasks, so what is felt here is what plays in the game. F9 rebuilds
 * a named battle, or leaves a catalog mission's battle for the catalog. Closing the window quits, as does continuing
 * from a named battle, which has no mission to settle. {@code -Psilent} turns the sound off and
 * {@code -Pvolume=<0..1>} sets its level.
 *
 * <p><b>Not a final check.</b> The game hands the screen a polluted GL state and refuses file access to shipped code;
 * this does neither, so something can work here and still fail in the game.
 *
 * <p>{@code -PplayScript=<file>} drives a run with no one at the keyboard, one line per step: {@code <seconds> move
 * <x> <y>}, {@code <seconds> click <x> <y> [right]}, {@code <seconds> audit <label>} (controls a player could not fully reach), {@code <seconds> key <NAME>} (an LWJGL key name),
 * {@code <seconds> press <id>} (a control by its markup id, holding the script until it is there and enabled and
 * shifting later steps by the wait), {@code <seconds> ids} (prints what can be pressed), {@code <seconds> shot <name>}
 * (a PNG in {@code build/play-battle}), {@code <seconds> boats <one-repairing|all-repairing|none>} (the company's shuttles as a battle would leave them), {@code <seconds> story <mission id>} (that story mission is behind the company), {@code <seconds> quit}, and {@code <seconds> battle}, which holds the script
 * until a battle is playing. A control or battle that never comes ends the run with an error. Seconds count from the window opening, or from the last
 * battle starting to play; a named battle waits for that before its first step.
 *
 * <p>{@code -PplayServer[=<port>]} opens {@link PlayDiagnosis}, so a Claude session can ask the running battle questions
 * while it is played.
 */
public final class PlayBattleMain {

    private static final String FIXTURES = "src/test/resources/battle-fixtures/";
    private static final Path SHOTS = Path.of("build/play-battle");
    private static final int DEFAULT_WIDTH = 1920;
    private static final int DEFAULT_HEIGHT = 1080;
    /** A frame longer than this, a stall or a battle rebuild, is not played as time passing. */
    private static final float MAX_FRAME_SECONDS = 0.1f;
    /** How long a script waits for a control to come and be enabled; a survey can take this long to prepare. */
    private static final long PRESS_WAIT_NANOS = 60_000_000_000L;
    private static final long BATTLE_WAIT_NANOS = 300_000_000_000L;

    private PlayBattleMain() {
    }

    /**
     * Keeps routes on screens that can run here: stationing and a colony need more of a world, so they land on
     * the company's HQ. A battle named up front has no mission to settle: continuing from it ends the run instead.
     */
    private static final class Context extends MarineOpsContext {
        private static final Set<ScreenId> UNHOSTED =
                EnumSet.of(ScreenId.STATIONING, ScreenId.POLITY_DOCTRINE);

        private final boolean direct;
        boolean finished;

        Context(PlanetAPI world, boolean direct) {
            super(world);
            this.direct = direct;
        }

        @Override public void goTo(ScreenId screen) {
            super.goTo(UNHOSTED.contains(screen) ? ScreenId.COMPANY_HQ : screen);
        }

        @Override boolean beginBattleSettlement() {
            if (!direct) return super.beginBattleSettlement();
            finished = true;
            return false;
        }
    }

    /**
     * What is on screen: Marine Operations at a world, or the world menu between them. Leaving the board closes the
     * dialog for the menu, as the game closes it for the campaign map; a fresh dialog opens at the next world.
     */
    private static final class Host {
        private final PositionAPI position;
        private final boolean direct;
        private final PlayWorldMenu menu = new PlayWorldMenu(world -> this.next = world, () -> this.finished = true,
                name -> this.loading = name, () -> this.saving = true);
        private final TextureLease menuImages = TextureLedger.shared().lease("playBattle world menu");
        private Context context;
        private MarineOpsPanelPlugin plugin;
        private PlanetAPI next;
        private boolean leaving;
        private String loading;
        private boolean saving;
        private ScreenId lastScreen;
        private boolean finished;

        Host(PositionAPI position, boolean direct) {
            this.position = position;
            this.direct = direct;
        }

        void open(PlanetAPI world, BattleSimulation battle) {
            PlayCompany.dockAt(world);
            lastScreen = null;
            context = new Context(world, direct);
            if (battle != null) {
                context.setBattleSimulation(battle);
                context.goTo(ScreenId.BATTLE);
            } else {
                context.goTo(ScreenId.MISSION_SELECT);
            }
            plugin = new MarineOpsPanelPlugin(context, null);
            plugin.positionChanged(position);
            // Leaving a room lands on the board, where the dialog opens; leaving the board, or a world without work, closes it.
            plugin.setOnBack(() -> {
                if (direct) finished = true;
                else if (context.getCurrentScreen() == ScreenId.MISSION_SELECT || !context.boardOpen()) leaving = true;
                else context.goTo(ScreenId.MISSION_SELECT);
            });
        }

        /** Between input and the next tick, never from inside a screen's own handler. */
        private void settle() {
            if (saving) {
                saving = false;
                save("save-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
                menu.detach();
                menu.attach(position, null, () -> { });
            }
            if (loading != null) {
                PlaySaves.State state = PlaySaves.read(loading);
                loading = null;
                if (plugin != null) {
                    plugin.dismiss();
                    plugin = null;
                    context = null;
                } else {
                    menu.detach();
                    UiSpriteCache.shared().leave(menuImages);
                }
                PlayCompany.restore(state);
                open(PlayCompany.docked(), null);
            }
            checkpoint();
            if (leaving) {
                leaving = false;
                plugin.dismiss();
                plugin = null;
                context = null;
                UiSpriteCache.shared().enter(menuImages);
                menu.attach(position, null, () -> { });
            }
            if (next != null) {
                PlanetAPI world = next;
                next = null;
                menu.detach();
                UiSpriteCache.shared().leave(menuImages);
                open(world, null);
            }
        }

        /** Each arrival at the board comes before any launch, so a save taken there can launch again. */
        private void checkpoint() {
            if (direct || context == null) return;
            ScreenId screen = context.getCurrentScreen();
            if (screen == ScreenId.MISSION_SELECT && lastScreen != screen) {
                // A checkpoint that cannot be written is worth a message, not the playtest.
                try {
                    List<String> written = PlaySaves.checkpoint(PlayCompany.snapshot());
                    System.out.println("[playBattle] saved " + String.join(" ", written));
                } catch (RuntimeException failure) {
                    failure.printStackTrace();
                }
            }
            lastScreen = screen;
        }

        void save(String name) {
            PlaySaves.write(name, PlayCompany.snapshot());
            System.out.println("[playBattle] saved " + name);
        }

        void load(String name) {
            loading = name;
        }

        void processInput(List<InputEventAPI> events) {
            if (plugin != null) plugin.processInput(events);
            else menu.processInput(events);
            settle();
        }

        void advance(float dt) {
            if (plugin != null) plugin.advance(dt);
            else menu.advance(dt);
            settle();
        }

        void render() {
            if (plugin != null) plugin.render(1f);
            else menu.render(1f);
        }

        void close() {
            if (plugin != null) {
                plugin.dismiss();
            } else {
                menu.detach();
                UiSpriteCache.shared().leave(menuImages);
            }
        }

        boolean finished() {
            return finished || context != null && context.finished;
        }

        BattleSimulation battle() {
            return context == null ? null : context.getBattleSimulation();
        }

        String screen() {
            return context == null ? "WORLDS" : String.valueOf(context.getCurrentScreen());
        }
    }

    private record Step(float seconds, String[] words) { }

    public static void main(String[] args) throws Exception {
        // What the game writes to starsector.log: a fallback logged here is otherwise invisible.
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.WARN);
        String battle = System.getProperty("play.battle", "").strip();
        String source = battle.isEmpty() ? null : sourceOf(battle);
        int[] size = windowSize(System.getProperty("play.window", ""));
        // The game at a UI scale: the same pixels, with everything laid out in a smaller space.
        int[] logical = {Math.round(size[0] / PlayInput.UI_SCALE), Math.round(size[1] / PlayInput.UI_SCALE)};
        Path project = Path.of("");
        Path core = Path.of(System.getProperty("starsectorDir"), "starsector-core");
        Deque<Step> script = script(System.getProperty("play.script", ""));
        // Before anything slow, so a port already taken fails at once rather than after a battle has been built.
        String port = System.getProperty("play.server");
        PlayDiagnosis diagnosis = port == null ? null : PlayDiagnosis.open(port, project, core);

        var strings = HeadlessBattles.install(project);
        InstalledHullEngines.install();
        InstalledVanillaData.install();
        InstalledWeaponSlots.install();
        PlayInput input = new PlayInput();
        Global.setSettings(new PlayBattleSettings(List.of(project.resolve("mod"), core, project.resolve("src/test/resources")), Global.getSettings(),
                logical[0], logical[1], input::pointerX, input::pointerY).settings());
        Global.setSoundPlayer(PlayBattleSettings.silence());
        // What the mod plugin loads at application start for the voices. Without them a battle has no radio or subtitles.
        WaylineNarration.loadBuiltins();
        OperationRadio.loadBuiltins();

        // The catalog lists its missions only with the cheats on, and they are read from the save.
        PlayCompany.install();
        System.out.println("[playBattle] saves: " + PlaySaves.profile() + " profile");
        String load = System.getProperty("play.load", "").strip();
        if (!load.isEmpty()) PlayCompany.restore(PlaySaves.read(load));
        Cheats.setEnabled(true);

        BattleSimulation sim = null;
        if (source != null) {
            long started = System.nanoTime();
            System.out.println("[playBattle] building " + source);
            sim = HeadlessBattles.build(source);
            System.out.println("[playBattle] built in " + (System.nanoTime() - started) / 1_000_000 + " ms");
        }

        openWindow(size[0], size[1], source);
        int status = 0;
        PlayBattleSound sound = null;
        try {
            if (!Boolean.getBoolean("play.silent")) {
                sound = PlayBattleSound.openOrNull(project.resolve("mod"),
                        Float.parseFloat(System.getProperty("play.volume", "1")));
            }
            if (sound != null) Global.setSoundPlayer(sound);
            // The game installs these at application load, which the window never runs.
            UiSounds.install(new UiSounds(id -> Global.getSoundPlayer().playUISound(id, 1f, 1f), System::nanoTime));
            Host host = new Host(position(logical[0], logical[1]), source != null);
            host.open(PlayCompany.docked(), sim);
            play(host, input, sound, source, size, script, diagnosis);
            BattleSimulation last = host.battle();
            if (last != null) {
                System.out.println("[playBattle] ended at tick " + last.getSimTickIndex()
                        + (last.isComplete() ? ", winner " + last.getWinner() : ", unfinished"));
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            status = 1;
        } finally {
            if (diagnosis != null) diagnosis.close();
            if (sound != null) sound.close();
            Display.destroy();
            strings.close();
        }
        // Simulation-owned executors are not all daemons; nothing else should hold the JVM up.
        System.exit(status);
    }

    private static void play(Host host, PlayInput input, PlayBattleSound sound, String source,
                             int[] size, Deque<Step> script, PlayDiagnosis diagnosis) throws Exception {
        long last = System.nanoTime();
        long clockSince = last;
        // With a battle named up front, a script's clock waits out its loading screen, as a `battle` step does later.
        boolean awaitingBattle = source != null;
        boolean quit = false;
        PlayControls controls = new PlayControls();
        Deque<String> asked = new ArrayDeque<>();
        PlayDiagnosis.View view = new PlayDiagnosis.View() {
            @Override public BattleSimulation battle() { return host.battle(); }
            @Override public String screen() { return host.screen(); }
            @Override public String world() { return PlayCompany.docked().getName(); }
            @Override public String controls() { return controls.list(); }
            @Override public String timeButton() {
                return controls.firstWithClass("time-selected", "battle-time-pause", "battle-time-normal", "battle-time-double", "battle-time-quad");
            }
            @Override public boolean press(String id) {
                if (controls.find(id) == null) return false;
                asked.add(id);
                return true;
            }
        };
        long[] held = {0L};
        while (!quit && !host.finished() && !Display.isCloseRequested()) {
            long now = System.nanoTime();
            float dt = Math.min((now - last) / 1e9f, MAX_FRAME_SECONDS);
            last = now;

            List<InputEventAPI> events = new ArrayList<>(input.poll());
            for (String id; (id = asked.poll()) != null; ) {
                int[] at = controls.find(id);
                if (at == null) continue;
                // The pointer goes back: someone is playing with it.
                int x = input.pointerX(), y = input.pointerY();
                events.add(input.move(at[0], at[1], 0, 0));
                events.add(input.button(0, true, at[0], at[1]));
                events.add(input.button(0, false, at[0], at[1]));
                events.add(input.move(x, y, 0, 0));
            }
            if (awaitingBattle) {
                BattleSimulation battle = host.battle();
                if (battle != null && battle.getSimTickIndex() > 0) {
                    awaitingBattle = false;
                    clockSince = now;
                }
            }
            if (awaitingBattle && !script.isEmpty()) {
                if (held[0] == 0L) held[0] = now;
                if (now - held[0] > BATTLE_WAIT_NANOS) throw new IllegalStateException("script waited for a battle that never played");
            } else if (!awaitingBattle && held[0] != 0L && !script.isEmpty() && !script.peek().words()[0].equals("press")) {
                held[0] = 0L;
            }
            String shot = null;
            boolean pressed = false;
            while (!awaitingBattle && !pressed && !script.isEmpty() && script.peek().seconds() <= (now - clockSince) / 1e9f) {
                Step step = script.peek();
                if (step.words()[0].equals("press") && !pressable(step.words()[1], controls, now, held)) break;
                String[] words = script.poll().words();
                switch (words[0]) {
                    case "battle" -> awaitingBattle = true;
                    case "press" -> {
                        int[] at = controls.find(words[1]);
                        events.add(input.move(at[0], at[1], 0, 0));
                        events.add(input.button(0, true, at[0], at[1]));
                        events.add(input.button(0, false, at[0], at[1]));
                        // What it waited for shifts the steps after it, so their times stay relative to it.
                        clockSince = now - (long) (step.seconds() * 1e9f);
                        pressed = true;
                    }
                    case "ids" -> System.out.println("[playBattle] " + host.screen() + ": " + controls.list());
                    case "audit" -> System.out.println("[playBattle] audit " + words[1] + ":\n  " + controls.audit());
                    case "move" -> events.add(input.move(Integer.parseInt(words[1]), Integer.parseInt(words[2]), 0, 0));
                    case "click" -> {
                        int x = Integer.parseInt(words[1]);
                        int y = Integer.parseInt(words[2]);
                        int button = words.length > 3 && words[3].equals("right") ? 1 : 0;
                        events.add(input.move(x, y, 0, 0));
                        events.add(input.button(button, true, x, y));
                        events.add(input.button(button, false, x, y));
                    }
                    case "key" -> {
                        int key = Keyboard.getKeyIndex(words[1].toUpperCase(Locale.ROOT));
                        events.add(input.key(key, true, '\0', false));
                        events.add(input.key(key, false, '\0', false));
                    }
                    case "shot" -> shot = words[1];
                    case "boats" -> PlayCompany.boats(words[1]);
                    case "story" -> PlayCompany.storyDone(words[1]);
                    case "save" -> host.save(words[1]);
                    case "load" -> host.load(words[1]);
                    case "quit" -> quit = true;
                    default -> throw new IllegalArgumentException("unknown script step: " + words[0]);
                }
            }

            for (InputEventAPI event : events) {
                if (event.isKeyDownEvent() && !event.isRepeat() && event.getEventValue() == Keyboard.KEY_F9) {
                    event.consume();
                    if (source != null) {
                        System.out.println("[playBattle] rebuilding " + source);
                        host.context.setBattleSimulation(HeadlessBattles.build(source));
                        host.plugin.positionChanged(host.position);
                        awaitingBattle = true;
                    } else if (host.context != null && host.context.getCurrentScreen() == ScreenId.BATTLE) {
                        host.context.setBattle(null, null, null);
                        host.context.goTo(ScreenId.DEBUG_MISSIONS);
                    }
                    last = System.nanoTime();
                }
            }
            host.processInput(events);
            controls.frameDone();
            host.advance(dt);
            // No tick is running here: this thread runs them all, and the next is a frame away.
            if (diagnosis != null) diagnosis.frame(view);
            if (sound != null) sound.update(dt);
            beginFrame(size[0], size[1]);
            host.render();
            if (shot != null) shot(size[0], size[1], shot);
            Display.update();
            Display.sync(60);
        }
        host.close();
    }

    /** Whether a script's {@code press} can go ahead; a control that never comes ends the run saying what is there. */
    private static boolean pressable(String id, PlayControls controls, long now, long[] held) {
        if (controls.find(id) != null) {
            held[0] = 0L;
            return true;
        }
        if (held[0] == 0L) held[0] = now;
        if (now - held[0] > PRESS_WAIT_NANOS) {
            throw new IllegalStateException("script: nothing to press called " + id + "; pressable now: " + controls.list());
        }
        return false;
    }

    /** The state the game's UI hooks can rely on: a screen-sized, y-up orthographic view with alpha blending. */
    private static void beginFrame(int pixelWidth, int pixelHeight) {
        int width = Math.round(pixelWidth / PlayInput.UI_SCALE), height = Math.round(pixelHeight / PlayInput.UI_SCALE);
        glViewport(0, 0, pixelWidth, pixelHeight);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, width, 0, height, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glDisable(GL_SCISSOR_TEST);
        glClearColor(0f, 0f, 0f, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(1f, 1f, 1f, 1f);
    }

    private static void openWindow(int width, int height, String source) throws LWJGLException {
        // LWJGL 2 reads this once, when its native loader first runs. Windows only: the game's Mac natives are x86_64.
        System.setProperty("org.lwjgl.librarypath", Path.of(System.getProperty("starsectorDir"),
                "starsector-core", "native", "windows").toAbsolutePath().toString());
        DisplayMode desktop = Display.getDesktopDisplayMode();
        // A title bar would push a desktop-sized window off the screen.
        if (width >= desktop.getWidth() || height >= desktop.getHeight()) {
            System.setProperty("org.lwjgl.opengl.Window.undecorated", "true");
        }
        Display.setDisplayMode(new DisplayMode(width, height));
        Display.setTitle("Starsector Marines" + (source == null ? "" : ": " + source));
        Display.setVSyncEnabled(true);
        Display.create(new PixelFormat(8, 24, 8));
        Keyboard.enableRepeatEvents(true);
        System.out.println("[playBattle] window " + Display.getWidth() + "x" + Display.getHeight());
    }

    private static PositionAPI position(int width, int height) {
        return (PositionAPI) Proxy.newProxyInstance(PositionAPI.class.getClassLoader(), new Class<?>[]{PositionAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getX", "getY" -> 0f;
                    case "getWidth" -> (float) width;
                    case "getHeight" -> (float) height;
                    case "getCenterX" -> width / 2f;
                    case "getCenterY" -> height / 2f;
                    case "containsEvent" -> {
                        InputEventAPI event = (InputEventAPI) args[0];
                        yield event.getX() >= 0 && event.getX() < width && event.getY() >= 0 && event.getY() < height;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "the window";
                    default -> throw new UnsupportedOperationException("PositionAPI." + method.getName());
                });
    }

    /** A checked-in fixture's bare name becomes its {@code fixture:} source. */
    private static String sourceOf(String battle) {
        Path fixture = Path.of(FIXTURES + battle + ".json");
        return !battle.contains(":") && Files.isRegularFile(fixture) ? "fixture:" + fixture : battle;
    }

    private static int[] windowSize(String window) {
        if (window.isBlank()) return new int[]{DEFAULT_WIDTH, DEFAULT_HEIGHT};
        String[] parts = window.toLowerCase(Locale.ROOT).split("x");
        if (parts.length != 2) throw new IllegalArgumentException("-Pwindow=<width>x<height>, not " + window);
        return new int[]{Integer.parseInt(parts[0].strip()), Integer.parseInt(parts[1].strip())};
    }

    private static Deque<Step> script(String file) throws IOException {
        Deque<Step> steps = new ArrayDeque<>();
        if (file.isBlank()) return steps;
        for (String line : Files.readAllLines(Path.of(file))) {
            String step = line.strip();
            if (step.isEmpty() || step.startsWith("#")) continue;
            String[] words = step.split("\\s+");
            steps.add(new Step(Float.parseFloat(words[0]), Arrays.copyOfRange(words, 1, words.length)));
        }
        return steps;
    }

    private static void shot(int width, int height, String name) throws IOException {
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = (x + (height - 1 - y) * width) * 4;
                image.setRGB(x, y, (pixels.get(i) & 0xFF) << 16 | (pixels.get(i + 1) & 0xFF) << 8 | pixels.get(i + 2) & 0xFF);
            }
        }
        Files.createDirectories(SHOTS);
        Path file = SHOTS.resolve(name + ".png");
        ImageIO.write(image, "png", file.toFile());
        System.out.println("[playBattle] shot " + file.toAbsolutePath());
    }
}

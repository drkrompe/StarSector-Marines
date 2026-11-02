package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.InstalledHullEngines;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.InstalledWeaponSlots;
import com.fs.starfarer.api.Global;
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
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

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
 * {@code gradlew playBattle -Pbattle=<source>}: the real {@link BattleScreen} in a window of its own, with no game
 * running, for jumping straight into a battle to playtest it. It takes everything {@code MarineOpsPanelPlugin} would
 * give the screen: a window, a frame loop, input events, and the settings and sound the game would answer with.
 *
 * <p>Sources are {@link HeadlessBattles#build}'s, or the name of a checked-in fixture. The simulation keeps the live
 * game's asynchronous defaults, unlike the evidence tasks, so what is felt here is what plays in the game. F9 rebuilds
 * the battle; closing the window or continuing from a finished battle quits. The battle is silent, there is no
 * campaign behind it, and nothing settles.
 *
 * <p><b>Not a final check.</b> The game hands the screen a polluted GL state and refuses file access to shipped code;
 * this does neither, so something can work here and still fail in the game.
 *
 * <p>{@code -PplayScript=<file>} drives a run with no one at the keyboard, one line per step, timed in seconds from
 * when the battle starts playing: {@code <seconds> move <x> <y>}, {@code <seconds> click <x> <y> [right]},
 * {@code <seconds> key <NAME>} (an LWJGL key name), {@code <seconds> shot <name>} (a PNG in {@code build/play-battle})
 * and {@code <seconds> quit}.
 */
public final class PlayBattleMain {

    private static final String FIXTURES = "src/test/resources/battle-fixtures/";
    private static final Path SHOTS = Path.of("build/play-battle");
    private static final int DEFAULT_WIDTH = 1920;
    private static final int DEFAULT_HEIGHT = 1080;
    /** A frame longer than this, a stall or a battle rebuild, is not played as time passing. */
    private static final float MAX_FRAME_SECONDS = 0.1f;

    private PlayBattleMain() {
    }

    /** No campaign to write a result back to: the player continuing from a finished battle ends the run instead. */
    private static final class Context extends MarineOpsContext {
        boolean finished;

        Context() {
            super(null);
        }

        @Override boolean beginBattleSettlement() {
            finished = true;
            return false;
        }
    }

    private record Step(float seconds, String[] words) { }

    public static void main(String[] args) throws Exception {
        // What the game writes to starsector.log: a fallback logged here is otherwise invisible.
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.WARN);
        String source = sourceOf(System.getProperty("play.battle", "relief").strip());
        int[] size = windowSize(System.getProperty("play.window", ""));
        Path project = Path.of("");
        Path core = Path.of(System.getProperty("starsectorDir"), "starsector-core");
        Deque<Step> script = script(System.getProperty("play.script", ""));

        var strings = HeadlessBattles.install(project);
        InstalledHullEngines.install();
        InstalledVanillaData.install();
        InstalledWeaponSlots.install();
        PlayInput input = new PlayInput();
        Global.setSettings(new PlayBattleSettings(List.of(project.resolve("mod"), core), Global.getSettings(),
                size[0], size[1], input::pointerX, input::pointerY).settings());
        Global.setSoundPlayer(PlayBattleSettings.silence());

        long started = System.nanoTime();
        System.out.println("[playBattle] building " + source);
        BattleSimulation sim = HeadlessBattles.build(source);
        System.out.println("[playBattle] built in " + (System.nanoTime() - started) / 1_000_000 + " ms");

        openWindow(size[0], size[1], source);
        int status = 0;
        try {
            Context context = new Context();
            context.setBattleSimulation(sim);
            BattleScreen screen = new BattleScreen();
            PositionAPI position = position(size[0], size[1]);
            screen.attach(position, context, () -> context.finished = true);
            play(screen, position, context, input, source, size, script);
            BattleSimulation last = context.getBattleSimulation();
            System.out.println("[playBattle] ended at tick " + last.getSimTickIndex()
                    + (last.isComplete() ? ", winner " + last.getWinner() : ", unfinished"));
        } catch (Throwable failure) {
            failure.printStackTrace();
            status = 1;
        } finally {
            Display.destroy();
            strings.close();
        }
        // Simulation-owned executors are not all daemons; nothing else should hold the JVM up.
        System.exit(status);
    }

    private static void play(BattleScreen screen, PositionAPI position, Context context, PlayInput input, String source,
                             int[] size, Deque<Step> script) throws Exception {
        long last = System.nanoTime();
        long playingSince = 0L;
        boolean quit = false;
        while (!quit && !context.finished && !Display.isCloseRequested()) {
            long now = System.nanoTime();
            float dt = Math.min((now - last) / 1e9f, MAX_FRAME_SECONDS);
            last = now;

            List<InputEventAPI> events = new ArrayList<>(input.poll());
            for (InputEventAPI event : events) {
                if (event.isKeyDownEvent() && !event.isRepeat() && event.getEventValue() == Keyboard.KEY_F9) {
                    event.consume();
                    System.out.println("[playBattle] rebuilding " + source);
                    context.setBattleSimulation(HeadlessBattles.build(source));
                    screen.attach(position, context, () -> context.finished = true);
                    playingSince = 0L;
                    last = System.nanoTime();
                }
            }
            // A script's clock starts when the loading screen is done, however long that took.
            if (playingSince == 0L && context.getBattleSimulation().getSimTickIndex() > 0) playingSince = now;
            String shot = null;
            while (playingSince != 0L && !script.isEmpty() && script.peek().seconds() <= (now - playingSince) / 1e9f) {
                String[] words = script.poll().words();
                switch (words[0]) {
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
                    case "quit" -> quit = true;
                    default -> throw new IllegalArgumentException("unknown script step: " + words[0]);
                }
            }

            screen.processInput(events);
            screen.advance(dt);
            beginFrame(size[0], size[1]);
            screen.render(1f);
            if (shot != null) shot(size[0], size[1], shot);
            Display.update();
            Display.sync(60);
        }
        screen.detach();
    }

    /** The state the game's UI hooks can rely on: a screen-sized, y-up orthographic view with alpha blending. */
    private static void beginFrame(int width, int height) {
        glViewport(0, 0, width, height);
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
        Display.setTitle("Starsector Marines: " + source + " (F9 restarts)");
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

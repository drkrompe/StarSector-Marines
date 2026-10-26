package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.audio.NarrationPlayer;
import com.dillon.starsectormarines.battle.audio.StoryNarrationPlayer;
import com.dillon.starsectormarines.battle.audio.WaylineNarration;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveProjection;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot;
import com.dillon.starsectormarines.battle.objective.side.SideObjective;
import com.dillon.starsectormarines.battle.objective.side.SideTrigger;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Cell;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveMoveOrder;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vision.BuildingVisibilityPass;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.BattleShotAudio;
import com.dillon.starsectormarines.ops.battleview.BattleSoundLog;
import com.dillon.starsectormarines.ops.battleview.BattleSoundMix;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.FfmpegVideo;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.json.JSONObject;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

/**
 * The whole Dust Ledger as a player would see it, for the owner's preview: the production chapter fixture under
 * autonomous command, with the side jobs done by squads sent there as a player sends them (the feed tap, the warden's
 * nod and the coupling to the Harrow yard), played to the end. Played twice: once undrawn for its timeline, then
 * recorded at 1x through a directed camera (the look preset with the mission beacons, the HUD rail and the story
 * subtitles) that cuts to where things happen, with the screen's soundtrack mixed in: the music director, the
 * battle's sounds and the screen's ambient bed. 1920x1080 at 15 frames a second. Opt-in.
 */
public final class DustLedgerMissionPreviewSuite implements SnapshotSuite {

    /** {@code true} plays the battle without drawing it and prints its timeline. */
    static final String DRY_RUN_PROPERTY = "snapshot.preview.dryRun";
    /** The longest the battle may run, in ticks: past the lift-off clock with room to spare. */
    static final int MAX_TICKS = Math.round(420f / BattleSimulation.TICK_DT);
    /** Seconds after the landing before the jobs are handed out, as a player would after the company is down. */
    static final float ORDERS_AT_SECONDS = 6f;
    static final int WIDTH = 1920, HEIGHT = 1080;
    /** A frame every this many ticks: 15 frames a second. */
    static final int EVERY_TICKS = 2;
    /** How long the recording runs on past the battle's end, for the aftermath music over the apron. */
    static final float AFTERMATH_SECONDS = 12f;
    /** The HUD's inset from the screen edge, as in play. */
    static final int HUD_INSET = 12;
    /** The Dust Ledger's narration lines, by their catalog mission id. */
    static final String NARRATION_MISSION = "story_corvus_dust_ledger";
    static final long AMBIENCE_SEED = 7L;
    /** How long a following camera takes to close most of the way on what it follows. */
    static final float EASE_SECONDS = 0.8f;
    /** How near the ledger room a squad must be to count as the assault. */
    static final float ASSAULT_RADIUS_CELLS = 45f;

    @Override public String id() { return "dust-ledger-mission-preview"; }

    @Override public String label() { return "The whole Dust Ledger with its side jobs done, as a player would see it"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            // Before any battle is built: a font first touched without its metrics draws no glyphs after.
            installFonts(context, Fonts.ORBITRON_12_BOLD, Fonts.INSIGNIA_15_AA);
            Timeline timeline = dryRun();
            if (Boolean.getBoolean(DRY_RUN_PROPERTY)) return List.of();
            return List.of(record(context, timeline));
        }
    }

    /** Plays the battle once undrawn, printing its timeline, and returns when each beat first fired. */
    private static Timeline dryRun() {
        try (BattleSimulation sim = fixture()) {
            Script script = new Script();
            var sites = sim.getDustLedger().sites();
            System.out.println("    [sites] apron " + sites.apron().centre() + " crate " + sites.crate() + " harrow " + sites.harrowYard().centre()
                    + " well " + sites.wellHouse().centre() + " feed " + sites.feedJunction().centre() + " ledger " + sites.ledgerRoom().centre()
                    + " ring " + sites.exclusionRing().centre() + " pad " + sites.skimmerPad() + " gate " + sites.eastGate()
                    + " technical " + sim.getDustLedger().technical());
            int lastPrint = -1;
            int tick = 0;
            for (; tick < MAX_TICKS && !sim.isComplete(); tick++) {
                script.tick(sim);
                sim.advance(BattleSimulation.TICK_DT);
                int second = (int) (tick * BattleSimulation.TICK_DT);
                if (second % 3 == 0 && second != lastPrint) {
                    lastPrint = second;
                    System.out.println(status(sim, script));
                }
            }
            Map<String, Integer> beats = new HashMap<>();
            for (StoryBeatLog.Event beat : sim.getStoryBeats()) {
                beats.putIfAbsent(beat.id(), beat.tick());
                System.out.printf(Locale.ROOT, "    [beat] %6.1f s %s%n", beat.tick() * BattleSimulation.TICK_DT, beat.id());
            }
            System.out.println("    [end] complete " + sim.isComplete() + " winner " + sim.getWinner()
                    + " grade " + sim.getDustLedger().progress().grade() + " broker " + sim.getDustLedger().progress().broker());
            return new Timeline(beats, script.landedAt, tick);
        }
    }

    /** Plays the same battle again, drawn through the directed camera with the HUD, subtitles and soundtrack. */
    private static SnapshotArtifact record(SnapshotContext context, Timeline timeline) throws Exception {
        Path modRoot = context.modRoot();
        Path scratch = context.projectRoot().resolve("build/tmp/mission-preview");
        Files.createDirectories(scratch);
        Path soundFile = scratch.resolve("dust-ledger-mission-preview.jsonl");
        BattleSoundLog sound = BattleSoundLog.open(soundFile).hearingTrackEnds(modRoot);
        Path video = Files.createTempFile(scratch, "preview-", ".mp4");
        int last = timeline.end() + Math.round(AFTERMATH_SECONDS / BattleSimulation.TICK_DT);
        try (BattleSimulation sim = fixture();
             FfmpegVideo encoder = FfmpegVideo.open(video, WIDTH, HEIGHT,
                     Math.round(1f / BattleSimulation.TICK_DT) + "/" + EVERY_TICKS)) {
            Script script = new Script();
            Director camera = new Director(timeline);
            Overlays overlays = new Overlays(context);
            Ambience ambience = new Ambience(new Random(AMBIENCE_SEED));
            StoryNarrationPlayer narration = new StoryNarrationPlayer(
                    WaylineNarration.parse(new JSONObject(Files.readString(modRoot.resolve(WaylineNarration.PATH)))),
                    NARRATION_MISSION, new NarrationPlayer(line -> null), line -> { });
            ReviewImpactFx fx = new ReviewImpactFx(modRoot);
            float[] view = camera.frame(sim, 0);
            BattleReviewFrameRenderer renderer = BattleReviewFrameRenderer.look(modRoot, WIDTH, HEIGHT, view[0], view[1], view[2])
                    .withMissionMarkers();
            int frames = 0;
            for (int tick = 0; tick <= last; tick++) {
                if (tick > 0) {
                    float[] ear = camera.frame(sim, tick);
                    ambience.tick(sound, ear);
                    sound.tick(sim, ear);
                    if (!sim.isComplete()) {
                        script.tick(sim);
                        sim.advance(BattleSimulation.TICK_DT);
                    }
                    BuildingVisibilityPass.advanceAlpha(sim.getBuildings(), BattleSimulation.TICK_DT);
                    fx.feed(sim);
                    narration.advance(BattleSimulation.TICK_DT, sim.getPlayerStoryBeatsAfter(narration.cursor()), false,
                            sim.isComplete());
                }
                if (tick % EVERY_TICKS != 0) continue;
                view = camera.frame(sim, tick);
                Overlays.Hud hud = overlays.hud(sim);
                BufferedImage frame = renderer.refocus(view[0], view[1], view[2])
                        .withHudCover(HUD_INSET + hud.railLeft(), HEIGHT - HUD_INSET - hud.railBottom())
                        .render(sim, "", ReviewAnnotations.NONE, fx);
                Graphics2D g = frame.createGraphics();
                g.drawImage(hud.image(), HUD_INSET, HUD_INSET, null);
                overlays.subtitle(g, narration.current());
                g.dispose();
                encoder.add(frame);
                frames++;
                if (frames % 150 == 0)
                    System.out.printf(Locale.ROOT, "    [preview] %.0f s of %.0f s%n", tick * BattleSimulation.TICK_DT,
                            last * BattleSimulation.TICK_DT);
            }
            Path silent = encoder.finish();
            Path wav = scratch.resolve("dust-ledger-mission-preview.wav");
            BattleSoundMix.mix(modRoot, soundFile, frames * EVERY_TICKS * (double) BattleSimulation.TICK_DT, wav);
            Path scored = Files.createTempFile(scratch, "preview-scored-", ".mp4");
            FfmpegVideo.mux(silent, wav, scored);
            Files.deleteIfExists(silent);
            camera.printShots();
            return SnapshotArtifact.video("preview/dust-ledger-mission-preview.mp4", scored, List.of());
        }
    }

    static BattleSimulation fixture() {
        var chapter = CorvusChapter.DUST_LEDGER;
        var mission = chapter.mission("look", chapter.planetName, "");
        var manifest = List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6),
                new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6));
        return chapter.fixture(mission.battleSeed, manifest, chapter.profile(chapter.surface)).build();
    }

    /**
     * The player's orders once the company is down: a squad to the feed junction, one to the well house, whichever
     * squad lifts the crate to the Harrow yard (after its wait if it is the warden's), and the rest staged at the yard,
     * all outside the ring. When the three jobs are settled the orders stop and the company goes in on its own.
     */
    static final class Script {
        int feedSquad = -1, wardenSquad = -1, couplingSquad = -1;
        int landedAt = -1;
        int releasedAt = -1;

        void tick(BattleSimulation sim) {
            DustLedgerSequence ledger = sim.getDustLedger();
            if (releasedAt >= 0 || !ledger.progress().quiet()) return;
            List<Squad> squads = infantry(sim);
            int tick = sim.getSimTickIndex();
            if (landedAt < 0) {
                if (squads.isEmpty() || squads.stream().anyMatch(s -> sim.isRiding(s.leaderId))) return;
                landedAt = tick;
            }
            if (tick < landedAt + Math.round(ORDERS_AT_SECONDS / BattleSimulation.TICK_DT)) return;
            DustLedgerSequence.Sites sites = ledger.sites();
            if (feedSquad < 0) feedSquad = nearest(sim, squads, sites.feedJunction().centre(), -1, -1);
            if (wardenSquad < 0) wardenSquad = nearest(sim, squads, sites.wellHouse().centre(), feedSquad, -1);
            if (couplingSquad < 0) couplingSquad = nearest(sim, squads, sites.crate(), feedSquad, wardenSquad);
            if (tick % 30 != 0) return;
            boolean feedDone = settled(sim, "dust-ledger-feed");
            boolean wardenDone = settled(sim, "dust-ledger-warden");
            SideObjective coupling = sim.getSideObjectives().job("dust-ledger-coupling");
            boolean couplingDone = coupling.state().isFinal();
            if (feedDone && wardenDone && couplingDone) {
                releasedAt = tick;
                return;
            }
            long carrier = coupling.trigger() instanceof SideTrigger.Carry carry ? carry.carrier() : 0L;
            int carrierSquad = carrier != 0L ? squadOf(sim, squads, carrier) : -1;
            Cell yard = sites.harrowYard().centre();
            for (Squad s : squads) {
                Cell to;
                if (s.id == feedSquad) to = sites.feedJunction().centre();
                else if (s.id == wardenSquad && !wardenDone) to = sites.wellHouse().centre();
                else if (s.id == carrierSquad) to = yard;
                else if (s.id == couplingSquad && carrier == 0L && !couplingDone) to = sites.crate();
                else to = yard;
                order(sim, s.id, to);
            }
        }

        private static boolean settled(BattleSimulation sim, String job) {
            return sim.getSideObjectives().job(job).state().isFinal();
        }

        private static int squadOf(BattleSimulation sim, List<Squad> squads, long unit) {
            for (Squad s : squads)
                for (int i = 0, n = sim.squadMemberCount(s.id); i < n; i++)
                    if (sim.squadMemberAt(s.id, i) == unit) return s.id;
            return -1;
        }

        private static void order(BattleSimulation sim, int squad, Cell to) {
            if (squad < 0) return;
            var active = sim.getSquadMoveOrderService().activeOrder(squad);
            if (active instanceof ActiveMoveOrder move
                    && move.requestedX() == to.x() && move.requestedY() == to.y()) return;
            sim.getSquadMoveOrderService().requestMove(squad, to.x(), to.y());
        }
    }

    static List<Squad> infantry(BattleSimulation sim) {
        List<Squad> out = new ArrayList<>();
        for (Squad s : sim.getSquads())
            if (s.faction == Faction.MARINE && !s.isMechSquad() && s.aliveMembers > 0) out.add(s);
        out.sort((a, b) -> Integer.compare(a.id, b.id));
        return out;
    }

    static int nearest(BattleSimulation sim, List<Squad> squads, Cell to, int not1, int not2) {
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (Squad s : squads) {
            if (s.id == not1 || s.id == not2 || !sim.world().isAlive(s.leaderId)) continue;
            double d = Math.hypot(sim.world().cellX(s.leaderId) - to.x(), sim.world().cellY(s.leaderId) - to.y());
            if (d < bestD) { bestD = d; best = s.id; }
        }
        return best;
    }

    private static String status(BattleSimulation sim, Script script) {
        var progress = sim.getDustLedger().progress();
        StringBuilder out = new StringBuilder(String.format(Locale.ROOT, "    [t] %5.1f s alarm %s", sim.getSimTickIndex()
                * BattleSimulation.TICK_DT, progress.alarm()));
        for (SideObjective job : sim.getSideObjectives().jobs())
            out.append(' ').append(job.key().replace("dust-ledger-", "")).append('=').append(job.state());
        long technical = sim.getDustLedger().technical();
        if (technical != 0L && sim.convoy().isVehicle(technical) && sim.convoy().body(technical) != null)
            out.append(String.format(Locale.ROOT, " tech@%.0f,%.0f", sim.convoy().body(technical).x, sim.convoy().body(technical).y));
        long broker = sim.getDustLedger().broker();
        if (broker != 0L && sim.world().isAlive(broker))
            out.append(String.format(Locale.ROOT, " broker@%d,%d", sim.world().cellX(broker), sim.world().cellY(broker)));
        for (Squad s : infantry(sim)) {
            if (s.id == script.couplingSquad && sim.getSideObjectives().job("dust-ledger-coupling").trigger() instanceof SideTrigger.Carry carry
                    && carry.carrier() != 0L)
                out.append(String.format(Locale.ROOT, " crate@%d,%d", sim.world().cellX(carry.carrier()), sim.world().cellY(carry.carrier())));
            String role = s.id == script.feedSquad ? "F" : s.id == script.wardenSquad ? "W" : s.id == script.couplingSquad ? "C" : "-";
            out.append(String.format(Locale.ROOT, " %s%d@%d,%d", role, s.id,
                    sim.world().isAlive(s.leaderId) ? sim.world().cellX(s.leaderId) : -1,
                    sim.world().isAlive(s.leaderId) ? sim.world().cellY(s.leaderId) : -1));
        }
        return out.toString();
    }

    /** When each beat first fired in the dry run, when the company was down, and the tick the battle ended. */
    record Timeline(Map<String, Integer> beats, int landedAt, int end) {
        static final String BEAT_PREFIX = "corvus.dust_ledger.";

        int at(String beat, int otherwise) {
            return beats.getOrDefault(BEAT_PREFIX + beat, otherwise);
        }
    }

    /**
     * The directed camera: a list of shots, each from a tick, cutting hard between them and easing within a shot that
     * follows something. Answers {@code {centreX, centreY, cellsAcross}} in map cells.
     */
    static final class Director {
        private record Shot(String name, int from, Function<BattleSimulation, float[]> aim) { }

        private final List<Shot> shots = new ArrayList<>();
        private final Timeline timeline;
        private Shot current;
        private float[] eased;
        private int framed = -1;

        Director(Timeline t) {
            timeline = t;
            int s = Math.round(1f / BattleSimulation.TICK_DT);
            int landed = Math.max(0, t.landedAt());
            int alarm = t.at("alarm_sighted", t.end());
            int boarded = t.at("broker_boarded", alarm + 30 * s);
            int seized = t.at("ledger_seized", boarded + 15 * s);
            int wardenIn = t.at("warden_approach", alarm) - 3 * s;
            int wardenOut = t.at("warden_nod", wardenIn + 8 * s) + 2 * s;
            shots.add(new Shot("landing", 0, sim -> site(sim, "apron", 80f)));
            shots.add(new Shot("coupling", landed + 4 * s, sim -> crate(sim, 60f)));
            shots.add(new Shot("well house", wardenIn, sim -> site(sim, "well", 50f)));
            shots.add(new Shot("feed and yard", wardenOut, sim -> site(sim, "feed and yard", 72f)));
            shots.add(new Shot("alarm", alarm, sim -> site(sim, "ring", 110f)));
            shots.add(new Shot("broker runs", alarm + 9 * s, sim -> broker(sim, 64f)));
            shots.add(new Shot("assault", alarm + 19 * s, sim -> company(sim, true, 72f)));
            shots.add(new Shot("skimmer pad", boarded - 5 * s, sim -> site(sim, "pad", 60f)));
            shots.add(new Shot("ledger room", boarded + 3 * s, sim -> company(sim, true, 64f)));
            shots.add(new Shot("seizure", seized - 6 * s, sim -> site(sim, "ledger", 50f)));
            shots.add(new Shot("return", seized + 6 * s, sim -> company(sim, false, 84f)));
            shots.add(new Shot("aftermath", t.end(), sim -> site(sim, "apron", 84f)));
            shots.sort((a, b) -> Integer.compare(a.from(), b.from()));
        }

        /** The camera at {@code tick}; asking twice in one tick answers the same. */
        float[] frame(BattleSimulation sim, int tick) {
            if (tick == framed && eased != null) return eased.clone();
            framed = tick;
            Shot shot = shots.get(0);
            for (Shot candidate : shots) if (candidate.from() <= tick) shot = candidate;
            float[] aim = shot.aim().apply(sim);
            if (shot != current || eased == null) {
                current = shot;
                eased = aim.clone();
            } else {
                float k = 1f - (float) Math.exp(-BattleSimulation.TICK_DT / EASE_SECONDS);
                for (int i = 0; i < 3; i++) eased[i] += (aim[i] - eased[i]) * k;
            }
            return eased.clone();
        }

        /** Prints each shot's start, for cutting the chapters. */
        void printShots() {
            for (Shot shot : shots)
                System.out.printf(Locale.ROOT, "    [shot] %6.1f s %s%n", shot.from() * BattleSimulation.TICK_DT, shot.name());
            System.out.printf(Locale.ROOT, "    [shot] %6.1f s battle ends%n", timeline.end() * BattleSimulation.TICK_DT);
        }

        private static float[] site(BattleSimulation sim, String place, float cells) {
            var sites = sim.getDustLedger().sites();
            Cell at = switch (place) {
                case "apron" -> sites.apron().centre();
                case "well" -> sites.wellHouse().centre();
                case "ring" -> sites.exclusionRing().centre();
                case "pad" -> sites.skimmerPad();
                case "ledger" -> sites.ledgerRoom().centre();
                default -> null;
            };
            if (at == null) {
                Cell feed = sites.feedJunction().centre(), yard = sites.harrowYard().centre();
                return new float[] {(feed.x() + yard.x()) / 2f, (feed.y() + yard.y()) / 2f, cells};
            }
            return new float[] {at.x(), at.y(), cells};
        }

        /** The crate's carrier, else the crate where it waits, else the Harrow yard once it is delivered. */
        private static float[] crate(BattleSimulation sim, float cells) {
            SideObjective coupling = sim.getSideObjectives().job("dust-ledger-coupling");
            if (coupling.trigger() instanceof SideTrigger.Carry carry && carry.carrier() != 0L
                    && sim.world().isAlive(carry.carrier()))
                return new float[] {sim.world().cellX(carry.carrier()), sim.world().cellY(carry.carrier()), cells};
            Cell at = coupling.state().isFinal() ? sim.getDustLedger().sites().harrowYard().centre()
                    : sim.getDustLedger().sites().crate();
            return new float[] {at.x(), at.y(), cells};
        }

        private static float[] broker(BattleSimulation sim, float cells) {
            long broker = sim.getDustLedger().broker();
            if (broker != 0L && sim.world().isAlive(broker))
                return new float[] {sim.world().cellX(broker), sim.world().cellY(broker), cells};
            return site(sim, "pad", cells);
        }

        /** The company's middle: of those near the ledger room during the assault, else of everyone. */
        private static float[] company(BattleSimulation sim, boolean assault, float cells) {
            Cell ledger = sim.getDustLedger().sites().ledgerRoom().centre();
            float sx = 0f, sy = 0f;
            int n = 0;
            for (Squad s : infantry(sim)) {
                if (!sim.world().isAlive(s.leaderId)) continue;
                int x = sim.world().cellX(s.leaderId), y = sim.world().cellY(s.leaderId);
                if (assault && Math.hypot(x - ledger.x(), y - ledger.y()) > ASSAULT_RADIUS_CELLS) continue;
                sx += x;
                sy += y;
                n++;
            }
            if (n == 0) return assault ? new float[] {ledger.x(), ledger.y(), cells} : site(sim, "apron", cells);
            return new float[] {sx / n, sy / n, cells};
        }
    }

    /** The HUD rail and the story subtitle, composed as the screen composes them. */
    static final class Overlays {
        record Hud(BufferedImage image, float railLeft, float railBottom) { }

        private final SnapshotContext context;
        private final HeadlessUiRenderer ui;
        private MissionObjectiveSnapshot shown;
        private Hud hud;
        private WaylineNarration.Line spoken;
        private BufferedImage subtitle;
        private UiViewport subtitleAt;

        Overlays(SnapshotContext context) {
            this.context = context;
            ui = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore());
        }

        /** The HUD as it stands, redrawn only when the mission picture changes. */
        Hud hud(BattleSimulation sim) throws Exception {
            MissionObjectiveSnapshot picture = MissionObjectiveProjection.capture(sim);
            if (hud != null && picture.equals(shown)) return hud;
            shown = picture;
            Reactor reactor = new Reactor();
            BattleHudOverlayModel model = new BattleHudOverlayModel(reactor, ignored -> { }, "Pause", "1x", "2x", "4x");
            model.updateProjected(1f, List.of());
            var presentation = model.updateMission(picture, ignored -> { });
            var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)),
                    List.of(BattleHudOverlay.COMPONENT_PATH));
            loader.reload();
            try (var instance = loader.build(reactor, BattleHudOverlay.COMPONENT, model.props())) {
                BattleHudOverlay.wireLayout(instance);
                var doc = new UiDocument(instance.root());
                instance.styles().forEach(doc::addStyleSheet);
                doc.theme(MarineOpsThemes.standard());
                int width = WIDTH - 2 * HUD_INSET;
                int height = Math.round(BattleHudOverlay.documentHeight(presentation));
                doc.layout(width, height);
                var rail = instance.requireElement("battle-command-rail").box().borderBox();
                hud = new Hud(ui.render(doc, width, height), rail.x(), rail.bottom());
                return hud;
            }
        }

        /** Draws {@code line}'s subtitle where the screen puts it, if a line is being spoken. */
        void subtitle(Graphics2D g, WaylineNarration.Line line) throws Exception {
            if (line != spoken) {
                spoken = line;
                subtitle = null;
                if (line != null) {
                    var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)),
                            List.of(StorySubtitleOverlay.COMPONENT_PATH, "data/ui/components/battle/company-subtitle.mlx"));
                    loader.reload();
                    Map<String, Object> props = Map.of("speaker", line.speaker(), "portrait", line.portrait(),
                            "subtitle", line.text());
                    try (MarkupInstance instance = loader.build(new Reactor(),
                            line.portrait().isBlank() ? "company-subtitle" : StorySubtitleOverlay.COMPONENT, props)) {
                        UiDocument document = new UiDocument(instance.root());
                        instance.styles().forEach(document::addStyleSheet);
                        document.theme(MarineOpsThemes.standard());
                        subtitleAt = StorySubtitleOverlay.viewport(new UiViewport(0, 0, WIDTH, HEIGHT));
                        subtitle = ui.render(document, Math.round(subtitleAt.documentWidth()),
                                Math.round(subtitleAt.documentHeight()));
                    }
                }
            }
            if (subtitle != null)
                g.drawImage(subtitle, Math.round(subtitleAt.screenX()),
                        Math.round(HEIGHT - subtitleAt.screenY() - subtitleAt.height()), null);
        }
    }

    /**
     * The battle screen's ambient bed, which the battle's own rules do not start: one or two machine loops at the
     * screen's level, and a distant boom every few seconds from off to one side, muffled or a pitched-down blast, at
     * the screen's levels and odds ({@code BattleScreen.driveAmbientBackground}). The loops sit at the listener: the
     * mix does not model distance, so the screen's far-off anchors would sound hard to one side at full level.
     */
    static final class Ambience {
        private static final String[] LOOPS = {"marines_ambient_fan_noise", "marines_ambient_motor_1",
                "marines_ambient_motor_2", "marines_ambient_loudmotor_3", "marines_ambient_radiator_1",
                "marines_ambient_helicopter_2"};
        private static final float LOOP_VOLUME = 0.2f;
        private static final float BOOM_MIN_GAP = 4f, BOOM_MAX_GAP = 12f;
        private static final float BOOM_DISTANCE_CELLS = 600f / BattleShotAudio.WORLD_UNITS_PER_CELL;
        private static final float MUFFLED_CHANCE = 0.6f, MUFFLED_VOLUME = 0.4f;
        private static final float NEAR_PITCH = 0.6f, NEAR_VOLUME = 0.3f, PITCH_JITTER = 0.15f;

        private final Random rng;
        private final List<String> loops;
        private float untilBoom;

        Ambience(Random rng) {
            this.rng = rng;
            List<String> pool = new ArrayList<>(List.of(LOOPS));
            Collections.shuffle(pool, rng);
            loops = pool.subList(0, 1 + rng.nextInt(2));
            untilBoom = gap();
        }

        void tick(BattleSoundLog log, float[] ear) {
            for (int i = 0; i < loops.size(); i++) log.held(loops.get(i), ear[0], ear[1], 1f, LOOP_VOLUME, i, ear);
            untilBoom -= BattleSimulation.TICK_DT;
            if (untilBoom > 0f) return;
            untilBoom = gap();
            double angle = rng.nextFloat() * Math.PI * 2.0;
            float x = ear[0] + (float) Math.cos(angle) * BOOM_DISTANCE_CELLS;
            float y = ear[1] + (float) Math.sin(angle) * BOOM_DISTANCE_CELLS;
            float jitter = 1f + (rng.nextFloat() * 2f - 1f) * PITCH_JITTER;
            if (rng.nextFloat() < MUFFLED_CHANCE) log.start("marines_explosion_muffled", x, y, jitter, MUFFLED_VOLUME, ear);
            else log.start("marines_explosion", x, y, NEAR_PITCH * jitter, NEAR_VOLUME, ear);
        }

        private float gap() {
            return BOOM_MIN_GAP + rng.nextFloat() * (BOOM_MAX_GAP - BOOM_MIN_GAP);
        }
    }

    /**
     * Installs each font's metrics from the mod (or the game's core) before any battle is built: a font first touched
     * without them draws no glyphs after, leaving a label's backing plate empty.
     */
    static void installFonts(SnapshotContext context, BitmapFont... fonts) throws Exception {
        for (BitmapFont font : fonts) {
            Path path = context.modRoot().resolve(font.sourcePath());
            if (!Files.exists(path)) path = context.starsectorCore().resolve(font.sourcePath());
            font.installMetrics(Files.readString(path));
        }
    }
}

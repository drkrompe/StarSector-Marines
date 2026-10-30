package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.combat.Crippled;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A Hound taken apart: <b>does a mech under rifle and rocket fire lose a capability before it loses
 * the machine?</b> The acceptance scene for mech locational damage ({@code mechs-nouns.md}, Section loss).
 *
 * <p>One Hound with its stock loadout (nose chaingun, one centred SRM pod) under an attack-move
 * across open ground at a ten-strong hostile rifle squad holding the far side, one seat of which
 * carries a rocket tube (three rounds) — the shape a rolled infantry kit takes. Nothing is scripted
 * after the opening: every round is the production fire path and every section roll the production
 * one. The story the model promises is the pod going first (the smallest pool, and every
 * shoulder-height round lands on it), then the legs (the largest share of hits), then a crippled
 * machine that limps on at its crawl and keeps firing, and finally a torso kill, the only way it
 * dies.
 *
 * <p><b>The order is a roll, not a law</b>, which is why there are two loops. The recorded
 * {@code rifles-and-rockets} loop is one seed in which the story comes out in order; the
 * {@code seed-sweep} loop plays eight seeds with the rocket tube and eight rifles-only and asserts
 * only what must hold on every one of them — death only through the torso, a crippled chassis never
 * walks faster than its crawl, a destroyed mount never fires — while it reports how often each ordering came out. A rocket
 * (162 damage, penetration 18) takes whichever section it lands on in one round and spills the rest
 * into torso structure, so the tube's first round decides most of the story: the pod on one seed,
 * the legs or the nose gun on another.
 *
 * <p>The opposition is given health enough to outlast the Hound. That is a statement about the
 * question, not about the fight: a squad the chaingun has killed stops shooting, and the scene would
 * then record how long the Hound takes to win rather than how it comes apart. Nothing on the Hound's
 * side is touched.
 */
public final class MechLocationalDamageScene implements BehaviorScene {

    static final String LOOP = "rifles-and-rockets";
    static final String SWEEP_LOOP = "seed-sweep";
    static final int FRAME_EVERY_TICKS = 10;

    private static final int WIDTH = 48;
    private static final int HEIGHT = 27;
    private static final int MAX_TICKS = 9000;
    /** Frames kept after the death so the wreck is on screen. */
    private static final int AFTER_DEATH_TICKS = 60;

    private static final int HOUND_X = 10;
    private static final int HOUND_Y = 13;
    private static final int SQUAD_X = 36;
    private static final int SQUAD_Y = 9;
    private static final int SQUAD_SIZE = 10;
    private static final int ROCKET_SEATS = 1;
    private static final float SQUAD_HEALTH = 100_000f;

    /**
     * The recorded seed: the first of the swept seeds (1-8) on which the tube's opening round lands
     * on the pod and the legs then go before the torso. Chosen, not typical; the sweep says how
     * typical.
     */
    private static final long SUBJECT_SEED = 4L;
    private static final int SWEEP_SEEDS = 8;

    private static final String RIFLE = "weapon.field-rifle";
    private static final String ROCKETS = "special.srm-1";
    /**
     * Crippled means slowed, never planted: the fastest step a crippled Hound takes is at most its crawl
     * ({@link Crippled#SPEED_SHARE} of the fastest it stepped on whole legs), with this much headroom for
     * the walk's own ramp and a pivot's shift.
     */
    private static final double CRAWL_HEADROOM = 1.15;
    /** Cells a tick a pivot on the spot may shift the body by, on top of the crawl. */
    private static final double PIVOT_SHIFT = 0.02;

    private static final String HOUND = "hound";
    private static final String SQUAD = "rifles";

    private Trace last;

    @Override public String id() { return "mech-locational-damage"; }

    @Override
    public String label() {
        return "Mech locational damage: a Hound under rifle and rocket fire loses its pod, then its legs,"
                + " fights on crippled, and dies only to a torso kill";
    }

    @Override public int frameDelayMillis() { return 70; }

    /** Ticks of the key moments of the last recorded play, for the snapshot suite's stills; -1 where absent. */
    int[] keyTicks() {
        Trace t = last;
        if (t == null) return new int[0];
        return new int[] {t.podLostTick, t.legsLostTick, t.firstCrippledShotTick, t.deathTick};
    }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        Trace subject = playOnce(frames, SUBJECT_SEED, ROCKET_SEATS);
        last = subject;
        List<Trace> sweep = new ArrayList<>();
        for (int rockets : new int[] {ROCKET_SEATS, 0}) {
            for (long seed = 1; seed <= SWEEP_SEEDS; seed++) {
                Trace t = playOnce(FrameSink.NONE, seed, rockets);
                t.seed = seed;
                t.rocketSeats = rockets;
                sweep.add(t);
            }
        }
        return List.of(report(subject), reportSweep(sweep));
    }

    private Trace playOnce(FrameSink frames, long seed, int rocketSeats) {
        SceneBuilder.SquadSpec rifles = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false).seed(seed)
                .squad(HOUND).faction(Faction.MARINE).size(1).at(HOUND_X + 2, HOUND_Y)
                .mech(MechVariant.HOUND, MechRole.ASSAULT)
                .assigned(id -> ObjectiveAssignment.attackMove(id, SQUAD_X, HOUND_Y))
                .done()
                .squad(SQUAD).faction(Faction.DEFENDER).type(UnitType.MARINE_RED).size(SQUAD_SIZE)
                .primary(RIFLE)
                .inFile(SQUAD_X, SQUAD_Y, 0, 1)
                .health(SQUAD_HEALTH)
                .assigned(id -> ObjectiveAssignment.defendArea(id, SQUAD_X, SQUAD_Y + SQUAD_SIZE / 2));
        if (rocketSeats > 0) rifles.special(ROCKETS, rocketSeats);
        SceneWorld world = rifles.done().build();
        Trace trace = new Trace(world.members(HOUND)[0]);
        SceneRun run = SceneRun.of(world).observe(trace)
                .frames(frames, LOOP, FRAME_EVERY_TICKS, trace::caption);
        try {
            while (run.tick() < MAX_TICKS
                    && !(trace.deathTick >= 0 && run.tick() > trace.deathTick + AFTER_DEATH_TICKS)) {
                run.step();
            }
        } finally {
            world.sim().close();
        }
        trace.ticks = run.tick();
        return trace;
    }

    // ---- instrument ----

    private static final class Trace implements TickObserver {
        final long hound;
        long seed = SUBJECT_SEED;
        int rocketSeats = ROCKET_SEATS;
        int ticks;
        /** Held from the first tick: the component may leave the world with the body. */
        MechLoadoutComponent loadout;
        MechSections sections;
        int firstHitTick = -1;
        int podLostTick = -1;
        int armsLostTick = -1;
        int legsLostTick = -1;
        int headLostTick = -1;
        int deathTick = -1;
        boolean torsoAtZeroOnDeath;
        float torsoStructureOnDeath = -1f;
        int shotsTotal;
        int shotsAfterCrippled;
        int firstCrippledShotTick = -1;
        /** Rounds from a mount observed after that mount was seen destroyed: must stay zero. */
        int shotsFromLostMounts;
        float crippledX = Float.NaN;
        float crippledY = Float.NaN;
        double crippledDrift;
        double travelBeforeCrippled;
        double travelCrippled;
        double topStepBefore;
        double topStepCrippled;
        float lastX = Float.NaN;
        float lastY = Float.NaN;

        Trace(long hound) {
            this.hound = hound;
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            boolean alive = sim.getRoster().isAliveById(hound);
            if (loadout == null && sim.world().hasMechLoadout(hound)) loadout = sim.world().mechLoadout(hound);
            if (sections == null && loadout != null) sections = loadout.sections();
            if (sections != null) {
                if (firstHitTick < 0) firstHitTick = tick;
                if (podLostTick < 0 && sections.destroyed(MechSection.LEFT_SHOULDER)) podLostTick = tick;
                if (armsLostTick < 0 && sections.destroyed(MechSection.ARMS)) armsLostTick = tick;
                if (legsLostTick < 0 && sections.destroyed(MechSection.LEGS)) legsLostTick = tick;
                if (headLostTick < 0 && sections.destroyed(MechSection.HEAD)) headLostTick = tick;
            }
            if (deathTick >= 0) return;
            if (!alive) {
                deathTick = tick;
                torsoAtZeroOnDeath = sections != null && sections.torsoDestroyed();
                torsoStructureOnDeath = sections != null ? sections.structure(MechSection.TORSO) : -1f;
                return;
            }
            float x = sim.world().x(hound);
            float y = sim.world().y(hound);
            boolean crippled = legsLostTick >= 0;
            double step = Float.isNaN(lastX) ? 0d : Math.hypot(x - lastX, y - lastY);
            if (!crippled) {
                travelBeforeCrippled += step;
                topStepBefore = Math.max(topStepBefore, step);
            } else if (tick > legsLostTick) {
                travelCrippled += step;
                topStepCrippled = Math.max(topStepCrippled, step);
            }
            lastX = x;
            lastY = y;
            if (crippled) {
                if (Float.isNaN(crippledX)) {
                    crippledX = x;
                    crippledY = y;
                }
                crippledDrift = Math.max(crippledDrift, Math.hypot(x - crippledX, y - crippledY));
            }
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (shot.shooterId != hound) continue;
                shotsTotal++;
                // Shots observed on the tick a loss is first seen left the barrel in the advance
                // that caused it; only later ones were fired after it.
                if (crippled && tick > legsLostTick) {
                    shotsAfterCrippled++;
                    if (firstCrippledShotTick < 0) firstCrippledShotTick = tick;
                }
                if (shot.mechWeaponDef != null
                        && (fromLostMount(shot, MechWeaponComponent.CHAINGUN, armsLostTick, tick)
                        || fromLostMount(shot, MechWeaponComponent.SRM_5, podLostTick, tick))) {
                    shotsFromLostMounts++;
                }
            }
        }

        private static boolean fromLostMount(ShotEvent shot, MechWeaponComponent component, int lostTick, int tick) {
            return lostTick >= 0 && tick > lostTick && component.weaponDef().id.equals(shot.mechWeaponDef.id);
        }

        String caption(int tick) {
            String state = sections == null ? "untouched"
                    : String.format(Locale.ROOT, "pod %s  gun %s  legs %s  head %s  torso %s",
                            section(MechSection.LEFT_SHOULDER), section(MechSection.ARMS),
                            section(MechSection.LEGS), section(MechSection.HEAD), section(MechSection.TORSO));
            String phase = deathTick >= 0 ? "DESTROYED"
                    : legsLostTick >= 0 ? "CRIPPLED, " + shotsAfterCrippled + " shots since"
                    : podLostTick >= 0 ? "pod lost" : "intact";
            return String.format(Locale.ROOT, "mech locational damage  t=%.1fs  Hound %s  |  armor+structure: %s",
                    tick / 30f, phase, state);
        }

        private String section(MechSection section) {
            if (sections.destroyed(section)) return "GONE";
            return String.format(Locale.ROOT, "%.0f+%.0f", sections.armor(section), sections.structure(section));
        }

        /** Whether the fastest step after the legs went is within the crawl. */
        boolean crawls() {
            return topStepCrippled <= topStepBefore * Crippled.SPEED_SHARE * CRAWL_HEADROOM + PIVOT_SHIFT;
        }

        /** Whether the nose gun or the pod was still fitted when the legs went. */
        boolean armedWhenCrippled() {
            boolean gunGone = armsLostTick >= 0 && armsLostTick <= legsLostTick;
            boolean podGone = podLostTick >= 0 && podLostTick <= legsLostTick;
            return legsLostTick >= 0 && !(gunGone && podGone);
        }

        boolean fullStory() {
            return podLostTick >= 0 && legsLostTick > podLostTick && deathTick > legsLostTick
                    && shotsAfterCrippled > 0 && torsoAtZeroOnDeath;
        }
    }

    // ---- verdicts ----

    private static String seconds(int tick) {
        return tick < 0 ? "never" : String.format(Locale.ROOT, "%.1fs (t%d)", tick / 30f, tick);
    }

    private SceneReport report(Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("sections-recorded", t.sections != null,
                t.sections != null ? "first hit seen at " + seconds(t.firstHitTick)
                        : "no section state ever written (locational damage off, or the Hound was never hit)"));
        verdicts.add(Verdict.of("pod-before-legs", t.podLostTick >= 0
                        && (t.legsLostTick < 0 || t.podLostTick < t.legsLostTick),
                "pod lost at " + seconds(t.podLostTick) + ", legs at " + seconds(t.legsLostTick)));
        verdicts.add(Verdict.of("legs-before-death", t.legsLostTick >= 0
                        && (t.deathTick < 0 || t.legsLostTick < t.deathTick),
                "legs lost at " + seconds(t.legsLostTick) + ", death at " + seconds(t.deathTick)));
        verdicts.add(Verdict.of("crippled-crawls", t.legsLostTick >= 0 && t.crawls(),
                String.format(Locale.ROOT, "fastest step %.3f cells a tick after the legs went, %.3f before (wanted"
                        + " at most %.0f%% of it); %.1f cells walked crippled, %.1f before", t.topStepCrippled,
                        t.topStepBefore, Crippled.SPEED_SHARE * 100f, t.travelCrippled, t.travelBeforeCrippled)));
        verdicts.add(Verdict.of("fires-crippled", t.shotsAfterCrippled > 0,
                t.shotsAfterCrippled + " shots fired after crippling, the first at "
                        + seconds(t.firstCrippledShotTick) + (t.armsLostTick >= 0
                        ? "; nose gun lost at " + seconds(t.armsLostTick) : "; nose gun still fitted")));
        verdicts.add(Verdict.of("lost-mounts-silent", t.shotsFromLostMounts == 0,
                t.shotsFromLostMounts + " rounds from a mount after it was destroyed (wanted none)"));
        verdicts.add(Verdict.of("torso-kill", t.deathTick >= 0 && t.torsoAtZeroOnDeath,
                "died at " + seconds(t.deathTick) + String.format(Locale.ROOT,
                        " with torso structure %.1f (wanted a death, and only with the torso at zero)",
                        t.torsoStructureOnDeath)));

        Map<String, Number> m = new LinkedHashMap<>();
        m.put("seed", t.seed);
        m.put("rocketSeats", t.rocketSeats);
        m.put("firstHitTick", t.firstHitTick);
        m.put("podLostTick", t.podLostTick);
        m.put("armsLostTick", t.armsLostTick);
        m.put("legsLostTick", t.legsLostTick);
        m.put("headLostTick", t.headLostTick);
        m.put("deathTick", t.deathTick);
        m.put("timeToKillSeconds", t.deathTick < 0 ? -1f : t.deathTick / 30f);
        m.put("shotsTotal", t.shotsTotal);
        m.put("shotsAfterCrippled", t.shotsAfterCrippled);
        m.put("firstCrippledShotTick", t.firstCrippledShotTick);
        m.put("shotsFromLostMounts", t.shotsFromLostMounts);
        m.put("crippledDriftCells", t.crippledDrift);
        m.put("travelBeforeCrippledCells", t.travelBeforeCrippled);
        m.put("travelCrippledCells", t.travelCrippled);
        m.put("topStepBeforeCells", t.topStepBefore);
        m.put("topStepCrippledCells", t.topStepCrippled);
        if (t.sections != null) {
            for (MechSection section : MechSection.values()) {
                String key = "end." + section.name();
                m.put(key + ".fitted", t.sections.fitted(section) ? 1 : 0);
                m.put(key + ".armor", t.sections.armor(section));
                m.put(key + ".structure", t.sections.structure(section));
                m.put(key + ".destroyed", t.sections.destroyed(section) ? 1 : 0);
            }
        }
        return new SceneReport(id(), LOOP, t.ticks, verdicts, m);
    }

    private SceneReport reportSweep(List<Trace> sweep) {
        int deaths = 0;
        int torsoKills = 0;
        int crippled = 0;
        int crippledCrawled = 0;
        int lostMountRounds = 0;
        int podFirst = 0;
        int podBeforeLegs = 0;
        int firedCrippled = 0;
        int crippledArmed = 0;
        int armedFiredCrippled = 0;
        int fullStory = 0;
        int ticks = 0;
        Map<String, Number> m = new LinkedHashMap<>();
        for (Trace t : sweep) {
            ticks += t.ticks;
            if (t.deathTick >= 0) deaths++;
            if (t.deathTick >= 0 && t.torsoAtZeroOnDeath) torsoKills++;
            if (t.legsLostTick >= 0) {
                crippled++;
                if (t.crawls()) crippledCrawled++;
                if (t.shotsAfterCrippled > 0) firedCrippled++;
                if (t.armedWhenCrippled()) {
                    crippledArmed++;
                    if (t.shotsAfterCrippled > 0) armedFiredCrippled++;
                }
            }
            lostMountRounds += t.shotsFromLostMounts;
            if (t.podLostTick >= 0 && (t.podLostTick <= firstLoss(t))) podFirst++;
            if (t.podLostTick >= 0 && (t.legsLostTick < 0 || t.podLostTick < t.legsLostTick)) podBeforeLegs++;
            if (t.fullStory()) fullStory++;
            String key = (t.rocketSeats > 0 ? "rockets" : "riflesOnly") + ".seed" + t.seed;
            m.put(key + ".pod", t.podLostTick);
            m.put(key + ".gun", t.armsLostTick);
            m.put(key + ".legs", t.legsLostTick);
            m.put(key + ".head", t.headLostTick);
            m.put(key + ".death", t.deathTick);
            m.put(key + ".shotsCrippled", t.shotsAfterCrippled);
        }
        m.put("plays", sweep.size());
        m.put("deaths", deaths);
        m.put("torsoKills", torsoKills);
        m.put("crippled", crippled);
        m.put("crippledCrawled", crippledCrawled);
        m.put("firedWhileCrippled", firedCrippled);
        m.put("crippledWithAWeaponLeft", crippledArmed);
        m.put("podAmongFirstLosses", podFirst);
        m.put("podBeforeLegs", podBeforeLegs);
        m.put("fullStoryInOrder", fullStory);
        putTimeToKill(m, sweep, true, "rockets");
        putTimeToKill(m, sweep, false, "riflesOnly");

        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("every-death-a-torso-kill", deaths > 0 && torsoKills == deaths,
                torsoKills + " of " + deaths + " deaths with the torso at zero, over " + sweep.size() + " plays"));
        verdicts.add(Verdict.of("every-crippled-hound-crawls", crippledCrawled == crippled,
                crippledCrawled + " of " + crippled + " crippled Hounds stepped no faster than their crawl"));
        verdicts.add(Verdict.of("lost-mounts-silent", lostMountRounds == 0,
                lostMountRounds + " rounds from destroyed mounts across the sweep (wanted none)"));
        // The limping promise: a crippled Hound fights on. Wanted on every play
        // that crippled one with a weapon still fitted; one disarmed first has nothing to fire.
        verdicts.add(Verdict.of("every-armed-crippled-hound-fires",
                crippledArmed > 0 && armedFiredCrippled == crippledArmed,
                armedFiredCrippled + " of " + crippledArmed + " Hounds crippled with a weapon left fired after the"
                        + " legs went (" + (crippled - crippledArmed) + " more were disarmed first)"));
        return new SceneReport(id(), SWEEP_LOOP, ticks, verdicts, m);
    }

    /** The earliest tick any section went, or the death if none did first. */
    private static int firstLoss(Trace t) {
        int first = Integer.MAX_VALUE;
        for (int tick : new int[] {t.podLostTick, t.armsLostTick, t.legsLostTick, t.headLostTick, t.deathTick}) {
            if (tick >= 0) first = Math.min(first, tick);
        }
        return first;
    }

    private static void putTimeToKill(Map<String, Number> m, List<Trace> sweep, boolean rockets, String key) {
        List<Integer> deaths = new ArrayList<>();
        for (Trace t : sweep) {
            if ((t.rocketSeats > 0) == rockets && t.deathTick >= 0) deaths.add(t.deathTick);
        }
        deaths.sort(null);
        m.put(key + ".killed", deaths.size());
        m.put(key + ".medianTimeToKillSeconds", deaths.isEmpty() ? -1f : deaths.get(deaths.size() / 2) / 30f);
        m.put(key + ".meanTimeToKillSeconds", deaths.isEmpty() ? -1f
                : (float) deaths.stream().mapToInt(Integer::intValue).average().orElse(0) / 30f);
    }
}

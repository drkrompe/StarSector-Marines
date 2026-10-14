package com.dillon.starsectormarines.battle.deployable;

import com.dillon.starsectormarines.battle.deployable.FieldMortarService.Phase;
import com.dillon.starsectormarines.battle.deployable.FieldMortarService.Tube;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.squad.Squad;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The mortar <b>sections</b> now in action: a squad that has halted to work a tube, the sub-squad that crews it and the
 * leash on everybody else.
 *
 * <p>A <b>section</b> is a squad behaviour, not a unit's. It opens when the squad's march (or an assigned fire mission)
 * brings something worth a tube into the tube's band: a crew of about four (the gunner's fire team, topped up to
 * {@link #CREW_SIZE} with the nearest squadmates) halts and sets the tube down, while the rest of the squad is
 * <b>leashed</b> to it, staying within {@link #LEASH_CELLS} of the tube, fighting from where it stands and not marching
 * off. It then has a <b>window</b> ({@link MortarSectionWindow}): the crew packs up when it runs out, and the squad
 * reforms and reconsiders moving, unless the tube is engaged, which holds the window open up to a cap.
 *
 * <p>The stages are {@link Stage#OPENING} (leashed, the gunner halting and committing the tube), {@link Stage#WORKING}
 * (the tube standing), and {@link Stage#CLOSING} (the tube being lifted, the leash still held so the crew is not left
 * behind). A section ends when its tube is gone, with a per-squad cooldown so a squad does not set up every few seconds
 * along its march. The leash is also dropped the moment the squad is under fire: a section is not a reason to stand in
 * the open being shot at.
 *
 * <p><b>Threading.</b> {@link #open}, {@link #tick} and every stage change run in the serial pass; the unit dispatch only
 * reads. Every number is <b>UNTUNED</b>.
 */
public final class MortarSections {

    /** Turns sections off: {@code -Dbattle.mortar.section=false} is the old behaviour (a gunner sets up only when settled). */
    public static final String PROPERTY = "battle.mortar.section";
    public static final String WINDOW_PROPERTY = "battle.mortar.setupWindowSeconds";
    public static final String EXTENSION_PROPERTY = "battle.mortar.engagedExtensionSeconds";
    public static final String CAP_PROPERTY = "battle.mortar.windowCapSeconds";
    public static final String COOLDOWN_PROPERTY = "battle.mortar.sectionCooldownSeconds";

    /**
     * Default seconds a section is held before an unengaged crew packs up. Thirty is enough for the four-second set-up and
     * about six crewed rounds (a four-second cycle) and short enough that the squad is not pinned for a whole march.
     */
    public static final float DEFAULT_WINDOW_SECONDS = 30f;
    /**
     * Default seconds an engaged crew holds the deadline ahead of itself: two and a half crewed cycles, so a tube between
     * rounds counts as engaged and one that has fallen silent does not.
     */
    public static final float DEFAULT_EXTENSION_SECONDS = 10f;
    /**
     * Default longest a section may hold, from activation: the whole fourteen-round magazine at the crewed cycle is 56
     * seconds, plus the four-second set-up and three-second pack, with margin. A section that has not emptied its magazine
     * by then has stopped being the reason the squad is standing there.
     */
    public static final float DEFAULT_CAP_SECONDS = 90f;
    /** Default seconds after a section ends before the same squad may open another. */
    public static final float DEFAULT_COOLDOWN_SECONDS = 45f;

    /** People who work the tube: the gunner and the loaders around him. */
    public static final int CREW_SIZE = 4;
    /** Cells from the tube within which the crew stands (a loader loads from within {@link FieldMortarService#LOADER_REACH_CELLS}). */
    public static final float CREW_CELLS = 3.5f;
    /** Cells from the tube within which the rest of the squad is leashed: inside the cohesion radius and outside the crew. */
    public static final float LEASH_CELLS = 8f;
    /** Seconds the gunner has after activation to commit the tube before the section is called off. */
    public static final float OPENING_GRACE_SECONDS = 8f;
    /** Seconds without a target after which a tube no longer counts as engaged. */
    public static final float ENGAGED_IDLE_SECONDS = 8f;
    /** Seconds a section must have stood before being under fire ends it, so the shot that triggered nothing does not. */
    static final float UNDER_FIRE_GRACE_SECONDS = 2f;
    /** A squad hit within this many seconds is under fire. */
    static final float UNDER_FIRE_SECONDS = 1f;

    public enum Stage { OPENING, WORKING, CLOSING }

    private static volatile boolean enabled = Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));
    private static volatile float windowSeconds = seconds(WINDOW_PROPERTY, DEFAULT_WINDOW_SECONDS);
    private static volatile float extensionSeconds = seconds(EXTENSION_PROPERTY, DEFAULT_EXTENSION_SECONDS);
    private static volatile float capSeconds = seconds(CAP_PROPERTY, DEFAULT_CAP_SECONDS);
    private static volatile float cooldownSeconds = seconds(COOLDOWN_PROPERTY, DEFAULT_COOLDOWN_SECONDS);

    private static float seconds(String property, float fallback) {
        String raw = System.getProperty(property);
        if (raw == null) return fallback;
        try {
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /** Whether sections are in effect. */
    public static boolean isEnabled() { return enabled; }
    public static float windowSeconds() { return windowSeconds; }
    public static float extensionSeconds() { return extensionSeconds; }
    public static float capSeconds() { return capSeconds; }
    public static float cooldownSeconds() { return cooldownSeconds; }

    /** Evidence seam: sets every dial for the rest of this JVM. Restore the previous values in a {@code finally}. */
    public static void setForEvidence(boolean on, float window, float extension, float cap, float cooldown) {
        enabled = on;
        windowSeconds = window;
        extensionSeconds = extension;
        capSeconds = cap;
        cooldownSeconds = cooldown;
    }

    /** One squad's section. */
    public static final class Section {
        public final int squadId;
        /** The crew, gunner first. */
        private final long[] crew;
        private final MortarSectionWindow window;
        private volatile long gunner;
        private volatile Stage stage = Stage.OPENING;
        private volatile float anchorX;
        private volatile float anchorY;
        private volatile Tube tube;
        private volatile String endReason = "";
        private float sinceOpen;

        Section(int squadId, long[] crew, float x, float y) {
            this.squadId = squadId;
            this.crew = crew.clone();
            this.gunner = crew[0];
            this.anchorX = x;
            this.anchorY = y;
            this.window = new MortarSectionWindow(windowSeconds, extensionSeconds, capSeconds);
        }

        public long gunner() { return gunner; }
        public Stage stage() { return stage; }
        public float anchorX() { return anchorX; }
        public float anchorY() { return anchorY; }
        public MortarSectionWindow window() { return window; }
        public String endReason() { return endReason; }
        public float sinceOpen() { return sinceOpen; }

        /** Whether {@code unit} is one of the people who work the tube. */
        public boolean isCrew(long unit) {
            for (long member : crew) if (member == unit) return true;
            return false;
        }

        /** How far from the tube a given unit is held: the crew close, the rest of the squad at the leash. */
        public float holdRadius(long unit) {
            return isCrew(unit) ? CREW_CELLS : LEASH_CELLS;
        }

        public int crewSize() { return crew.length; }
    }

    private final Map<Integer, Section> bySquad = new ConcurrentHashMap<>();
    private final Map<Integer, Float> cooldowns = new ConcurrentHashMap<>();
    private int opened;
    private int expired;
    private int extended;
    private int abandoned;
    private int underFire;
    private int ended;
    private float secondsHeld;
    private int rounds;
    private int missionRounds;

    /** The section {@code squadId} is in, or null. */
    public Section sectionOf(int squadId) {
        return squadId == Squad.NO_SQUAD ? null : bySquad.get(squadId);
    }

    /** Whether {@code squadId} may open a section now: none open and its cooldown has run out. */
    public boolean mayOpen(int squadId) {
        return enabled && squadId != Squad.NO_SQUAD && !bySquad.containsKey(squadId) && !cooldowns.containsKey(squadId);
    }

    /** Every section now open, for evidence. */
    public List<Section> sections() { return new ArrayList<>(bySquad.values()); }

    /** Sections opened so far. */
    public int opened() { return opened; }
    /** Sections ended because their window ran out. */
    public int expired() { return expired; }
    /** Sections whose window an engaged tube held past the plain window. */
    public int extended() { return extended; }
    /** Sections called off before their tube was ever committed. */
    public int abandoned() { return abandoned; }
    /** Sections ended because the squad came under fire. */
    public int underFire() { return underFire; }
    /** Sections ended in total, for any reason. */
    public int ended() { return ended; }
    /** Total seconds sections spent open (a section's leash is a cost), summed over ended sections. */
    public float secondsHeld() { return secondsHeld; }
    /** Rounds fired by tubes that sections set down, summed over ended sections. */
    public int roundsFired() { return rounds; }
    /** Of those, rounds fired at a body on an assigned fire mission. */
    public int missionRoundsFired() { return missionRounds; }

    /**
     * Opens a section for {@code squadId} crewed by {@code crew} (gunner first), standing where the gunner stands.
     * Serial pass only.
     */
    public Section open(int squadId, long[] crew, float x, float y) {
        Section section = new Section(squadId, crew, x, y);
        bySquad.put(squadId, section);
        opened++;        return section;
    }

    /** The serial pass: ages windows, moves sections through their stages and ends them. */
    public void tick(float dt, FieldMortarService mortars, BattleControl sim) {
        if (!cooldowns.isEmpty()) {
            for (Map.Entry<Integer, Float> entry : new ArrayList<>(cooldowns.entrySet())) {
                float left = entry.getValue() - dt;
                if (left <= 0f) cooldowns.remove(entry.getKey());
                else cooldowns.put(entry.getKey(), left);
            }
        }
        if (bySquad.isEmpty()) return;
        for (Section section : new ArrayList<>(bySquad.values())) {
            advance(section, dt, mortars, sim);
        }
    }

    private void advance(Section section, float dt, FieldMortarService mortars, BattleControl sim) {
        section.sinceOpen += dt;
        Squad squad = sim.getSquad(section.squadId);
        if (squad == null || squad.aliveMembers <= 0) {
            finish(section, "squad gone", false);
            return;
        }
        boolean opening = section.stage == Stage.OPENING;
        Tube tube = section.tube;
        if (opening) {
            tube = mortars.tubeOfCarrier(section.gunner);
            if (tube != null) {
                section.tube = tube;
                section.anchorX = tube.cellX() + 0.5f;
                section.anchorY = tube.cellY() + 0.5f;
                section.stage = Stage.WORKING;
            } else if (sim.resolveUnit(section.gunner) == 0L) {
                finish(section, "gunner lost before the tube was down", true);
                return;
            } else if (section.sinceOpen > OPENING_GRACE_SECONDS) {
                finish(section, "the tube was never committed", true);
                return;
            }
        }
        if (tube != null) {
            if (mortars.tubeOfCarrier(tube.carrierId()) != tube) {
                finish(section, "tube gone", false);
                return;
            }
            section.gunner = tube.carrierId();
        }
        boolean engaged = tube != null && tube.phase() == Phase.SERVING
                && tube.idleSeconds() < ENGAGED_IDLE_SECONDS;
        float before = section.window.deadline();
        section.window.advance(dt, engaged);
        if (section.window.deadline() > before && before <= windowSeconds + 1e-3f) extended++;
        if (section.stage == Stage.WORKING) {
            boolean shot = section.sinceOpen > UNDER_FIRE_GRACE_SECONDS
                    && squad.timeSinceUnderFire < UNDER_FIRE_SECONDS;
            if (shot) {
                section.endReason = "under fire";
                underFire++;
                close(section, mortars);
            } else if (section.window.expired()) {
                section.endReason = "window expired";
                expired++;
                close(section, mortars);
            } else if (tube != null && tube.phase() == Phase.PACKING) {
                section.endReason = "tube lifted";
                section.stage = Stage.CLOSING;
            }
        } else if (section.stage == Stage.CLOSING && tube != null && tube.phase() == Phase.SERVING) {
            mortars.requestPack(tube.carrierId());
        }
    }

    private void close(Section section, FieldMortarService mortars) {
        section.stage = Stage.CLOSING;
        Tube tube = section.tube;
        if (tube != null) mortars.requestPack(tube.carrierId());
    }

    private void finish(Section section, String reason, boolean neverCommitted) {
        if (bySquad.remove(section.squadId, section)) {
            if (section.endReason.isEmpty()) section.endReason = reason;
            if (section.tube != null) {
                rounds += section.tube.roundsFired();
                missionRounds += section.tube.missionRoundsFired();
            }
            if (neverCommitted) abandoned++;
            ended++;
            secondsHeld += section.sinceOpen;
            if (cooldownSeconds > 0f) cooldowns.put(section.squadId, cooldownSeconds);
        }
    }
}

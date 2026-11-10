package com.dillon.starsectormarines.battle.derelict;

/**
 * A survey stake on a lattice point (`fallen-hull-nouns.md`, A stake's stages): a prefabricated outpost the forge
 * printed and a train planted, rising in stages into an ordinary security zone.
 *
 * <ul>
 *   <li>{@link Stage#PLANTED}: a folded pylon, armoured and unarmed, counting down {@link #RAISE_SECONDS} to raising.
 *       Killed now it is {@link Stage#SCRAP}, leaves no wreck, and its point is open again.</li>
 *   <li>{@link Stage#RAISED}: the pylon has opened into a node and a cradle, a new dormant zone; it counts down
 *       {@link #ESTABLISH_SECONDS} to established.</li>
 *   <li>{@link Stage#ESTABLISHED}: folded posts, a service bay and a feedstock {@link #store()}; the next lattice point
 *       beyond it becomes plantable.</li>
 * </ul>
 *
 * <p>A stake builds (counts down, takes feedstock, receives printed walkers) only while its relay chain holds. Once
 * {@linkplain #strand stranded} it keeps every unit and stage it has and builds nothing again.
 */
public final class SurveyStake {

    public enum Stage { PLANTED, RAISED, ESTABLISHED, SCRAP }

    /** Seconds a planted stake stands folded before it rises. UNTUNED. */
    public static final float RAISE_SECONDS = 60f;
    /** Seconds a raised stake takes to establish. UNTUNED. */
    public static final float ESTABLISH_SECONDS = 90f;

    /** The lattice point it stands on. */
    public final int pointId;
    /** What its chain runs through first: another stake's point, or {@link ClaimLattice#HULL}. */
    public final int parent;

    private Stage stage;
    private float elapsed;
    private boolean stranded;
    private Feedstock store;

    public SurveyStake(int pointId, int parent) {
        this(pointId, parent, Stage.PLANTED);
    }

    /** A stake the ground stands at a stage before the battle (`fallen-hull-nouns.md`, In a battle: setup). */
    public SurveyStake(int pointId, int parent, Stage stage) {
        if (stage == Stage.SCRAP) throw new IllegalArgumentException("A stake is never laid as scrap");
        this.pointId = pointId;
        this.parent = parent;
        this.stage = stage;
        if (stage == Stage.ESTABLISHED) store = new Feedstock();
    }

    public Stage stage() {
        return stage;
    }

    public boolean stranded() {
        return stranded;
    }

    /** Whether it still builds: standing, and its chain unbroken. */
    public boolean builds() {
        return !stranded && stage != Stage.SCRAP;
    }

    /** Whether its chain carries the claim on from it: established and unbroken. */
    public boolean anchors() {
        return builds() && stage == Stage.ESTABLISHED;
    }

    /** Its feedstock store, from established on; null before. */
    public Feedstock store() {
        return store;
    }

    /** How far the stake is to its next stage, 0 to 1; 1 once established. */
    public float progress() {
        return switch (stage) {
            case PLANTED -> Math.min(1f, elapsed / RAISE_SECONDS);
            case RAISED -> Math.min(1f, elapsed / ESTABLISH_SECONDS);
            case ESTABLISHED, SCRAP -> 1f;
        };
    }

    /**
     * Counts down {@code dt} seconds while the stake builds.
     *
     * @return the stage it rose to in this step, or null
     */
    public Stage tick(float dt) {
        if (!builds() || stage == Stage.ESTABLISHED) return null;
        elapsed += dt;
        float needed = stage == Stage.PLANTED ? RAISE_SECONDS : ESTABLISH_SECONDS;
        if (elapsed < needed) return null;
        elapsed = 0f;
        stage = stage == Stage.PLANTED ? Stage.RAISED : Stage.ESTABLISHED;
        if (stage == Stage.ESTABLISHED) store = new Feedstock();
        return stage;
    }

    /** Its chain is cut: it keeps what it has and builds nothing from here on. */
    public void strand() {
        stranded = true;
    }

    /**
     * The folded pylon is destroyed: a planted stake is scrap. A raised or established stake is fought as a zone, by
     * its node, and is not killed this way.
     *
     * @return whether it was scrapped now
     */
    public boolean kill() {
        if (stage != Stage.PLANTED) return false;
        stage = Stage.SCRAP;
        return true;
    }
}

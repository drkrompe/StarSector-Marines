package com.dillon.starsectormarines.battle.infantry;

import com.fs.starfarer.api.Global;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/**
 * What a squad medic can do ({@code squad-medic.md}): at one rank of the
 * officer's Field Medicine training, or with a field-aid kit carried in a suit. Both are authored in
 * {@code data/marines/squad-medicine.json}, ranks by number and kits by the
 * integral system's id. A kit's own medicine is the weaker form. A squad with
 * a kit anywhere in it is medicated by its kits alone; the officer's training
 * promotes a medic only in a squad that carries none.
 *
 * <p>The limit is an energy pool: it regenerates slowly and continuously, and a
 * treatment spends it for every point of health or armor it restores
 * ({@link MedicEnergy}). Restoration is flat per second rather than a fraction
 * of the patient's maximum, for the reason {@code FieldAidSpec} gives: a bandage
 * is a bandage, and it goes further on somebody with less to lose.
 *
 * @param energyMax            the pool when full; a medic deploys with it full
 * @param energyRegenPerSecond energy regained per second, always, up to the full pool
 * @param energyPerPoint       energy spent per point of health or armor restored
 * @param minStartEnergy       a treatment starts only with at least this much, or with
 *                             enough to make the patient whole if that is less
 * @param woundFloorFraction   a patient missing less than this fraction of their health
 *                             and armor together is never started on
 * @param retrySeconds         after an approach is abandoned, how long before the medic
 *                             sets out again, so an unreachable patient is not chased
 *                             afresh every tick
 * @param healthPerSecond      structure restored per second of treatment
 * @param armorPerSecond       armor capacity restored per second of treatment
 * @param reachCells           how close the medic must be to the patient to treat
 * @param leashCells           how far from the medic a patient may be to be chosen; the
 *                             squad's cohesion radius, not authored
 * @param approachSeconds      how long the medic may spend reaching a patient
 * @param treatBelowHealth     a patient qualifies below this fraction of maximum health
 * @param treatBelowArmor      or below this fraction of armor capacity, broken included
 * @param heavyFirePressure    incoming pressure at which the medic will not set out
 */
public record SquadMedicine(float energyMax, float energyRegenPerSecond, float energyPerPoint,
                            float minStartEnergy, float woundFloorFraction, float retrySeconds,
                            float healthPerSecond, float armorPerSecond,
                            float reachCells, float leashCells, float approachSeconds,
                            float treatBelowHealth, float treatBelowArmor,
                            float heavyFirePressure) {

    /** Highest Field Medicine rank an officer can train. */
    public static final int MAX_RANK = 3;

    /** The authored table, one entry per rank. */
    public static final String PATH = "data/marines/squad-medicine.json";

    /** The fire team a medic is drawn from first: the middle one, Bravo. */
    public static final int MEDIC_FIRE_TEAM = 1;

    private static volatile SquadMedicine[] byRank;
    private static volatile Map<String, SquadMedicine> byKit;

    /** Reads one authored file. */
    @FunctionalInterface
    public interface Source {
        JSONObject read(String path) throws Exception;
    }

    /** Installs the table from the game's own data. */
    public static void loadBuiltins() throws Exception {
        loadBuiltins(path -> Global.getSettings().loadJSON(path, true));
    }

    /** Installs the table read through {@code source}; fails on a missing or malformed rank. */
    public static void loadBuiltins(Source source) throws Exception {
        JSONObject root = source.read(PATH);
        SquadMedicine[] ranks = parse(root);
        byKit = parseKits(root);
        byRank = ranks;
    }

    public static boolean installed() { return byRank != null; }

    static SquadMedicine[] parse(JSONObject root) throws JSONException {
        SquadMedicine[] table = new SquadMedicine[MAX_RANK + 1];
        JSONArray ranks = root.getJSONArray("ranks");
        for (int i = 0; i < ranks.length(); i++) {
            JSONObject r = ranks.getJSONObject(i);
            int rank = r.getInt("rank");
            if (rank < 1 || rank > MAX_RANK) throw new JSONException("Squad medicine rank " + rank + " is out of range");
            if (table[rank] != null) throw new JSONException("Squad medicine rank " + rank + " is authored twice");
            table[rank] = parseOne(r, "rank " + rank);
        }
        for (int rank = 1; rank <= MAX_RANK; rank++) {
            if (table[rank] == null) throw new JSONException("Squad medicine rank " + rank + " is not authored");
        }
        return table;
    }

    /** The kits, keyed by the integral system id a suit's catalog entry declares. */
    static Map<String, SquadMedicine> parseKits(JSONObject root) throws JSONException {
        Map<String, SquadMedicine> kits = new HashMap<>();
        JSONArray authored = root.optJSONArray("kits");
        if (authored == null) return Map.copyOf(kits);
        for (int i = 0; i < authored.length(); i++) {
            JSONObject k = authored.getJSONObject(i);
            String system = k.getString("system");
            if (kits.put(system, parseOne(k, "kit " + system)) != null) {
                throw new JSONException("Squad medicine kit " + system + " is authored twice");
            }
        }
        return Map.copyOf(kits);
    }

    private static SquadMedicine parseOne(JSONObject r, String label) throws JSONException {
        SquadMedicine m = new SquadMedicine(
                positive(r, "energyMax"), positive(r, "energyRegenPerSecond"),
                positive(r, "energyPerPoint"), positive(r, "minStartEnergy"),
                fraction(r, "woundFloorFraction"), positive(r, "retrySeconds"),
                positive(r, "healthPerSecond"), positive(r, "armorPerSecond"),
                positive(r, "reachCells"), InfantryCohesion.COHESION_RADIUS,
                positive(r, "approachSeconds"),
                fraction(r, "treatBelowHealth"), fraction(r, "treatBelowArmor"),
                positive(r, "heavyFirePressure"));
        if (m.minStartEnergy() > m.energyMax()) {
            throw new JSONException("Squad medicine " + label
                    + " needs more energy to start a treatment than its pool holds");
        }
        return m;
    }

    private static float positive(JSONObject json, String key) throws JSONException {
        float value = (float) json.getDouble(key);
        if (!(value > 0f) || !Float.isFinite(value)) {
            throw new JSONException("Squad medicine '" + key + "' must be positive, got " + value);
        }
        return value;
    }

    private static float fraction(JSONObject json, String key) throws JSONException {
        float value = (float) json.getDouble(key);
        if (!(value >= 0f && value <= 1f)) {
            throw new JSONException("Squad medicine '" + key + "' must be a fraction, got " + value);
        }
        return value;
    }

    /**
     * Which billet carries the squad's medic: the first eligible marine of the
     * middle fire team in roster order, else the first eligible marine at all.
     * The battle (live, not the leader) and the company card (ready, not the
     * NCO) both ask this, so the marine marked on the card is the one who treats.
     *
     * @param count    billets to consider, in roster order
     * @param eligible whether the billet at an index may carry the medic
     * @param fireTeam the fire team of the billet at an index
     * @return the chosen index, or -1 when nobody is eligible
     */
    public static int medicBillet(int count, IntPredicate eligible, IntUnaryOperator fireTeam) {
        int fallback = -1;
        for (int i = 0; i < count; i++) {
            if (!eligible.test(i)) continue;
            if (fireTeam.applyAsInt(i) == MEDIC_FIRE_TEAM) return i;
            if (fallback < 0) fallback = i;
        }
        return fallback;
    }

    /**
     * The medicine an officer of {@code rank} gives each squad, or null below
     * rank one. Fails loud when the table was never installed: a silent null
     * would read as an officer without the specialty.
     */
    public static SquadMedicine forRank(int rank) {
        if (rank <= 0) return null;
        SquadMedicine[] table = byRank;
        if (table == null) throw new IllegalStateException("Squad medicine is not installed: " + PATH);
        return table[Math.min(rank, MAX_RANK)];
    }

    /**
     * The medicine a field-aid kit gives its wearer. Fails loud when the kit is not authored: a suit that
     * declares field aid and treats nobody would be a silent dead system.
     */
    public static SquadMedicine forKit(String systemId) {
        Map<String, SquadMedicine> kits = byKit;
        if (kits == null) throw new IllegalStateException("Squad medicine is not installed: " + PATH);
        SquadMedicine m = kits.get(systemId);
        if (m == null) {
            throw new IllegalStateException("Field-aid kit '" + systemId + "' has no medicine in " + PATH);
        }
        return m;
    }
}

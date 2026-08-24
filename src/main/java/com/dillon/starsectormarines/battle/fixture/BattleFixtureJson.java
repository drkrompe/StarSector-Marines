package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Headless JSON codec for versioned battle-construction fixtures. */
public final class BattleFixtureJson {

    public static final int SCHEMA_VERSION = 1;

    private BattleFixtureJson() {}

    public static JSONObject toJson(BattleFixture fixture) throws Exception {
        if (!(fixture instanceof CivilianRescueBattleFixture rescue)) {
            throw new IllegalArgumentException("Unsupported battle fixture: " + fixture);
        }
        JSONObject root = new JSONObject();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("kind", rescue.kind());
        root.put("seed", rescue.seed());
        root.put("enemyHasHeavyArmor", rescue.enemyHasHeavyArmor());
        root.put("risk", rescue.risk().name());
        root.put("swarmCount", rescue.swarmCount());
        root.put("stressTest", rescue.stressTest());

        JSONArray shuttles = new JSONArray();
        for (ShuttleAssignment shuttle : rescue.manifest()) {
            JSONObject encoded = new JSONObject();
            encoded.put("type", shuttle.type.name());
            encoded.put("cycles", shuttle.cycles);
            shuttles.put(encoded);
        }
        root.put("shuttles", shuttles);
        root.put("targetProfile", targetProfileToJson(rescue.targetProfile()));
        return root;
    }

    /**
     * Reads either a standalone fixture document or a tick-profile dump whose
     * {@code battleFixture} member contains the fixture document.
     */
    public static BattleFixture fromJson(JSONObject document) throws Exception {
        JSONObject root = document.has("battleFixture")
                ? document.getJSONObject("battleFixture") : document;
        int version = root.getInt("schemaVersion");
        return switch (version) {
            case 1 -> decodeV1(root);
            default -> throw new IllegalArgumentException(
                    "Unsupported battle fixture schemaVersion: " + version);
        };
    }

    /** Retained decoder branch so future schema bumps can keep loading V1. */
    private static BattleFixture decodeV1(JSONObject root) throws Exception {
        String kind = root.getString("kind");
        if (!CivilianRescueBattleFixture.KIND.equals(kind)) {
            throw new IllegalArgumentException("Unsupported battle fixture kind: " + kind);
        }

        JSONArray encodedShuttles = root.getJSONArray("shuttles");
        List<ShuttleAssignment> shuttles = new ArrayList<>();
        for (int i = 0; i < encodedShuttles.length(); i++) {
            JSONObject encoded = encodedShuttles.getJSONObject(i);
            shuttles.add(new ShuttleAssignment(
                    enumValue(ShuttleType.class,
                            encoded.getString("type"), "shuttle type"),
                    encoded.getInt("cycles")));
        }

        return new CivilianRescueBattleFixture(
                root.getLong("seed"),
                shuttles,
                root.getBoolean("enemyHasHeavyArmor"),
                enumValue(RiskLevel.class, root.getString("risk"), "risk"),
                root.getInt("swarmCount"),
                targetProfileFromJson(root.getJSONObject("targetProfile")),
                root.getBoolean("stressTest"));
    }

    private static JSONObject targetProfileToJson(
            TargetProfile profile) throws Exception {
        JSONObject encoded = new JSONObject();
        encoded.put("marketSize", profile.marketSize());
        encoded.put("stability", profile.stability());
        encoded.put("defenseLevel", profile.defenseLevel());
        encoded.put("spaceportTier", profile.spaceportTier());
        encoded.put("factionId", profile.factionId());
        JSONArray functions = new JSONArray();
        for (EconomicFunction function : EconomicFunction.values()) {
            if (profile.functions().contains(function)) functions.put(function.name());
        }
        encoded.put("functions", functions);
        return encoded;
    }

    private static TargetProfile targetProfileFromJson(
            JSONObject encoded) throws Exception {
        JSONArray encodedFunctions = encoded.getJSONArray("functions");
        Set<EconomicFunction> functions = EnumSet.noneOf(EconomicFunction.class);
        for (int i = 0; i < encodedFunctions.length(); i++) {
            functions.add(enumValue(EconomicFunction.class,
                    encodedFunctions.getString(i), "economic function"));
        }
        return new TargetProfile(
                encoded.getInt("marketSize"),
                encoded.getInt("stability"),
                encoded.getInt("defenseLevel"),
                encoded.getInt("spaceportTier"),
                encoded.getString("factionId"),
                functions);
    }

    private static <E extends Enum<E>> E enumValue(
            Class<E> enumType, String value, String field) {
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown " + field + ": " + value, ex);
        }
    }
}

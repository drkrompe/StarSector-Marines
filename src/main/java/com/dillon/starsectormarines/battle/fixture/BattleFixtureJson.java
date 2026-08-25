package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.fixture.ConquestBattleFixture.WingCommitment;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.OperationTier;
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
        JSONObject root = new JSONObject();
        root.put("schemaVersion", SCHEMA_VERSION);
        if (fixture instanceof CivilianRescueBattleFixture rescue) {
            encodeCommon(root, rescue.kind(), rescue.seed(), rescue.manifest(),
                    rescue.enemyHasHeavyArmor(), rescue.risk(),
                    rescue.targetProfile());
            root.put("swarmCount", rescue.swarmCount());
            root.put("stressTest", rescue.stressTest());
            return root;
        }
        if (fixture instanceof ConquestBattleFixture conquest) {
            encodeCommon(root, conquest.kind(), conquest.seed(),
                    conquest.manifest(), conquest.enemyHasHeavyArmor(),
                    conquest.risk(), conquest.targetProfile());
            root.put("tier", conquest.tier().name());
            root.put("marineFighterSupport",
                    wingsToJson(conquest.marineFighterSupport()));
            root.put("enemyFighterSupport",
                    wingsToJson(conquest.enemyFighterSupport()));
            return root;
        }
        throw new IllegalArgumentException("Unsupported battle fixture: " + fixture);
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
        return switch (kind) {
            case CivilianRescueBattleFixture.KIND -> decodeCivilianRescue(root);
            case ConquestBattleFixture.KIND -> decodeConquest(root);
            default -> throw new IllegalArgumentException(
                    "Unsupported battle fixture kind: " + kind);
        };
    }

    private static CivilianRescueBattleFixture decodeCivilianRescue(
            JSONObject root) throws Exception {
        return new CivilianRescueBattleFixture(
                root.getLong("seed"),
                shuttlesFromJson(root.getJSONArray("shuttles")),
                root.getBoolean("enemyHasHeavyArmor"),
                enumValue(RiskLevel.class, root.getString("risk"), "risk"),
                root.getInt("swarmCount"),
                targetProfileFromJson(root.getJSONObject("targetProfile")),
                root.getBoolean("stressTest"));
    }

    private static ConquestBattleFixture decodeConquest(
            JSONObject root) throws Exception {
        return new ConquestBattleFixture(
                root.getLong("seed"),
                shuttlesFromJson(root.getJSONArray("shuttles")),
                root.getBoolean("enemyHasHeavyArmor"),
                enumValue(OperationTier.class, root.getString("tier"), "tier"),
                enumValue(RiskLevel.class, root.getString("risk"), "risk"),
                targetProfileFromJson(root.getJSONObject("targetProfile")),
                wingsFromJson(root.getJSONArray("marineFighterSupport")),
                wingsFromJson(root.getJSONArray("enemyFighterSupport")));
    }

    private static void encodeCommon(
            JSONObject root, String kind, long seed,
            List<ShuttleAssignment> manifest, boolean enemyHasHeavyArmor,
            RiskLevel risk, TargetProfile targetProfile) throws Exception {
        root.put("kind", kind);
        root.put("seed", seed);
        root.put("enemyHasHeavyArmor", enemyHasHeavyArmor);
        root.put("risk", risk.name());
        root.put("shuttles", shuttlesToJson(manifest));
        root.put("targetProfile", targetProfileToJson(targetProfile));
    }

    private static JSONArray shuttlesToJson(
            List<ShuttleAssignment> manifest) throws Exception {
        JSONArray shuttles = new JSONArray();
        for (ShuttleAssignment shuttle : manifest) {
            JSONObject encoded = new JSONObject();
            encoded.put("type", shuttle.type.name());
            encoded.put("cycles", shuttle.cycles);
            shuttles.put(encoded);
        }
        return shuttles;
    }

    private static List<ShuttleAssignment> shuttlesFromJson(
            JSONArray encodedShuttles) throws Exception {
        List<ShuttleAssignment> shuttles = new ArrayList<>();
        for (int i = 0; i < encodedShuttles.length(); i++) {
            JSONObject encoded = encodedShuttles.getJSONObject(i);
            shuttles.add(new ShuttleAssignment(
                    enumValue(ShuttleType.class,
                            encoded.getString("type"), "shuttle type"),
                    encoded.getInt("cycles")));
        }
        return shuttles;
    }

    private static JSONArray wingsToJson(
            List<WingCommitment> commitments) throws Exception {
        JSONArray wings = new JSONArray();
        for (WingCommitment commitment : commitments) {
            JSONObject encoded = new JSONObject();
            encoded.put("profile", commitment.profile().name());
            encoded.put("side", commitment.side().name());
            encoded.put("sortieCount", commitment.sortieCount());
            encoded.put("firstArrivalSec", commitment.firstArrivalSec());
            encoded.put("spawnIntervalSec", commitment.spawnIntervalSec());
            wings.put(encoded);
        }
        return wings;
    }

    private static List<WingCommitment> wingsFromJson(
            JSONArray encodedWings) throws Exception {
        List<WingCommitment> wings = new ArrayList<>();
        for (int i = 0; i < encodedWings.length(); i++) {
            JSONObject encoded = encodedWings.getJSONObject(i);
            wings.add(new WingCommitment(
                    enumValue(FighterProfile.class,
                            encoded.getString("profile"), "fighter profile"),
                    enumValue(Faction.class,
                            encoded.getString("side"), "fighter side"),
                    encoded.getInt("sortieCount"),
                    (float) encoded.getDouble("firstArrivalSec"),
                    (float) encoded.getDouble("spawnIntervalSec")));
        }
        return wings;
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

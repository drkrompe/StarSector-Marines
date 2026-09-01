package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.setup.ShuttleArrivalPlan;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.ConquestArrivalConfig;
import com.dillon.starsectormarines.ops.MarineArrivalPolicy;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.FieldPresencePolicy;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Headless JSON codec for versioned battle-construction fixtures. */
public final class BattleFixtureJson {

    public static final int LEGACY_SCHEMA_VERSION = 1;
    public static final int SCHEMA_VERSION = 2;
    public static final int LEGACY_LAUNCH_SCHEMA_VERSION = 2;
    public static final int PRE_FIELD_PRESENCE_LAUNCH_SCHEMA_VERSION = 3;
    public static final int LAUNCH_SCHEMA_VERSION = 4;

    private BattleFixtureJson() {}

    public static JSONObject toJson(BattleFixture fixture) throws Exception {
        if (fixture instanceof BattleLaunchFixture launch) {
            JSONObject root = new JSONObject();
            root.put("schemaVersion", LAUNCH_SCHEMA_VERSION);
            root.put("kind", launch.kind());
            root.put("construction", toJson(launch.construction()));
            root.put("launch", launchToJson(launch.launch()));
            return root;
        }
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
            root.put("arrivalPlan", arrivalPlanToJson(conquest.arrivalPlan()));
            return root;
        }
        if (fixture instanceof SabotageBattleFixture sabotage) {
            encodeCommon(root, sabotage.kind(), sabotage.seed(),
                    sabotage.manifest(), sabotage.enemyHasHeavyArmor(),
                    sabotage.risk(), sabotage.targetProfile());
            root.put("tier", sabotage.tier().name());
            root.put("marineFighterSupport",
                    wingsToJson(sabotage.marineFighterSupport()));
            root.put("enemyFighterSupport",
                    wingsToJson(sabotage.enemyFighterSupport()));
            return root;
        }
        if (fixture instanceof AssaultBattleFixture assault) {
            encodeCommon(root, assault.kind(), assault.seed(),
                    assault.manifest(), assault.enemyHasHeavyArmor(),
                    assault.risk(), assault.targetProfile());
            root.put("tier", assault.tier().name());
            root.put("marineFighterSupport",
                    wingsToJson(assault.marineFighterSupport()));
            root.put("enemyFighterSupport",
                    wingsToJson(assault.enemyFighterSupport()));
            return root;
        }
        if (fixture instanceof RaidBattleFixture raid) {
            encodeCommon(root, raid.kind(), raid.seed(), raid.manifest(),
                    raid.enemyHasHeavyArmor(), raid.risk(), raid.targetProfile());
            root.put("tier", raid.tier().name());
            root.put("marineFighterSupport",
                    wingsToJson(raid.marineFighterSupport()));
            root.put("enemyFighterSupport",
                    wingsToJson(raid.enemyFighterSupport()));
            return root;
        }
        if (fixture instanceof ExtractionBattleFixture extraction) {
            encodeCommon(root, extraction.kind(), extraction.seed(),
                    extraction.manifest(), extraction.enemyHasHeavyArmor(),
                    extraction.risk(), extraction.targetProfile());
            root.put("tier", extraction.tier().name());
            root.put("marineFighterSupport",
                    wingsToJson(extraction.marineFighterSupport()));
            root.put("enemyFighterSupport",
                    wingsToJson(extraction.enemyFighterSupport()));
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
            case LEGACY_LAUNCH_SCHEMA_VERSION -> root.has("construction")
                    ? decodeLegacyLaunchV2(root) : decodeConstructionV2(root);
            case PRE_FIELD_PRESENCE_LAUNCH_SCHEMA_VERSION,
                 LAUNCH_SCHEMA_VERSION -> decodeLaunchV3(root);
            default -> throw new IllegalArgumentException(
                    "Unsupported battle fixture schemaVersion: " + version);
        };
    }

    private static BattleLaunchFixture decodeLegacyLaunchV2(JSONObject root) throws Exception {
        JSONObject encodedConstruction = root.getJSONObject("construction");
        if (encodedConstruction.getInt("schemaVersion") != LEGACY_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "A launch fixture must contain a V1 construction fixture");
        }
        BattleFixture construction = decodeV1(encodedConstruction);
        String outerKind = root.getString("kind");
        if (!outerKind.equals(construction.kind())) {
            throw new IllegalArgumentException("Launch kind '" + outerKind
                    + "' does not match construction kind '" + construction.kind() + "'");
        }
        return new BattleLaunchFixture(construction,
                launchFromJson(root.getJSONObject("launch")));
    }

    private static BattleLaunchFixture decodeLaunchV3(JSONObject root) throws Exception {
        JSONObject encodedConstruction = root.getJSONObject("construction");
        BattleFixture construction = decodeConstruction(encodedConstruction);
        String outerKind = root.getString("kind");
        if (!outerKind.equals(construction.kind())) {
            throw new IllegalArgumentException("Launch kind '" + outerKind
                    + "' does not match construction kind '" + construction.kind() + "'");
        }
        return new BattleLaunchFixture(construction,
                launchFromJson(root.getJSONObject("launch")));
    }

    private static BattleFixture decodeConstruction(JSONObject root) throws Exception {
        return switch (root.getInt("schemaVersion")) {
            case LEGACY_SCHEMA_VERSION -> decodeV1(root);
            case SCHEMA_VERSION -> decodeConstructionV2(root);
            default -> throw new IllegalArgumentException(
                    "Unsupported construction fixture schemaVersion: "
                            + root.getInt("schemaVersion"));
        };
    }

    private static BattleFixture decodeConstructionV2(JSONObject root) throws Exception {
        String kind = root.getString("kind");
        return switch (kind) {
            case CivilianRescueBattleFixture.KIND -> decodeCivilianRescue(root);
            case ConquestBattleFixture.KIND -> decodeConquestV2(root);
            case SabotageBattleFixture.KIND -> decodeSabotage(root);
            case AssaultBattleFixture.KIND -> decodeAssault(root);
            case RaidBattleFixture.KIND -> decodeRaid(root);
            case ExtractionBattleFixture.KIND -> decodeExtraction(root);
            default -> throw new IllegalArgumentException(
                    "Unsupported battle fixture kind: " + kind);
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

    private static ConquestBattleFixture decodeConquestV2(
            JSONObject root) throws Exception {
        return new ConquestBattleFixture(
                root.getLong("seed"),
                shuttlesFromJson(root.getJSONArray("shuttles")),
                root.getBoolean("enemyHasHeavyArmor"),
                enumValue(OperationTier.class, root.getString("tier"), "tier"),
                enumValue(RiskLevel.class, root.getString("risk"), "risk"),
                targetProfileFromJson(root.getJSONObject("targetProfile")),
                wingsFromJson(root.getJSONArray("marineFighterSupport")),
                wingsFromJson(root.getJSONArray("enemyFighterSupport")),
                arrivalPlanFromJson(root.getJSONObject("arrivalPlan")));
    }

    private static SabotageBattleFixture decodeSabotage(
            JSONObject root) throws Exception {
        return new SabotageBattleFixture(
                root.getLong("seed"),
                shuttlesFromJson(root.getJSONArray("shuttles")),
                root.getBoolean("enemyHasHeavyArmor"),
                enumValue(OperationTier.class, root.getString("tier"), "tier"),
                enumValue(RiskLevel.class, root.getString("risk"), "risk"),
                targetProfileFromJson(root.getJSONObject("targetProfile")),
                wingsFromJson(root.getJSONArray("marineFighterSupport")),
                wingsFromJson(root.getJSONArray("enemyFighterSupport")));
    }

    private static AssaultBattleFixture decodeAssault(
            JSONObject root) throws Exception {
        return new AssaultBattleFixture(
                root.getLong("seed"),
                shuttlesFromJson(root.getJSONArray("shuttles")),
                root.getBoolean("enemyHasHeavyArmor"),
                enumValue(OperationTier.class, root.getString("tier"), "tier"),
                enumValue(RiskLevel.class, root.getString("risk"), "risk"),
                targetProfileFromJson(root.getJSONObject("targetProfile")),
                wingsFromJson(root.getJSONArray("marineFighterSupport")),
                wingsFromJson(root.getJSONArray("enemyFighterSupport")));
    }

    private static RaidBattleFixture decodeRaid(JSONObject root) throws Exception {
        return new RaidBattleFixture(
                root.getLong("seed"),
                shuttlesFromJson(root.getJSONArray("shuttles")),
                root.getBoolean("enemyHasHeavyArmor"),
                enumValue(OperationTier.class, root.getString("tier"), "tier"),
                enumValue(RiskLevel.class, root.getString("risk"), "risk"),
                targetProfileFromJson(root.getJSONObject("targetProfile")),
                wingsFromJson(root.getJSONArray("marineFighterSupport")),
                wingsFromJson(root.getJSONArray("enemyFighterSupport")));
    }

    private static ExtractionBattleFixture decodeExtraction(JSONObject root)
            throws Exception {
        return new ExtractionBattleFixture(
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
            encoded.put("seatsPerSortie", shuttle.seatsPerSortie);
            encoded.put("embarkedPersonnel", shuttle.embarkedPersonnel);
            shuttles.put(encoded);
        }
        return shuttles;
    }

    private static List<ShuttleAssignment> shuttlesFromJson(
            JSONArray encodedShuttles) throws Exception {
        List<ShuttleAssignment> shuttles = new ArrayList<>();
        for (int i = 0; i < encodedShuttles.length(); i++) {
            JSONObject encoded = encodedShuttles.getJSONObject(i);
            ShuttleType type = enumValue(ShuttleType.class,
                    encoded.getString("type"), "shuttle type");
            int cycles = encoded.getInt("cycles");
            int seats = encoded.has("seatsPerSortie")
                    ? encoded.getInt("seatsPerSortie") : type.capacity;
            shuttles.add(new ShuttleAssignment(type, cycles, seats,
                    encoded.has("embarkedPersonnel")
                            ? encoded.getInt("embarkedPersonnel")
                            : cycles * seats));
        }
        return shuttles;
    }

    private static JSONObject arrivalPlanToJson(ShuttleArrivalPlan plan) throws Exception {
        JSONObject encoded = new JSONObject();
        encoded.put("policy", plan.policy().name());
        encoded.put("firstPlayerShuttle", plan.firstPlayerShuttle());
        encoded.put("dropZoneCount", plan.arrivalConfig().dropZoneCount());
        encoded.put("shuttlePairsPerZone",
                plan.arrivalConfig().shuttlePairsPerZone());
        encoded.put("timingJitterSec",
                plan.arrivalConfig().timingJitterSec());
        return encoded;
    }

    private static ShuttleArrivalPlan arrivalPlanFromJson(JSONObject encoded) throws Exception {
        MarineArrivalPolicy policy = enumValue(MarineArrivalPolicy.class,
                encoded.getString("policy"), "marine arrival policy");
        ConquestArrivalConfig defaults = policy == MarineArrivalPolicy.PAIRED_HALF_SQUAD
                ? ConquestArrivalConfig.DEFAULT : ConquestArrivalConfig.LEGACY;
        ConquestArrivalConfig config = new ConquestArrivalConfig(
                encoded.has("dropZoneCount")
                        ? encoded.getInt("dropZoneCount") : defaults.dropZoneCount(),
                encoded.has("shuttlePairsPerZone")
                        ? encoded.getInt("shuttlePairsPerZone")
                        : defaults.shuttlePairsPerZone(),
                encoded.has("timingJitterSec")
                        ? (float) encoded.getDouble("timingJitterSec")
                        : defaults.timingJitterSec());
        return new ShuttleArrivalPlan(
                policy, encoded.getInt("firstPlayerShuttle"), config);
    }

    private static JSONArray wingsToJson(
            List<FighterWingCommitment> commitments) throws Exception {
        JSONArray wings = new JSONArray();
        for (FighterWingCommitment commitment : commitments) {
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

    private static List<FighterWingCommitment> wingsFromJson(
            JSONArray encodedWings) throws Exception {
        List<FighterWingCommitment> wings = new ArrayList<>();
        for (int i = 0; i < encodedWings.length(); i++) {
            JSONObject encoded = encodedWings.getJSONObject(i);
            wings.add(new FighterWingCommitment(
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

    private static JSONObject launchToJson(BattleLaunchOverlay launch) throws Exception {
        JSONObject encoded = new JSONObject();
        encoded.put("playerShuttleMissionsToSkip",
                launch.playerShuttleMissionsToSkip());
        encoded.put("marineSeats", marineSeatsToJson(launch.marineSeats()));
        encoded.put("marineFighterSupport",
                wingsToJson(launch.marineFighterSupport()));
        encoded.put("debugFighterSupport",
                wingsToJson(launch.debugFighterSupport()));
        encoded.put("commandPowers", powersToJson(launch.commandPowers()));
        encoded.put("fieldPresencePolicy", launch.fieldPresencePolicy().name());
        JSONObject resources = new JSONObject();
        resources.put("supplies", launch.startingSupplies());
        encoded.put("commandPowerResources", resources);
        return encoded;
    }

    private static BattleLaunchOverlay launchFromJson(JSONObject encoded) throws Exception {
        return new BattleLaunchOverlay(
                encoded.getInt("playerShuttleMissionsToSkip"),
                marineSeatsFromJson(encoded.getJSONArray("marineSeats")),
                wingsFromJson(encoded.getJSONArray("marineFighterSupport")),
                wingsFromJson(encoded.getJSONArray("debugFighterSupport")),
                powersFromJson(encoded.getJSONArray("commandPowers")),
                encoded.getJSONObject("commandPowerResources").getInt("supplies"),
                enumValue(FieldPresencePolicy.class,
                        encoded.optString("fieldPresencePolicy",
                                FieldPresencePolicy.UNRESTRICTED.name()),
                        "field presence policy"));
    }

    private static JSONArray marineSeatsToJson(
            List<MarineSeatCommitment> commitments) throws Exception {
        JSONArray seats = new JSONArray();
        for (MarineSeatCommitment seat : commitments) {
            JSONObject encoded = new JSONObject();
            putNullable(encoded, "campaignSoldierId", seat.campaignSoldierId());
            encoded.put("primaryWeaponId", seat.primaryWeaponId());
            encoded.put("equipmentGrade", seat.equipmentGrade().name());
            JSONObject profile = new JSONObject();
            profile.put("aptitude", seat.soldierProfile().aptitude().name());
            profile.put("experienceXp", seat.soldierProfile().experienceXp());
            encoded.put("soldierProfile", profile);
            putNullable(encoded, "specialEquipmentId", seat.specialEquipmentId());
            putNullable(encoded, "armorFamily",
                    seat.armorFamily() != null ? seat.armorFamily().name() : null);
            encoded.put("armorCapacity", seat.armorCapacity());
            encoded.put("armorRating", seat.armorRating());
            encoded.put("armorMoveSpeedMult", seat.armorMoveSpeedMult());
            encoded.put("armorIncomingAccuracyMult",
                    seat.armorIncomingAccuracyMult());
            if (seat.campaignSquadId() != null) {
                JSONObject squad = new JSONObject();
                squad.put("squadId", seat.campaignSquadId());
                squad.put("label", seat.campaignSquadLabel());
                squad.put("leader", seat.campaignSquadLeader());
                squad.put("strength", seat.campaignSquadStrength());
                squad.put("fireTeamIndex", seat.campaignFireTeamIndex());
                encoded.put("campaignSquad", squad);
            }
            seats.put(encoded);
        }
        return seats;
    }

    private static List<MarineSeatCommitment> marineSeatsFromJson(
            JSONArray encodedSeats) throws Exception {
        List<MarineSeatCommitment> seats = new ArrayList<>();
        for (int i = 0; i < encodedSeats.length(); i++) {
            JSONObject encoded = encodedSeats.getJSONObject(i);
            JSONObject squad = encoded.optJSONObject("campaignSquad");
            seats.add(new MarineSeatCommitment(
                    nullableString(encoded, "campaignSoldierId"),
                    encoded.getString("primaryWeaponId"),
                    enumValue(EquipmentGrade.class,
                            encoded.getString("equipmentGrade"), "equipment grade"),
                    soldierProfileFromJson(encoded.getJSONObject("soldierProfile")),
                    nullableString(encoded, "specialEquipmentId"),
                    nullableEnum(LayeredArmorFamily.class,
                            nullableString(encoded, "armorFamily"), "armor family"),
                    (float) encoded.getDouble("armorCapacity"),
                    (float) encoded.getDouble("armorRating"),
                    (float) encoded.getDouble("armorMoveSpeedMult"),
                    (float) encoded.getDouble("armorIncomingAccuracyMult"),
                    squad != null ? squad.getString("squadId") : null,
                    squad != null ? squad.getString("label") : null,
                    squad != null && squad.getBoolean("leader"),
                    squad != null ? squad.getInt("strength") : 0,
                    squad != null ? squad.getInt("fireTeamIndex") : -1));
        }
        return List.copyOf(seats);
    }

    private static JSONArray powersToJson(
            List<CommandPowerCommitment> commitments) throws Exception {
        JSONArray powers = new JSONArray();
        for (CommandPowerCommitment commitment : commitments) {
            JSONObject encoded = new JSONObject();
            encoded.put("id", commitment.id());
            JSONArray deployments = new JSONArray();
            for (MechDeploymentSpec deployment : commitment.mechDeployments()) {
                JSONObject mech = new JSONObject();
                mech.put("variantId", deployment.variant().id);
                mech.put("role", deployment.role().name());
                mech.put("missileReplenisherId",
                        deployment.missileReplenisher().id());
                mech.put("armsComponentId", deployment.arms().id);
                putNullable(mech, "leftShoulderComponentId",
                        componentId(deployment.leftShoulder()));
                putNullable(mech, "rightShoulderComponentId",
                        componentId(deployment.rightShoulder()));
                deployments.put(mech);
            }
            encoded.put("mechDeployments", deployments);
            powers.put(encoded);
        }
        return powers;
    }

    private static SoldierProfile soldierProfileFromJson(JSONObject encoded)
            throws Exception {
        return new SoldierProfile(
                enumValue(SoldierAptitude.class,
                        encoded.getString("aptitude"), "soldier aptitude"),
                encoded.getInt("experienceXp"));
    }

    private static List<CommandPowerCommitment> powersFromJson(
            JSONArray encodedPowers) throws Exception {
        List<CommandPowerCommitment> powers = new ArrayList<>();
        for (int i = 0; i < encodedPowers.length(); i++) {
            JSONObject encoded = encodedPowers.getJSONObject(i);
            JSONArray encodedDeployments = encoded.getJSONArray("mechDeployments");
            List<MechDeploymentSpec> deployments = new ArrayList<>();
            for (int j = 0; j < encodedDeployments.length(); j++) {
                JSONObject mech = encodedDeployments.getJSONObject(j);
                String replenisherId = mech.getString("missileReplenisherId");
                MissileReplenisherComponent replenisher =
                        MissileReplenisherComponent.requireById(replenisherId);
                MechVariant variant = MechVariant.fromId(mech.getString("variantId"));
                deployments.add(new MechDeploymentSpec(
                        variant,
                        enumValue(MechRole.class, mech.getString("role"), "mech role"),
                        replenisher,
                        component(mech, "armsComponentId", variant.arms),
                        component(mech, "leftShoulderComponentId", variant.leftShoulder),
                        component(mech, "rightShoulderComponentId", variant.rightShoulder)));
            }
            powers.add(new CommandPowerCommitment(
                    encoded.getString("id"), deployments));
        }
        return List.copyOf(powers);
    }

    private static void putNullable(JSONObject object, String key, String value)
            throws Exception {
        object.put(key, value != null ? value : JSONObject.NULL);
    }

    private static String componentId(MechWeaponComponent component) {
        return component != null ? component.id : null;
    }

    private static MechWeaponComponent component(JSONObject object, String key,
                                                  MechWeaponComponent fallback) {
        if (!object.has(key) || object.isNull(key)) return fallback;
        MechWeaponComponent component = MechWeaponComponent.findById(object.optString(key, null));
        return component != null ? component : fallback;
    }

    private static String nullableString(JSONObject object, String key)
            throws Exception {
        return object.isNull(key) ? null : object.getString(key);
    }

    private static <E extends Enum<E>> E nullableEnum(
            Class<E> enumType, String value, String field) {
        return value != null ? enumValue(enumType, value, field) : null;
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
        encoded.put("surface", profile.surface().name());
        encoded.put("link", profile.link().name());
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
        SurfacePalette surface = encoded.has("surface")
                ? enumValueIgnoreCase(SurfacePalette.class,
                        encoded.getString("surface"), "surface palette")
                : SurfacePalette.ROCK;
        SettlementLink link = encoded.has("link")
                ? enumValueIgnoreCase(SettlementLink.class,
                        encoded.getString("link"), "settlement link")
                : SettlementLink.ROAD;
        return new TargetProfile(
                encoded.getInt("marketSize"),
                encoded.getInt("stability"),
                encoded.getInt("defenseLevel"),
                encoded.getInt("spaceportTier"),
                encoded.getString("factionId"),
                functions,
                surface,
                link);
    }

    private static <E extends Enum<E>> E enumValue(
            Class<E> enumType, String value, String field) {
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown " + field + ": " + value, ex);
        }
    }

    private static <E extends Enum<E>> E enumValueIgnoreCase(
            Class<E> enumType, String value, String field) {
        for (E constant : enumType.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(value)) return constant;
        }
        throw new IllegalArgumentException("Unknown " + field + ": " + value);
    }
}

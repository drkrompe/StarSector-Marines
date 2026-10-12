package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.fixture.RaidBattleFixture;
import com.fs.starfarer.api.campaign.RepLevel;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.campaign.LastLiftProgress;
import com.dillon.starsectormarines.campaign.FreightRunProgress;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.ops.detachment.DebugCompanyStage;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.ops.detachment.MissionForceEnvelope;
import com.fs.starfarer.api.campaign.PlanetAPI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Disposable mission fixtures. Debug identity never doubles as a campaign receipt. */
public final class DebugMissionCatalog {
    private static final String PREFIX = "debug:story:";
    public enum Scenario {
        RELIEF("Wayline", "Hold Until Relieved", "Reinforce the freight checkpoint against road attacks.", 1, false),
        COUNTERATTACK("Wayline", "Take Back the Depot", "Recover Wayward, capture dispatch and hold against two counterattacks.", 1, false),
        JUNCTION("Broken Supply Route", "Open the Junction", "Capture the three road posts while militia hold your gains.", 2, true),
        LAST_LIFT("Broken Supply Route", "Last Lift", "Evacuation branch: escort and board a fresh civilian manifest.", 2, true),
        RELAY_NINE("Broken Supply Route", "Relay Nine", "Freight branch: capture and hold the relay against recapture attempts.", 2, true),
        THUNDER_RUN("Broken Supply Route", "Thunder Run", "Open the corridor for the tank, APCs and three loaded freight haulers.", 2, true),
        DUST_LEDGER("The Corvus Account", "The Dust Ledger", "Seize the dispatch archive and extract on Asharu.", 2, true),
        BLIND_WATCH("The Corvus Account", "Blind Watch", "Cover the planters at the three Warion installations.", 3, true),
        COLD_ACCOUNT("The Corvus Account", "A Cold Account", "Secure the records, withdraw and call in the Navy on Garnir.", 4, true),
        MILITIA_RELIEF("Repeatable work", "Militia Relief", "A fresh checkpoint relief posting.", 1, false),
        DEPOT_CLEARANCE("Repeatable work", "Depot Clearance", "Clear the depot without the story Hound or warehouse counterattacks.", 1, false),
        CREW_RECOVERY("Repeatable work", "Crew Recovery", "A fresh rescue manifest; no previous rescues or losses.", 2, true),
        ROUTE_SECURITY("Repeatable work", "Route Security", "A fresh relay security contract.", 2, true),
        VETERANS_JOB("Independent stories", "The Veteran's Job", "Raid the broker's cache and extract.", 3, false),
        SILENT_COLONY("Campaign events", "Silent Colony", "Investigate the archive and recover the stranded survivors.", 2, false);
        public final String group, title, description;
        public final int squads;
        public final boolean hound;
        Scenario(String group, String title, String description, int squads, boolean hound) {
            this.group = group; this.title = title; this.description = description; this.squads = squads; this.hound = hound;
        }
        CorvusChapter corvus() {
            return switch (this) {
                case DUST_LEDGER -> CorvusChapter.DUST_LEDGER;
                case BLIND_WATCH -> CorvusChapter.BLIND_WATCH;
                case COLD_ACCOUNT -> CorvusChapter.COLD_ACCOUNT;
                default -> null;
            };
        }
        boolean opening() { return this == RELIEF || this == COUNTERATTACK || this == MILITIA_RELIEF || this == DEPOT_CLEARANCE; }
        public Mission mission() {
            CorvusChapter chapter = corvus();
            Mission.Builder builder = chapter == null ? Mission.builder().type(this == LAST_LIFT || this == CREW_RECOVERY || this == SILENT_COLONY
                    ? MissionType.EXTRACTION : this == VETERANS_JOB ? MissionType.RAID : MissionType.ASSAULT)
                    .tier(this == VETERANS_JOB ? OperationTier.ESTABLISHED : OperationTier.FIRST_CONTRACT)
                    .risk(this == VETERANS_JOB || this == SILENT_COLONY ? RiskLevel.MEDIUM : RiskLevel.LOW).targetPlanetName("Jangala").flavor(description)
                    : Mission.builder(chapter.mission("unused", chapter.planetName, ""));
            return builder.id(PREFIX + name()).name(title).source(MissionSource.DEBUG).payout(0)
                    .requiredDrops(squads * 2).employerShuttles(0)
                    .marineArrivalPolicy(MarineArrivalPolicy.PAIRED_HALF_SQUAD)
                    .fieldPresencePolicy(FieldPresencePolicy.UNRESTRICTED)
                    .requirements(squads + " starter squads" + (hound ? " + Wayward (chaingun)" : "") + "; preset boats supplied")
                    .battleSeed(chapter != null ? chapter.mission("unused", chapter.planetName, "").battleSeed
                            : 8101L + (this == DEPOT_CLEARANCE ? COUNTERATTACK.ordinal() : ordinal()) * 101).build();
        }
    }
    public record Preset(DebugCompanyStage stage, int squads, int mechs, ShuttleType boat, int boats, boolean starterIssue) {
        public String summary() {
            return squads + (squads == 1 ? " squad" : " squads") + " / " + squads * 12 + " marines / "
                    + boats + " " + boat.displayName() + (mechs > 0 ? " / " + mechs + (starterIssue ? " chaingun Hound" : " mechs") : " / no deployed mechs");
        }
    }
    private DebugMissionCatalog() { }
    public static Scenario scenario(Mission m) {
        if (m == null || !m.source.isDebug() || m.id == null || !m.id.startsWith(PREFIX)) return null;
        try { return Scenario.valueOf(m.id.substring(PREFIX.length())); }
        catch (IllegalArgumentException ignored) { return null; }
    }
    public static Preset preset(Mission m) {
        Scenario story = scenario(m);
        if (story != null) return new Preset(DebugCompanyStage.FIRST_CONTRACT, story.squads,
                story.hound ? 1 : 0, ShuttleType.AEROSHUTTLE, story.opening() ? 1 : 2, true);
        DebugCompanyStage stage = switch (m.tier) {
            case FIRST_CONTRACT -> DebugCompanyStage.FIRST_CONTRACT;
            case ESTABLISHED -> DebugCompanyStage.ESTABLISHED;
            case VETERAN -> DebugCompanyStage.VETERAN_COMPANY;
            case REINFORCED -> DebugCompanyStage.REINFORCED;
            case FULL_STRENGTH -> DebugCompanyStage.FULL_STRENGTH;
        };
        return new Preset(stage, MissionForceEnvelope.recommendedSquads(m), stage.mechs,
                m.type == MissionType.CONQUEST ? ShuttleType.AEROSHUTTLE : ShuttleType.VALKYRIE,
                m.type == MissionType.CONQUEST ? 6 : 2, false);
    }
    public static List<Mission> missions(PlanetAPI planet) {
        List<Mission> result = new ArrayList<>();
        for (Scenario scenario : Scenario.values()) result.add(scenario.mission());
        result.addAll(MissionGenerator.generateDebugGrid(planet, client()));
        return List.copyOf(result);
    }
    public static Client client() { return new Client(MarineOpsContext.DEBUG_CLIENT_FACTION_ID, "DEBUG Missions", null, RepLevel.NEUTRAL, false, null); }
    public static MarineRoster starterCompany(int squads) {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(squads * 12);
        for (var squad : roster.squads()) if (!squad.reserve())
            roster.applySquadEquipment(squad.id(), SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS,
                    SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR);
        return roster;
    }
    static List<ShuttleAssignment> manifest(Mission m, List<FittedBoat> boats, int personnel) {
        List<ShuttleAssignment> result = new ArrayList<>();
        if (scenario(m).opening()) result.add(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 6));
        result.addAll(DetachmentResolver.buildShuttleManifestForPersonnel(m, boats, personnel));
        return List.copyOf(result);
    }
    static BattleSimulation build(Scenario scenario, long seed, List<ShuttleAssignment> manifest) {
        CorvusChapter chapter = scenario.corvus();
        if (chapter != null) return chapter.fixture(seed, manifest, chapter.profile(chapter.surface)).build();
        TargetProfile profile = new TargetProfile(0, 0, 0, 0, "pirates", Set.of(), SurfacePalette.VERDANT, SettlementLink.ROAD);
        return switch (scenario) {
            case RELIEF, MILITIA_RELIEF -> BattleSetup.createOpeningOperation(seed, manifest, 1, OpeningOperationKind.RELIEF, profile, false);
            case COUNTERATTACK, DEPOT_CLEARANCE -> BattleSetup.createOpeningOperation(seed, manifest, 1, OpeningOperationKind.COUNTERATTACK, profile, scenario == Scenario.COUNTERATTACK);
            case JUNCTION -> BattleSetup.createJunction(seed, manifest, profile);
            case LAST_LIFT, CREW_RECOVERY -> BattleSetup.createLastLift(seed, manifest, profile, new LastLiftProgress("debug-rill").previewAttempt());
            case RELAY_NINE, ROUTE_SECURITY -> BattleSetup.createRelayNine(seed, manifest, profile);
            case THUNDER_RUN -> BattleSetup.createThunderRun(seed, manifest, profile, new FreightRunProgress("debug-freight").previewAttempt(), 0);
            case SILENT_COLONY -> BattleSetup.createSilentColony(seed, seed, 12, manifest, RiskLevel.MEDIUM);
            case VETERANS_JOB -> RaidBattleFixture.fromFactoryInputs(seed, manifest, false, OperationTier.ESTABLISHED, RiskLevel.MEDIUM,
                    profile, FlybyRoster.EMPTY, FlybyRoster.EMPTY).build();
            default -> throw new IllegalArgumentException("Unknown debug scenario: " + scenario);
        };
    }
}

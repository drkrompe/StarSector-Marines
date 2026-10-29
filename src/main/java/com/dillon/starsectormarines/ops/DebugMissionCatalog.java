package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.cordon.CordonVariant;
import com.dillon.starsectormarines.battle.story.TermsOfProtectionProgress;
import com.dillon.starsectormarines.battle.fixture.BloomHeartBattleFixture;
import com.dillon.starsectormarines.battle.fixture.CordonBattleFixture;
import com.dillon.starsectormarines.battle.fixture.RaidBattleFixture;
import com.dillon.starsectormarines.battle.setup.BloomHeartSetup;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMap;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.HullClass;
import com.dillon.starsectormarines.battle.world.gen.ship.HullRole;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipsBoats;
import com.fs.starfarer.api.campaign.RepLevel;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.MissileAaTestSetup;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.campaign.LastLiftProgress;
import com.dillon.starsectormarines.campaign.WellhollowBloomProgress;
import com.dillon.starsectormarines.campaign.WellhollowBloomProgress.Chapter;
import com.dillon.starsectormarines.campaign.FreightRunProgress;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.marine.BoatFittingSlot;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.marine.EquipmentTemplateCatalog;
import com.dillon.starsectormarines.marine.SquadWeaponIssue;
import com.dillon.starsectormarines.marine.SquadEquipmentResult;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.ops.detachment.DebugCompanyStage;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.ops.detachment.MissionForceEnvelope;
import com.fs.starfarer.api.campaign.PlanetAPI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Disposable mission fixtures. Debug identity never doubles as a campaign receipt. */
public final class DebugMissionCatalog {
    private static final String PREFIX = "debug:story:";
    public enum Scenario {
        RELIEF("Wayline", "Hold Until Relieved", "Reinforce the freight checkpoint against road attacks.", 1, false),
        COUNTERATTACK("Wayline", "Take Back the Depot", "Recover the Hound, capture dispatch and hold against two counterattacks.", 1, false),
        JUNCTION("Broken Supply Route", "Open the Junction", "Capture the three road posts while militia hold your gains.", 2, true),
        LAST_LIFT("Shelved concepts", "Last Lift concept", "Escort and board a civilian manifest; later campaign placement and designation undecided.", 2, true),
        RELAY_NINE("Battlefield fixtures", "Relay Nine", "Capture-and-hold map used by recurring Route Security.", 2, true),
        THUNDER_RUN("Broken Supply Route", "Thunder Run", "Open the corridor for the tank, APCs and three loaded freight haulers.", 2, true),
        DUST_LEDGER("The Corvus Account", "The Dust Ledger", "Seize the dispatch archive and extract on Asharu.", 2, true),
        BLIND_WATCH("The Corvus Account", "Blind Watch", "Cover the planters at the three Warion installations.", 3, true),
        COLD_ACCOUNT("The Corvus Account", "A Cold Account", "Secure the records, withdraw and call in the Navy on Garnir.", 4, true),
        MILITIA_RELIEF("Repeatable work", "Militia Relief", "A fresh checkpoint relief posting.", 1, false),
        DEPOT_CLEARANCE("Repeatable work", "Depot Clearance", "Clear the depot without the story Hound or warehouse counterattacks.", 1, false),
        CREW_RECOVERY("Shelved concepts", "Crew Recovery concept", "Detached rescue variation; no campaign posting.", 2, true),
        ROUTE_SECURITY("Repeatable work", "Route Security", "A fresh relay security contract.", 2, true),
        VETERANS_JOB("Independent stories", "The Veteran's Job", "Raid the broker's cache and extract.", 3, false),
        SILENT_COLONY("Campaign events", "Silent Colony", "Investigate the archive and recover the stranded survivors.", 2, false),
        SALVAGE_RIGHTS("A Working Ship", "Salvage Rights", "Night recovery: secure the camp, hand the workshop to militia and cover Morrow's inspection.", 2, false),
        COLD_START("A Working Ship", "Cold Start", "Escort repair cargo, restore yard power and build Morrow's first road gun.", 2, false),
        WORKING_PASSAGE("A Working Ship", "Working Passage", "Escort Morrow through the basin with service pulse rifles and starting Ward armor; build road defenses and secure physical climb-out.", 2, false),
        WATER_UNDER_SEAL("Agreus", "Water Under Seal", "Escort the technicians and repair loads to the Agreus water works and hold it.", 3, false),
        LIMITED_WARRANT("Agreus", "Limited Warrant", "Raid the calibration works under the warrant's limits.", 3, false),
        YARD_OWNERS("Agreus", "Yard Owners", "Take the yard from the pirates who hold it and secure its tooling.", 3, false),
        TERMS_OF_PROTECTION("Agreus", "Terms of Protection", "Escort the representatives and records on the house custody route.", 3, false),
        SMALL_WAR("Agreus", "A Small War", "Defend the custodians and fuel works on the house custody route.", 3, false),
        DISTRICT_ESCORT("Agreus", "District Escort", "Link up with the party, deliver the consignment and withdraw.", 3, false),
        CORDON_LINE("Rav Bloom", "Cordon Line", "Hold the mobile reserve behind the redoubts while refugees walk out. Density, biome and seed are chosen on the launcher.", 3, false),
        CORDON_ROAD("Rav Bloom", "Cordon Road", "Guard the relief road through the gap to the cut-off town while convoys bring its people out. Density, biome and seed are chosen on the launcher.", 3, false),
        BROAD_FRONT("Rav Bloom", "Broad Front", "Push north on the wide front to the heart and collapse every mother. Density, biome and seed are chosen on the launcher.", 3, false),
        THE_BURN("Rav Bloom", "The Burn", "Drive east through the deep country to the heart, under the Burn's guns. Density, biome and seed are chosen on the launcher.", 3, false),
        WELLHOLLOW_BREACH("The Khettara Bloom", "@wellhollowChapterColonyBreach", "@wellhollowFlavorColonyBreach", WellhollowBloomCampaign.RECOMMENDED_SQUADS, false),
        WELLHOLLOW_ROAD("The Khettara Bloom", "@wellhollowChapterCordonRoad", "@wellhollowFlavorCordonRoad", WellhollowBloomCampaign.RECOMMENDED_SQUADS, false),
        WELLHOLLOW_BURN("The Khettara Bloom", "@wellhollowChapterTheBurn", "@wellhollowFlavorTheBurn", WellhollowBloomCampaign.RECOMMENDED_SQUADS, false),
        WELLHOLLOW_FRONT("The Khettara Bloom", "@wellhollowChapterBroadFront", "@wellhollowFlavorBroadFront", WellhollowBloomCampaign.RECOMMENDED_SQUADS, false),
        MISSILE_AA("Battlefield fixtures", "@missileAaTestTitle", "@missileAaTestDescription", 1, false);
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
        /** The Khettara Bloom chapter this plays, on its fixed Asharu map; null for every other scenario. */
        public Chapter wellhollow() {
            return switch (this) {
                case WELLHOLLOW_BREACH -> Chapter.COLONY_BREACH;
                case WELLHOLLOW_ROAD -> Chapter.CORDON_ROAD;
                case WELLHOLLOW_BURN -> Chapter.THE_BURN;
                case WELLHOLLOW_FRONT -> Chapter.BROAD_FRONT;
                default -> null;
            };
        }
        /** Whether the picker lists it: the campaign's story and repeatable work, and the Rav Bloom missions. */
        public boolean offered() {
            return switch (this) {
                case LAST_LIFT, CREW_RECOVERY, RELAY_NINE, MISSILE_AA, SILENT_COLONY -> false;
                default -> true;
            };
        }
        /**
         * Whether the chapter this stands in for has its employer lift the company's armour, as
         * {@code PowerCatalog.arrangedHeavyLift} answers for the campaign mission: the Broken
         * Supply Route's operations but Thunder Run, the Corvus Account, the Shepherd recovery,
         * and Agreus from Yard Owners on.
         */
        public boolean arrangesHeavyLift() {
            return switch (this) {
                case JUNCTION, RELAY_NINE, ROUTE_SECURITY, DUST_LEDGER, BLIND_WATCH, COLD_ACCOUNT,
                        SALVAGE_RIGHTS, COLD_START, WORKING_PASSAGE, YARD_OWNERS, TERMS_OF_PROTECTION,
                        SMALL_WAR, DISTRICT_ESCORT -> true;
                default -> false;
            };
        }
        /** The Agreus arc's chapters and its escort work, each standing in for its campaign offer. */
        public boolean agreus() {
            return this == WATER_UNDER_SEAL || this == LIMITED_WARRANT || this == YARD_OWNERS
                    || this == TERMS_OF_PROTECTION || this == SMALL_WAR || this == DISTRICT_ESCORT;
        }
        /** The four Rav Bloom missions, whose density, biome and seed come from {@link RavBloomDebugChoice}. */
        public boolean ravBloom() { return this == CORDON_LINE || this == CORDON_ROAD || this == BROAD_FRONT || this == THE_BURN; }
        StoryMap poolMap() {
            CordonVariant variant = this == CORDON_ROAD ? CordonVariant.ROAD : CordonVariant.LINE;
            return switch (this) {
                case CORDON_LINE, CORDON_ROAD -> CordonSetup.storyMap(variant, RavBloomDebugChoice.cordonDensity());
                case BROAD_FRONT -> BloomHeartSetup.storyMap(Form.BROAD_FRONT, RavBloomDebugChoice.heartDensity());
                case THE_BURN -> BloomHeartSetup.storyMap(Form.THE_BURN, RavBloomDebugChoice.heartDensity());
                default -> throw new IllegalStateException(name() + " is not a Rav Bloom mission");
            };
        }
        boolean opening() { return this == RELIEF || this == COUNTERATTACK || this == MILITIA_RELIEF || this == DEPOT_CLEARANCE; }
        /** Local story order; detached concepts and repeatables stay distinct. */
        public String sequence() {
            return switch (this) {
                case MISSILE_AA -> "AA";
                case WATER_UNDER_SEAL -> "01";
                case LIMITED_WARRANT -> "02";
                case YARD_OWNERS -> "03";
                case TERMS_OF_PROTECTION -> "04";
                case SMALL_WAR -> "05";
                case DISTRICT_ESCORT -> "ESC";
                case CORDON_LINE -> "RB1";
                case CORDON_ROAD -> "RB2";
                case BROAD_FRONT -> "RB3";
                case THE_BURN -> "RB4";
                case WELLHOLLOW_BREACH, WELLHOLLOW_ROAD, WELLHOLLOW_BURN, WELLHOLLOW_FRONT -> "WB" + wellhollow().number();
                case RELIEF -> "01";
                case COUNTERATTACK -> "02";
                case JUNCTION -> "03";
                case LAST_LIFT -> "D1";
                case RELAY_NINE -> "MAP";
                case THUNDER_RUN -> "04";
                case DUST_LEDGER, BLIND_WATCH, COLD_ACCOUNT -> "01";
                case MILITIA_RELIEF -> "R1";
                case DEPOT_CLEARANCE -> "R2";
                case CREW_RECOVERY -> "D2";
                case ROUTE_SECURITY -> "R3";
                case VETERANS_JOB -> "S1";
                case SILENT_COLONY -> "E1";
                case SALVAGE_RIGHTS -> "02";
                case COLD_START -> "03";
                case WORKING_PASSAGE -> "04";
            };
        }
        public String location() {
            if (this == MISSILE_AA) return Strings.get("missileAaTestLocation");
            if (ravBloom()) return "Bloomed world";
            if (agreus()) return "Agreus";
            if (wellhollow() != null) return "Asharu";
            if(this==SALVAGE_RIGHTS || this==COLD_START || this==WORKING_PASSAGE)return "Asharu";
            if (corvus() != null) return corvus().planetName;
            return this == VETERANS_JOB || this == SILENT_COLONY || this == LAST_LIFT || this == CREW_RECOVERY ? "Unfixed site" : "Jangala";
        }
        public String sequenceHint() {
            return switch (this) {
                case MISSILE_AA -> Strings.get("missileAaTestHint");
                case WATER_UNDER_SEAL -> "Agreus arc 1 of 5 / After Asharu 04";
                case LIMITED_WARRANT -> "Agreus arc 2 of 5 / After Water Under Seal";
                case YARD_OWNERS -> "Agreus arc 3 of 5 / After Limited Warrant";
                case TERMS_OF_PROTECTION -> "Agreus arc 4 of 5 / After Yard Owners";
                case SMALL_WAR -> "Agreus arc 5 of 5 / After Terms of Protection";
                case DISTRICT_ESCORT -> "Repeatable work / Opens after A Small War";
                case CORDON_LINE, CORDON_ROAD -> "Rav Bloom / The Cordon / " + RavBloomDebugChoice.label();
                case BROAD_FRONT, THE_BURN -> "Rav Bloom / The heart / " + RavBloomDebugChoice.label();
                case WELLHOLLOW_BREACH -> "Khettara Bloom 1 of 4 / After A Working Ship / Fixed Asharu map";
                case WELLHOLLOW_ROAD -> "Khettara Bloom 2 of 4 / After The Khettara / Fixed Asharu map";
                case WELLHOLLOW_BURN -> "Khettara Bloom 3 of 4 / After Ain Tafrant / Fixed Asharu map";
                case WELLHOLLOW_FRONT -> "Khettara Bloom 4 of 4 / After The Channel Root / Fixed Asharu map";
                case RELIEF -> "Wayline introduction";
                case COUNTERATTACK -> "After Jangala 01 / Relief";
                case JUNCTION -> "After Jangala 02 / Counterattack";
                case LAST_LIFT -> "Later campaign / Placement and designation undecided";
                case RELAY_NINE -> "Detached battlefield / Used by Route Security";
                case THUNDER_RUN -> "After 03 / Junction";
                case DUST_LEDGER -> "Corvus Account 1 of 3 / Begins after Broken Supply Route";
                case BLIND_WATCH -> "Corvus Account 2 of 3 / After Asharu 01";
                case COLD_ACCOUNT -> "Corvus Account 3 of 3 / After Warion 01";
                case CREW_RECOVERY -> "Detached rescue variation / No campaign posting";
                case MILITIA_RELIEF, DEPOT_CLEARANCE, ROUTE_SECURITY -> "Repeatable work / Outside the numbered story sequence";
                case VETERANS_JOB -> "Standalone story / Location varies in campaign";
                case SILENT_COLONY -> "Campaign event / Location varies in campaign";
                case SALVAGE_RIGHTS -> "A Working Ship 1 of 3 / After Garnir 01";
                case COLD_START -> "A Working Ship 2 of 3 / After Asharu 02";
                case WORKING_PASSAGE -> "A Working Ship 3 of 3 / After Asharu 03";
            };
        }
        public Mission mission() {
            if (wellhollow() != null) return wellhollowMission(wellhollow());
            CorvusChapter chapter = corvus();
            Mission.Builder builder = chapter == null ? Mission.builder().type(this == LAST_LIFT || this == CREW_RECOVERY || this == SILENT_COLONY
                    ? MissionType.EXTRACTION : this == VETERANS_JOB ? MissionType.RAID : MissionType.ASSAULT)
                    .tier(this == VETERANS_JOB ? OperationTier.ESTABLISHED : OperationTier.FIRST_CONTRACT)
                    .risk(ravBloom() ? RiskLevel.HIGH : this == VETERANS_JOB || this == SILENT_COLONY ? RiskLevel.MEDIUM : RiskLevel.LOW).targetPlanetName(location()).flavor(Strings.resolve(description))
                    : Mission.builder(chapter.mission("unused", chapter.planetName, ""));
            return builder.id(PREFIX + name()).name(Strings.resolve(title)).source(MissionSource.DEBUG).payout(0)
                    .requiredDrops(squads * 2).employerShuttles(0)
                    .marineArrivalPolicy(this == COLD_START || this == RELIEF ? MarineArrivalPolicy.SECURED_GROUND : MarineArrivalPolicy.PAIRED_HALF_SQUAD)
                    .fieldPresencePolicy(FieldPresencePolicy.UNRESTRICTED)
                    .requirements(squads + (this == WORKING_PASSAGE ? " squads / service pulse rifles / starting Ward armor / no Hound or officer bonuses" : " starter squads" + (hound ? " + the Hound (chaingun)" : "")) + "; preset boats supplied")
                    .battleSeed(chapter != null ? chapter.mission("unused", chapter.planetName, "").battleSeed
                            : ravBloom() ? RavBloomDebugChoice.seed(poolMap())
                            : 8101L + (this == DEPOT_CLEARANCE ? COUNTERATTACK.ordinal() : ordinal()) * 101).build();
        }
        /** The chapter as the Warden briefs it on first play, with no fee and on its fixed map. */
        private Mission wellhollowMission(Chapter chapter) {
            boolean breach = chapter == Chapter.COLONY_BREACH;
            return Mission.builder().id(PREFIX + name()).name(Strings.resolve(title)).source(MissionSource.DEBUG).payout(0)
                    .type(breach ? MissionType.EXTRACTION : MissionType.ASSAULT)
                    .tier(OperationTier.ESTABLISHED).risk(RiskLevel.HIGH).targetPlanetName(location())
                    .flavor(Strings.resolve(description))
                    .dialogue(WellhollowBloomCampaign.briefing(new WellhollowBloomProgress(), chapter, false))
                    .requirements(Strings.format("wellhollowPlace", chapter.number()) + Strings.get("wellhollowForce"))
                    .requiredDrops(breach ? 4 : 6).employerShuttles(0)
                    .marineArrivalPolicy(MarineArrivalPolicy.PAIRED_HALF_SQUAD)
                    .fieldPresencePolicy(FieldPresencePolicy.UNRESTRICTED)
                    .battleSeed(WellhollowBloomCampaign.fixedSeed(chapter)).build();
        }
    }
    public record Preset(DebugCompanyStage stage, int squads, int mechs, ShuttleType boat, int boats, boolean starterIssue, boolean serviceRifles) {
        public String summary() {
            return squads + (squads == 1 ? " squad" : " squads") + " / " + squads * 12 + " marines / "
                    + boats + " " + boat.displayName() + (mechs > 0 ? " / " + mechs + (starterIssue ? " chaingun Hound" : " mechs") : " / no deployed mechs")
                    + (serviceRifles ? " / service pulse rifles / starting Ward armor / no officer bonuses" : "");
        }
    }
    /**
     * The Second Wind as the campaign gives her to the player, bay restored: the hull facts of
     * {@code CerberusCompanyStart}'s ship (a converted frigate freighter, 20 to 68 crew, 75 cargo), headless.
     * The proportions only stand in for her outline; the berth count does not depend on them.
     */
    private static final CompanyShip SECOND_WIND = new CompanyShip(HullClass.FRIGATE, HullRole.FREIGHTER,
            20, 68, 75, 0.75f, null, null, true, CompanyShip.BoatBayState.RESTORED);
    /**
     * Every debug mission lifts the company as the campaign does: the boats the Second Wind carries,
     * read off her hull by the same {@link ShipsBoats} answer a campaign briefing asks, and fitted
     * to the top by {@link #maxFitted}. No debug preset names a boat of its own.
     */
    static final ShuttleType CAMPAIGN_LIFT = ShipsBoats.carriedBy(SECOND_WIND);
    static final int CAMPAIGN_LIFT_BOATS = ShipsBoats.aboard(SECOND_WIND).size();
    /**
     * A debug lift boat: {@code pattern} with the top tier of every fitting its hull takes. The frame's
     * weight rating is a campaign yard's limit, not a battle rule, so a debug boat is not held to it.
     */
    public static FittedBoat maxFitted(ShuttleType pattern) {
        Map<BoatFittingSlot, BoatFitting> fit = new EnumMap<>(BoatFittingSlot.class);
        for (BoatFittingSlot slot : BoatFittingSlot.values()) {
            BoatFitting best = BoatFitting.standard(slot);
            for (BoatFitting fitting : BoatFitting.catalog(slot))
                if (fitting.supports(pattern) && fitting.tier() > best.tier()) best = fitting;
            fit.put(slot, best);
        }
        return new FittedBoat(pattern, fit, null, null);
    }
    private DebugMissionCatalog() { }
    public static Scenario scenario(Mission m) {
        return m == null ? null : scenario(m.id, m.source);
    }
    static Scenario scenario(String id, MissionSource source) {
        if (source != MissionSource.DEBUG || id == null || !id.startsWith(PREFIX)) return null;
        try { return Scenario.valueOf(id.substring(PREFIX.length())); }
        catch (IllegalArgumentException ignored) { return null; }
    }
    /** Whether a debug mission's armour has the employer's heavy lift behind the company boat; never for generated missions. */
    public static boolean arrangesHeavyLift(Mission m) {
        Scenario story = scenario(m);
        return story != null && story.arrangesHeavyLift();
    }
    public static Preset preset(Mission m) {
        Scenario story = scenario(m);
        if (story != null && story.wellhollow() != null) return new Preset(DebugCompanyStage.VETERAN_COMPANY, story.squads,
                WellhollowBloomCampaign.RECOMMENDED_MACHINES, CAMPAIGN_LIFT, CAMPAIGN_LIFT_BOATS, false, false);
        if (story != null) return new Preset(DebugCompanyStage.FIRST_CONTRACT, story.squads,
                story.hound ? 1 : 0, CAMPAIGN_LIFT, CAMPAIGN_LIFT_BOATS, true, story == Scenario.WORKING_PASSAGE);
        DebugCompanyStage stage = switch (m.tier) {
            case FIRST_CONTRACT -> DebugCompanyStage.FIRST_CONTRACT;
            case ESTABLISHED -> DebugCompanyStage.ESTABLISHED;
            case VETERAN -> DebugCompanyStage.VETERAN_COMPANY;
            case REINFORCED -> DebugCompanyStage.REINFORCED;
            case FULL_STRENGTH -> DebugCompanyStage.FULL_STRENGTH;
        };
        return new Preset(stage, MissionForceEnvelope.recommendedSquads(m), stage.mechs,
                CAMPAIGN_LIFT, CAMPAIGN_LIFT_BOATS, false, false);
    }
    public static List<Mission> missions(PlanetAPI planet) {
        List<Mission> result = new ArrayList<>();
        for (Scenario scenario : Scenario.values()) if (scenario.offered()) result.add(scenario.mission());
        for (Mission m : MissionGenerator.generateDebugGrid(planet, client()))
            if (m.type == MissionType.CONQUEST || colonyBreach(m)) result.add(m);
        return List.copyOf(result);
    }
    /** The production-shaped Colony Breach the picker lists with the Rav Bloom missions; the force-scaled stress rescues stay out. */
    public static boolean colonyBreach(Mission m) { return m != null && m.source == MissionSource.DEBUG_CANONICAL_CIVILIAN_RESCUE; }
    public static Client client() { return new Client(MarineOpsContext.DEBUG_CLIENT_FACTION_ID, "DEBUG Missions", null, RepLevel.NEUTRAL, false, null); }
    public static MarineRoster starterCompany(int squads) {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(squads * 12);
        for (var squad : roster.squads()) if (!squad.reserve())
            roster.applySquadEquipment(squad.id(), SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS,
                    SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR);
        return roster;
    }
    /** Detached expected issue for one authored debug operation; campaign equipment is untouched. */
    public static MarineRoster expectedCompany(Mission mission, int squads) {
        MarineRoster roster = starterCompany(squads);
        if (scenario(mission) != Scenario.WORKING_PASSAGE) return roster;
        roster.armory().acquireEquipmentTemplate(EquipmentTemplateCatalog.primaryId(WeaponRegistry.PULSE_RIFLE_ID, EquipmentGrade.SERVICE));
        var issue = new SquadWeaponIssue("Rifleman", WeaponRegistry.PULSE_RIFLE_ID, EquipmentGrade.SERVICE, null);
        var doctrine = roster.armory().ensureWeaponDoctrine("debug:weapons:working-passage",
                "Working Passage service rifles", Collections.nCopies(12, issue));
        for (var squad : roster.squads()) if (!squad.reserve()
                && roster.applySquadEquipment(squad.id(), doctrine.id(), SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR) != SquadEquipmentResult.APPLIED)
            throw new IllegalStateException("Unable to issue Working Passage debug equipment");
        return roster;
    }
    static List<ShuttleAssignment> manifest(Mission m, List<FittedBoat> boats, int personnel) {
        List<ShuttleAssignment> result = new ArrayList<>();
        if (scenario(m).opening()) result.add(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 6));
        result.addAll(DetachmentResolver.buildShuttleManifestForPersonnel(m, boats, personnel));
        return List.copyOf(result);
    }
    static BattleSimulation build(Scenario scenario, long seed, List<ShuttleAssignment> manifest) {
        if (scenario.wellhollow() != null) return WellhollowBloomCampaign.fixture(scenario.wellhollow(), seed, manifest).build();
        CorvusChapter chapter = scenario.corvus();
        if (chapter != null) return chapter.fixture(seed, manifest, chapter.profile(chapter.surface)).build();
        TargetProfile profile = new TargetProfile(0, 0, 0, 0, "pirates", Set.of(), SurfacePalette.VERDANT, SettlementLink.ROAD);
        return switch (scenario) {
            case MISSILE_AA -> MissileAaTestSetup.create(seed, manifest);
            case WORKING_PASSAGE -> BattleSetup.createWorkingPassage(seed,manifest,new TargetProfile(0,0,0,0,"pirates",Set.of(),SurfacePalette.ARID,SettlementLink.ROAD));
            case COLD_START -> BattleSetup.createColdStart(seed,manifest,new TargetProfile(0,0,0,0,"pirates",Set.of(),SurfacePalette.ARID,SettlementLink.ROAD),false,false);
            case SALVAGE_RIGHTS -> BattleSetup.createSalvageRights(seed,manifest,new TargetProfile(0,0,0,0,"pirates",Set.of(),SurfacePalette.ARID,SettlementLink.ROAD));
            case RELIEF, MILITIA_RELIEF -> BattleSetup.createOpeningOperation(seed, manifest, 1, OpeningOperationKind.RELIEF, profile, false,
                    scenario == Scenario.RELIEF);
            case COUNTERATTACK, DEPOT_CLEARANCE -> BattleSetup.createOpeningOperation(seed, manifest, 1, OpeningOperationKind.COUNTERATTACK, profile, scenario == Scenario.COUNTERATTACK);
            case JUNCTION -> BattleSetup.createJunction(seed, manifest, profile);
            case LAST_LIFT, CREW_RECOVERY -> BattleSetup.createLastLift(seed, manifest, profile, new LastLiftProgress("debug-rill").previewAttempt());
            case RELAY_NINE, ROUTE_SECURITY -> BattleSetup.createRelayNine(seed, manifest, profile);
            case THUNDER_RUN -> BattleSetup.createThunderRun(seed, manifest, profile, new FreightRunProgress("debug-freight").previewAttempt(), 0);
            case WATER_UNDER_SEAL -> BattleSetup.createWaterUnderSeal(seed, manifest, profile);
            case LIMITED_WARRANT -> BattleSetup.createLimitedWarrant(seed, manifest, profile, false);
            case YARD_OWNERS -> BattleSetup.createYardOwners(seed, manifest, profile);
            case TERMS_OF_PROTECTION -> BattleSetup.createTermsOfProtection(seed, manifest, profile,
                    TermsOfProtectionProgress.Route.HOUSE_CUSTODY);
            case SMALL_WAR -> BattleSetup.createSmallWar(seed, manifest, profile, true);
            case DISTRICT_ESCORT -> BattleSetup.createDistrictEscort(seed, manifest, profile,
                    DistrictEscortCampaign.Letterhead.RUSK_OFFICE.partyName());
            case CORDON_LINE -> new CordonBattleFixture(seed, CordonVariant.LINE, RavBloomDebugChoice.biome(),
                    RavBloomDebugChoice.cordonDensity(), manifest).build();
            case CORDON_ROAD -> new CordonBattleFixture(seed, CordonVariant.ROAD, RavBloomDebugChoice.biome(),
                    RavBloomDebugChoice.cordonDensity(), manifest).build();
            case BROAD_FRONT -> new BloomHeartBattleFixture(seed, Form.BROAD_FRONT, RavBloomDebugChoice.biome(),
                    RavBloomDebugChoice.heartDensity(), manifest).build();
            case THE_BURN -> new BloomHeartBattleFixture(seed, Form.THE_BURN, RavBloomDebugChoice.biome(),
                    RavBloomDebugChoice.heartDensity(), manifest).build();
            case SILENT_COLONY -> BattleSetup.createSilentColony(seed, seed, 12, manifest, RiskLevel.MEDIUM);
            case VETERANS_JOB -> RaidBattleFixture.fromFactoryInputs(seed, manifest, false, OperationTier.ESTABLISHED, RiskLevel.MEDIUM,
                    profile, FlybyRoster.EMPTY, FlybyRoster.EMPTY).build();
            default -> throw new IllegalArgumentException("Unknown debug scenario: " + scenario);
        };
    }
}

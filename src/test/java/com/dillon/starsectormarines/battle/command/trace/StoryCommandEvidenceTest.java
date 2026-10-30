package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.power.YardConstruction;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.control.ShipCommandService;
import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.power.ShipOnStation;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.TermsOfProtectionProgress;
import com.dillon.starsectormarines.battle.story.WorkingPassageProgress;
import com.dillon.starsectormarines.battle.tender.BuildOption;
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.campaign.AgreusYardAgreement;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.battle.world.gen.ShepherdBasin;
import com.dillon.starsectormarines.ops.DebugMissionCatalog;
import com.dillon.starsectormarines.ops.DistrictEscortCampaign;
import com.dillon.starsectormarines.ops.WorkingShipCompany;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.WorkingShipMuster;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Commander evidence for the story missions whose commanders hold no shared planner of their own: The Yard's Owners,
 * Water Under Seal, Limited Warrant, District Escort, Terms of Protection, A Small War (Black Sluice), Salvage Rights,
 * Cold Start and Working Passage (`commander-evidence-missions.md`, story missions). Each is played with its own
 * production factory and its own story commanders, the company issued as the mission's snapshot suite issues it, and
 * records the authored beats, the phase walk, the receipt, losses, the place-fight mix and the defender's reactive
 * manning, so a run and its control ({@code -Dbattle.story.reactiveDefence=false}) can be read side by side.
 *
 * <p>Zero input, except where the mission itself waits on the player's ship bar: Water Under Seal and Terms of
 * Protection call Ship on Station and order the road gun, Cold Start requests yard construction, and Working Passage
 * orders a gun at each stop, when the snapshot suite does; a gun order is given again until it is laid, as a player
 * would. Every such request is listed in the
 * fixture's {@code playerInputs}. Opt-in and excluded from {@code test} (the class tag); select with
 * {@code -Pmission=<id>}, whose tag is the method's.
 */
@Tag("story-command-evidence")
class StoryCommandEvidenceTest {

    private static final String PREFIX = "story.command.evidence";
    private static final TargetProfile ROCK = new TargetProfile(0, 0, 0, 0, "pirates", Set.of(), SurfacePalette.ROCK, SettlementLink.ROAD);
    private static final TargetProfile ARID = new TargetProfile(0, 0, 0, 0, "pirates", Set.of(), SurfacePalette.ARID, SettlementLink.ROAD);
    /** Terms of Protection's road-gun plot at the Cinder Steps gate approach, as the snapshot suite lays it. */
    private static final int TERMS_BUILD_X = 114, TERMS_BUILD_Y = 50;

    @Test @Tag("yard-owners-command-evidence") void yardOwners() throws Exception { run(YARD_OWNERS); }
    @Test @Tag("water-under-seal-command-evidence") void waterUnderSeal() throws Exception { run(WATER_UNDER_SEAL); }
    @Test @Tag("limited-warrant-command-evidence") void limitedWarrant() throws Exception { run(LIMITED_WARRANT); }
    @Test @Tag("district-escort-command-evidence") void districtEscort() throws Exception { run(DISTRICT_ESCORT); }
    @Test @Tag("terms-of-protection-command-evidence") void termsOfProtection() throws Exception { run(TERMS_OF_PROTECTION); }
    @Test @Tag("small-war-command-evidence") void smallWar() throws Exception { run(SMALL_WAR); }
    @Test @Tag("salvage-rights-command-evidence") void salvageRights() throws Exception { run(SALVAGE_RIGHTS); }
    @Test @Tag("cold-start-command-evidence") void coldStart() throws Exception { run(COLD_START); }
    @Test @Tag("working-passage-command-evidence") void workingPassage() throws Exception { run(WORKING_PASSAGE); }

    /** One fixture: a seed, the company's size, and the mission's own variant (route, records, delivery). */
    record Fixture(String id, long seed, int personnel, String variant) {
        JSONObject json() throws JSONException {
            return new JSONObject().put("id", id).put("seed", seed).put("personnel", personnel).put("variant", variant);
        }
    }

    /** What a story mission needs from the harness: how to stand it up, read it, and any ship-bar input it waits on. */
    private interface Story {
        BattleSimulation build(Fixture fixture);
        Enum<?> phase(BattleSimulation sim);
        Object receipt(BattleSimulation sim);
        /** Called before every advance; a request is made once and listed in {@code inputs}. */
        default void beforeAdvance(BattleSimulation sim, Fixture fixture, Map<String, Integer> done, JSONArray inputs) throws JSONException { }
    }

    private record Mission(String id, int defaultTicks, List<Fixture> fixtures, Story story) {}

    private static List<Fixture> fixtures(Object[]... rows) {
        List<Fixture> out = new ArrayList<>();
        for (Object[] row : rows) out.add(new Fixture((String) row[0], ((Number) row[1]).longValue(), (Integer) row[2], (String) row[3]));
        return out;
    }

    private static final Mission YARD_OWNERS = new Mission("yard-owners", 18000, fixtures(
            new Object[]{"15303", 15303, 36, ""}, new Object[]{"16203", 16203, 36, ""},
            new Object[]{"16204", 16204, 36, ""}, new Object[]{"15303-24", 15303, 24, ""}), new Story() {
        public BattleSimulation build(Fixture f) { return deploy(BattleSetup.createYardOwners(f.seed(), lift(f), ROCK), expected(f), f.personnel()); }
        public Enum<?> phase(BattleSimulation sim) { return sim.getYardOwners().progress().phase(); }
        public Object receipt(BattleSimulation sim) { return sim.getYardOwners().report(sim); }
    });

    private static final Mission WATER_UNDER_SEAL = new Mission("water-under-seal", 27000, fixtures(
            new Object[]{"15301", 15301, 24, ""}, new Object[]{"16101", 16101, 24, ""},
            new Object[]{"16102", 16102, 24, ""}, new Object[]{"15301-36", 15301, 36, ""}), new Story() {
        public BattleSimulation build(Fixture f) {
            BattleSimulation sim = deploy(BattleSetup.createWaterUnderSeal(f.seed(), lift(f), ROCK), expected(f), f.personnel());
            sim.setCommandPowers(List.of(new ShipOnStation(List.of(morrow("water")))));
            return sim;
        }
        public Enum<?> phase(BattleSimulation sim) { return sim.getWaterUnderSeal().progress().phase(); }
        public Object receipt(BattleSimulation sim) { return sim.getWaterUnderSeal().report(sim); }
        public void beforeAdvance(BattleSimulation sim, Fixture f, Map<String, Integer> done, JSONArray inputs) throws JSONException {
            var sequence = sim.getWaterUnderSeal();
            if (!"CONSTRUCTION".equals(phase(sim).name())) return;
            if (!done.containsKey("ship") && sim.playerSeesCell(sequence.buildX(), sequence.buildY())) {
                sim.getCommandPowerService().requestActivation(ShipOnStation.ID, sequence.buildX(), sequence.buildY());
                input(sim, done, inputs, "ship", "Ship on Station");
            }
            if (done.containsKey("ship")) lay(sim, sequence.tender(), sequence.buildX(), sequence.buildY(), "build", done, inputs);
        }
    });

    private static final Mission LIMITED_WARRANT = new Mission("limited-warrant", 18000, fixtures(
            new Object[]{"15302", 15302, 24, ""}, new Object[]{"16302", 16302, 24, ""},
            new Object[]{"16303", 16303, 24, ""}, new Object[]{"15302-records", 15302, 24, "records"}), new Story() {
        public BattleSimulation build(Fixture f) {
            return deploy(BattleSetup.createLimitedWarrant(f.seed(), lift(f), ROCK, f.variant().equals("records")), expected(f), f.personnel());
        }
        public Enum<?> phase(BattleSimulation sim) { return sim.getLimitedWarrant().progress().phase(); }
        public Object receipt(BattleSimulation sim) { return sim.getLimitedWarrant().report(sim); }
    });

    private static final Mission DISTRICT_ESCORT = new Mission("district-escort", 18000, fixtures(
            new Object[]{"custody-15400", 15400, 24, "custody"}, new Object[]{"compact-15401", 15401, 24, "compact"},
            new Object[]{"compact-16211", 16211, 24, "compact"}, new Object[]{"custody-15400-36", 15400, 36, "custody"}), new Story() {
        public BattleSimulation build(Fixture f) {
            String party = DistrictEscortCampaign.Letterhead.of(agreement(f)).partyName();
            return deploy(BattleSetup.createDistrictEscort(f.seed(), lift(f), ROCK, party), expected(f), f.personnel());
        }
        public Enum<?> phase(BattleSimulation sim) { return sim.getDistrictEscort().progress().phase(); }
        public Object receipt(BattleSimulation sim) { return sim.getDistrictEscort().report(sim); }
    });

    private static final Mission TERMS_OF_PROTECTION = new Mission("terms-of-protection", 21600, fixtures(
            new Object[]{"custody-15304", 15304, 36, "custody"}, new Object[]{"custody-16304", 16304, 36, "custody"},
            new Object[]{"compact-15304", 15304, 36, "compact"}, new Object[]{"compact-16304", 16304, 36, "compact"}), new Story() {
        public BattleSimulation build(Fixture f) {
            var route = agreement(f) == AgreusYardAgreement.HOUSE_CUSTODY
                    ? TermsOfProtectionProgress.Route.HOUSE_CUSTODY : TermsOfProtectionProgress.Route.YARD_COMPACT;
            BattleSimulation sim = deploy(BattleSetup.createTermsOfProtection(f.seed(), lift(f), ROCK, route), expected(f), f.personnel());
            sim.setCommandPowers(List.of(new ShipOnStation(List.of(morrow("terms")))));
            return sim;
        }
        public Enum<?> phase(BattleSimulation sim) { return sim.getTermsOfProtection().progress().phase(); }
        public Object receipt(BattleSimulation sim) { return sim.getTermsOfProtection().report(sim); }
        public void beforeAdvance(BattleSimulation sim, Fixture f, Map<String, Integer> done, JSONArray inputs) throws JSONException {
            if (!"COMMISSIONING".equals(phase(sim).name())) return;
            if (!done.containsKey("ship") && sim.playerSeesCell(TERMS_BUILD_X, TERMS_BUILD_Y)) {
                sim.getCommandPowerService().requestActivation(ShipOnStation.ID, TERMS_BUILD_X, TERMS_BUILD_Y);
                input(sim, done, inputs, "ship", "Ship on Station");
            }
            Tender tender = sim.tenders().tenders().isEmpty() ? null : sim.tenders().tenders().get(0);
            if (done.containsKey("ship") && tender != null && !tender.grounded())
                lay(sim, tender, TERMS_BUILD_X, TERMS_BUILD_Y, "build", done, inputs);
        }
    });

    private static final Mission SMALL_WAR = new Mission("small-war", 21600, fixtures(
            new Object[]{"custody-15305", 15305, 36, "custody"}, new Object[]{"custody-16205", 16205, 36, "custody"},
            new Object[]{"compact-16206", 16206, 36, "compact"}, new Object[]{"custody-16207", 16207, 36, "custody"},
            new Object[]{"custody-15305-24", 15305, 24, "custody"}, new Object[]{"custody-16205-24", 16205, 24, "custody"}), new Story() {
        public BattleSimulation build(Fixture f) {
            return deploy(BattleSetup.createSmallWar(f.seed(), lift(f), ROCK, agreement(f) == AgreusYardAgreement.HOUSE_CUSTODY), expected(f), f.personnel());
        }
        public Enum<?> phase(BattleSimulation sim) { return sim.getSmallWar().progress().phase(); }
        public Object receipt(BattleSimulation sim) { return sim.getSmallWar().report(sim); }
    });

    /** The company Salvage Rights is balanced against, in the debug preset's boats, calling its four machines down. */
    private static final Mission SALVAGE_RIGHTS = new Mission("salvage-rights", 18000, fixtures(
            new Object[]{"14101", 14101, 60, ""}, new Object[]{"14102", 14102, 60, ""},
            new Object[]{"15101", 15101, 60, ""}, new Object[]{"14101-48", 14101, 48, ""}), new Story() {
        public BattleSimulation build(Fixture f) { return WorkingShipMuster.build(ShepherdBasin.Visit.SALVAGE_RIGHTS, f.seed(), f.personnel() / 12, true); }
        public Enum<?> phase(BattleSimulation sim) { return sim.getSalvageRights().progress().phase(); }
        public Object receipt(BattleSimulation sim) { return sim.getSalvageRights().report(sim); }
        public void beforeAdvance(BattleSimulation sim, Fixture f, Map<String, Integer> done, JSONArray inputs) throws JSONException {
            callMachines(sim, done, inputs);
        }
    });

    /** Calls the A Working Ship company's machines down one at a time, each as soon as the drop is ready. */
    private static void callMachines(BattleSimulation sim, Map<String, Integer> done, JSONArray inputs) throws JSONException {
        for (int pick = 0; pick < WorkingShipCompany.MACHINES; pick++) {
            if (done.containsKey("machine:" + pick)) continue;
            if (WorkingShipMuster.callMachine(sim, pick)) input(sim, done, inputs, "machine:" + pick, "machine " + pick + " called");
            return;
        }
    }

    /** Cold Start's scripted road gun: the recovery tender's machine gun. */
    private static final String YARD_MG = YardConstruction.ID_PREFIX + BuildOption.HEAVY_MG_TURRET.id();

    /** The company Cold Start is balanced against, ashore on the apron, calling its four machines down. */
    private static final Mission COLD_START = new Mission("cold-start", 18000, fixtures(
            new Object[]{"14101", 14101, 60, ""}, new Object[]{"14102", 14102, 60, ""},
            new Object[]{"15101", 15101, 60, ""}, new Object[]{"14101-48", 14101, 48, ""}), new Story() {
        public BattleSimulation build(Fixture f) { return WorkingShipMuster.build(ShepherdBasin.Visit.COLD_START, f.seed(), f.personnel() / 12, true); }
        public Enum<?> phase(BattleSimulation sim) { return sim.getColdStart().progress().phase(); }
        public Object receipt(BattleSimulation sim) { return sim.getColdStart().report(sim); }
        public void beforeAdvance(BattleSimulation sim, Fixture f, Map<String, Integer> done, JSONArray inputs) throws JSONException {
            callMachines(sim, done, inputs);
            var powers = sim.getCommandPowerService();
            if (!done.containsKey("build") && "CONSTRUCTION".equals(phase(sim).name())
                    && powers.canActivate(powers.getPower(YARD_MG))) {
                powers.requestActivation(YARD_MG, sim.getColdStart().buildX(), sim.getColdStart().buildY());
                input(sim, done, inputs, "build", "yard construction");
            }
        }
    });

    /** The company Working Passage is balanced against, in the debug preset's boats, calling its four machines down. */
    private static final Mission WORKING_PASSAGE = new Mission("working-passage", 27000, fixtures(
            new Object[]{"14101", 14101, 60, ""}, new Object[]{"15101", 15101, 60, ""},
            new Object[]{"15102", 15102, 60, ""}, new Object[]{"15101-48", 15101, 48, ""}), new Story() {
        public BattleSimulation build(Fixture f) { return WorkingShipMuster.build(ShepherdBasin.Visit.WORKING_PASSAGE, f.seed(), f.personnel() / 12, true); }
        public Enum<?> phase(BattleSimulation sim) { return sim.getWorkingPassage().progress().phase(); }
        public Object receipt(BattleSimulation sim) { return sim.getWorkingPassage().report(sim); }
        public void beforeAdvance(BattleSimulation sim, Fixture f, Map<String, Integer> done, JSONArray inputs) throws JSONException {
            callMachines(sim, done, inputs);
            var sequence = sim.getWorkingPassage();
            var phase = sequence.progress().phase();
            if ((phase == WorkingPassageProgress.Phase.BEND_BUILD || phase == WorkingPassageProgress.Phase.NARROWS_BUILD)
                    && sequence.tender().groundSiteReachable(sequence.buildX() + .5f, sequence.buildY() + .5f, sim.getGrid()))
                lay(sim, sequence.tender(), sequence.buildX(), sequence.buildY(), phase.name(), done, inputs);
        }
    });

    private static void input(BattleSimulation sim, Map<String, Integer> done, JSONArray inputs, String key, String what) throws JSONException {
        done.put(key, sim.getSimTickIndex());
        inputs.put(new JSONObject().put("tick", sim.getSimTickIndex()).put("input", what));
    }

    /** How long a player waits for a road-gun order to be laid before giving it again. */
    private static final int RELAY_TICKS = 150;

    /**
     * Orders a heavy MG on the plot as a player on the ship bar would: again every {@value #RELAY_TICKS} ticks until the
     * ship command service reports it laid, then never again under {@code key}.
     */
    private static void lay(BattleSimulation sim, Tender tender, int x, int y, String key, Map<String, Integer> done,
                            JSONArray inputs) throws JSONException {
        if (tender == null || done.containsKey(key + ":laid")) return;
        if (sim.shipCommands().notice(tender.ship()) == ShipCommandService.Notice.LAID && done.containsKey(key)) {
            input(sim, done, inputs, key + ":laid", "road gun laid (" + key + ")");
            return;
        }
        Integer last = done.get(key);
        if (last != null && sim.getSimTickIndex() - last < RELAY_TICKS) return;
        sim.shipCommands().requestBuild(tender, BuildOption.HEAVY_MG_TURRET, x, y);
        input(sim, done, inputs, key, "road gun ordered (" + key + ")");
    }

    private static AgreusYardAgreement agreement(Fixture f) {
        return f.variant().equals("compact") ? AgreusYardAgreement.YARD_COMPACT : AgreusYardAgreement.HOUSE_CUSTODY;
    }

    private static ShipSupportSource morrow(String mission) {
        return new ShipSupportSource("shepherd", "Morrow", true, Map.of(), mission + "-morrow-member");
    }

    /** Two six-seat boats making as many trips as the company needs, twelve seats a trip. */
    private static List<ShuttleAssignment> lift(Fixture f) {
        int trips = f.personnel() / 12;
        return List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, trips, 6), new ShuttleAssignment(ShuttleType.AEROSHUTTLE, trips, 6));
    }

    /** The Agreus company as the Agreus snapshot suites issue it. */
    private static MarineRoster expected(Fixture f) {
        return DebugMissionCatalog.agreusCompany(f.personnel() / 12);
    }


    /** Puts the named company into the mission's own lift, or ashore where the mission starts the company on the ground. */
    private static BattleSimulation deploy(BattleSimulation sim, MarineRoster roster, int personnel) {
        Set<String> selected = roster.squads().stream().filter(s -> !s.reserve()).map(s -> s.id()).collect(Collectors.toSet());
        CampaignMarineDeployment.freezeSelection(roster, selected, personnel).applyToAuthoredPlayerLift(sim);
        return sim;
    }

    private record Result(String id, JSONObject summary, String trace) {}

    private void run(Mission mission) throws Exception {
        assertEquals(Integer.MAX_VALUE, UnitUpdateSystem.configuredMinimumParallelUnits());
        Path root = Path.of(System.getProperty("user.dir"));
        DiskRegistries.installMapGeneration(root);
        HeadlessSvgAssets.install(root.resolve("mod"));
        HeadlessArmoryPreviewRenderer.installCatalogs(root.resolve("mod"));
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        Path output = Path.of(System.getProperty(PREFIX + ".outputDir", "build/reports/commander/" + mission.id()));
        Files.createDirectories(output);
        List<Fixture> fixtures = mission.fixtures();
        String custom = System.getProperty(PREFIX + ".fixture.path", "");
        if (!custom.isBlank()) {
            fixtures = new ArrayList<>();
            JSONArray rows = new JSONArray(Files.readString(Path.of(custom)));
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                fixtures.add(new Fixture(row.getString("id"), row.getLong("seed"), row.optInt("personnel", 24), row.optString("variant", "")));
            }
        }
        int ticks = Integer.getInteger(PREFIX + ".maxTicks", mission.defaultTicks());
        if (ticks < 1) throw new IllegalArgumentException("maxTicks must be positive");
        List<Callable<Result>> jobs = new ArrayList<>();
        for (Fixture fixture : fixtures) for (int replica = 0; replica < EvidenceFanOut.repeat(); replica++)
            jobs.add(() -> play(mission, fixture, ticks));
        List<Result> results = EvidenceFanOut.run(jobs);
        JSONArray summaries = new JSONArray();
        StringBuilder md = new StringBuilder("# ").append(mission.id()).append(" commander evidence\n\n")
                .append("Story switch `battle.story.reactiveDefence=").append(System.getProperty("battle.story.reactiveDefence", "true"))
                .append("`, ").append(EvidenceFanOut.replayWording(EvidenceFanOut.repeat())).append(", max ").append(ticks).append(" ticks.\n\n")
                .append("| fixture | end | winner | ticks | phase | losses M/A/D | beats | brawls | defender from a post | ally from a post | man orders | offered | ally man / locked pulses | inputs |\n")
                .append("|---|---|---|---:|---|---|---:|---:|---:|---:|---:|---:|---|---|\n");
        for (int index = 0; index < results.size(); index += EvidenceFanOut.repeat()) {
            Result first = results.get(index);
            List<String> traces = new ArrayList<>();
            for (int r = 0; r < EvidenceFanOut.repeat(); r++) traces.add(results.get(index + r).trace());
            EvidenceFanOut.assertReplaysByteStable(first.id(), traces);
            Path report = output.resolve(first.id());
            Files.createDirectories(report);
            Files.writeString(report.resolve("summary.json"), first.summary().toString(2));
            Files.writeString(report.resolve("trace.jsonl"), first.trace());
            summaries.put(first.summary());
            md.append(row(first.summary())).append('\n');
        }
        Files.writeString(output.resolve("summary.json"), summaries.toString(2));
        Files.writeString(output.resolve("summary.md"), md.toString());
        System.out.println("[" + mission.id() + "-evidence] " + output.resolve("summary.json"));
        System.out.println(md);
    }

    private static Result play(Mission mission, Fixture fixture, int maxTicks) throws Exception {
        Story story = mission.story();
        try (BattleSimulation sim = story.build(fixture)) {
            sim.setCommandTraceEnabled(true, mission.id().toUpperCase(Locale.ROOT).replace('-', '_'));
            PlaceFightProbe places = new PlaceFightProbe();
            JSONArray phases = new JSONArray(), inputs = new JSONArray();
            Map<String, Integer> done = new HashMap<>();
            Enum<?> phase = story.phase(sim);
            phases.put(new JSONObject().put("tick", 0).put("phase", phase.name()));
            int firstLanding = -1;
            while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                story.beforeAdvance(sim, fixture, done, inputs);
                sim.advance(BattleSimulation.TICK_DT);
                if (firstLanding < 0 && count(sim, Faction.MARINE) > 0) firstLanding = sim.getSimTickIndex();
                Enum<?> now = story.phase(sim);
                if (now != phase) {
                    phase = now;
                    phases.put(new JSONObject().put("tick", sim.getSimTickIndex()).put("phase", phase.name()));
                }
                places.sample(sim);
            }
            if (!sim.isComplete()) sim.recordCommandTraceTimeout(maxTicks);
            String trace = sim.getCommandTraceJsonLines();
            String runId = mission.id() + "/" + fixture.id();
            System.out.println(places.line(runId, sim, trace));
            System.out.println(ReactiveDefenceEvidence.line(runId, sim, trace));
            JSONObject place = places.json(sim, trace);
            JSONObject summary = new JSONObject().put("fixture", fixture.id()).put("mission", mission.id())
                    .put("definition", fixture.json())
                    .put("reactiveDefenceEnabled", !"false".equalsIgnoreCase(System.getProperty("battle.story.reactiveDefence", "true")))
                    .put("termination", sim.isComplete() ? "COMPLETE" : "TIMEOUT")
                    .put("winner", sim.getWinner() == null ? JSONObject.NULL : sim.getWinner().name())
                    .put("ticks", sim.getSimTickIndex()).put("firstPlayerLandingTick", firstLanding)
                    .put("finalPhase", phase.name()).put("receipt", String.valueOf(story.receipt(sim)))
                    .put("remaining", new JSONObject().put("MARINE", count(sim, Faction.MARINE))
                            .put("ALLY", count(sim, Faction.ALLY)).put("DEFENDER", count(sim, Faction.DEFENDER)))
                    .put("losses", place.optJSONObject("losses"))
                    .put("phases", phases).put("playerInputs", inputs)
                    .put("storyBeats", StoryBeatEvidence.snapshot(sim))
                    .put("placeFight", place).put("reactiveDefence", ReactiveDefenceEvidence.figures(sim));
            return new Result(fixture.id(), summary, trace);
        }
    }

    private static String row(JSONObject s) throws JSONException {
        JSONObject losses = s.optJSONObject("losses");
        JSONObject mix = s.getJSONObject("placeFight").getJSONObject("fightMix");
        JSONObject all = mix.optJSONObject("ALL");
        JSONObject defender = mix.optJSONObject("DEFENDER");
        JSONObject allied = mix.optJSONObject("ALLY");
        JSONObject figures = s.getJSONObject("reactiveDefence");
        JSONObject ally = figures.optJSONObject("ally");
        return "| " + s.getString("fixture") + " | " + s.getString("termination") + " | " + s.opt("winner") + " | " + s.getInt("ticks")
                + " | " + s.getString("finalPhase")
                + " | " + (losses == null ? 0 : losses.optInt("MARINE")) + "/" + (losses == null ? 0 : losses.optInt("ALLY"))
                + "/" + (losses == null ? 0 : losses.optInt("DEFENDER"))
                + " | " + s.getJSONArray("storyBeats").length()
                + " | " + share(all, "openOpen") + " | " + share(defender, "postAtOpen", "postPost")
                + " | " + share(allied, "postAtOpen", "postPost")
                + " | " + (figures.has("manOrders") ? figures.getInt("manOrders") : "-")
                + " | " + (figures.has("offeredSquadPulses") ? figures.getInt("offeredSquadPulses") : "-")
                + " | " + (ally == null ? "-" : ally.optInt("manOrders") + " / " + ally.optInt("lockedSquadPulses"))
                + " | " + s.getJSONArray("playerInputs").length() + " |";
    }

    private static String share(JSONObject row, String... keys) throws JSONException {
        if (row == null || row.optLong("contacts") == 0) return "-";
        long part = 0;
        for (String key : keys) part += row.optLong(key);
        return Math.round(100.0 * part / row.getLong("contacts")) + "%";
    }

    private static int count(BattleSimulation sim, Faction faction) {
        int count = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            if (sim.identity().faction(id) == faction && sim.identity().type(id).combatant) count++;
        }
        return count;
    }
}

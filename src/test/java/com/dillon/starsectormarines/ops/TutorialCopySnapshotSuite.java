package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.OfficerInduction;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.ops.UiSnapshotSuite.UiRoom;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.json.JSONObject;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Opt-in: every line Castle says as a tutorial, each on its room, for the owner reading the wording. A callout the
 * flow suites already reach comes from them; the rest are mounted over the room through the production callout and
 * spotlight, with the targets and milestones their lessons use. 1920x1080, 100%.
 */
public final class TutorialCopySnapshotSuite implements SnapshotSuite {
    private static final Runnable NOOP = () -> { };

    @Override public String id() { return "tutorial-copy"; }
    @Override public String label() { return "Castle's tutorial, every line on its room"; }
    @Override public boolean inAll() { return false; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.modRoot().getParent());
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        CompanyManagementGuide.install(new JSONObject(Files.readString(context.modRoot().resolve(CompanyManagementGuide.PATH))));
        var frames = new ArrayList<SnapshotArtifact>();
        for (var frame : new BoatRecoverySnapshotSuite().render(context)) {
            if (Set.of("castle.png", "ready.png").contains(frame.relativePath().toString()))
                frames.add(new SnapshotArtifact("boat-recovery-" + frame.relativePath(), frame.image()));
        }
        try (var strings = new HeadlessStrings(context.modRoot())) {
            var renderer = new HeadlessUiRenderer(new HeadlessBattleSceneRenderer(context.modRoot()),
                    context.modRoot(), context.starsectorCore());
            frames.addAll(UiSnapshotSuite.renderOfficerInduction(context, renderer, 1f));
            frames.addAll(UiSnapshotSuite.renderCampaignGantries(context));
            for (int line = 0; line < 4; line++) {
                String state = "guide-line-" + line;
                for (var frame : UiSnapshotSuite.renderBarracksCommand(context, List.of(state), false, false)) {
                    if (frame.relativePath().toString().endsWith("1920x1080.png")) frames.add(frame);
                }
            }
            refitFrames(context, frames);
            callouts(context, renderer, frames);
            workPanels(context, renderer, frames);
            secondSquad(context, frames);
        }
        return frames;
    }

    private static void refitFrames(SnapshotContext context, List<SnapshotArtifact> frames) throws Exception {
        var suite = new FirstRefitSnapshotSuite();
        var roster = new MarineRoster();
        for (int i = 0; i < 2; i++) {
            var squad = roster.createSquad();
            roster.recruitToSquad(squad.id(), 12);
        }
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        var reactor = new Reactor();
        var model = new FirstRefitModel(reactor, roster, EquipmentIssueResources.UNLIMITED, NOOP);
        frames.add(suite.frame(context, reactor, model, "refit-squad.png", null));
        model.selectSquad(roster.squads().stream().filter(s -> !s.reserve()).findFirst().orElseThrow().id());
        frames.add(suite.frame(context, reactor, model, "refit-compare.png", "refit-path:STEADY_LINE"));
        frames.add(suite.frame(context, reactor, model, "refit-claim.png", null));
    }

    private static void callouts(SnapshotContext context, HeadlessUiRenderer renderer, List<SnapshotArtifact> frames)
            throws Exception {
        MarineRoster preparing = preparing();
        var hq = CompanyHqViewModel.openingPreview(preparing);
        String home = OfficerInductionGuide.line(OfficerInduction.Step.SHIP, "Morgan Hale").text();
        String fireDepot = Strings.format("openingCompanyPreparationGuideNothingWasSpentByLeavingCommanderOur", "Jangala");
        String hermesDepot = Strings.format("openingCompanyPreparationGuideTheHermesPartsAreStillWaitingAt", "Jangala");
        String conversion = Strings.format("openingCerberusCompanyStartFirstWeNeedMercenaryCompanyConversionOur",
                Strings.get("openingCerberusCompanyStartIHaveKeptThatPaymentSeparateFrom"), "Jangala");
        String installed = Strings.format("openingCerberusCompanyStartTheConversionIsInstalledStayDockedAt", "Jangala");

        record Frame(String file, UiRoom room, PreparationGuide.Lesson lesson) { }
        List<Frame> list = new ArrayList<>();
        list.add(new Frame("hq-conversion-required.png", UiRoom.HQ, prep(0, "openingCompanyPreparationGuideMercenaryCompanyConversion",
                conversion, List.of("page-nav-return"), null, 310)));
        list.add(new Frame("hq-conversion-installed.png", UiRoom.HQ, prep(0, "openingCompanyPreparationGuideOurCompanyCommandVessel",
                installed, List.of("company-hq-ship"), "openingCompanyPreparationGuideChooseShip", 310)));
        list.add(new Frame("hq-home.png", UiRoom.HQ, prep(0, "openingCompanyPreparationGuideAHomeForTheCompany", home,
                List.of("company-hq-ship"), "openingCompanyPreparationGuideChooseShip", 310)));
        list.add(new Frame("hq-preparing.png", UiRoom.HQ, prep(1, "openingCompanyPreparationGuidePreparingOurShip",
                Strings.get("openingCompanyPreparationGuideTheCrewIsPreparingOurQuartersAnd"), List.of("page-nav-barracks"), null, 310)));
        list.add(new Frame("hq-ready.png", UiRoom.HQ, prep(1, "openingCompanyPreparationGuideLetSGetTheCompanyReady",
                Strings.get("openingCompanyPreparationGuideOurLiftIsAboardComeToBarracks"), List.of("page-nav-barracks"),
                "openingCompanyPreparationGuideMeetSabine", 310)));
        list.add(new Frame("hq-stores-squad.png", UiRoom.HQ, prep(1, "openingCompanyPreparationGuideOurStoresAreStillReserved",
                fireDepot, List.of("page-nav-return"), "openingCompanyPreparationGuideReturnToDepot", 320)));
        list.add(new Frame("hq-stores-hermes.png", UiRoom.HQ, prep(1, "openingCompanyPreparationGuideOurStoresAreStillReserved",
                hermesDepot, List.of("page-nav-return"), "openingCompanyPreparationGuideReturnToDepot", 320)));
        list.add(new Frame("armory-one-step.png", UiRoom.ARMORY, prep(1, "openingCompanyPreparationGuideOneStepAtATime",
                Strings.get("openingCompanyPreparationGuideWeWillMusterTheSquadOnceOur"), List.of(),
                "openingCompanyPreparationGuideContinue", 310)));
        list.add(new Frame("armory-muster.png", UiRoom.ARMORY, prep(2, "openingCompanyPreparationGuideMusterOurFirstSquad",
                Strings.get("openingCompanyPreparationGuideTwelveMarinesThirtySixSuppliesAndTwenty"), List.of("page-nav-barracks"),
                "openingCompanyPreparationGuideBarracks", 310)));
        list.add(new Frame("armory-officer.png", UiRoom.ARMORY, prep(3, "openingCompanyPreparationGuideAFormationNeedsItsOfficer",
                Strings.get("openingCompanyPreparationGuideTheSquadAndItsFieldKitAre"), List.of("page-nav-barracks"),
                "openingCompanyPreparationGuideBarracks", 310)));
        list.add(new Frame("boats-one-step.png", UiRoom.BOATS, prep(1, "openingCompanyPreparationGuideOneStepAtATime",
                Strings.get("openingCompanyPreparationGuideTheBoatsCanWait"), List.of("page-nav-barracks"),
                "openingCompanyPreparationGuideMeetSabine", 310)));
        // First lift, as CampaignLessons.firstLift joins its sentences.
        String stores = Strings.get("openingCompanyPreparationGuideOurPrepaidStoresAreAvailableWhileDocked");
        String wreck = Strings.get("openingCompanyPreparationGuideSecondWindCameWithTwoWreckedHermes");
        String empty = Strings.get("openingOfficerInductionGuideOurPeopleNeedAWayDownAnd");
        String select = Strings.get("openingCompanyPreparationGuideSelectTheFirstBerthOnTheRight");
        String confirm = Strings.get("openingCompanyPreparationGuideCheckTheMaterialBillThenConfirmThe");
        list.add(new Frame("first-lift-repair-select.png", UiRoom.BOATS, prep(1, "openingCompanyPreparationGuideRepairOurFirstHermes",
                wreck + select + stores, List.of("boat-list"), null, 280)));
        list.add(new Frame("first-lift-repair-confirm.png", UiRoom.BOATS, prep(1, "openingCompanyPreparationGuideRepairOurFirstHermes",
                wreck + confirm + stores, List.of("boat-list"), null, 280)));
        list.add(new Frame("first-lift-commission-select.png", UiRoom.BOATS, prep(1, "openingCompanyPreparationGuideCommissionOurFirstShuttle",
                empty + select + stores, List.of("boat-list"), null, 280)));
        list.add(new Frame("first-lift-commission-confirm.png", UiRoom.BOATS, prep(1, "openingCompanyPreparationGuideCommissionOurFirstShuttle",
                empty + confirm + stores, List.of("boat-list"), null, 280)));
        // The handoffs, from Headquarters.
        list.add(new Frame("hq-handoff-boat-recovery.png", UiRoom.HQ, CampaignLessons.lesson(1, "boatLessonTitle", "boatLessonHandoff",
                List.of("page-nav-boats"), "boatLessonVisit", NOOP)));
        list.add(new Frame("hq-handoff-first-lift.png", UiRoom.HQ, CampaignLessons.lesson(1, "lessonFirstLiftTitle", "lessonFirstLiftHandoff",
                List.of("page-nav-boats"), "boatLessonVisit", NOOP)));
        list.add(new Frame("barracks-handoff-refit.png", UiRoom.BARRACKS, CampaignLessons.lesson(0, "lessonRefitTitle", "lessonRefitHandoff",
                List.of("barracks-equipment"), "lessonEquipment", NOOP)));
        list.add(new Frame("hq-handoff-gantry.png", UiRoom.HQ, CampaignLessons.lesson(1, "lessonGantryTitle", "lessonGantryHandoff",
                List.of("page-nav-mech-lab"), "lessonMechLab", NOOP)));
        list.add(new Frame("hq-investments.png", UiRoom.HQ, CampaignLessons.lesson(2, "lessonInvestmentsTitle", "lessonInvestmentsCopy",
                List.of("page-nav-boats", "page-nav-engineering"), "lessonContinue", NOOP)));
        list.add(new Frame("hq-handoff-formation.png", UiRoom.HQ, later(CampaignLessons.lesson(3, "lessonFormationTitle", "lessonFormationHandoff",
                List.of("page-nav-barracks"), "lessonBarracks", NOOP))));
        // Boat recovery past the two frames its own suite draws.
        list.add(new Frame("boats-recovery-gone.png", UiRoom.BOATS, CampaignLessons.lesson(1, "boatLessonTitle", "boatLessonGone",
                List.of("boat-list"), "lessonContinue", NOOP)));
        // Two formations, in Barracks.
        List<String> board = List.of("barracks-command-list", "barracks-formation-board", "barracks-fill-selected", "barracks-missions");
        list.add(new Frame("barracks-formation-found.png", UiRoom.BARRACKS, later(CampaignLessons.lesson(3, "lessonFormationTitle",
                "lessonFormationFound", List.of("barracks-raise-squad"), null, null))));
        list.add(new Frame("barracks-formation-assign.png", UiRoom.BARRACKS, later(CampaignLessons.lesson(3, "lessonFormationTitle",
                "lessonFormationAssign", board, "lessonBriefing", NOOP))));
        list.add(new Frame("barracks-formation-recover.png", UiRoom.BARRACKS, later(CampaignLessons.lesson(3, "lessonFormationTitle",
                "lessonFormationRecover", board, "lessonHeadquarters", NOOP))));
        list.add(new Frame("barracks-formation-wounded.png", UiRoom.BARRACKS, later(CampaignLessons.lesson(3, "lessonFormationTitle",
                "lessonFormationWounded", List.of("barracks-command-list", "barracks-formation-board", "barracks-missions"),
                "lessonHeadquarters", NOOP))));
        for (Frame frame : list) {
            frames.add(new SnapshotArtifact(frame.file(), UiSnapshotSuite.renderRoomWithLesson(context, renderer,
                    frame.room(), hq, null, frame::lesson)));
        }
    }

    private static PreparationGuide.Lesson prep(int milestone, String titleKey, String copy, List<String> targets,
                                                String actionKey, int height) {
        return new PreparationGuide.Lesson(milestone, Strings.get(titleKey), copy, targets,
                actionKey == null ? null : Strings.get(actionKey), actionKey == null ? null : NOOP, height);
    }

    private static PreparationGuide.Lesson later(PreparationGuide.Lesson lesson) {
        return new PreparationGuide.Lesson(lesson.milestone(), lesson.title(), lesson.copy(), lesson.targets(),
                lesson.actionLabel(), lesson.action(), lesson.height(), NOOP);
    }

    private static MarineRoster preparing() {
        MarineRoster roster = new MarineRoster();
        MarineCaptain morgan = new MarineCaptain("Morgan Hale", "graphics/portraits/sabine-hale.png", Rank.LIEUTENANT, 0f);
        roster.add(morgan);
        roster.beginCompanyPreparation(morgan);
        return roster;
    }

    /** Headquarters' contract panel, which also speaks for Castle, in each state it has its own line. */
    private static void workPanels(SnapshotContext context, HeadlessUiRenderer renderer, List<SnapshotArtifact> frames)
            throws Exception {
        MarineRoster fresh = new MarineRoster();
        frames.add(panel(context, renderer, fresh, "hq-panel-fresh.png"));
        MarineRoster squad = new MarineRoster();
        squad.bootstrapInitialComplement(12);
        frames.add(panel(context, renderer, squad, "hq-panel-first-squad.png"));
        squad.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        frames.add(panel(context, renderer, squad, "hq-panel-relief-won.png"));
        frames.add(panel(context, renderer, preparing(), "hq-panel-preparing.png"));
    }

    private static SnapshotArtifact panel(SnapshotContext context, HeadlessUiRenderer renderer, MarineRoster roster,
                                          String file) throws Exception {
        return new SnapshotArtifact(file, UiSnapshotSuite.renderCompanyHq(context, renderer, 1920, 1080, 1f,
                CompanyHqViewModel.openingPreview(roster)));
    }

    /** The second-squad briefing lesson through its production copy: found, fill, or nothing needed. */
    private static void secondSquad(SnapshotContext context, List<SnapshotArtifact> frames)
            throws Exception {
        var mission = SecondSquadPreparationTest.junction();
        var renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                new HeadlessBattleSceneRenderer(context.modRoot(), true));
        var props = CastleCampaignSnapshotSuite.junctionProps(mission);
        props.put("commandSummary", "0 / 5 slots");
        props.put("powerRows", List.of());
        props.put("deployLamps", List.of());
        props.put("replenishLabel", "");
        props.put("replenishClasses", "briefing-assign-hidden");
        props.put("replenishDisabled", false);
        var stores = UiSnapshotSuite.snapshotSquadFoundingResources();

        MarineRoster one = new MarineRoster();
        var first = one.createSquad();
        one.recruitToSquad(first.id(), 12);
        frames.add(new SnapshotArtifact("briefing-second-found.png", UiSnapshotSuite.renderRoomWithLesson(context, renderer,
                UiRoom.BRIEFING, null, props, () -> SecondSquadPreparation.lesson(mission, one, stores, true, NOOP))));

        MarineRoster two = new MarineRoster();
        var a = two.createSquad();
        var b = two.createSquad();
        two.recruitToSquad(a.id(), 12);
        two.recruitToSquad(b.id(), 12);
        two.applySoldierOutcome(Map.of(two.squadMembers(b).get(0).id(), MarineSoldierStatus.KIA), 0f, 0.6f);
        frames.add(new SnapshotArtifact("briefing-second-existing.png", UiSnapshotSuite.renderRoomWithLesson(context, renderer,
                UiRoom.BRIEFING, null, props, () -> SecondSquadPreparation.lesson(mission, two, stores, true, NOOP))));

        MarineRoster full = new MarineRoster();
        var c = full.createSquad();
        var d = full.createSquad();
        full.recruitToSquad(c.id(), 12);
        full.recruitToSquad(d.id(), 12);
        frames.add(new SnapshotArtifact("briefing-second-full.png", UiSnapshotSuite.renderRoomWithLesson(context, renderer,
                UiRoom.BRIEFING, null, props, () -> SecondSquadPreparation.lesson(mission, full, stores, true, NOOP))));
    }
}

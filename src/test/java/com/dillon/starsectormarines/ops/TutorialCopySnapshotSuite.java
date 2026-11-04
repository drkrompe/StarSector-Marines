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
        frames.add(suite.frame(context, reactor, model, "refit-compare.png", "refit-path:STEADY_LINE"));
        frames.add(suite.frame(context, reactor, model, "refit-claim.png", null));
    }

    private static void callouts(SnapshotContext context, HeadlessUiRenderer renderer, List<SnapshotArtifact> frames)
            throws Exception {
        MarineRoster preparing = preparing();
        var hq = CompanyHqViewModel.openingPreview(preparing);
        String home = OfficerInductionGuide.line(OfficerInduction.Step.SHIP, "Morgan Hale").text();
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
                "openingCompanyPreparationGuideMeetMorgan", 310)));
        list.add(new Frame("boats-one-step.png", UiRoom.BOATS, prep(1, "openingCompanyPreparationGuideOneStepAtATime",
                Strings.get("openingCompanyPreparationGuideTheBoatsCanWait"), List.of("page-nav-barracks"),
                "openingCompanyPreparationGuideMeetMorgan", 310)));
        // First lift, as CampaignLessons.firstLift joins its sentences.
        String wreck = Strings.get("openingCompanyPreparationGuideSecondWindCameWithTwoWreckedHermes");
        String empty = Strings.get("openingOfficerInductionGuideOurPeopleNeedAWayDownAnd");
        String select = Strings.get("openingCompanyPreparationGuideSelectTheFirstBerthOnTheRight");
        String confirm = Strings.get("openingCompanyPreparationGuideThisFirstOneCostsUsNothing");
        list.add(new Frame("first-lift-repair-select.png", UiRoom.BOATS, prep(1, "openingCompanyPreparationGuideRepairOurFirstHermes",
                wreck + select   , List.of("boat-list"), null, 280)));
        list.add(new Frame("first-lift-repair-confirm.png", UiRoom.BOATS, prep(1, "openingCompanyPreparationGuideRepairOurFirstHermes",
                wreck + confirm   , List.of("boat-list"), null, 280)));
        list.add(new Frame("first-lift-commission-select.png", UiRoom.BOATS, prep(1, "openingCompanyPreparationGuideCommissionOurFirstShuttle",
                empty + select   , List.of("boat-list"), null, 280)));
        list.add(new Frame("first-lift-commission-confirm.png", UiRoom.BOATS, prep(1, "openingCompanyPreparationGuideCommissionOurFirstShuttle",
                empty + confirm   , List.of("boat-list"), null, 280)));
        // The handoffs, from Headquarters.
        list.add(new Frame("barracks-handoff-refit.png", UiRoom.BARRACKS, CampaignLessons.lesson(0, "lessonRefitTitle", "lessonRefitHandoff",
                List.of("barracks-equipment"), "lessonEquipment", NOOP)));
        list.add(new Frame("hq-handoff-gantry.png", UiRoom.HQ, CampaignLessons.lesson(1, "lessonGantryTitle", "lessonGantryHandoff",
                List.of("page-nav-mech-lab"), "lessonMechLab", NOOP)));
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

    private static MarineRoster preparing() {
        MarineRoster roster = new MarineRoster();
        MarineCaptain morgan = new MarineCaptain("Morgan Hale", "graphics/portraits/morgan-hale.png", Rank.LIEUTENANT, 0f);
        roster.add(morgan);
        roster.beginCompanyPreparation(morgan);
        return roster;
    }

    /** Headquarters' contract panel, which also speaks for Castle, in each state it has its own line. */
    private static void workPanels(SnapshotContext context, HeadlessUiRenderer renderer, List<SnapshotArtifact> frames)
            throws Exception {
        MarineRoster fresh = new MarineRoster();
        frames.add(panel(context, renderer, fresh, "hq-panel-paid.png"));
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
}

package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.cinematic.ArrivalCinematic;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.campaign.start.CerberusCompanyStart;
import com.dillon.starsectormarines.campaign.start.CompanyPrologue;
import com.dillon.starsectormarines.campaign.start.CompanyYard;
import com.fs.starfarer.api.Global;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The escape story, before the ordinary paid preparation flow begins. */
public final class CompanyPrologueScreen extends MissionFlowMlxScreen {
    public static final String ROOT = "company-prologue";
    public static final List<String> COMPONENTS = List.of("data/ui/components/company/company-prologue.mlx");
    private static final List<String> SCENES = List.of("second-wind", "claim", "last-carrier",
            "raising-her", "departure-roster", "somewhere-to-dock", "cargo-billets", "yard-estimate");

    public CompanyPrologueScreen() { super(ROOT, COMPONENTS); }
    @Override protected List<String> requiredElementIds() {
        return List.of("prologue-root", "prologue-next", "prologue-skip", "prologue-dialogue");
    }
    @Override protected Map<String, Object> props() {
        var planet = CerberusCompanyStart.destination();
        var cargo = Global.getSector().getPlayerFleet().getCargo();
        return model(CompanyPrologue.page(Global.getSector().getPersistentData()),
                planet == null ? null : planet.getName(), planet != null && "jangala".equals(planet.getId()),
                planet != null && "jangala".equals(planet.getId()) && ArrivalCinematic.available(),
                cargo.getCrew(), (int) cargo.getCredits().get(), CompanyYard.reservedCredits(CerberusCompanyStart.startingShip()),
                this::next, this::back, this::finish);
    }
    private void next() {
        var saved = Global.getSector().getPersistentData();
        int page = CompanyPrologue.page(saved);
        if (page == CompanyPrologue.PAGE_COUNT - 1) { finish(); return; }
        CompanyPrologue.moveTo(saved, page + 1);
        rebuildDocument();
    }
    private void back() {
        var saved = Global.getSector().getPersistentData();
        CompanyPrologue.moveTo(saved, CompanyPrologue.page(saved) - 1);
        rebuildDocument();
    }
    private void finish() {
        var sector = Global.getSector();
        var destination = CerberusCompanyStart.destination();
        if (destination != null) sector.getCampaignUI().layInCourseForNextStep(destination);
        CompanyPrologue.finish(sector.getPersistentData());
        dismissDialog.run();
    }

    /** Shared production projection for live UI and visual evidence. */
    public static Map<String, Object> model(int page, String destination, boolean jangala,
                                            int crew, int credits, int reserve, Runnable next, Runnable back, Runnable skip) {
        return model(page, destination, jangala, jangala, crew, credits, reserve, next, back, skip);
    }

    /**
     * {@code arrivalAhead}: the Jangala arrival cinematic will play on docking, so Castle's Nessa
     * memory moves there (over C4) and page six only promises it.
     */
    public static Map<String, Object> model(int page, String destination, boolean jangala, boolean arrivalAhead,
                                            int crew, int credits, int reserve, Runnable next, Runnable back, Runnable skip) {
        String port = destination == null ? Strings.get("openingCompanyPrologueAWorkingPlanetarySpaceport") : destination;
        String[] titles = {Strings.get("openingCompanyPrologueSecondWind"), Strings.get("openingCompanyPrologueTheWritingOnTheWall"),
                Strings.get("openingCompanyPrologueWhatWeLeftBehind"), Strings.get("openingCompanyPrologueRaisingHer"), Strings.get("openingCompanyPrologueTheDepartureRoster"),
                Strings.get("openingCompanyPrologueSomewhereToDock"), Strings.get("openingCompanyPrologueAWayToEarn"), Strings.get("openingCompanyPrologueTheFirstStep")};
        String[] narrative = {
                Strings.get("openingCompanyPrologueTheAlarmAboveTheHatchHasFinally"),
                Strings.get("openingCompanyPrologueHarkerSClaimWasThreeWeeksHaul"),
                Strings.get("openingCompanyPrologueTheLastOreCarrierTookTheMine"),
                Strings.get("openingCompanyPrologueTheCerberusByTheLoadingLocksHad"),
                Strings.get("openingCompanyPrologueTheDepartureRosterIsInYourHandwriting"),
                jangala ? Strings.get("openingCompanyPrologueCorvusHasWorkingYardsAndRegularTraffic")
                        : Strings.get("openingCompanyPrologueCastleSearchesThePortListingsForSomewhere"),
                Strings.get("openingCompanyPrologueACargoHandSleepsBesideTheBridge"),
                Strings.get("openingCompanyPrologueCastlePutsTheYardEstimateBesideThe")
        };
        String[] dialogue = {
                Strings.get("openingCompanyPrologueYouCanLetGoOfTheChair"),
                Strings.get("openingCompanyPrologueIRanTheClaimSManifestsForNine"),
                Strings.format("openingCompanyPrologueStillHadAMonthSPayOn", (reserve > 0 ? Strings.get("openingCompanyPrologueTheRealMoneySMostlySpokenFor")
                        : Strings.get("openingCompanyPrologueIVePaidForShuttlePartsAnd"))),
                Strings.get("openingCompanyPrologueFourDaysLeftOnTheChalkWhen"),
                Strings.get("openingCompanyPrologueYouRememberTheNightTheyShutOff"),
                destination == null ? Strings.get("openingCompanyPrologueNoAnswerFromAYardWeCan")
                        : jangala ? Strings.get(arrivalAhead ? "openingCompanyPrologueAnOldFriendIOweHerMore"
                                : "openingCompanyPrologueNessaVennWeRanOreTogetherBefore")
                        : Strings.format("openingCompanyPrologueHasAWorkingYardIDonT", port),
                Strings.get("openingCompanyPrologueYouSaidWeCouldTakeContractWork"),
                Strings.format("openingCompanyPrologueThenWeGetABoatFlyingAnd", (destination == null ? Strings.get("openingCompanyPrologueWeLlFindABerthThenBunks")
                        : jangala ? Strings.get("openingCompanyPrologueNessaSentAnEstimateCalledTheJob")
                        : Strings.format("openingCompanyPrologueWeLlGetTheYardAtTo", port))
                        + (reserve > 0 ? Strings.get("openingCompanyPrologueIVeKeptThePaymentSafe")
                        : Strings.get("openingCompanyPrologueWeLlHaveToWatchThatYard")))
        };
        int index = Math.max(0, Math.min(CompanyPrologue.PAGE_COUNT - 1, page));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("title", titles[index]); props.put("narrative", narrative[index]);
        props.put("dialogue", dialogue[index]); props.put("portrait", CompanyManagementGuide.portrait());
        props.put("image", image(SCENES.get(index)));
        props.put("caption", titles[index]);
        props.put("heading", Strings.get("openingUiCompanyPrologueSecondWindPrologue"));
        props.put("note", Strings.get("openingUiCompanyPrologueAtPortMarineOperationsMercenaryCompanyConversion"));
        props.put("skipLabel", Strings.get("openingUiCompanyPrologueSkipIntroduction"));
        props.put("nextDisabled", false);
        props.put("page", (index + 1) + " / " + CompanyPrologue.PAGE_COUNT);
        props.put("manifest", Strings.format("openingCompanyPrologueCrewAboardSpendableCredits", crew, String.format(Locale.ROOT, "%,d", credits)));
        props.put("reserve", reserve > 0 ? Strings.format("openingCompanyPrologueCreditsHeldForTheConversionFirstBoat", String.format(Locale.ROOT, "%,d", reserve))
                : Strings.get("openingCompanyPrologueConversionPaymentDueFromFleetFundsPreparation"));
        props.put("destination", destination == null ? Strings.get("openingCompanyPrologueFindAWorkingPlanetarySpaceport") : Strings.format("openingCompanyPrologueReach", destination));
        props.put("nextLabel", index < CompanyPrologue.PAGE_COUNT - 1 ? Strings.get("openingCompanyPrologueContinue")
                : destination == null ? Strings.get("openingCompanyPrologueTakeCommand") : Strings.format("openingCompanyPrologueSetCourseFor", destination));
        props.put("backDisabled", index == 0);
        props.put("next", next); props.put("back", back); props.put("skip", skip);
        return props;
    }

    static String image(String scene) { return "graphics/illustrations/company/" + scene + ".jpg"; }
}

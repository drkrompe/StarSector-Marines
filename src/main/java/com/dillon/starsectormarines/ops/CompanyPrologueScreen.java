package com.dillon.starsectormarines.ops;

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
    public static final String IMAGE = "graphics/illustrations/company/second-wind.jpg";

    public CompanyPrologueScreen() { super(ROOT, COMPONENTS); }
    @Override protected List<String> requiredElementIds() {
        return List.of("prologue-root", "prologue-next", "prologue-skip", "prologue-dialogue");
    }
    @Override protected Map<String, Object> props() {
        var planet = CerberusCompanyStart.destination();
        var cargo = Global.getSector().getPlayerFleet().getCargo();
        return model(CompanyPrologue.page(Global.getSector().getPersistentData()),
                planet == null ? null : planet.getName(), planet != null && "jangala".equals(planet.getId()),
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
        String port = destination == null ? Strings.get("openingCompanyPrologueAWorkingPlanetarySpaceport") : destination;
        String[] titles = {Strings.get("openingCompanyPrologueSecondWind"), Strings.get("openingCompanyPrologueWhatWeLeftBehind"), Strings.get("openingCompanyPrologueTheDepartureRoster"),
                Strings.get("openingCompanyPrologueSomewhereToDock"), Strings.get("openingCompanyPrologueAWayToEarn"), Strings.get("openingCompanyPrologueTheFirstStep")};
        String[] narrative = {
                Strings.get("openingCompanyPrologueTheAlarmAboveTheHatchHasFinally"),
                Strings.get("openingCompanyPrologueTheLastOreCarrierTookTheMine"),
                Strings.get("openingCompanyPrologueTheDepartureRosterIsInYourHandwriting"),
                jangala ? Strings.get("openingCompanyPrologueCorvusHasWorkingYardsAndRegularTraffic")
                        : Strings.get("openingCompanyPrologueCastleSearchesThePortListingsForSomewhere"),
                Strings.get("openingCompanyPrologueACargoHandSleepsBesideTheBridge"),
                Strings.get("openingCompanyPrologueCastlePutsTheYardEstimateBesideThe")
        };
        String[] dialogue = {
                Strings.get("openingCompanyPrologueYouCanLetGoOfTheChair"),
                Strings.format("openingCompanyPrologueStillHadAMonthSPayOn", (reserve > 0 ? Strings.get("openingCompanyPrologueTheRealMoneySMostlySpokenFor")
                        : Strings.get("openingCompanyPrologueIVePaidForShuttlePartsAnd"))),
                Strings.get("openingCompanyPrologueYouRememberTheNightTheyShutOff"),
                destination == null ? Strings.get("openingCompanyPrologueNoAnswerFromAYardWeCan")
                        : jangala ? Strings.get("openingCompanyPrologueNessaVennWeRanOreTogetherBefore")
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
        props.put("image", IMAGE); props.put("page", (index + 1) + " / " + CompanyPrologue.PAGE_COUNT);
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
}

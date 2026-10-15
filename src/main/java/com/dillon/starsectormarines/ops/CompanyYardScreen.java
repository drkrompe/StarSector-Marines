package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.start.CerberusCompanyStart;
import com.dillon.starsectormarines.campaign.start.CompanyYard;
import com.dillon.starsectormarines.campaign.start.MercenaryCompanyConversion;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.CompanyYardReview.Stage;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Second Wind's initial conversion, using the prologue's document and host renderer. */
public final class CompanyYardScreen extends MissionFlowMlxScreen {
    private final FleetMemberAPI ship;
    private final boolean jangala;
    private final CompanyYardReview review = new CompanyYardReview();

    public CompanyYardScreen(FleetMemberAPI ship, boolean jangala) {
        super(CompanyPrologueScreen.ROOT, CompanyPrologueScreen.COMPONENTS);
        this.ship = ship;
        this.jangala = jangala;
    }
    @Override protected List<String> requiredElementIds() {
        return List.of("prologue-root", "prologue-next", "prologue-skip", "prologue-dialogue");
    }
    @Override protected Map<String, Object> props() {
        int credits = (int) Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        String refusal = review.stage() == Stage.COMPLETE ? null : CompanyYard.refusal(ship, CompanyYard.dockedMarket());
        return model(review.stage(), jangala, ship.getShipName(), credits, CompanyYard.reservedCredits(ship),
                refusal, review.failure(), CerberusCompanyStart.depot() != null,
                this::next, this::back, () -> dismissDialog.run());
    }
    private void next() {
        if (review.stage() == Stage.COMPLETE) { dismissDialog.run(); return; }
        review.next(() -> CompanyYard.install(ship, CompanyYard.dockedMarket()), () -> dismissDialog.run());
        rebuildDocument();
    }
    private void back() { review.back(); rebuildDocument(); }
    @Override protected void onCancel() { dismissDialog.run(); }

    static Map<String, Object> model(Stage stage, boolean jangala, String shipName, int credits, int reserved,
                                     String refusal, String failure, boolean depot,
                                     Runnable next, Runnable back, Runnable leave) {
        boolean complete = stage == Stage.COMPLETE;
        boolean order = stage == Stage.ORDER;
        int cash = MercenaryCompanyConversion.CREDIT_COST - reserved;
        var props = new LinkedHashMap<String, Object>();
        props.put("heading", Strings.get("openingYardHeading"));
        props.put("page", complete ? Strings.get("openingYardReceipt") : (order ? "2 / 2" : "1 / 2"));
        props.put("title", Strings.get(complete ? "openingYardHomeTitle" : order ? "openingYardOrderTitle" : "openingYardSurveyTitle"));
        props.put("image", CompanyPrologueScreen.image(complete ? "berths-fitted" : order ? "yard-hull" : "cargo-billets"));
        props.put("caption", Strings.get(complete ? "openingYardAfterCaption" : order ? "openingYardEstimateCaption" : "openingYardBeforeCaption"));
        props.put("narrative", Strings.get(complete ? "openingYardHomeNarrative" : order ? "openingYardOrderNarrative" : "openingYardSurveyNarrative"));
        String dialogue;
        if (complete) {
            dialogue = Strings.get("openingYardHomeDialogue");
            if (jangala) dialogue += "\n\n" + Strings.get("openingCompanyYardCMDNessaSaysSheLlKeepTheOld");
            if (depot) dialogue += "\n\n" + Strings.get("openingCompanyYardCMDOurPrepaidPreparationStoresAreInThis");
        } else if (order) {
            dialogue = Strings.get(reserved > 0 ? "openingCompanyYardCMDIVeKeptThisPaymentAsideSince" : "openingYardCashDialogue");
            if (jangala) dialogue += "\n\n" + Strings.get("openingCompanyYardCMDHerRacksAreHalfEmptyMostOf");
        } else {
            dialogue = Strings.get(jangala ? "openingCompanyYardCMDNessaVennMetUsAtTheBerth"
                    : "openingCompanyYardCMDTheYardCanMakeRoomForBunks") + "\n\n" + Strings.get("openingYardSurveyDialogue");
        }
        props.put("dialogue", dialogue);
        props.put("portrait", CompanyManagementGuide.portrait());
        props.put("manifest", Strings.get("openingYardConversionFacts"));
        props.put("reserve", complete ? Strings.get("openingYardPaid")
                : Strings.format("openingYardBill", reserved, cash, Math.max(0, credits), Math.max(0, cash - Math.max(0, credits))));
        props.put("destination", failure != null ? failure : refusal != null ? refusal
                : Strings.format("openingYardShip", shipName == null ? "Second Wind" : shipName));
        props.put("note", Strings.get(complete ? "openingYardEngineeringNote" : "openingYardReviewNote"));
        props.put("skipLabel", Strings.get("openingCompanyYardCMDReturnToPort"));
        props.put("nextLabel", Strings.get(complete ? "openingCompanyYardCMDReturnToPort"
                : order ? "openingYardInstall" : "openingYardReview"));
        props.put("nextDisabled", order && refusal != null);
        props.put("backDisabled", !order);
        props.put("next", next); props.put("back", back); props.put("skip", leave);
        return props;
    }
}

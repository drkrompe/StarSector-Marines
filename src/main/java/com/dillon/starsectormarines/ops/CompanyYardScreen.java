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

/**
 * Second Wind's founding conversion as a storybook, using the prologue's document and host renderer:
 * the bill (paid by the first page's Next), the runway it leaves, the road up and the papers that let
 * the company take it. The story is Jangala's at whichever port the start chose. A straight click-through,
 * so the footer Skip is off.
 */
public final class CompanyYardScreen extends MissionFlowMlxScreen {
    /** Story board and berth for the forty-five aboard, in hundredths of a credit a day (story only; never charged). */
    static final int STORY_DAILY_BURN_CENTS = 2134;
    private final FleetMemberAPI ship;
    private final CompanyYardReview review = new CompanyYardReview();

    public CompanyYardScreen(FleetMemberAPI ship) {
        super(CompanyPrologueScreen.ROOT, CompanyPrologueScreen.COMPONENTS);
        this.ship = ship;
    }
    @Override protected List<String> requiredElementIds() {
        return List.of("prologue-root", "prologue-next", "prologue-skip", "prologue-dialogue");
    }
    @Override protected Map<String, Object> props() {
        int credits = (int) Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        String refusal = review.stage() == Stage.COST ? CompanyYard.refusal(ship, CompanyYard.dockedMarket()) : null;
        return model(review.stage(), ship.getShipName(), credits, CompanyYard.reservedCredits(ship),
                refusal, review.failure(), CerberusCompanyStart.depot() != null,
                this::next, this::back, () -> dismissDialog.run());
    }
    private void next() {
        review.next(() -> CompanyYard.install(ship, CompanyYard.dockedMarket()), () -> dismissDialog.run());
        rebuildDocument();
    }
    private void back() { review.back(); rebuildDocument(); }
    @Override protected void onCancel() { dismissDialog.run(); }

    /** Whole days of runway that {@code credits} buys at the story's daily board and berth. */
    static int runwayDays(int credits) { return Math.max(0, credits) * 100 / STORY_DAILY_BURN_CENTS; }

    /**
     * {@code reserved}: Castle's founding reserve still unspent, which pays the bill on the first page.
     * {@code credits}: the ship's account as it stands, so after payment on the later pages.
     */
    static Map<String, Object> model(Stage stage, String shipName, int credits, int reserved,
                                     String refusal, String failure, boolean depot,
                                     Runnable next, Runnable back, Runnable leave) {
        int pages = Stage.values().length;
        boolean last = stage.ordinal() == pages - 1;
        var props = new LinkedHashMap<String, Object>();
        props.put("heading", Strings.get("openingYardHeading"));
        props.put("page", (stage.ordinal() + 1) + " / " + pages);
        props.put("portrait", CompanyManagementGuide.portrait());
        props.put("manifest", "");
        props.put("note", "");
        props.put("destination", last && depot ? Strings.get("openingYardNextStep")
                : Strings.format("openingYardShip", shipName == null ? "Second Wind" : shipName));
        switch (stage) {
            case COST -> {
                int cash = CompanyYard.cashCost(reserved);
                int available = Math.max(0, credits);
                props.put("title", Strings.get("openingYardCostTitle"));
                props.put("image", CompanyPrologueScreen.image("yard-chits"));
                props.put("caption", Strings.get("openingYardCostCaption"));
                String estimate = Strings.get("openingYardCostNarrative");
                props.put("narrative", reserved > 0 ? estimate + "\n\n" + Strings.get("openingYardCostChits") : estimate);
                props.put("dialogue", Strings.get(reserved <= 0 ? "openingYardCashDialogue"
                        : "openingYardCostDialogue"));
                props.put("manifest", Strings.get("openingYardConversionFacts"));
                props.put("reserve", reserved > 0
                        ? Strings.format("openingYardCostBillReserved", MercenaryCompanyConversion.CREDIT_COST, reserved,
                                CompanyYard.waived(reserved), cash)
                        : Strings.format("openingYardCostBillCash", MercenaryCompanyConversion.CREDIT_COST, cash,
                                available, Math.max(0, cash - available)));
                if (failure != null || refusal != null) props.put("destination", failure != null ? failure : refusal);
                props.put("note", Strings.get("openingYardCostNote"));
                props.put("nextLabel", Strings.get("openingYardCostNext"));
            }
            case RUNWAY -> {
                props.put("title", Strings.get("openingYardRunwayTitle"));
                props.put("image", CompanyPrologueScreen.image("yard-tally"));
                props.put("caption", Strings.get("openingYardRunwayCaption"));
                props.put("narrative", Strings.get("openingYardRunwayNarrative"));
                props.put("dialogue", Strings.get("openingYardRunwayDialogue"));
                props.put("reserve", Strings.format("openingYardRunwayBill", Math.max(0, credits), runwayDays(credits)));
                props.put("nextLabel", Strings.get("openingYardRunwayNext"));
            }
            case ROAD -> {
                props.put("title", Strings.get("openingYardRoadTitle"));
                props.put("image", CompanyPrologueScreen.image("yard-road"));
                props.put("caption", Strings.get("openingYardRoadCaption"));
                props.put("narrative", Strings.get("openingYardRoadNarrative"));
                props.put("dialogue", Strings.get("openingYardRoadDialogue"));
                props.put("reserve", Strings.get("openingYardRoadFacts"));
                props.put("nextLabel", Strings.get("openingYardRoadNext"));
            }
            case PAPERS -> {
                props.put("title", Strings.get("openingYardPapersTitle"));
                props.put("image", CompanyPrologueScreen.image("yard-ore-dust"));
                props.put("caption", Strings.get("openingYardPapersCaption"));
                props.put("narrative", "");
                props.put("dialogue", Strings.get("openingYardPapersDialogue"));
                props.put("reserve", Strings.get("openingYardPapersFacts"));
                props.put("nextLabel", Strings.get("openingYardPapersNext"));
            }
        }
        props.put("skipLabel", "");
        props.put("skipClasses", CompanyPrologueScreen.SKIP_OFF_CLASSES);
        props.put("nextDisabled", stage == Stage.COST && refusal != null);
        props.put("backDisabled", !CompanyYardReview.backAllowed(stage));
        props.put("backClasses", CompanyPrologueScreen.BACK_CLASSES);
        props.put("next", next); props.put("back", back); props.put("skip", leave);
        return props;
    }
}

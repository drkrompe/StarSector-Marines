package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.start.SecondWindKeep.Cause;
import com.dillon.starsectormarines.campaign.start.SecondWindKeeper;
import com.dillon.starsectormarines.campaign.start.SecondWindLoss;
import com.dillon.starsectormarines.i18n.Strings;
import com.fs.starfarer.api.Global;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Getting the Second Wind back, in place of Marine operations while she is gone: one storybook page in Castle's
 * voice, using the prologue's document. How she was lost decides the story - towed off a battlefield into a
 * yard, or turned up on the open market - and the page carries the bill and the d-mod she comes back with. Buying
 * is never refused: what the account cannot cover becomes debt at the month's end.
 */
public final class SecondWindBuyBackScreen extends MissionFlowMlxScreen {
    private final Runnable bought;

    /** @param bought runs after the purchase, before the page closes */
    public SecondWindBuyBackScreen(Runnable bought) {
        super(CompanyPrologueScreen.ROOT, CompanyPrologueScreen.COMPONENTS);
        this.bought = bought;
    }
    @Override protected List<String> requiredElementIds() {
        return List.of("prologue-root", "prologue-next", "prologue-skip", "prologue-dialogue");
    }
    @Override protected Map<String, Object> props() {
        SecondWindLoss loss = SecondWindKeeper.loss();
        int credits = (int) Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        return model(loss == null ? Cause.BATTLE : loss.cause(),
                loss == null || loss.ship() == null ? null : loss.ship().getShipName(), credits,
                loss == null ? 0 : loss.bill(), loss == null ? null : loss.dMod(), this::buy, () -> dismissDialog.run());
    }
    private void buy() {
        if (SecondWindKeeper.buyBack() && bought != null) bought.run();
        dismissDialog.run();
    }
    @Override protected void onCancel() { dismissDialog.run(); }

    static Map<String, Object> model(Cause cause, String shipName, int credits, int bill, String dMod,
                                     Runnable buy, Runnable leave) {
        String name = shipName == null || shipName.isBlank() ? "Second Wind" : shipName;
        boolean market = cause.onTheMarket();
        var props = new LinkedHashMap<String, Object>();
        props.put("heading", Strings.get("secondWindBuyBackHeading"));
        props.put("page", "");
        props.put("portrait", CompanyManagementGuide.portrait());
        props.put("image", CompanyPrologueScreen.image(market ? "yard-chits" : "yard-hull"));
        props.put("title", Strings.get(market ? "secondWindBuyBackMarketTitle" : "secondWindBuyBackBattleTitle"));
        props.put("caption", Strings.get(market ? "secondWindBuyBackMarketCaption" : "secondWindBuyBackBattleCaption"));
        props.put("narrative", Strings.format(switch (cause) {
            case BATTLE -> "secondWindBuyBackBattleNarrative";
            case SOLD -> "secondWindBuyBackSoldNarrative";
            case SCUTTLED -> "secondWindBuyBackScuttledNarrative";
            case OTHER -> "secondWindBuyBackOtherNarrative";
        }, name));
        props.put("dialogue", Strings.format(market ? "secondWindBuyBackMarketDialogue" : "secondWindBuyBackBattleDialogue",
                name));
        props.put("manifest", dMod == null ? Strings.get("secondWindBuyBackNoDMod")
                : Strings.format("secondWindBuyBackDMod", dMod));
        int after = credits - bill;
        props.put("reserve", Strings.format(after >= 0 ? "secondWindBuyBackBill" : "secondWindBuyBackBillDebt",
                credits(bill), credits(Math.max(0, credits)), credits(Math.abs(after))));
        props.put("destination", Strings.get("secondWindBuyBackDestination"));
        props.put("note", Strings.get("secondWindBuyBackNote"));
        props.put("nextLabel", Strings.format("secondWindBuyBackBuy", credits(bill)));
        props.put("skipLabel", Strings.get("secondWindBuyBackLeave"));
        props.put("skipClasses", CompanyPrologueScreen.SKIP_CLASSES);
        props.put("nextDisabled", false);
        props.put("backDisabled", true);
        props.put("backClasses", CompanyPrologueScreen.BACK_OFF_CLASSES);
        props.put("next", buy); props.put("back", (Runnable) () -> { }); props.put("skip", leave);
        return props;
    }

    private static String credits(int amount) { return String.format(Strings.locale(), "%,d", amount); }
}

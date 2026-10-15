package com.dillon.starsectormarines.campaign.polity;

import com.dillon.starsectormarines.battle.world.gen.MarketGarrison;

/**
 * Moves a polity market's garrison estimate by the polity's ground doctrine, so the doctrine's points act on the same
 * dials every market's garrison has rather than through a formula of their own ({@code market-garrison-model.md}).
 *
 * <ul>
 *   <li><b>Quality</b> raises the elite share.</li>
 *   <li><b>Heavy support</b> raises the heavy lean, so the polity keeps more of the mechs and emplacements an
 *       operation offers it.</li>
 *   <li><b>Numbers</b> is not applied here: it already multiplies the allied garrison's headcount
 *       ({@link PolityDoctrine#numbersMultiplier()}), and applying it twice would count it twice.</li>
 * </ul>
 */
public final class PolityGarrisonDoctrine {

    /** Elite share gained per quality point. UNTUNED: two points move a size 5 colony from a third to over half. */
    public static final float QUALITY_STEP = 0.1f;

    /** Heavy lean gained per heavy-support point. UNTUNED: two points take a market with no works to full mechs. */
    public static final float HEAVY_STEP = 0.1f;

    private PolityGarrisonDoctrine() {}

    /** The estimate under this doctrine; {@link MarketGarrison#NONE} and a doctrine of nothing spent pass through. */
    public static MarketGarrison apply(MarketGarrison garrison, PolityDoctrine doctrine) {
        if (garrison == null || garrison.isNone() || doctrine == null) return garrison;
        if (doctrine.quality() == 0 && doctrine.heavySupport() == 0) return garrison;
        return new MarketGarrison(garrison.strength(),
                garrison.quality() + QUALITY_STEP * doctrine.quality(),
                garrison.heavy() + HEAVY_STEP * doctrine.heavySupport(),
                garrison.readiness(),
                garrison.whole());
    }
}

/**
 * Feature domain — the player polity's own ground doctrine.
 *
 * <p>Category: feature domain (campaign tier).
 * <br>Charter:  {@code polity-ground-doctrine.md}. Grades the colonies' production
 *           capacity on a four-step ladder
 *           ({@link com.dillon.starsectormarines.campaign.polity.GroundProductionQuality}),
 *           holds the three zero-sum doctrine points
 *           ({@link com.dillon.starsectormarines.campaign.polity.PolityDoctrine}),
 *           derives the player faction's
 *           {@code GroundRosterProfile} from those plus the equipment template
 *           cards the company has released
 *           ({@link com.dillon.starsectormarines.campaign.polity.PolityRosterDerivation}),
 *           and owns the two accessors that read those inputs off
 *           {@code CampaignState}
 *           ({@link com.dillon.starsectormarines.campaign.polity.ReleasedKit},
 *           {@link com.dillon.starsectormarines.campaign.polity.PolityDoctrineLedger}).
 * <br>Boundary: the model is a pure function of its arguments. The ladder, the
 *           doctrine value, and the derivation read no campaign state, no
 *           market, and no registry, and one live read is isolated behind
 *           {@link com.dillon.starsectormarines.campaign.polity.ProductionSignals}
 *           so its pure counterpart
 *           ({@link com.dillon.starsectormarines.campaign.polity.MarketProductionSignals})
 *           is what everything else measures. Nothing here installs the derived
 *           profile or runs on a clock: {@code PolityRosterSystem} owns the
 *           daily rebuild and the one write into the roster registry.
 *
 * <p>The laws this package must not break are in
 * {@code polity-ground-doctrine.md}: a release grants a definition and never
 * stock (law 2); grade comes from production quality and nothing else, so
 * doctrine may tighten a table but never admit a grade the industry cannot make
 * (law 3); experience comes from issued armour, so there is no training axis
 * (law 4).
 */
package com.dillon.starsectormarines.campaign.polity;

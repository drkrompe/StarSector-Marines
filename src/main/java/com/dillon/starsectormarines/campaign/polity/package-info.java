/**
 * Feature domain — the player polity's own ground doctrine.
 *
 * <p>Category: feature domain (campaign tier).
 * <br>Charter:  the pure half of {@code polity-ground-doctrine.md}. Reads the
 *           colonies' production capacity as a four-step ladder
 *           ({@link com.dillon.starsectormarines.campaign.polity.GroundProductionQuality}),
 *           holds the three zero-sum doctrine points
 *           ({@link com.dillon.starsectormarines.campaign.polity.PolityDoctrine}),
 *           and derives the player faction's
 *           {@code GroundRosterProfile} from those plus the equipment template
 *           cards the company has released
 *           ({@link com.dillon.starsectormarines.campaign.polity.PolityRosterDerivation}).
 * <br>Boundary: everything here is a pure function of its arguments. No
 *           Starsector campaign state, no market reads, no registry
 *           installation, no persistence. The adapter that reads a market's
 *           industries and deficits, the state that persists released cards and
 *           doctrine points, and the system that registers the derived profile
 *           each live with their own tier and call in here.
 *
 * <p>The laws this package must not break are in
 * {@code polity-ground-doctrine.md}: a release grants a definition and never
 * stock (law 2); grade comes from production quality and nothing else, so
 * doctrine may tighten a table but never admit a grade the industry cannot make
 * (law 3); experience comes from issued armour, so there is no training axis
 * (law 4).
 */
package com.dillon.starsectormarines.campaign.polity;

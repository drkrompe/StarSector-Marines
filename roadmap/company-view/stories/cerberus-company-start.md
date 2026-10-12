# Cerberus company start

Status: IMPLEMENTED; live acceptance open
Written: 2026-10-12

## Outcome

An explicit new-game choice puts the player in command of an unconverted Cerberus.
Castle directs the player to a populated planet with a working spaceport. There the
player reviews and pays for a permanent Mercenary Company Conversion hullmod before
the ship can be designated as the company command vessel. Designation resumes the
existing paid-lift, Mira specialty, squad founding and assignment tutorial, ending
at the first Jangala contract.

## Scope and authority

`company-view-nouns.md` owns preparation; `ship-interiors-nouns.md` owns the
conversion's spatial program. The vanilla new-game rules and character creation
API own start selection. Existing starts and saves remain unchanged.

The conversion trades 25 cargo capacity for 18 additional personnel places,
expanded barracks, company command eligibility and a compact single-gantry Mech Lab.
The Cerberus retains one reusable Hermes boat berth; extra beds do not mint boats.
It remains a frigate and combat freighter. Installation is a reviewed planetary
yard order costing 5,000 credits, not automatic designation or a free space refit.
It installs a permanent hullmod on the actual owned variant; duplicate orders are
refused without charging. No new boat or mech is awarded by the conversion.

Starting logistics must cover the conversion and the existing preparation recipes
without overloading the fleet or requiring a shopping detour. Travel supplies are
separate from the workshop bill. The player chose one Cerberus and a staged planetary
consignment, with no support freighter. The start supplies 15,000 credits, minimum
crew plus five, 25 travel supplies and up to 40 fuel. The first successful conversion
of this start's ship establishes a finite depot at that planet: 30 metals, 8 heavy
machinery, 56 supplies, 12 Marines and 24 food, derived from the real Hermes and
squad recipes. Workshop orders draw depot stock first, then cargo. The depot is
available only during interaction with its accessible working planetary port;
leaving, visiting another market, reloading, or installing another conversion
cannot replenish it. Ordinary starts receive no depot.

## Acceptance

- Additive selectable start, normal/easy choice, actual Cerberus flagship and player
  captain, no automatic company designation or preinstalled conversion.
- Begin in space with a reachable planetary destination and Castle's portrait guidance.
- Refuse installation in space, on stations, at inaccessible or nonworking ports,
  on incompatible/mothballed/unowned hulls, after installation, and without funds.
- Failed orders change neither credits nor variant. Successful work persists and
  is visible to actual ship eligibility, deck generation and all room routes.
- Full room/berth evidence on a real Cerberus silhouette; unconverted control stays
  ineligible; compact Mech Lab cannot advertise absent gantries.
- Focused transaction/start-policy tests, retained UI evidence, package boundary.
- Live character creation, travel, installation and save/reload acceptance remain
  explicit if not exercised in the game. No live installation/deployment is implied.

## Implementation and evidence

The additive new-game option offers Normal/Easy and keeps vanilla's skip-tutorial
initialization before applying this start. Castle's Intel marker and plotted course
point to Jangala (or an accessible planetary fallback). The native planet menu owns
the reviewed permanent yard order. Castle's existing portrait popover and interactive
highlights resume the company-room sequence after designation.

The conversion is saved on the actual variant and included in deck cache identity.
The resolver uses the hull spec's ship-file path: vanilla Cerberus is stored in
`warhound.ship`, so assuming a filename from hull ID would silently lose its shape.
The compact workshop uses a full-depth single rank and advertises its physical
number of gantries. Existing stored assets remain owned after moving to smaller
quarters.

`cerberus-start` renders an unconverted control and five converted campaign seeds
using the installed collision polygon, requiring every programmed room, sufficient
squad berthing, exactly one machine gantry and exactly one boat berth. Focused tests
cover the real preparation orders, depot serialization and exhaustion, partial
cargo draws, failed bills, port-only availability, one-time installation/depot issue,
and stale Mech Lab fabrication after a capacity change.

Validation: 45 focused tests passed, the shaded mod jar built, six Cerberus deck
frames and 75 existing Barracks/preparation frames rendered successfully.

Still open: live character creation, travel, planet-menu ordering, native hullmod
persistence and campaign save/reload acceptance. New Castle lines are text with her
portrait; narration has not been generated. No live game deployment is part of this
change.

# Cerberus company start

Status: IMPLEMENTED; live acceptance open
Written: 2026-10-12
Updated: 2026-10-20 — The founding yard is a four-page storybook (bill, runway, road up, ore dust); start funds 473, estimate 4,986, Castle's reserve 4,862 with the pressure test waived. The first Hermes waits for the first-lift lesson after Relief. Live acceptance remains.

## Outcome

An explicit new-game choice puts the player in command of an unconverted Cerberus.
The illustrated Second Wind prologue introduces the escape from the mining asteroid Harker's Claim, the
crew's trust in their captain, and Castle's practical plan to fund a new company.
Castle directs the player to Jangala and Nessa Venn, an old freight colleague now
working as a yard forewoman, or an accessible fallback port. There the
player reviews and pays from Castle's protected reserve for a permanent Mercenary Company Conversion hullmod before
the ship can be designated as the company command vessel. Designation resumes the
existing Sabine specialty, squad founding and assignment tutorial, ending at the
first Jangala contract. The prepaid Hermes waits for the first-lift lesson after Relief.

## Scope and authority

`company-view-nouns.md` owns preparation; `ship-interiors-nouns.md` owns the
conversion's spatial program. The vanilla new-game rules and character creation
API own start selection. Existing starts and saves remain unchanged.

The conversion trades 25 cargo capacity for 18 additional personnel places,
expanded barracks, company command eligibility and a compact single-gantry Mech Lab.
The original Second Wind hull instead reserves four berths; `ship-condition-survey.md`
owns its first-berth recovery and three paid repairs.
The Cerberus begins with one working Hermes berth and a reserved damaged second bay;
`cerberus-bay-restoration.md` owns its later paid repair. Extra beds do not mint boats.
It remains a frigate and combat freighter. Installation is a reviewed planetary
yard order costing 4,986 credits, not automatic designation or a free space refit.
It installs a permanent hullmod on the actual owned variant; duplicate orders are
refused without charging. No new boat or mech is awarded by the conversion.

Starting logistics must cover the conversion and the existing preparation recipes
without overloading the fleet or requiring a shopping detour. Travel supplies are
separate from the workshop bill. The player chose one Cerberus and a staged planetary
consignment, with no support freighter. The start supplies 473 spendable credits,
a separate 4,862-credit reserve usable only for the original ship's conversion (the
yard waives the estimate's 124-credit pressure test when it pays), minimum
crew plus five, 25 travel supplies and up to 25 fuel (the opening's work is all in Corvus, where
travel burns none). Nothing aboard can buy a second squad before Relief, which is why the story
Relief carries no squad cap: the depot founds Sabine's squad only, any other pays the full founding
bill from the hold, and selling every crewman, all the fuel and both guns at the most favourable
vanilla prices still falls short (`SecondSquadAffordabilityTest`). The first successful conversion
of this start's ship establishes a finite depot at that planet: 30 metals, 8 heavy
machinery, 56 supplies, 12 Marines and 24 food, derived from the real Hermes and
squad recipes. The boat and squad bills are earmarked separately; unrelated workshop
orders cannot consume either allocation. Eligible orders draw their depot stock
first, then cargo. Existing campaigns retain their funds and depot policy without
new grants or deductions. The depot is
available only during interaction with its accessible working planetary port;
leaving, visiting another market, reloading, or installing another conversion
cannot replenish it. Ordinary starts receive no depot.

## Acceptance

- Required conversion is named explicitly as Mercenary Company Conversion, a
  4,986-credit planetary yard order. Marine operations opens its review directly
  while docked before conversion; vanilla Refit is not the installation route.
- Opening preparation and Sabine induction have no Later or Escape dismissal.
  Return to campaign remains available for travel or resupply, with the required
  lesson returning on re-entry. Company designation cannot bypass the conversion.
- Leave before confirming squad founding, reopen through both the company shortcut
  and the planet menu, and save/reload mid-step. The same stock and incomplete
  order survive. Away from the depot, Castle names its planet and offers a course
  back; returning restores access without issuing another grant. Repeat for the
  Hermes repair in the first-lift lesson after Relief. Mission-board Barracks and other direct room entrances resume the
  current required step instead of bypassing it; battle settlement remains unaffected.
- Additive selectable start, normal/easy choice, actual Cerberus flagship and player
  captain, no automatic company designation or preinstalled conversion.
- Begin in space with a reachable planetary destination and Castle's portrait guidance.
- A fresh selection opens the illustrated prologue once, when the campaign map is
  available; other dialogs and menus are never replaced. Back/Continue preserve
  progress, while Skip and the final course action finish it permanently. A load
  resumes an opted-in unfinished prologue, but never opts in an established save.
- Final course, objective text and Intel marker share the accessible destination;
  Jangala-specific contact and Corvus copy are absent for fallback ports. No port
  leaves a readable spaceport requirement and an exit instead of a dead button.
- Castle's memories and the crew's belongings suggest their history; navigation
  instructions stay in the UI and Intel. Nessa's personal yard dialogue belongs
  only to the original Second Wind conversion on Jangala.
- Review all eight production pages and the four founding-yard storybook pages
  (`opening-dialogue.md` §2) at 1920x1080 and 100% scale through the
  `company-prologue` snapshot suite. Page 1's Next is the only install; the pages
  have no exit control, and Back is off on pages 1 and 2. Live host dismissal and
  save/load remain open.
- Refuse installation in space, on stations, at inaccessible or nonworking ports,
  on incompatible/mothballed/unowned hulls, after installation, and without funds.
- Failed orders change neither credits nor variant. Successful work persists and
  is visible to actual ship eligibility, deck generation and all room routes.
- A new start can convert with zero fleet credits and complete the real squad
  founding, and after Relief the first Hermes repair, with empty cargo. Unrelated orders cannot spend
  the protected stock, and neither reserve refills after consumption or reload.
- Full room/berth evidence on a real Cerberus silhouette; unconverted control stays
  ineligible; compact Mech Lab cannot advertise absent gantries.
- Focused transaction/start-policy tests, retained UI evidence, package boundary.
- Live character creation, travel, installation and save/reload acceptance remain
  explicit if not exercised in the game. No live installation/deployment is implied.

## Implementation and evidence

The additive new-game option offers Normal/Easy and keeps vanilla's skip-tutorial
initialization before applying this start. Castle's Intel marker and plotted course
point to Jangala (or an accessible planetary fallback). The native planet menu opens
the founding yard's retained story review, sharing the prologue renderer while the
planet remains the docking authority. Generic later conversions retain their ordinary
yard menu. Castle's existing portrait popover and interactive
highlights resume the company-room sequence after designation.

The conversion is saved on the actual variant and included in deck cache identity.
The resolver uses the hull spec's ship-file path: vanilla Cerberus is stored in
`warhound.ship`, so assuming a filename from hull ID would silently lose its shape.
The compact workshop uses a full-depth single rank and advertises its physical
number of gantries. Existing stored assets remain owned after moving to smaller
quarters.

`cerberus-start` renders an unconverted control and five converted campaign seeds
using the installed collision polygon, requiring every programmed room, sufficient
squad berthing, four machine gantries when fully restored, and one or two working
boat berths according to condition. `second-wind-lance` checks the original hull's
zero-through-four working machine berths and fixed layout across repairs. Focused tests
cover the real preparation orders, depot serialization and exhaustion, partial
cargo draws, failed bills, port-only availability, one-time installation/depot issue,
and stale Mech Lab fabrication after a capacity change.

Initial validation covered 45 focused tests, the shaded mod jar, six Cerberus deck
frames and 75 Barracks/preparation frames. The required-conversion UX follow-up
passes 11 focused tests and the package boundary check; 77 retained frames cover
conversion directions, preparation and induction at 100% and 150%. Pointer tests
verify that Escape cannot hide the required lesson, unrelated controls stay blocked,
Return stays usable, and a real order releases the spotlight. Yard review spends
nothing; an alternative hull cannot bypass the founding conversion.

Still open: live character creation, travel, planet-menu ordering, native hullmod
persistence and campaign save/reload acceptance. New Castle lines are text with her
portrait; narration has not been generated. No live game deployment is part of this
change.

# Campaign kit arc

Status: DRAFT — proposed direction for which loadouts each campaign arc puts in the
player's hands, which wait for later, and which stay with approved military forces.
Nothing here is implemented.

Written: 2026-10-15

Read `campaign-overview.md` for the route, `meta-progression.md` for the stages and
the growth rule, `role-and-access.md` for how a card fills a squad, and
`s6-unlock-ladder-expansion.md` for the acquisition channels this doc would retune.

## The story the kit should tell

The authored campaign is a climb in legitimacy. An unaffiliated company protects
freight drivers on Jangala, chases a pirate crew across Corvus, stands beside the
Hegemony at Garnir and learns what real military power looks like, earns a working
ship, and graduates into sponsored paramilitary work on Agreus. Its armoury should
read the same way, in four registers:

| Register | What the company fields | Where it comes from | Example |
| --- | --- | --- | --- |
| **Scrounged** | Mongrel kit: surplus rifles, security shells, whatever the last fight left behind | Starter issue, open markets, recovery | Watchman's Kit, Outlaw Raider Protection |
| **Issued** | Coherent local kit a patron hands out for the job | Story rewards, Common and Advanced licences | A Proper Issue, Trench-Clearing Issue |
| **Recovered** | Worn military patterns, real but lesser, rebuilt from wrecks | Named story recoveries, never random | Recovered Specter, Recovered Arbalest |
| **Approved** | Proper military issue: whole powered sections, current doctrine | Only by a named authorisation from the tradition that builds it | XIV Shock, Bulwark, Reliquary, Lion's Mantle |

The player meets each register first **across the line**, then in their own hands.
Garnir is where the company sees approved kit fight beside it; Agreus is where it
starts wearing recovered versions of the same machines. That gap, visible for a
whole arc before it closes, is the story.

## What is wrong today

Measured from the catalog (`loadoutCardSheet`), not from intent:

- **Approved kit is not reserved.** Every full powered sheet is Prestige, and every
  one of them sits in the Independent `recovery` pool, so fifteen high-risk victories
  can hand a mercenary a Lion's Guard Mantle or a Knights Reliquary section from a
  random wreck. The Hegemony Arbalest Section is Advanced and opens through the same
  pool after five victories, earlier than the story's own recovered Arbalest. That is
  the "military patterns first" gap `campaign-learning-curve.md` already records.
- **The newest toys have no card.** The Arrestor pack, Palisade pod and Rampart
  revetment are learnable templates, but a squad is equipped by one weapon card and
  one armour sheet, and no card carries any of them. A player can own the pack and
  never field it.
- **Story beats award money where they could award a way of fighting.** Corvus and
  Broken Supply Route pay only credits; their tactical lessons (defended positions,
  convoy escort, counter-battery) have no kit that names them.

## Proposed access model

Keep Common, Advanced and Prestige as they are, and add a fourth tier above them.

**Restricted** — a card that is never offered by a market, licence, patron roll or
recovery roll. It is granted only by a **warrant**: a named, recorded authorisation
from one tradition (a story beat, or a patron relationship at its highest standing
with that faction). A warrant names exactly which Restricted cards it opens, and it is
permanent once earned, like sheet knowledge.

- All six full powered sections become Restricted: Hegemony XIV Shock, Tri-Tachyon
  Specter Protection, League Bulwark, Knights Reliquary, Lion's Guard Mantle, and the
  military Hegemony Arbalest Section.
- **Outlaw Foundry-Breaker stays Prestige and recoverable.** Pirates and the Path
  issue no warrants, which is the point: the dirty road to powered armour is open
  earlier, and it is cruder, louder and breaks first. Choosing it over waiting for an
  approved section is a real decision about what kind of company this is.
- **Some kit is never the player's.** Martyr rigs already carry a non-player reason.
  The Lion's Guard Mantle and Knights Reliquary should need that faction's own warrant
  at its highest standing, so most companies only ever see them across the line.
  Restricted cards still need an acquisition owner for the stranded-asset audit; a
  warrant is that owner.

This is the growth rule applied: approved kit consumes Standing with one tradition,
and its counter-pressure is the obligations that come with the warrant (and, for the
Hegemony, the scrutiny of being a licensed military auxiliary).

## The arc map

What each arc should hand the player, see across the line, and hold back. "New card"
means a loadout card that does not exist yet.

| Arc | Shows the player (enemy or ally) | Fun in the player's hands | Held back |
| --- | --- | --- | --- |
| **Wayline opening** — Relief, Counterattack | Militia holding a checkpoint behind revetments | Watchman's Kit; A Proper Issue's choice (Steady Line or Forward Screen). Add a third path, **Checkpoint Issue** (new card): service rifles, two Rampart revetments, smoke. It teaches digging in, which is what Relief is about | Everything above Common |
| **Broken Supply Route** — Junction, Thunder Run | Raiders with rocket tubes against the convoy | **Trench-Clearing Issue** as the Junction reward (opening a defended junction is exactly its job); **Convoy Escort Issue** (new card): rockets plus one Palisade pod, as the Thunder Run reward | Powered anything |
| **The Corvus Account** — Dust Ledger, Blind Watch, Cold Account | Sable Wake mortars and missiles at Blind Watch; at Garnir, Kord's Hegemony marines in XIV Shock with Arbalests and Arrestor packs, fighting beside the company | **Counter-Battery Issue** (new card, Hegemony): service rifles, two Arrestor packs, one anti-materiel rifle. Awarded once by Kord's quartermaster after Cold Account, as a nod rather than a licence | XIV Shock, the military Arbalest: the company has now seen what it cannot have |
| **A Working Ship** — Salvage Rights, Cold Start, Working Passage | Builders working under fire | **Builder's Guard** (new card): revetments and Palisade pods from Tamsin Vale's yard stores, for holding a construction site | — |
| **A Small War** — Agreus | Greyhook's crude boarding rigs and mortar fire on the commissioning work | Recovered Specter and Recovered Arbalest (as shipped), the Salvage-assisted Section, and the planned Boarding Jack. Counter-Battery Issue pairs naturally with the mixed section | Approved sections. The arc's planned procurement lead becomes the **first warrant** |
| **Beyond Agreus** — the procurement lead | — | A **Hegemony auxiliary warrant**: opens the military Arbalest Section first, XIV Shock later, each tied to service obligations | Other traditions' approved kit, which needs their own warrants |

Two things this keeps true:

- **Every arc adds a way of fighting, not only a better number.** Dig in, open a
  position, escort, counter-battery, hold a site, mix powered and regular troops.
  Each is a card a player can feel in the first battle they field it.
- **Recovered before approved, always.** No ordinary channel can deliver a military
  powered section before the story's recovered ones, so the Agreus beat lands.

## What later stays to unlock

Even after Agreus, most of the catalog stays ahead of the company:

- Approved sections from every tradition except the one that warranted it.
- The Tri-Tachyon point-defence laser (planned: shoulder-wrap emitter), as
  Tri-Tachyon Restricted kit. It is the energy counterpart to the Arrestor, and the
  natural reward for a Tri-Tachyon warrant.
- Masterwork grades and faction Prestige line sheets, through MRB 20 and patrons as
  today.
- Whatever the Foundry-Breaker road leads to: pirate and Path powered kit is the
  alternative to warrants, not a stage before them.

## Work this implies

Not stories yet; the order they would need to land in:

1. **Restricted tier and warrants.** An access tier the faction picker never offers,
   a persistent warrant record that names its cards, the stranded-asset audit
   accepting a warrant as an owner, and save migration that keeps every card a
   company already holds.
2. **Retune the pools.** Move the six approved sections to Restricted and out of
   every random pool; keep Foundry-Breaker recoverable.
3. **New cards.** Checkpoint Issue, Convoy Escort Issue, Counter-Battery Issue,
   Builder's Guard. Each is a weapon card, so it pairs with whatever armour sheet the
   squad already wears. Place each on the `loadoutMatchup` ladder before shipping.
4. **Story rewards.** Wire each card to its arc beat through the existing once-only
   reward settlement, and reconcile with the victory milestones and the breadth safety
   net so a card is never granted twice.
5. **Show approved kit across the line.** Kord's marines at Garnir field the approved
   kit the player will later want; Greyhook fields Foundry-Breaker-style rigs.

## Open questions

- **How hard is Restricted?** A warrant-only tier (proposed) versus keeping Prestige
  and only pulling the powered sections out of the Independent recovery pool. The
  first tells the story; the second is a data change.
- **Is the Foundry-Breaker road wanted?** It gives impatient players a crude powered
  option earlier. It could instead be held to Agreus too.
- **Can a company ever field Lion's Guard or Knights kit?** Proposed: only through
  that faction's own warrant at its highest standing. The alternative is never.
- **Does a warrant cost anything to keep?** Proposed: service obligations (periodic
  required contracts for the warranting faction). Without one, a warrant is Capability
  with no counter-pressure, which `meta-progression.md` refuses.

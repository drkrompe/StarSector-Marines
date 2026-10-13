# Campaign overview

Status: ACTIVE — campaign route and planning index; individual arcs retain their implementation and acceptance status.

Written: 2026-10-13

Updated: 2026-10-13 — consolidate the five authored arcs into dedicated plan folders.

## The company's journey

The authored campaign begins with a small company earning its first commercial
relationship on Jangala. Wayline's recovery leads to harder work across Corvus,
an encounter with the limits of an unaffiliated company's power, and an earned
construction ship. Leaving Corvus for Agreus then opens a proposed graduation
into restricted paramilitary work under a local sponsor.

This is the route through authored content. Recurring contracts, company
investment, house politics and later systemic conflicts coexist with it;
they are not extra mandatory chapters in this sequence.

```text
Company preparation / Castle's first lessons
  -> Wayline: An Open Road — Jangala, Corvus
  -> Broken Supply Route — accepted Wayline office and local sites
       Open the Junction -> Last Lift OR Thunder Run
  -> The Corvus Account — Asharu -> Warion -> Garnir
  -> A Working Ship — Asharu, Corvus
       Salvage Rights -> Cold Start -> Working Passage -> Morrow joins fleet
  -> A Small War — Agreus, Arcadia
       Water Under Seal -> four planned follow-on chapters
```

## Arc directory

The gate column describes the campaign condition, not merely narrative order.
An implemented chapter can still require live campaign and save/reload acceptance.
Read the arc overview for its missions, cast and rewards, then its local
`design/stories.md` for the remaining work.

| Arc home under `arcs/` | Overview | Entry and exit | Current scope |
| --- | --- | --- | --- |
| `wayline-opening/` | `wayline-opening-arc.md` | Accessible Mara Venn office, canonically Jangala; completing both chapters opens Broken Supply Route. | Both chapters, first-payment correspondence and separate recurring jobs implemented; first-offer discovery and linked live acceptance remain. |
| `broken-supply-route/` | `broken-supply-route-arc.md` | Completed opening arc; the accepted local site is frozen. Either closed branch leads onward, including a finite rescue/freight ledger closed in failure. | Junction, permanent support choice, both alternative finales, persistent losses and recurring arrangements implemented; live campaign/save acceptance remains. |
| `wayline-corvus/` | `wayline-corvus-arc.md` | Closed supply-route branch; Dust Ledger, Blind Watch and A Cold Account cross three existing Corvus worlds. Cold Account victory and acknowledgment of its aftermath enable the Shepherd referral. | Three chapters and their campaign sequence implemented; player-controlled difficulty tuning, live travel/save acceptance and broader generated-map access remain open. |
| `shepherd-recovery/` | `shepherd-recovery-arc.md` | Post-Cold Account reference to Tamsin Vale on Asharu; actual Working Passage departure and fleet handover unlock the Agreus referral. | All three contracts and once-only Morrow/crew transfer implemented; live campaign/save and later owned support acceptance remain. |
| `a-small-war/` | `a-small-war-arc.md` | Actual Morrow handover; optional referral to Baronet Elian Rusk on existing Agreus in Arcadia. Cold Start alone is insufficient. | Referral and Water Under Seal implemented. Live transition, Chapters 2–5, warrant, suit rewards and yard agreement remain planned/open. |

## Capability and stakes

| Step | What changes for the company | Authority for detail |
| --- | --- | --- |
| First Wayline work | First fees and reputation with the drivers; an equipment choice and actual issue; the recovered Hound after the depot. | `wayline-opening-arc.md`, `progression-nouns.md` |
| Supply-route commitment | Two-squad coordination, fitted lift, owned heavy support and a permanent choice about whom Wayline can help. Optional Secondhand Wings is an investment, not a required chapter. | `broken-supply-route-arc.md`, `company-view-nouns.md` |
| Corvus pursuit | Greater initiative and wider objectives, followed by the contrast with Hegemony military power at Garnir. Cooperation grants no permanent military protection. | `wayline-corvus-arc.md` |
| Shepherd recovery | The company earns a real construction tender by restoring the cooperative's yard and protecting departure. A recovery claim or grounded gun does not itself grant a ship. | `shepherd-recovery-arc.md` |
| Agreus sponsorship | Planned limited procurement authority, recovered power suits, crude pirate technology and a consequential yard agreement. Water Under Seal currently pays 26,000 credits and grants no suit pattern or warrant. | `a-small-war-arc.md`, `agreus-latchkey.md`, `power-suit-infantry.md` |

Power suits are intended to increase what fewer squads can accomplish as the
company becomes paramilitary. Equipment belongs to individual marines: a mixed
squad may combine regular armor, suit types, weapons and systems. An arc reward
must not imply identical equipment for every member or silently promote recovered
and pirate rigs to proper military issue. The remaining equipment progression
is explicit in `a-small-war.md` and `power-suit-field-acceptance.md`.

## Shared plans and systems

| Concern | Start here |
| --- | --- |
| Persistent campaign ownership and settlement boundaries | `campaign-nouns.md`, `architecture.md` |
| Long-term standing, capability, pressure and reach | `meta-progression.md`, `economy.md`, `themes.md` |
| Authored chapter, retry, reward and recurring-work laws | `early-operation-nouns.md` |
| Learning, reward overlap, affordability and force growth across arcs | `campaign-learning-curve.md`, `castle-campaign-lessons.md` |
| Company preparation and presentation | `first-mission-preparation.md`, `company-view-nouns.md` |
| Patrons, agreements and local political consequences | `living-world-nouns.md`, `contracts-nouns.md`, `campaign-event-nouns.md` |
| Personnel, recovery and investment | `personnel-nouns.md`, `loot-nouns.md`, `infrastructure-nouns.md` |
| Truthful correspondence and tactical speech | `narrative-nouns.md`, `battle-radio-nouns.md` |
| Exceptional late-game political escalation | `t3-endgame-nouns.md` |
| Release gates and live acceptance | `v0.1-alpha.md` |

The systemic late game, including civil-war chains and Last Testament, is not
an authored sixth arc after Agreus. Its existing feature plans remain under
their owning domains until a specific new story sequence is designed.

## Documentation structure and upkeep

```text
campaign/
  README.md
  design/
    campaign-overview.md       campaign route and navigation
    campaign-nouns.md          shared campaign model
    stories.md                campaign-wide open work
  arcs/
    wayline-opening/
    broken-supply-route/
    wayline-corvus/
    shepherd-recovery/
    a-small-war/
      design/                 arc overview, setting, cast and open-work board
      stories/                remaining implementation and acceptance plans
  early-operations/            shared authored-operation model and learning work
  contracts/, living-world/, personnel/, ...
```

Every arc folder follows the same `design/` and `stories/` convention illustrated
above. Its uniquely named `*-arc.md` is an enduring reading guide. It identifies
premise, places, cast, entry/exit conditions, mission order, rewards, and which
parts are implemented versus planned. Detailed rules remain with the cited
canonical noun document; the overview does not create a second reward schedule
or save-state authority.

Keep one owning open-work board for each story. The shared early-operations
board routes readers to arc boards instead of duplicating their rows. Preserve
stable document basenames when moving a plan so existing bare-slug citations
continue to resolve. Historical shipped ledgers remain where their work was
recorded; they are not current arc plans.

When an implementation story ships, fold its durable rules into the owning
design/noun document, record the completion in that feature's `design/shipped.md`,
redirect citations, and delete the finished story. The arc overview survives
as the guide to its place in the campaign. Add a new arc here when its concrete
premise and gate are agreed; mark unimplemented missions and rewards as planned.

# Role and access

Status: ACTIVE — direction for how equipment is organised and how a squad is
put together. Shipped: the role vocabulary, the filled matrix, provenance as
data, doctrine as a role mix resolved against access, and the Armory surface
that makes the split visible.

Written: 2026-08-29
Updated: 2026-08-30 — a role's billets now spread across the patterns at the
top of its band instead of all taking the single best one, and the Armory rates
and ranks what it lists.

Read `progression-nouns.md` for the standing rules equipment must obey,
`integral-system-slate.md` for which traditions build a suit capability and
what it should be, and `equipment-lore-catalog.md` for each pattern's
provenance. This doc sits underneath all three: it is about what the catalog's
*shape* should be, which those docs assume and none of them owns.

**`powered-assault-armor-roles.md` owns the role vocabulary**, and this doc does
not introduce a second one. That story already establishes the closed role set
and the principle this doc rests on — *"Role is deliberately not quality. Future
masterwork recon armor can remain light"* — and lists a closed role vocabulary
among its outstanding scope. What follows is the other half of the same idea: the
story says role must stop being inferred from tier, and this doc says the catalog
currently makes that impossible to demonstrate, because no role spans two tiers.

## The problem, stated as evidence

Every armour pattern in the catalog, by the role its own copy claims and the
tier it is sold at:

| Role | I | II | III | IV |
| --- | --- | --- | --- | --- |
| LINE | field-fatigues | — | combat, aegis, palatine, furnace-line, line | — |
| SECURITY | cordon-shell | militia | — | — |
| RAIDER | lashplate-harness | outlaw | reaver | — |
| SCOUT | — | scout | — | — |
| ASSAULT | — | — | — | heavy, specter, bulwark, reliquary, lions-mantle, foundry-breaker |

Eighteen patterns on a diagonal. Six of them share one cell. Two roles exist at
exactly one tier each. (This table is the state that motivated the doc; it is
kept as written because the argument is about the shape, and the shape is what
changed.)

**Role is not a category here. It is a label stuck on a rung of a ladder.**
Buying up the ladder does not upgrade a marine, it changes what that marine
*is* — a company that can afford tier IV stops having scouts, because no
tier-IV scout exists to have.

The visible consequence is that a fully equipped company fields nothing but
assault suits, every assault suit carries a breaching screen by
`integral-system-slate.md`'s role rule, and so every marine in the force has
the same capability. That reads as "tier grants the ability", which was never
the intent and is not what the code does. The cause is one missing column, not
a rule that needs changing.

## Two axes, currently fused

**Role** — what a marine is *for*. Recon, holding a line, getting through a
door, carrying the heavy thing. It is set by how the unit is organised, and it
barely moves as a company gets richer: a squad that needed a scout last year
still needs one.

**Access** — what the company can *afford*, and what its patrons and markets
will sell it. It moves constantly, and when it moves it should raise the whole
squad rather than reshape it.

A poor company fields a scout, a support gunner, and riflemen in patched
kit. A rich company fields *the same squad* in Domain-grade kit. Nobody stops
having a scout because they got rich, and nobody acquires a breaching
speciality merely by getting rich either.

Today these are one axis. `SquadArmorDoctrine` names twelve concrete pattern
ids, so a doctrine cannot say "a scout, four breachers, seven riflemen" without
also fixing exactly how good each one's kit is. That single fact is what forces
`DebugCompany` to select doctrines by exact tier match — a workaround for an
axis that should not have been fused in the first place, and one that
disappears on its own once they are separated.

**Role does not yet exist as a concept.** `unitClass` on a catalog entry is
free text, validated only for non-emptiness, read only by the Armory to print a
word on a card. Making role real is part of this work rather than a use of
something already there.

## Squad composition

A squad is twelve marines in three fire teams of four. The composition is the
doctrine's business and the kit quality is the company's, so:

- **A plan names a role mix**, per billet — `SquadRoleMix`. "One scout, one
  support, four assault, six line" is a plan. So is "two teams of breachers" — a
  deliberately lopsided unit, which becomes a real doctrinal choice with a real
  cost rather than the only thing the top of the ladder can mean.
- **Available stock resolves each slot to a pattern**, through
  `ArmorIssueResolver`. The same plan at tier II and at tier IV is the same
  unit, better equipped. Nothing in the resolver reads a tier: access is
  expressed as "which patterns may I use", so a company ceiling, a player's
  unlocked cards, and a test's hand-picked set all go through one rule.
- **Tradition chooses among what access allows**, and it is now data
  (`ArmorTradition`) rather than a sentence in a description.

**A role with several billets does not put all of them in one suit.** For each
role the resolver takes the best tier it can reach and deals *every* pattern
available at that tier across that role's billets. A section with two weapons
carriers, offered a missile pod and a corpsman's satchel, fields one of each.

Only the top band spreads. A tier is a price band, so patterns inside one are
comparable by construction and choosing between them is a matter of what the
squad needs rather than of what it can afford; reaching below the band would
hand a rifleman frontier kit because there happened to be some in the hold.

Issuing a role's single best pattern to all of its billets is a rule that reads
as obviously right and hides two real defects:

- **A shipped suit can be unwearable.** Two patterns in the same role, tradition
  and tier are separated only by the tie-break, which was the alphabet. The
  Hegemony's corpsman rig and its Arbalest support battlesuit share that cell, so
  no company owning both ever fielded a corpsman, and the field-aid capability
  quietly did not exist at the top of its ladder.
- **A contributed pattern can be unreachable.** The submod contract is add-only
  by design, so an author cannot replace a core id — and if their pattern occupies
  a cell a core one already holds, it can never be issued either. There was no
  way in from outside. See `submod-catalog-contract.md`.

Both are the same bug and neither is fixable by tuning the tie-break: the cell
holds two things that are worth having and the section has room for both.

**Tradition outranks tier, deliberately.** A Hegemony plan issues its own
tier-III Pathfinder to a recon billet rather than the League's tier-IV Outrider,
because a Hegemony section fields Hegemony kit. Buying the better scout suit
means buying into another tradition, which is a real decision rather than an
automatic upgrade. It also means a section can be mixed-tier by intent, and two
consequences follow that are worth stating:

- **An armour plan can no longer be refused for missing stock.** It names roles,
  and the armoury fills them from whatever is owned — degrading to worse kit,
  and finally to line kit for a role nobody has bought. A company that has never
  bought a scout suit still fields its sections; it just has no scout in one.
  Composition is the plan's business and supply is the armoury's, and only the
  weapon side can now refuse an issue.
- **A squad's experience band is the squad's, not one marine's.** Experience is
  issued with the armour, so reading the leader's own suit was indistinguishable
  from reading the squad's while all twelve wore the same pattern. It is not any
  more: a section whose scout wears a cheaper specialist suit would otherwise
  lose its sergeant to the accident of who happened to be senior.

The three bands that motivated this doc then fall out of the model instead of
being special-cased:

1. **Early.** A role mix at tiers I–II. The scout billet's pattern carries a
   capability and so does a line billet in militia kit, so a starting squad has
   two, and they are nothing alike.
2. **Established.** Tier III. Scout, line, assault and support patterns each
   carry theirs, so capability is varied within the squad, by role.
3. **Specialist.** A lopsided doctrine at tier IV — every marine a breacher —
   chosen because that is what the unit is for, and paid for in what it gives
   up.

None of those is a rule. They are what a role mix plus an access level produces.

## Capability follows role, and ladders with tier

`integral-system-slate.md` already establishes the first half: a capability
attaches to what a suit is *for*, so all six assault patterns breach and no
other pattern does. That rule is correct and this doc does not amend it.

What it could not demonstrate is the second half, because no effect exists at
more than one tier: the sweep only at II, the pod only at III, the screen only
at IV. So "the claim is the role, not the tier" has been true in intent and
unobservable in practice.

Once a role spans tiers, the same capability exists at more than one strength,
and the ladder between them becomes the thing that expresses access:

- A tier-II Janus sweep and a tier-IV one are the same capability. The better
  suit reads further, holds it longer, or recovers faster — a real progression a
  player can feel by upgrading rather than by re-roling.
- **Grade stays description, not arithmetic.** `progression-nouns.md` is
  deliberate that a Masterwork system is finely made rather than automatically
  strongest, and the Specter proves it: masterwork build, nearly the smallest
  pool, sold on recycle speed. Tier is the strength axis; grade remains the
  character axis; a suit may be a masterwork example of a cheap tier.
- Because a capability then exists at more than one tier, the ladder is
  *checkable*, and the claim is stronger than it first looks: **every** suit at a
  tier must be worth more than **every** suit below it. A tier that only raised
  its ceiling would let a company pay for tier IV and be handed something worse
  than what it already owned.
- That comparison needs an index rather than an authored field, because suits
  within one tier are deliberate side-grades whose headline numbers disagree
  sevenfold. `ArmorCatalogShapeTest` owns it: a screen counts over the arc it
  covers, a shove is priced in soak-equivalents, and the total is scaled by how
  much of the time the system is up. It is a shape check and the game never
  reads it.

## The role set

`powered-assault-armor-roles.md` already names three: **light infiltration /
recon**, **standard line combat**, and **heavy mechanized battlesuit**, with
`ARMORLESS` as an unpowered kit rather than a fourth. Those are the roles, and
this doc adopts them rather than proposing others. In the table above they read
as SCOUT, LINE and ASSAULT.

Two of the five labels in that table are not roles at all and its closed
vocabulary would already retire them:

- **SECURITY** reads as *garrison and low-intensity policing*, but every pattern
  wearing the label is also cheap. It is a price band with a name — light-role
  kit at the bottom of the ladder.
- **RAIDER** reads as *provenance*: pirate and Pather manufacture. An outlaw
  rifleman is a line marine in outlaw-made armour, and the tradition already
  carries that flavour without needing a role slot.

A fourth is adopted: **support** — the marine carrying the heavy or indirect
weapon, and whatever else the squad needs carried for it.
`integral-system-slate.md` records that the micro-missile pod's home was "a
deliberate choice among three plausible homes" before settling on a *line* suit,
which is what having nowhere to put it looks like. The Aegis is that role's first
member, and the pod stops being a line suit's oddity.

The vocabulary is therefore `ArmorRole`: UNPOWERED, RECON, LINE, ASSAULT,
SUPPORT. It is closed and validated at parse time, which is what makes the
catalog's shape countable at all — the free text it replaced could describe a
price band or a manufacturer and nothing would object.

## Which cells to fill

Not every cell. A role should span the range its users actually operate at, and
an empty cell is a statement that nobody builds that thing.

The gaps that mattered are filled. Six patterns, no new art — every one reuses
an appearance family and a tier icon that already shipped:

| Pattern | Role | Tier | Tradition | Carries |
| --- | --- | --- | --- | --- |
| Kestrel riot shell | assault | II | Corporate security surplus | A real shield and no shove worth the name |
| Packframe support harness | support | II | Independent crews | One rocket on a welded rail |
| Pathfinder recon suit | recon | III | Hegemony | A survey array that reads further than a Janus and announces itself doing it |
| Redoubt siege plate | line | IV | Hegemony | The steadiest brace anyone fields, because it was never going anywhere |
| Outrider deep-recon suit | recon | IV | Persean League | The deepest return anyone fields, built to be shared |
| Arbalest support battlesuit | support | IV | Hegemony | Four rounds and a doctrine for spending them |

The Redoubt shipped carrying nothing, and that was honest at the time: the LINE
role had no capability at all, and inventing one for the top of its ladder would
have been the "give a faction a system so it has one" failure at role scale.
What it was waiting for was the brace — the answer to what a line suit is *for* —
which now runs on three of them and gives that role the same shape the others
have.

Each was written to be a different suit rather than a scaled copy, per the
standing rule below. The Kestrel is the clearest case: it is the only breacher
whose whole value is the screen and whose assist is negligible, which is what a
riot rig actually is and is nothing like the six crossing suits above it.

Still deliberately empty: a tier-I version of everything.
`integral-system-slate.md`'s "do not give a faction a system so it has one"
applies to cells too — a cell exists because somebody would build that thing,
not to make the table rectangular.

Deliberately *not* filled: a tier-I version of everything. `integral-system-slate.md`'s
"do not give a faction a system so it has one" applies to cells too — a cell
exists because somebody would build that thing, not to make the table
rectangular.

## What the player is buying

A plan is presented as a **tactic sheet**: the player assigns one to a squad, and
it equips the whole squad at once. That framing is the model made sayable — a
sheet is a way of organising twelve marines, and it is bought once and kept,
while the kit inside it improves as the company does.

Each sheet in the Armory therefore shows two lines that must never be confused:

- **SECTION** — the role mix. One scout, seven line, two assault, two support.
  This is what the player is choosing between, and it does not move.
- **ISSUED** — what this company's own stock currently puts in those billets.
  This is what improves, and it is the only thing that changes when the company
  grows richer.

The header carries the tradition and the band the sheet would *field today*
rather than an authored tier, because a sheet has no tier of its own. Two
companies looking at the same sheet see the same SECTION line and different
ISSUED lines, which is the whole model in two rows of text.

**A role the company owns nothing for is marked on the SECTION line**, because
the resolver's fallback is otherwise invisible and misleading. A sheet asking
for five recon billets, read by a company with no recon kit, issues twelve line
suits and says nothing about it — the player sees a section they did not choose
and no reason for it. Naming the gap where the choice is made turns an
unfillable sheet into the thing it should be: a reason to go and buy a scout
suit.

**Every loadout is rated, and the list is ordered by it.** A tier is a price
band and a rarity is provenance; neither answers the question a player actually
has at this screen, which is *which of the things I can field right now is the
strongest*. `LoadoutEffectiveness` answers it in the units the battle settles
in — armour removed per second for a weapon definition, damage absorbed for a
tactic sheet — computed from the same numbers `InfantryCombatStats` and
`DurabilityModel` read, and quoted as a share of the best the catalog could do.

Three things about it are load-bearing:

- **Penetration is in the weapon number.** It is the term most easily left out
  and the one that changes the answer most: the submachine gun has the highest
  raw output in the catalog and strips a battlesuit at a sixth of a marksman
  rifle's rate. A rating that ranked by damage per second would recommend the
  wrong weapon in exactly the fights a player loses.
- **Evasion is in the armour number.** A shot that misses costs nothing, so a
  suit that is harder to hit is literally more armour rather than a separate
  virtue sitting beside it.
- **The ceiling is the catalog's, not the company's.** A rating that rose
  because the player got poorer would be useless for the one job it has.

What it deliberately does not fold in is everything that is not plate and
ballistics: special equipment, integral systems, reach, cover, morale and the
order the squad is given. Two of those already have their own line on the tile,
which is the point — a single scalar that priced a field-aid satchel against a
millimetre of ceramic would be an invention rather than a measurement, and
CARRIES says what the RATING cannot.

**The list can also be narrowed to what the fleet can pay for today.** Rarity is
a radio and supplies are a switch, so they are separate controls: the supplies
filter hides any loadout whose issue to the selected squad would cost more cargo
than the fleet is carrying. It asks only the supplies question — a stationed or
under-strength squad cannot be issued anything at all, and hiding every tile
behind that would empty the list with no explanation the apply row does not
already give.

**Compare Patterns shows what the company holds, not what exists.** The screen
answers "what is my kit and which of it should the section be in", and a table
mostly made of suits nobody has ever held answers a different question badly. Its
summary still names the catalog's size, so the fact that there is more out there
survives without pretending it is choosable.

**The third line is what the section would carry.** SECTION says which jobs the
sheet organises and ISSUED says which patterns fill them; neither tells a player
that one sheet fields nine braces and another fields four breachers unless they
have the catalog by heart. CARRIES counts the issued twelve by capability family,
ordered by headcount. Like ISSUED it is derived from the issue rather than the
plan, so it improves as the company's stock does; a sheet whose patterns carry
nothing says so rather than rendering blank.

## What this does not change

- **The one-family-one-role rule.** Assault patterns breach; nothing else does.
  Filling the matrix does not license spreading effects around.
- **Provenance discipline.** `equipment-lore-catalog.md` still decides whether a
  tradition has anything to say. A new cell that would be a faction-coloured
  clone of an existing pattern should stay empty.
- **Suits sharing an effect must be different suits.** Two tiers of the same
  capability must differ by more than a scaled number, or they are the palette
  swap the catalog exists to prevent.
- **The simulation.** Nothing here needs a new mechanic. The capability model,
  the durability model, and the activation policy are all unaffected; this is
  about which suits exist and who wears them.

## Consequences

- `SquadArmorPlan` is the authored form; `SquadArmorDoctrine` survives as the
  *resolved* twelve, which is what the Armory, saves and custom loadouts already
  understood. Nothing about persistence changed.
- `DebugCompany`'s `bestArmorTier(doctrine) == plan.maxArmorTier()` exact match
  is deleted rather than relaxed. It existed only because doctrines carried
  tier; a company now applies its ceiling to whatever plan it is running, and
  every plan is admissible at every stage.
- `DebugCompanyStage`'s ladder was non-monotonic — Reinforced dropped back to
  SEASONED between two HARDENED stages, so a company lost its battlesuits by
  growing. Fixed alongside this work.
- `unitClass` became `ArmorRole`, validated at parse time — the closed vocabulary
  `powered-assault-armor-roles.md` had outstanding. Two of the five words it
  replaced were retired rather than translated: SECURITY was a price band and
  RAIDER was provenance, so their patterns are line suits that happen to be cheap
  or pirate-made.
- `integral-system-slate.md` gains the tier ladder for each capability it
  already lists; its role rule is unchanged.
- The Lashplate salvage harness and the Corpsman field rig were both unwearable
  before a role's billets spread — each shares its role, tradition and tier with
  a pattern that sorted ahead of it by id. Neither was a content mistake and
  neither needed a catalog edit; the resolver was refusing to issue them.

## Why this was not visible earlier

Worth recording, because the failure was one of evidence rather than judgement.
`powered-assault-armor-roles.md` has said "role is not quality" since it was
written, and the catalog has contradicted it the whole time. Nothing caught that,
because every check that could have was written against one pattern at a time —
a pattern's role is well-formed, a pattern's stats are in band — and the defect
only exists in the shape of the whole table. It surfaced when a player-facing
question ("why do I never see this ability?") was chased far enough to notice
that a fully equipped company can only buy one role.

A rule about a catalog's shape needs a check that reads the whole catalog.
`ArmorCatalogShapeTest` is that check. It pins what holds today — every role has
a pattern, every role is reachable through the player's own supply, and no
capability is carried by two roles — and *reports* the role/tier matrix rather
than asserting it, because the invariant this doc actually wants does not hold
yet and a failing test is not a plan.

That report is now an assertion. `everyRoleIsAvailableAtMoreThanOneTier` is the
invariant this doc exists for, and a second — `aCapabilityGetsStrongerWithTheTierOfTheSuitCarryingIt`
— pins the ladder: the best example of an effect at each populated tier is at
least as strong as the best at the tier below, measured on that effect's own
headline axis. It is compared between populated tiers and on the *best* example
rather than every one, because a capability may skip a tier and because suits
within a tier are deliberate side-grades — the Foundry-breaker and the Reliquary
are both tier IV and their pools differ sevenfold.

Neither test could have been written before the matrix was filled. The second
one especially: a ladder needs two rungs.

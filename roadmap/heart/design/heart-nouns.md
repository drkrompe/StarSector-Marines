# The Heart — Nouns

Status: ACTIVE

Written: 2026-10-22

Updated: 2026-10-22 — the owner's flow picks settled; the battle as built (sleeping mothers, wake rules, beats).

The heart is the third and last movement of the Rav Bloom arc
(`bloom-mission-arc.md`, Mission 3): months into a bloom the world's military
has held its cordon long enough to strike back, and the company leads the push
into the overgrown country to collapse the mother bores at the bloom's root.
One mission, two variants, each a single battle and never combined (owner,
2026-10-22): **Broad Front**, the company as the spearhead lane among allied
columns converging on the heart, which shows off large-scale combat; and **The
Burn**, the company alone behind the world's guns, burning its way in behind a
walking barrage. The campaign picks the variant from how the Cordon went; the
player picks only whether to take the beat.

This doc owns the heart's map, its bores' starting state, the flow, beats and
end conditions of both variants, and the barrage. The swarm is Colony Breach's
swarm: bores, tiers, surges, packs, harvest, creep, spore and ground colonies,
strains, growth stages and the Matriarch are `colony-breach-nouns.md`'s, reused
and never restated here. Fire (fuel, spread, burnt ground, the firebreak, smoke,
incendiaries) is `fire-nouns.md`'s. Read both first; every one of their laws
holds here unless this doc names an exception.

## The fantasy

The colony fell, the cordon held, and the country between them has been the
bloom's for weeks. The fields nearest the line are still green; past them the
creep runs over everything, the woods stand as bone-pale skeletons, the market
town's roofs have caved in under it, and at the far side of the country, round
the old reservoir workings, two or three mother bores breathe in the oldest
growth of all. This is where the bloom roots on this world. Collapse every one
of them and the bloom on this world is over; leave one and it seeds the traffic
again next season.

In Broad Front the company steps off from the farmland with allied columns on
either side of it, each column taking its own lane into the overgrowth, and
the swarm comes out of every bore at once to meet them. In The Burn nobody
comes with the company but the guns: a barrage walks into the forest ahead of
it on a timetable, the forest catches, and the company follows the fire line
in, as close behind the shells as it dares.

## Vocabulary

**The heart.** A bloom's root on its world: the reservoir under the mother bore
Colony Breach did not collapse, weeks or months grown. In the model it is not a
new kind of thing but a lot of known things at once: two or three **mother
bores** on one reservoir round the old workings, each with her Matriarch to
come, **satellite bores** between the heart and the start line, every one at
maw, and creep over everything within their reach. The only battle that starts
with more than one mother bore. Text says the heart is down and the country can
be taken back; it never says the Bloom is destroyed (`swarm-lore-guide.md`).

**Mother bore (of the heart).** A mother bore as `colony-breach-nouns.md` has
it, several at once: numbered from 1 and keyed `mother-N` in beats and the
director snapshot (Colony Breach's one mother keeps the bare key `mother`).
Each opens at setup already fed to maw and fed past the Matriarch's elder
threshold, so her Matriarch comes out at elder stage the first time she
releases at maw. They stand round the workings, at least twenty-two cells
apart: one reservoir, several mouths. Every mother collapsed is the win.

**Satellite bore.** An ordinary bore opened at setup and fed to maw, standing
between the heart and the start line. Broad Front has five, one to each fifth
of the front, so every lane meets one on its way in; The Burn strings three
along its one approach. A satellite collapsed is scored, and is a bore whose
surges and creep are gone, but it is never required.

**Heart country.** The map (`HeartCountry`): green country the bloom has held
for weeks. Along the start edge, the **cordon's farmland**, the surveyed rural
country of an open-field landing (`mapgen-nouns.md`, Rural country), where the
company comes down and which the creep has not yet taken. Past it, **woods**
thicker than any other map's, the **dark settlement** (Broad Front's market town
in the middle of the map; The Burn's village near the start, at the infection's
edge), and the **workings** against the far edge, a few sheds and a yard on
short roads, where the mothers stand.

**Matured bloom.** The heart's creep is the bores' own, grown to full reach at
setup as creep weeks old (`colony-breach-nouns.md`, Creep can be matured): it
fills everything within a maw's reach, crusts every wall it reached, has killed
every tree it holds to a bone-pale skeleton and brought down every roof it
stands under. Nothing is painted on. A bore collapsed in the battle withers its
creep back as any bore does.

**Start line.** The near edge of the cordon's farmland, along the farm road the
fields are worked from. Where the attack steps off and, in The Burn, the first
firebreak behind the fire line.

**Lane** (Broad Front). A way into the overgrowth for one column: a track
cleared from the farmland toward the heart along the front, four of them at
about a sixth, a third, two thirds and five sixths of the way across, with the
country road out of the middle of the farmland as the company's own. A lane is
ground, not a corridor: a column may leave it, and the creep has grown over it.

**Ride** (The Burn). A straight strip a forester cuts across a wood to stop a
fire: bare earth three cells wide across the whole approach, two of them, at
about three tenths and a half of the way in. A ride outside the creep is a
firebreak; a ride the creep has grown over is fuel again, as creep over a road
is (`fire-nouns.md`).

**Brook** (The Burn). A stream across the approach, deep in the bloom beyond
the village: water three cells wide with sand on either bank, crossed on foot
at a ford every forty-six cells and wherever a road bridges it. Nobody walks
through water and creep does not climb it, so the brook is the map's natural
firebreak and a line the creep crosses only at its fords and bridges. A road
over it is its bridge; where the settlement's streets cross its line it runs
under them.

**Barrage plan** (The Burn). The world's guns' timetable, frozen at the
briefing: a sequence of **barrage lines** across the approach, each a depth
into the map, with the second it opens and the seconds it fires before it
**lifts** to the next. Shells fall as placed blasts along the current line
(the Orbital Barrage's pending detonation, many times over, `command-powers-nouns.md`),
with a warning before each line opens. The plan ends at the **last line**,
short of the heart: the batteries' reach, or the ammunition the briefing names.

**Burn line** (The Burn). The company's front, which is a firebreak moving:
the fire the barrage and the company light ahead of it, and the burnt ground it
leaves behind, which no creep re-takes for minutes (`fire-nouns.md`, Firebreak).

**Danger close.** How near the barrage a squad may stand: the field mortar's
friendly veto at launch (`FieldMortarService.friendlyUnderRound`) is the model.
A squad advancing behind the barrage keeps outside it; a shell never falls on a
cell the veto refuses.

## The map

Both variants are laid out by one plan, `HeartCountry`, in one of two **forms**,
on a living world (`SurfacePalette.VERDANT`) whatever world the bloom is on:
the heart is the same kind of place on every green world, and a fixture names
no market.

| | **Broad Front** | **The Burn** |
| --- | --- | --- |
| Size | 560x336 (`MapScale.CONQUEST`), wide | 400x240 (`MapScale.COLONY`), deep |
| Start edge, heart edge | south, north | west, east |
| Farmland | the open-field landing's country along the long edge | the same along the short edge |
| Settlement | a market town in the middle of the map | a village near the start, at the infection's edge |
| Woods | 70% of the wild ground | 80%: the fuel the burn runs through |
| Cleared ground | a track along each of four allied lanes | two rides across the approach |
| Water | none | the brook, about three fifths of the way in |
| Bores | 2-3 mothers round the workings, 5 satellites at 52-72% depth, one per fifth of the front | 2-3 mothers, 3 satellites from 44% to 74% depth |

A plan states its country through its **landscape** (`mapgen-nouns.md`,
Authored forests: a plan's landscape): how much of the wild ground is wood,
which tracks are cleared before the woods grow, which brooks run across it.

**The plan is the reachability proof.** Forest crowns are impassable on the
grid, so a heart country could seal a mother behind a wood. The heart's sites
are chosen only on ground walkable from the start line (`HeartBores`), and a
map that cannot seat every mother and satellite on such ground re-rolls the
seed. Fire is never counted on to open the way to a mother: a wood that burns
or is felled opens more ground, never the only way in.

**The cordon's farmland stays green at the start.** Satellites stand deep
enough that a maw's reach does not carry its creep onto the landing field; the
creep comes up to the cordon's wire and, on some seeds, over a field or two.

## Flow

Both variants end the same way: **the heart is down when every mother bore of
it has collapsed** (the company wins), and the company **loses** when it is
broken or withdraws. Bores are fought, not scripted: nothing in either flow
collapses a bore by fiat.

### Broad Front

1. **Step-off.** The company comes down by shuttle in the cordon's farmland at
   the middle of the start edge (`landfall`). Four allied columns stand on the
   start line, one at the foot of each lane, and go up their lanes together;
   the company's own lane is the country road.
2. **Advance to contact.** The satellites surge on their own clocks from the
   start and hunt whatever they sense, so contact comes lane by lane. The
   mothers **sleep**: they grow, but send nothing.
3. **The heart wakes.** The first satellite to fall wakes the heart: every
   living mother surges at once, packs streaming down the field toward the
   lanes (`heart_surge`; owner's pick, 2026-10-22). From then on the mothers
   surge on their own clocks.
4. **The bores fall in turn.** Lanes take their satellites, then converge on
   the heart. A mother at maw puts out her Matriarch the first time she
   releases there, elder from the start. **A mother's fall makes every
   surviving mother surge at once** (owner's pick): the heart fights harder as
   it dies. Beats `bore_collapsed_N`, `matriarch_emerged_mother-N`,
   `mother_collapsed_N`.
5. **Heart down.** The last mother collapses: `heart_down`, the win. Allied
   lanes broken are a cost on the outcome, never a loss.

Allied columns are first scripted, as the Cordon's are: squads and a vehicle
per lane under an allied lane commander that advances them along their lane and
holds what they take (`heart-allied-lanes.md` replaces the scripting with the
general allied column once the Cordon has built it).

### The Burn

1. **The briefing's timetable.** The barrage plan is shown and frozen: its
   lines, when each opens and lifts, and the last line short of the heart. The
   wind blows from the start edge toward the heart for the whole battle.
2. **Lighting the line.** The barrage's opening shoot is incendiary and sets
   the forest alight along the near ride; the company relights the line past
   every firebreak it reaches (owner's pick: both). The fire runs before the
   wind into the overgrowth. Beat `line_lit`.
3. **The barrage walks.** Each line opens with a warning, fires for its
   seconds and lifts on time whatever the company does. Infantry close behind
   it arrive as it lifts and meet a swarm the shells have stunned and thinned;
   infantry that lag meet it recovered. The barrage's shells hurt what they land
   on through ordinary damage: a bore on the line is worn, not collapsed. Beats
   `barrage_line_N`, `barrage_lifted_N`.
4. **The burn line goes in.** Fire takes the creep and the growths on it and
   leaves burnt ground the creep cannot re-take for minutes, so a satellite
   behind the line cannot claw back what the burn has passed. Brooks, rides
   outside the creep, roads and fallow are firebreaks; the company relights
   past each with its incendiaries.
5. **Breakout.** The swarm breaks out through the fire: bodies come through the
   gaps the fire leaves (fords, burnt ground, roads, a ride the creep had
   covered). **A bore the fire, the guns or the company hurts surges at once**,
   at most every twelve seconds however long it burns, and a mother hurt so
   wakes (owner's pick): the breakouts come where the guns have just been, in
   front of the company. The mothers otherwise sleep as on Broad Front, and a
   mother's fall makes the survivors surge on The Burn too. Beat
   `breakout_<bore>` (observer-only, recurring).
6. **The last line.** The barrage stops at its last line, short of the heart:
   `barrage_ended`. The last stretch is the company's: the mothers are
   collapsed by satchels, heavy weapons and machines, as any bore is.
7. **Heart down**, as Broad Front. The score adds how much of the bloom's
   creep the burn took.

## Beats

Keyed `heart.<key>`, published by the battle's one story sequence; presentation
and evidence, never inputs to the simulation. Both variants publish `start`, `landfall`,
`bore_collapsed_N` (a satellite), `matriarch_emerged_mother-N`,
`mother_collapsed_N`, `heart_down` and `company_broken`.

| Key | Variant | When |
| --- | --- | --- |
| `landfall` | both | the first Marine is on the ground |
| `heart_surge` | Broad Front | the first satellite falls and the mothers wake |
| `contact_lane_N`, `lane_broken_N` | Broad Front | a lane first takes swarm fire; a column below a third of its strength (`heart-allied-lanes.md`) |
| `line_lit` | The Burn | the fire line takes |
| `barrage_line_N`, `barrage_lifted_N` | The Burn | a line opens (after its warning); it lifts |
| `breakout_<bore>` | The Burn | a hurt bore surges (observer-only, recurring) |
| `barrage_ended` | The Burn | the last line lifts; the guns are done |

## Laws

- **Every mother, and only the mothers.** The win is every mother bore of the
  heart collapsed; satellites are scored and never required.
- **The heart is the objective.** The company plays it as Purge from the
  start (`colony-breach-nouns.md`, Hive posture): there is no evacuation to
  weigh it against.
- **Bores are fought, not scripted.** A bore dies to ordinary damage, a shell's
  included; nothing collapses one by fiat, and burning never collapses one.
- **The guns cannot take the heart.** The barrage is bounded: it lifts on its
  timetable to a last line short of the heart, and the last stretch is the
  company's. The artillery form of *air cannot clear the map*.
- **The barrage does not wait.** A timetable, not a reaction to the company.
- **Danger close is the mortar's veto.** No shell falls where it would refuse
  a mortar round.
- **A firebreak holds creep back for a while**, never for good: burnt ground
  refuses creep until it recovers, and ground a living source still reaches
  beside the break is still creep's (`fire-nouns.md`).
- **The map proves its own reachability.** Every mother is reachable on foot
  from the start line without fire or felling.
- **Allies never end the battle.** Allied lanes broken are a cost, never a
  loss; the company alone wins or loses it (`ai-nouns.md`, Sides).
- **Deterministic.** Same seed, same heart: country, sites, growth, timetable.

## The company's command

The company plays the heart as Purge under its own commander
(`HeartAssaultCommand`): it goes in together on one bore at a time, first the
satellites standing in its lane, then the nearest living mother, opening each as
the thing to breach. Its lane is the band of ground either side of the line it
advances along from its landing: about a seventh of the map's width either side
on Broad Front, so the other lanes' satellites are the allies' work, and the
whole approach on The Burn. The director needs no civilians to cover here: it
spawns only out of the heart's bores from the first tick, and nothing closes it
but their collapse.

## Settled choices

The owner's picks (2026-10-22, `heart-flow-options`): a satellite's fall wakes
the heart; a mother's fall makes the survivors surge; the barrage and the
company both light The Burn's line; a hurt bore surges.

## Boundaries

- The swarm and its laws, matured creep: `colony-breach-nouns.md`.
- Fire, fuel, the firebreak, incendiaries: `fire-nouns.md`.
- The Cordon, which picks the variant and hands over its allies:
  `cordon-nouns.md`.
- Rural country, forests and a plan's landscape: `mapgen-nouns.md`.
- Allied side and commanders: `ai-nouns.md`; the general allied column:
  `cordon-allied-columns.md`.
- Placed blasts and the Orbital Barrage: `command-powers-nouns.md`.
- The campaign event and its exactly-once lifecycle: `campaign-event-nouns.md`;
  the arc's ledger and hooks: `bloom-mission-arc.md`.

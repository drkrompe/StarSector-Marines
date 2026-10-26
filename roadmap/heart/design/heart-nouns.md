# The Heart — Nouns

Status: ACTIVE

Written: 2026-10-22

Updated: 2026-10-25 — a forward observer attached to each Broad Front lane calls the military's air wing

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
edge), and the **origin** against the far edge, where the mothers stand: the
facility the bloom came up in, drawn from the seed stepped by form and density
(`HeartOrigin`, `heart-origins.md`): the reservoir's pump station, a freight
terminal or a research compound, fenced, or an open park, its yard dressed
with the clean props of what it was, and its core building (the park's pond) broken
open by the authored damage layer from the side the first mother came up on.

**Matured bloom.** The heart's creep is the bores' own, grown to full reach at
setup as creep weeks old (`colony-breach-nouns.md`, Creep can be matured): it
fills everything within a maw's reach, crusts every wall it reached, has killed
every tree it holds to a bone-pale skeleton and brought down every roof it
stands under. Nothing is painted on. A bore collapsed in the battle withers its
creep back as any bore does.

**Cordon ridge.** The last high ground before the bloom, just past the
farmland: a band of hill segments across the whole front, each climbed by a
ramp from the cordon's side, with a pass wherever a road, a lane, a ride or a
settlement crosses it (`elevation-nouns.md`, Ridges). The line was dug along
it, and it looks down into the overgrowth. The Burn's village stands across most of its
band, so The Burn's ridge is short. A city's cordon has no ridge: a city's
height is its buildings (`elevation-nouns.md`), its blocks fill the band, and
the line is dug on the last open ground before the streets instead.

**Hollow.** The low ground the workings sit in: a rim of high ground wrapped
round them on the attack's side, climbed from outside, its inner face a cliff
over the yard, with a pass straight toward the start and wherever a road or
route crosses it. The rim's ground is sealed before the places grow
(`elevation-nouns.md`, Sealed ground): the places grow round it and every road,
weld and route goes round it and crosses only at its passes. It stands as most
of a ring, broken by The Burn's brook and short gaps (`heart-map-intent.md`).

**Knoll.** The rise Broad Front's market town was built on, the reason it is
where it is: the town grows only on the knoll's top (`elevation-nouns.md`,
Knolls), its gates roads ramped down toward the start and either side, so the
lost column's holdout has height over the lanes round it. Rural and town maps;
a city has none.

**Cordon's works.** What the cordon dug and built facing the bloom, laid as
named pieces of the heart's mission layout: the forward line of trench and
wire along the cordon ridge's crest, broken at every lane; an observation post forward
of it in each half of the front; and on the farmland behind it the logistics
base's ammunition dumps and vehicle park, with a supply road up to the
settlement. Where no road can be laid (a city's ground) the streets are the
supply road, as the Cordon's are: the way a truck drives from the landing to the
settlement, a street priced cheapest. Each beat's set piece is laid the
same way (the lost column's holdout, the reservoir yard, the battery's flank
works, the village holdout, the company's ford). The heart's runtime holds
what was laid by name, so a beat that needs a place asks whether it is there
(`heart-map-intent.md`).

**Density.** How built-up the country between the cordon and the heart is:
rural, town or city, from the world's market size. Denser country has more
streets for the lanes to pass through and less open ground for armour; works
yield to what is built.

**Start line.** The near edge of the cordon's farmland, along the farm road the
fields are worked from. Where the attack steps off and, in The Burn, the first
firebreak behind the fire line.

**Lane** (Broad Front). A way into the overgrowth for one column: a track
cleared from the farmland toward the heart along the front, four of them at
about a sixth, a third, two thirds and five sixths of the way across, with the
country road out of the middle of the farmland as the company's own. A lane is
ground, not a corridor: a column may leave it, and the creep has grown over it.

**Logistics base.** The allies' tool shed behind the start line: the layout's
vehicle park, ammunition dumps and supply road (`heart-map-intent.md`), run as
one producer over the shared catalogue (`allied-tools.md`). It holds each tool
as stock and restocks it at a pace, and nothing sets out while the swarm is on
the park, so losing the base is what stops the help. It never fights and never
ends the battle.
Fresh armour and ammunition reach it up the supply road: while the satellite
beside the road has its swarm on the road (a **cut**, `heart.road_cut_<n>`, held
ten seconds past the last body seen), the base restocks no armour pair, mech
lance or truck, though what it holds can still go. Taking that satellite opens
the road for good (F6). The gunship is not road-borne.
- On Broad Front it holds all four of the owner's tools: armour pairs (one per
  lane, at the old wave pace), mech lances in town and city, one gunship gated
  on open air, and one ammunition truck.
- On The Burn, which has no lanes, it holds ammunition trucks alone.
Every truck in the heart comes from it, the B5 beat's included.
The base digs **flak** at its first ammunition dump and its vehicle park, and on
The Burn among the battery (`HeartObjectives.airDefence`), each on the guns'
stock. That is what meets the flier law over the heart: while any of it stands,
the satellites' brood spires launch Harrier packs with their surges and each
Matriarch comes out with her brood (`colony-breach-nouns.md`, Brood spire); with
none standing, nothing flies. The packs hunt wherever the flak does not reach,
which is most of the country: the lanes and the company far more than the base.
The air beats (F3, B3) use these pits rather than digging their own.

**Rounds on hand.** The heart's own guns, the Burn's battery and the flak pits
the beats dig, keep a stock of rounds per place (`GunStock`). Every barrage
shell is a round off its gun, and a dry gun fires one shell in four. A place
under half its rounds calls a truck from the base, and the truck's arrival
refills it, so a base the swarm has cut off shows as a barrage thinning out.

**Allied commander** (Broad Front). The lanes' own commander
(`heart-allied-commander.md`): authored rules, not a general that weighs the
field. It never orders the company and never ends the battle.
- **How a lane goes forward:** from bore to bore as a screened advance, with
  armour screening on open ground and militia screening through woods and
  streets.
- **When to call help:** a lane calls its next machines when it is
  **uncovered** (it has lost a machine), broken, or **stalled** (its formation
  pushed through two contacts at the cap in a row, clearing neither). In town
  and city it asks for a mech lance first; elsewhere, an armour pair. A lane
  never has two calls on the road. A broken lane the company has gone into does
  not call: the company is plugging that gap.
- **When to commit the gunship:** onto a Matriarch on a lane's armour, else the
  bore a broken lane is on, else the bore the most stalled lanes are on. Only
  with the air open. While the air is shut, the base shoots the colonies
  keeping it shut instead, since nothing else in play reaches them.

**Forward observer** (Broad Front). The lanes' waves are militia, but the
world's military attaches one observer to each lane: the radio goes to a
soldier other than the leader of the first team to step off on the lane's
ground (`air-nouns.md`, Forward observer). Through it the military's **air
wing** (two bombers and two fighters, flying in over the farmland behind the
start line) strikes a swarm mass that observer has in sight. It is a different
tool from the base's gunship: the gunship is the commander's one heavy sortie
onto a bore, flown when called; the wing is the observers' own, onto the masses
in front of them, judged on the anti-air the side has seen, so both are
sometimes shot down by colonies nobody had seen. Never within ten cells of a
friendly soldier, never at a Harrier pack overhead. A lane whose observer dies
calls no more air. Now and then the military also sends a roving observer
team (`air-nouns.md`, Roving observer) to stand ten cells behind a lane's
foremost team, on the way back to its foot, the lanes with the most squads in
contact first, so the team follows the push. The Burn has none of either: its
guns are its fire support.

**Ride** (The Burn). A straight strip a forester cuts across a wood to stop a
fire: bare earth five cells wide across the whole approach, two of them, at
about three tenths and a half of the way in. Fire jumps up to three cells of
ground with nothing to burn (`fire-nouns.md`), so a firebreak is five wide. A
ride is cut through any field it crosses, so it is unbroken bare earth. It holds
inside the bloom too: creep over bare earth or paving is a film that does not
burn (`fire-nouns.md`).

**Field margin.** A ragged edge of rough grass and scrub on every field, along
a seeded line that wanders up to three cells either side of the parcel's
straight edge: out over the bare earth beside it, or into the parcel. A parcel
is a rectangle, and a burn that takes its crop and creep would otherwise leave
it showing as one, with no wall to explain the edge; with the margin the line
is organic. A ride is cleared after the
margins grow, so a margin never bridges one.

**Brook** (The Burn). A stream across the approach, deep in the bloom beyond
the village: water five cells wide with sand on either bank and no crown
overhanging it, crossed on foot
at a stony ford every forty-six cells (stone, not sand, so the creep over a ford is a film and the ford holds as a break; shallow water over it, so it is waded slowly and reads as a crossing) and wherever a road bridges it. Nobody walks
through water and creep does not climb it, so the brook is the map's natural
firebreak and a line the creep crosses only at its fords and bridges. It runs in a shallow valley: raised banks either side,
climbed from the country, broken at every ford and road, so a crossing is a
descent to a ford (`elevation-nouns.md`, Ridges). A road
over it is its bridge; where the settlement's streets cross its line it runs
under them.

**Battery** (The Burn). The guns that fire the barrage: Locust rocket-artillery
batteries dug in across the start line, the company's own. Every shell is a
round fired from the gun nearest its point, lobbing in on a high, slow arc, so
the warning is the rounds themselves coming in: a player reads the walk by
watching where they land. The guns can be killed, and a battery with no gun
left falls silent. They also defend themselves against what comes at them.
Each gun is dug into a **gun pit**: a ring of sandbag round it, open at the back
toward the start edge, manned by a fireteam of gunners who hold the pit under
the battery's own garrison order and never join the push. The crews are an
attached detachment on the allied side, so they never keep a broken company's
battle alive. The
pits are what the swarm has to come through to reach the guns, and **a battery
overrun, every gun of it dead before the heart is down, is a loss** (owner,
2026-10-22): without guns there is no barrage to push the burn under.

**Allied front** (The Burn). The battle's whole width of attack, divided
across into **sectors**: the company holds one, allied battlegroups the rest,
and the whole front follows the barrage together. The company's is a local
task inside a larger push, not the push itself (owner, 2026-10-22: "a proper
battlefield with the player's forces playing a more localized role"). Broad
Front stays lanes into overgrowth; The Burn's front is a walking barrage with
fire. `-Dbattle.heart.burnFront=false` is the control: the company alone
behind the barrage, which lost on every measured seed whatever single lever
was tried (following the guns, frontage, forming on the farmland, more
shuttles, calmer bores, weaker Spitters). The swarm's numbers beat 24 marines,
not any one creature.

The front is won by **sustained** strength, not an opening blow. Measured on
seeds 202-606: a front committed only while the barrage walks breaks after
it ends, while the mothers keep surging. Infantry waves after the barrage
take the satellites, but the mothers fall only once fresh armour keeps coming
up too. The barrage stays short of the mothers. Walking it onto them measured
worse, because the front is held back behind the guns for longer.

**Armour leads its infantry on The Burn.** A rifleman, a gun crew or a mech
holds its fire while a friend stands in its lane, so armour posted behind its
own militia mostly waits. The Burn's battlegroup armour posts four cells
ahead of its infantry rather than four behind
(`-Dbattle.heart.burnArmourBehind`). Each hull's slot is a post that faces the
advance, so allied command stands it on that line and never ahead of it
(`allied-command.md`, posts and stands); a hull whose slot it reports
unservable is moved to a second rank one spacing deeper, until the line moves
to a new aim. A fresh tank and technical come up
eight times, two minutes apart (`-Dbattle.heart.burnFrontArmourWaves`). On
Broad Front those waves are the logistics base's stock, called up by need
(Allied commander; `-Dbattle.heart.armourByNeed=false` is the timetable).
Broad Front's armour stays behind (`-Dbattle.heart.broadArmourBehind`),
because its lanes march into the overgrowth before the armour can follow.

**Battlegroup** (The Burn). One allied sector's force: the planetary militia
committed in strength (owner, 2026-10-22: "no need to be stingy with infantry
and vehicles"). Its infantry comes in **waves**: the first steps off at the
opening, one more as each of the barrage's first lines lifts, and more at a
steady cadence once the barrage has ended, feeding the push on the mothers. A
wave marches to wherever its lane is aimed now and joins the lane there. On
Broad Front a lane that already stands has its next wave march to the lane
itself, where its militia stand, and go on with it, rather than past it to the
bore. A column held short of its bore by the fight on its own lane's ground has
**reached the front** and joins there (`cordon-nouns.md`, Allied column): a bore
spawns its own defenders, so the 12 cells of its mouth are ground no column
reaches except by winning. Its
armour is an escort APC, two technicals, two breakthrough tanks and a Hound
pair, with a fresh tank and technical coming up every two minutes once the
barrage has ended. It follows the barrage on its own line across the front, armour stepping with
its infantry. Its armour is the allied commander's (`allied-command.md`):
the mission names each machine's post and the commander drives and fights it, a
machine the woods bar holding as far forward as it can drive. It owns its sector's satellites, then goes on to the
nearest mother. It is `ALLY` (`ai-nouns.md`, Sides): it never ends the battle
and never touches the roster.

**Company sector** (The Burn). The band of the front the company holds,
centred on its landing: its own satellite first, then the nearest mother.

**Barrage plan** (The Burn). The battery's timetable, frozen at the
briefing: a sequence of **barrage lines** across the approach, each a depth
into the map, with the second it opens and the seconds it fires before it
**lifts** to the next. Shells fall at a steady rate, scattered along the
current line. A round carries no danger marker, as a called strike does:
nothing is drawn on the ground, and the battle announces the guns by the line,
in its beats. How the walk should read beyond the rounds (ranging smoke, a
marker) is open (`heart-barrage-read`). A line may fire incendiary shells, which light what they
land on through fire's ignition seam. The plan ends at the **last line**, short
of the heart: the batteries' reach, or the ammunition the briefing names. The
Burn's plan follows **firebreaks hold** (the owner's pick): the fire stops at
each ride and the brook, so the guns light it and relight it with incendiary
shells on a line just clear of each firebreak's far side, and lift high
explosive a sixteenth of the approach at a time between them, to a last line
seven tenths of the way in, some five minutes of shooting; the country is dry and the
wind blows toward the heart for the whole battle. `WalkingBarrage` is general:
any battle with a ground to map depth onto may carry one.

**Burn line** (The Burn). The company's front, which is a firebreak moving:
the fire the barrage and the company light ahead of it, and the burnt ground it
leaves behind, which no creep re-takes for minutes (`fire-nouns.md`, Firebreak).

**Danger close.** How near the barrage a squad may stand: the field mortar's
friendly veto at launch (`FieldMortarService.friendlyUnderRound`) is the model,
widened by how far a body walks while the shell is in the air.
A squad advancing behind the barrage keeps outside it; a shell never falls on a
cell the veto refuses.

## The map

Both variants are laid out by one plan, `HeartCountry`, in one of two **forms**,
on the bloom world's own ground (its `SurfacePalette`, with pooled maps for
each): the heart is the same kind of place on every world, and a fixture names
a biome, never a market.

| | **Broad Front** | **The Burn** |
| --- | --- | --- |
| Size | 560x336 (`MapScale.CONQUEST`), wide | 400x240 (`MapScale.COLONY`), deep |
| Start edge, heart edge | south, north | west, east |
| Farmland | the open-field landing's country along the long edge | the same along the short edge |
| Settlement | a market town in the middle of the map | a village near the start, at the infection's edge |
| Woods | 70% of the wild ground | 80%: the fuel the burn runs through |
| Cleared ground | a track along each of four allied lanes | two rides across the approach |
| Water | none | the brook, about three fifths of the way in |
| Bores | 2-3 mothers round the workings, 5 satellites at 52-72% depth, one per fifth of the front, except the road satellite: the one nearest the supply road's far end stands beside the road instead (F6) | 2-3 mothers, 3 satellites from 44% to 74% depth |

A plan states its country through its **landscape** (`mapgen-nouns.md`,
Authored forests: a plan's landscape): how much of the wild ground is wood,
which tracks are cleared before the woods grow, which brooks run across it.

**The plan is the reachability proof.** Forest crowns are impassable on the
grid, so a heart country could seal a mother behind a wood. The heart's sites
are chosen only on ground walkable from the start line (`HeartBores`), and a
map that cannot seat every mother and satellite on such ground re-rolls the
seed. Each form plays from its own pool of vetted maps (`story-map-pools.md`),
seeds whose sites seat on the first generation, so the campaign never reaches
the re-roll and `commanderEvidence -Pmission=heart` plays every pooled map of
both forms. Fire is never counted on to open the way to a mother: a wood that burns
or is felled opens more ground, never the only way in.

**The cordon's farmland stays green at the start.** Satellites stand deep
enough that a maw's reach does not carry its creep onto the landing field; the
creep comes up to the cordon's wire and, on some seeds, over a field or two.

## Flow

Both variants end the same way: **the heart is down when every mother bore of
it has collapsed** (the company wins), and the company **loses** when it is
broken or withdraws, or, in The Burn, when its battery is overrun. Bores are fought, not scripted: nothing in either flow
collapses a bore by fiat.

### Broad Front

1. **Step-off.** The company comes down by shuttle in the cordon's farmland at
   the middle of the start edge (`landfall`). Four allied columns stand on the
   start line, one at the foot of each lane, and go up their lanes together;
   the company's own lane is the country road. Each lane is the planetary
   militia in strength, as The Burn's battlegroups are: four teams a wave, a
   wave a minute, an escort APC, a technical, a breakthrough tank and a Hound
   pair, and a fresh tank and technical every two minutes. With no barrage to
   wait for, the company holds its landing for its first two minutes, so it
   goes for its satellite with the lanes up beside it rather than alone. Its
   own sector gets armour too, a breakthrough tank and a Hound pair stationed
   on its target and a fresh tank every two minutes, so no part of the front
   goes in without it.
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

   **The company falls back.** Its own satellite down, the company does not go
   on alone to a mother. Its own is the satellite nearest its line: the bands
   overlap, and a satellite an allied lane's line lies nearer is that lane's,
   even inside the company's band. On seed 5 (2026-10-24) the company took
   lane 3's satellite beside the mothers for its own, went for it as the heart
   woke, and was wiped there on every biome. It falls back to its landing and holds there while
   any satellite still stands, breaking off from whatever it is fighting (a squad
told only to defend a cell far behind it fought where it stood). When the last one falls it goes in on a mother
   only with the converging lanes round her (`HeartAssaultCommand.goesIn`, owner's pick
   heart-mother-push, 2026-10-25: wait), waiting for them where it stands rather
   than back at its landing, and stays in once it has; going in
   ahead of them, it was wiped short of her. Its satellite sits nearest the heart, so the
   heart's waking surge and the first Matriarchs come for it first. Holding
   the taken ground kept more of the company alive but still lost it there on
   one seed; falling back won every seed. The company is the spearhead onto
   its satellite and the backstop after.

   **It helps the lanes** (`battle.heart.helpStranded`, on trial). The well-found
   company that falls back stood unaware at its landing for half the battle while
   flank satellites the lanes could not take stood to the cap. So, its own
   satellites down, it goes to the nearest one still standing, whichever lane's
   it is, and to the mothers only when none does. Off, it falls back as above.

   Controls: `-Dbattle.heart.broadStrength=false`, `-Dbattle.heart.broadFormUp=0`,
   `-Dbattle.heart.broadCompanyArmour=false`, `-Dbattle.heart.broadWaitForLanes=false`
   and `-Dbattle.heart.broadFallBack=false`. Measured on seeds 202-606:
   - lanes' strength alone: 1 of 5 won;
   - with the form-up: 2;
   - with the company's armour: 3;
   - holding its taken ground: 3, with far more of the company alive;
   - falling back to its landing: 5.
   Sending the lanes across to the company's satellite once their own fell
   measured worse.

   Those readings predate the friendly-fire rule. Once allies held fire
   through each other, allied damage on the swarm fell by about a quarter,
   and the eight armour waves won back 4 of 5. Main's later combat changes
   cost more: rounds landing on a hull's footprint flipped seed 404 to a
   loss. On main of 2026-10-23 Broad Front wins 1 of 5 and The Burn 2 of 5
   inside the fifteen-minute evidence cap. Retuning for that waits until
   the combat changes settle (owner, 2026-10-23: no overtuning yet).
5. **Heart down.** The last mother collapses: `heart_down`, the win. Allied
   lanes broken are a cost on the outcome, never a loss.

The allied columns are scripted (`HeartLanes`): a wave of militia teams at the
foot of each of the four lanes steps off at the opening, and the rest follow
on the lane's cadence, each marching to wherever its lane is aimed then. The
first wave marches to its lane's first satellite as the Cordon's scripted columns march to a redoubt
(`cordon-nouns.md`, Allied column). Once a column has arrived it is the lane's:
when the bore it is on falls it goes on to its lane's next satellite, then to
the nearest living mother. An allied lane's band is about a tenth of the front's
width either side of its line, so neighbouring lanes do not take each other's
satellites, and the company's middle satellite is the company's. The general
allied column and an allied line commander (`cordon-allied-columns.md`) will
replace the scripting when they exist.

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
`mother_collapsed_N`, `heart_down` and `company_broken` (`battery_overrun`
instead in The Burn when the lost battle's guns are all dead).

| Key | Variant | When |
| --- | --- | --- |
| `landfall` | both | the first Marine is on the ground |
| `heart_surge` | Broad Front | the first satellite falls and the mothers wake |
| `lane_arrived_N` | Broad Front | lane N's column reaches its first bore |
| `lane_broken_N` | Broad Front | lane N below a third of the militia its waves arrived with, counted as each came in |
| `gunship_called_N`, `gunship_lost_N` | Broad Front | the commander's Nth sortie is granted; the Nth sortie is shot down |
| `line_lit` | The Burn | the fire line takes |
| `barrage_line_N`, `barrage_lifted_N` | The Burn | a line opens (after its warning); it lifts |
| `breakout_<bore>` | The Burn | a hurt bore surges (observer-only, recurring) |
| `barrage_ended` | The Burn | the last line lifts; the guns are done |
| `battery_overrun` | The Burn | the battle is lost with every gun dead |

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

**A well-found company.** The heart is fought late in the campaign, so the
company it stands up itself is a strong one (owner, 2026-10-25: "We should
assume they have fairly powerful equipment and weapon cards + vehicle's /
mechs"): veterans and elites in heavy suits with Milspec or Masterwork issue,
each squad carrying a sapper's satchel, a squad automatic, a flamer, an
anti-materiel rifle and a launcher (`InfantryLoadoutRolls.lateCampaignSquad`).
The satchel is what collapses a bore from beside it; without one the company
only shot the mothers down from thirty cells, and lost a third of itself doing
it (Broad Front frozen 5, 2026-10-25). A campaign launch lands the
player's own company over those seats, and its machines through Mech Support;
a fixture's `declaredMechDrops` is how debug launches and evidence bring down a
Marine lance, called early and claimed by the company's command, which moves it
with the squads. The heart evidence fixtures declare one.

The company plays the heart as Purge under its own commander
(`HeartAssaultCommand`): it goes in together on one bore at a time, first the
satellites standing in its lane, then the nearest living mother, opening each as
the thing to breach. Its lane is the band of ground either side of the line it
advances along from its landing: about a seventh of the map's width either side
on Broad Front, so the other lanes' satellites are the allies' work, and the
whole approach on The Burn. The director needs no civilians to cover here: it
spawns only out of the heart's bores from the first tick, and nothing closes it
but their collapse.

The squads go in on a **frontage**, each to its own cell spread across the
advance, never all to one: a bunched company is what a Spitter's lobbed acid
finds. On The Burn the company **follows the barrage**: while its bore lies
past the line that is firing, it forms on that frontage a safe distance short
of the line (the shell's danger-close veto plus the line's spread, so the
company never silences the guns) and comes up as each line lifts; it goes for
the bore only once the barrage has passed it. The hold is read off the
timetable frozen at the briefing, never off the shells. It needs no fire rule:
the burn runs downwind ahead of the line and is out by the time the company
crosses its ground.

## Settled choices

The owner's picks (2026-10-22, `heart-flow-options`): a satellite's fall wakes
the heart; a mother's fall makes the survivors surge; the barrage and the
company both light The Burn's line; a hurt bore surges. On The Burn's pace (`heart-burn-pace`),
firebreaks hold: the fire stops at each ride, the brook and roads, and is
relit past each. Rides, roads and fords hold under the bloom because fire treats
creep over bare earth or paving as a film (`fire-nouns.md`). High explosive lights
dry tinder as well, so where the brook meanders back toward the guns the line
before its relight line can light the far bank a little early.

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

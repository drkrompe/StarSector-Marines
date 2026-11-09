# Player raid fork: live pass

Status: IN PROGRESS — built and unit-tested; the owner's live pass remains.

Written: 2026-11-07

Read `raid-resolution.md` in full first.

## Goal

Confirm in the real game, with and without Nexerelin, that leading a raid with
Marine Operations commits through the game's own raid command exactly as the
model says, and that launching it as usual is unchanged.

## Live pass

`playBattle`'s planet menu (`build-and-run.md`) already plays the mod's side of
every item below against the real `rules.csv` rows, with and without Nexerelin's,
and traces each match's scores. What only the game can show is the real raid
command's own effects: loot, disruption, reputation, cooldown and Nexerelin's
listeners.

Run each with Nexerelin enabled, then again with it disabled.

1. **Standard path unchanged.** Set up a raid for valuables, Launch raid,
   choose Launch the raid as usual. Losses roll, loot shows, reputation and
   stability change as before.
2. **Led raid, won.** Same setup, choose Lead the raid. Back out of the
   briefing once: the fork returns and nothing is committed (no cooldown, no
   reputation change). Then fight and win. On closing, the raid result shows
   "no casualties", the spoils screen opens, and the market is marked raided.
3. **Led raid, lost.** Retreat or lose. The raid is on record (hostile timeout,
   reputation, cooldown) and takes nothing.
4. **Disrupt.** A disrupting raid's briefing targets the chosen industry; a won
   battle disrupts it for the command's own duration.
5. **Nexerelin listeners.** With a Nexerelin disrupt-a-competitor mission
   active, a won led raid on its target completes the mission; a lost one does
   not.
6. **Setting.** Turn "Ask before raids" off on the Settings page: the next
   raid launches as usual without the fork, across save and reload. Turn it
   back on: the fork returns.
7. **Unsupported.** A raid from a station's market, and the story-point secret
   raid, never show the fork.
8. **Rule precedence.** The fork's row (score 3) wins over Nexerelin's (2) and
   vanilla's (1): check `starsector.log` for the matched rule.

## Done

Every item passes on both setups. Then fold this story into
`raid-resolution.md`, add a row to `shipped.md`, and delete it.

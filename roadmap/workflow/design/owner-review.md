# Showing the owner work, and asking the owner questions

Status: ACTIVE

Written: 2026-10-24

How a set of images reaches the owner, where it is stored, and how a decision
is put to them. `CLAUDE.md` keeps the rules; this is the procedure. Read it
before delivering a review set, a preview, or a question.

## Google Drive: storage, not delivery

Rendered output and other large regenerable files that should reach the other
machine or the owner's phone go to the shared Google Drive folder, never into
git: `G:\My Drive\starsector` on Windows and
`~/Library/CloudStorage/GoogleDrive-<account>/My Drive/starsector` on the Mac.
It syncs in about half a minute. A build copies what it needs locally before
reading it.

### Review sets

Every set of images or video put to the owner for a decision is stored under
`starsector/review/`, by asset, then by run:

```
review/<asset>/<YYYY-MM-DD>_<what>_<machine>/
    NOTES.md
    01-<view>.png  02-<view>.png  ...
    <subfolder>/   (only when a run has natural groups: states, close-ups)
```

- `<asset>` is the package's slug without its suffix (`second-wind`, `hound`,
  `mudskipper`, `legion`, `fighters`, `cinematic-<shot>`): one folder per thing
  the owner reviews, whichever session or machine made it.
- `<what>` names the pass in a few hyphenated words (`pass2-detail`,
  `damage-round3`); `<machine>` is `windows` or `mac`.
- **One run, one writer.** A session creates a new run folder and writes only
  into it. It never adds to, renames or deletes another run's folder, and a
  re-render after feedback is a new run. That keeps two machines from producing
  Drive conflict copies.
- `NOTES.md` is short: the commit the renders were built from, what changed
  since the last run, what to look at and decide, and any measured numbers.
  Image names start with a two-digit order matching the notes.
- Images obey the preview rule (1920x1080, 100% scale). Copy finished images
  only, never frame sequences or scratch.
- Create only your own run folder (and the asset folder if missing). Drive
  shows a second same-named folder made on the other machine as `name (1)`; if
  one appears, move its contents into the original and remove the twin.
- After posting a run, prune its asset:
  `python3 tools/review_prune.py --asset <asset> --apply` (`python` on
  Windows). Each asset keeps its three newest runs plus any marked with a
  folder name ending `_keep` or a `KEEP` file inside; deletes go to Drive's
  trash for 30 days. Without `--apply` it only reports.

### Owner-facing images come from the look preset

A behaviour scene's loops are drawn on a bare arena with faction dots, labels
and a caption, and without smoke, contrails or shadows: evidence for a verdict,
not a picture of how anything looks. Give the scene suite a `lookShots()`
(`LookReview`: real generated ground, effects and shadows on, no annotations,
play zoom and close) and show `build/snapshots/<scene>/look/`;
`-Dsnapshot.lookOnly=true` renders only those. See `snapshot-suites.md`.

## Delivering: show the pictures

The run folder is not delivery. When a set is ready, put the images themselves
in front of the owner: send them into the chat (`SendUserFile`,
`display: "render"`) or publish them on a picks page. "Review in G drive" or a
bare folder path does not count. A session started by another session gives
its coordinating session the image paths, and the coordinator shows them.

**A finished-work preview that asks nothing** (a GIF of a feature, a set of
stills) also goes on the parent's picks page, in its Previews section: a
`previews.json` of `[{id, area, date, title, note, lead?, shots:[{file,label}]}]`,
newest first, passed to `build.py --previews`, with the media under
`media/<preview id>/`. A shot may be an `.mp4`, which plays in place with its
sound; an Artifact file is at most 15 MB, so encode a 30 s clip near 3.5 Mb/s.

## Questions: the picks page

Every decision the owner has to make goes on a picks page as picture cards,
never only in chat: a question asked in a chat message is lost when the owner
switches sessions.

**One picks page per top-level parent session.** Each top-level session in the
sidebar owns one page for its bundle of child sessions; never post to another
parent's page. A page is an Artifact built from `tools/picks/template.html` with
`python tools/picks/build.py --title "<parent> picks" questions.json out.html`,
published with the `db` capability; each answer is a `db` document
`picks/<question id>` holding `{choice, note, at}`. A parent with no page yet
creates one with its first question and adds a row to `tools/picks/pages.md`
(parent session title, page URL) in its next commit.

- **A question is an `options.json`**: `[{id, question, options:[{key, label,
  file, note}]}]`, one 1920x1080 image per option rendered on the real scene
  (real ground, real buildings, play zoom), a short `note` on what each option
  does in play, and `(recommended)` in the label you would pick. A question about
  sound or motion gives each option a `video` (an `.mp4` under 15 MB) the card plays
  in place with its sound, the image as its poster. Yes/no and
  "how big" questions get pictures too.
- **Sub-sessions raise it to their parent.** A session started by another
  session never posts or asks the owner itself: it sends its parent the
  `options.json` path, the image paths and what it is building on meanwhile.
  Each parent passes it up until the top-level parent, which posts it, reads
  the answer and sends it back down.
- **The top-level parent posts.** Read its own page (`Artifact` `read`), add the
  question to the page's `QUESTIONS` array and its images under
  `media/<question id>/`, republish to the same URL (merging, never
  overwriting, if the publish reports a newer version), and read the answer from
  `picks/<question id>`. Use JPEG for option images: a page holds at most
  256 MiB across its versions.
- **Don't wait on the answer.** Build on the recommended option and switch if
  the owner picks otherwise. Tell the owner in one line that a question is up.

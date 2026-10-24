# Colony Breach — Preview Media Contract

Status: ACTIVE

Written: 2026-10-21

The owner follows Colony Breach through a living preview page (a claude.ai
Artifact the orchestrating session publishes and keeps current). Sessions that
land work produce **media**; the orchestrator assembles the page from it. This
contract is what a producer writes and what the orchestrator reads.

## Where media goes

Each producer writes **one new run folder** on the shared Drive, following the
one-run-one-writer rule in `owner-review.md` (never add to, rename or delete another
run; a re-render is a new run):

```
G:\My Drive\starsector\review\colony-breach-preview\<YYYY-MM-DD>_<what>_<machine>\
    preview.json
    NOTES.md
    <media files>
```

`<what>` is a few hyphenated words (`preview-1`, `bores-spawning`);
`<machine>` is `windows` or `mac`. Mac path:
`~/Library/CloudStorage/GoogleDrive-<account>/My Drive/starsector/review/colony-breach-preview/`.
After posting, prune: `python tools/review_prune.py --asset colony-breach-preview --apply`.
Tell the orchestrator the run folder path.

## preview.json

A JSON array; one entry per media file:

```json
{ "id": "colony-whole-map", "section": "colony", "title": "Whole-map battle",
  "caption": "One or two sentences on what the viewer is looking at.",
  "file": "01-colony-whole-map.gif", "kind": "gif", "commit": "f42ac7048" }
```

- `id` — slug, unique within the run. A later run may reuse an id to supersede
  an earlier run's entry; the orchestrator shows the newest.
- `section` — one of `colony`, `swarm`, `bores`, `air`, `defences`, `audio`, `lore`.
- `kind` — `image`, `gif` or `audio`. `file` is relative to the run folder.
  An audio entry may add `"fileMp3"` naming its `.mp3` copy.
- `commit` — the commit the media was built from (media copied from an earlier
  run keeps that run's commit).
- Captions state what is shown, not whether it is good.

## Limits

- Stills and GIFs are 1920x1080 at 100% UI scale (the preview rule in
  `CLAUDE.md`); each GIF under 8 MB.
- Audio ships as `.ogg` **and** an `.mp3` copy (iPhones will not play Ogg):
  `ffmpeg -i in.ogg -codec:a libmp3lame -q:a 4 out.mp3`.
- Name files with a two-digit order prefix, as other review sets do.

## Renders never enter git

Media is regenerated output. It lives on Drive only; the run's `NOTES.md`
records the commit it was built from. Only this contract, scripts and suites are
tracked.

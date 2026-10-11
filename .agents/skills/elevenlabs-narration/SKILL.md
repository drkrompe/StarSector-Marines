---
name: elevenlabs-narration
description: Prepare or generate ElevenLabs narration for Starsector Marines mission briefings, character dialogue, and story radio lines, including voice direction and local API credential setup. Use for this project's ElevenLabs voice production, not ordinary mission prose or unrelated sound effects.
---

# ElevenLabs mission narration

Generate recordings during authoring and package approved audio for offline
playback. Follow CLAUDE.md for repository work and read the applicable mission
noun doc before changing story content or playback behavior. This skill does
not itself authorize paid generation, installation, publishing, or deployment;
use the scope already authorized in the conversation.

## Read current guidance

Before preparing synthesis inputs, read the relevant model sections of
[ElevenLabs' best practices](https://elevenlabs.io/docs/overview/capabilities/text-to-speech/best-practices).
Give the user that link when explaining voice direction. Keep this short guide
as project guidance rather than copying the vendor manual. Consult the current
[models](https://elevenlabs.io/docs/overview/models) and
[official CLI](https://github.com/elevenlabs/cli) for model availability,
authentication, and command syntax. If unavailable, disclose that current
behavior is unverified instead of inventing parameters.

Model-specific reminders, checked 2026-10-11:

- For v4, use the v4 prompting section rather than applying the page's older
  SSML examples. V4 does not support SSML break tags; use punctuation, text
  structure, and supported audio tags for delivery and pauses.
- V4 supports inline IPA between forward slashes. Audition important names
  with the selected voice; do not assume one pronunciation works for all voices.
- Make delivery tags describe the intended vocal quality clearly. V4 can
  generate sound effects too, so ambiguous directions may add unwanted sounds.
- Expand ambiguous numbers and abbreviations in synthesis text while retaining
  the intended wording in subtitles. Recheck model-specific settings before
  carrying settings over from a different model.

## Credentials stay on the author's machine

Use `ELEVENLABS_API_KEY` from the process environment. On Windows, the preferred
persistent setup is **Edit environment variables for your account** → **User
variables** → **New**: name `ELEVENLABS_API_KEY`, value entered privately by the
user. Fully restart Codex and terminals afterward so new processes inherit it.
This keeps the key outside every checkout and available across session worktrees.
User environment variables are not an encrypted vault; local processes running
as the user can read them.

Never request the key in chat, print its value, dump the environment, put it in
command arguments, or write it into source, skill files, manifests, generated
metadata, logs, screenshots, or shipped assets. Check presence with a boolean
only. Do not make live requests just to test credential setup unless requested.
If an existing app process must load the Windows user variable, assign it to
the process environment without outputting the value.

An optional local `.env` also works with the official CLI. It is ignored by
this repository, along with `.env.*`, except `.env.example` for placeholders.
Keep any such file outside `mod/`, which is copied wholesale during deployment.
Verify the exact file with `git check-ignore -v -- <path>` and ensure it is not
already tracked. Gitignore prevents ordinary accidental adds, not force-adds
or previously tracked secrets. Never commit a real key in an example file.
If a key is exposed or committed, revoke/rotate it; deleting the current file
does not invalidate it or erase Git history.

## Produce and integrate recordings

- Preserve stable line IDs, speaker-to-voice assignments, clean subtitle text,
  synthesis text, model ID, settings, and selected take. Store no credentials
  with that metadata. Audition recurring characters with a small authorized
  sample before generating their whole script.
- Keep approved source takes. Regenerate only requested or changed lines;
  ordinary builds must not invoke a paid API. Bound retries and report failed
  generations rather than repeatedly spending credits without progress.
- Use one clip per independently triggered beat. Where a continuous exchange
  needs shared timing, consider Text to Dialogue and retain the speaker mapping.
  Keep radio processing separate from clean speech unless a baked effect is
  deliberately requested.
- Inspect the existing audio manifest at
  `asset-pipeline/src/tool/resources/audio/audio.json` and its `processAudio`
  implementation before adding assets. It encodes source clips into generated
  `mod/sounds/`; register playback IDs in `mod/data/config/sounds.json`.
  Preserve source audio outside the shipped `mod/` tree.
- `StoryBeatLog` publishes battle facts with stable IDs. Playback, selection,
  queueing, and overlap handling belong to presentation, not the simulation.
  Keep readable text available when audio is absent or disabled.
- Check approved takes for names, delivery, unwanted sounds, clipping, and
  intelligibility against the battle mix. Report what was actually auditioned;
  successful file generation alone does not establish performance quality.

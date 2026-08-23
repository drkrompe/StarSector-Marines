# U1b — Intrinsic text and typography roles

Status: IN PROGRESS — implementation and headless verification complete; live acceptance pending
Written: 2026-08-23

Read `ui-nouns.md` and `ui-toolkit.md` first. This is a live-acceptance correction to U1 and U3.

## Problem

The first workbench assigned button widths by hand and painted every label through one display
face. “HIGH CONTRAST” received a 154 px border box although its glyph advance plus frame required
170 px, while the footer left a 20 px line box only 14 px of content height. The result was clipped
copy, uncertain centering, and a heading face carrying too much ordinary interface text.

## Outcome

Single-line text participates in retained layout through one measurement seam shared with paint.
Auto-sized text in a row pays its content width plus padding and border; an authored width still
wins. The Marine Ops theme distinguishes a regular body face from a display heading face, centers
button text explicitly, and reserves authored all-caps copy for headings.

## Scope

- Bitmap-font width and line-height measurement behind a retained text-measurement seam.
- Intrinsic single-line width and height when the corresponding authored size is `auto`.
- Layout invalidation when retained text changes.
- CSS `text-align` with start, center, and end values.
- A regular body/button face and a bold display heading face in the workbench theme.
- Removal of hand-tuned footer button widths and repair of its vertical frame budget.

Rich text, wrapping, font synthesis, font-size, font-weight selection, and the complete CSS flex
shrink algorithm remain outside this correction.

## Acceptance

- “High Contrast” is fully visible without a declared width.
- Auto row text receives measured advance plus its frame; an explicit CSS width wins.
- Changing the text of an auto-sized element schedules exactly one layout pass.
- Button text uses the measured line box for horizontal and vertical placement.
- Body copy and buttons resolve to the regular face; semantic headings resolve to the display face.
- Headings may remain all caps, while controls, values, and prose use ordinary casing.
- Focus, keyboard actions, clipping, scrolling, theme replacement, and transitions remain green.

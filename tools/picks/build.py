"""Build a parent session's picks page from the shared template.

    python tools/picks/build.py --title "Rav Bloom picks" questions.json out.html

questions.json is the page's whole QUESTIONS array: a list of
{id, question, multi?, options: [{key, label, file, note}]}, with each `file` the
published path of its image (media/<question id>/<name>.jpg); `multi: true`
lets the owner pick any number of options (stored comma-joined).

--previews takes a previews.json: things for the owner to look at that ask no
question (a GIF of a finished feature, a set of stills), newest first, as
[{id, area, date, title, note, lead?, shots: [{file, label}]}] with each `file`
under media/<preview id>/; `lead: true` draws the first shot full width. Publish
out.html with the Artifact tool, declaring the `db` capability, and pass the
images through `files`. See `owner-review.md` (Questions: the picks page).
"""
import argparse
import json
from pathlib import Path

TEMPLATE = Path(__file__).with_name("template.html")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--title", required=True)
    parser.add_argument("--blurb", default="Open look decisions, as pictures. Tap the option you want under "
                        "each question; tap it again to clear it. Add a note if none is quite right. Choices save "
                        "as you go and go back to the session that owns each piece.")
    parser.add_argument("--previews", help="previews.json: sets to look at, shown below the questions")
    parser.add_argument("questions")
    parser.add_argument("out")
    args = parser.parse_args()

    questions = json.loads(Path(args.questions).read_text(encoding="utf-8"))
    page = TEMPLATE.read_text(encoding="utf-8")
    page = page.replace("__TITLE__", args.title)
    previews = json.loads(Path(args.previews).read_text(encoding="utf-8")) if args.previews else []
    page = page.replace("__BLURB__", args.blurb)
    page = page.replace("__LKEY__", "picks-" + "".join(c if c.isalnum() else "-" for c in args.title.lower()))
    page = page.replace("__QUESTIONS__", json.dumps(questions, ensure_ascii=False))
    page = page.replace("__PREVIEWS__", json.dumps(previews, ensure_ascii=False))
    Path(args.out).write_text(page, encoding="utf-8")


if __name__ == "__main__":
    main()

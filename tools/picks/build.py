"""Build a parent session's picks page from the shared template.

    python tools/picks/build.py --title "Rav Bloom picks" questions.json out.html

questions.json is the page's whole QUESTIONS array: a list of
{id, question, multi?, options: [{key, label, file, note}]}, with each `file` the
published path of its image (media/<question id>/<name>.jpg); `multi: true`
lets the owner pick any number of options (stored comma-joined). Publish
out.html with the Artifact tool, declaring the `db` capability, and pass the
images through `files`. See "Questions for the owner" in CLAUDE.md.
"""
import argparse
import json
from pathlib import Path

TEMPLATE = Path(__file__).with_name("template.html")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--title", required=True)
    parser.add_argument("questions")
    parser.add_argument("out")
    args = parser.parse_args()

    questions = json.loads(Path(args.questions).read_text(encoding="utf-8"))
    page = TEMPLATE.read_text(encoding="utf-8")
    page = page.replace("__TITLE__", args.title)
    page = page.replace("__QUESTIONS__", json.dumps(questions, ensure_ascii=False))
    Path(args.out).write_text(page, encoding="utf-8")


if __name__ == "__main__":
    main()

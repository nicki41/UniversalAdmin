"""MkDocs hooks for the documentation website (mkdocs.yml -> hooks).

The Markdown in docs/ is written to be read on GitHub, so it links to files
outside docs/ - Java sources, the README, CHANGELOG.md, ROADMAP.md. On the
website those would be dead links. Before a page is rendered:

* a link that leaves docs/ points to the file on GitHub instead,
* changelog.md and roadmap.md get the content of CHANGELOG.md and ROADMAP.md
  from the repository root, with their links rewritten the same way.
"""
import posixpath
import re
from pathlib import Path

REPOSITORY = "https://github.com/universaladmin-mc/UniversalAdmin"
ROOT = Path(__file__).resolve().parent.parent
GENERATED = {"changelog.md": "CHANGELOG.md", "roadmap.md": "ROADMAP.md"}

# [text](target) - not images' inner brackets, not autolinks; target without spaces.
LINK = re.compile(r"(?<!!)\]\(([^)\s]+)\)")


def _rewrite(markdown, page_dir):
    """Rewrites every relative link of a page in docs/<page_dir> that leaves docs/."""

    def replace(match):
        target = match.group(1)
        if re.match(r"^[a-z]+:|^#|^/", target):
            return match.group(0)
        path, _, anchor = target.partition("#")
        resolved = posixpath.normpath(posixpath.join("docs", page_dir, path))
        if resolved.startswith("docs/"):
            return match.group(0)
        kind = "tree" if (ROOT / resolved).is_dir() else "blob"
        url = f"{REPOSITORY}/{kind}/main/{resolved}" + (f"#{anchor}" if anchor else "")
        return f"]({url})"

    return LINK.sub(replace, markdown)


def _from_root(markdown):
    """A root file's links, for the page that shows it at the top of docs/: docs/x.md -> x.md, the rest to GitHub."""

    def replace(match):
        target = match.group(1)
        if re.match(r"^[a-z]+:|^#|^/", target):
            return match.group(0)
        if target.startswith("docs/"):
            return "](" + target[len("docs/"):] + ")"
        path, _, anchor = target.partition("#")
        resolved = posixpath.normpath(path)
        kind = "tree" if (ROOT / resolved).is_dir() else "blob"
        return f"]({REPOSITORY}/{kind}/main/{resolved}" + (f"#{anchor}" if anchor else "") + ")"

    return LINK.sub(replace, markdown)


def on_page_markdown(markdown, page, config, files):
    src = page.file.src_uri
    if src in GENERATED:
        return _from_root((ROOT / GENERATED[src]).read_text(encoding="utf-8"))
    return _rewrite(markdown, posixpath.dirname(src))

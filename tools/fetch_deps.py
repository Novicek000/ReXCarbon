#!/usr/bin/env python3
"""Fetch exact upstream sources, including the pinned linker submodule."""
import io
import pathlib
import tarfile
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
PINS = {
    "libadrenotools": ("bylaws/libadrenotools", "8fae8ce254dfc1344527e05301e43f37dea2df80"),
    "libadrenotools/lib/linkernsbypass": ("bylaws/liblinkernsbypass", "aa3975893d83ef1bc84c321ec60c65fbf1287887"),
}

def fetch(path, repo, sha):
    dest = ROOT / "third_party" / path
    marker = dest / ".source-commit"
    if marker.exists() and marker.read_text().strip() == sha:
        return
    data = urllib.request.urlopen(f"https://codeload.github.com/{repo}/tar.gz/{sha}", timeout=60).read()
    with tarfile.open(fileobj=io.BytesIO(data)) as archive:
        for member in archive.getmembers():
            parts = pathlib.PurePosixPath(member.name).parts[1:]
            if not member.isfile() or not parts or ".." in parts:
                continue
            out = dest.joinpath(*parts)
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_bytes(archive.extractfile(member).read())
    marker.write_text(sha + "\n")
    print(repo, sha)

if __name__ == "__main__":
    for path, (repo, sha) in PINS.items():
        fetch(path, repo, sha)

#!/usr/bin/env python3
"""Validate the Morphe source feed against the *actual* GitHub Release asset.

The release can fail AFTER semantic-release has pushed a tag and rewritten the
JSON metadata. Morphe would then read a valid-looking feed whose .mpp returns
404 and display Metadata N/A. This script detects that exact failure mode.
"""
import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

REPO = "Alastor-Kaneki/Morphe-Patches"
BASE = "https://github.com/" + REPO
API = "https://api.github.com/repos/" + REPO


def require(condition, message):
    if not condition:
        raise SystemExit("ERROR: " + message)


def local():
    feed = json.loads(Path("patches-bundle.json").read_text(encoding="utf-8"))
    latest = json.loads(Path("releases/latest.json").read_text(encoding="utf-8"))
    listing = json.loads(Path("patches-list.json").read_text(encoding="utf-8"))
    version = feed.get("version")
    require(re.fullmatch(r"\d+\.\d+\.\d+(?:-[\w.-]+)?", version or "") is not None,
            "source metadata has an invalid version")
    require(feed.get("created_at"), "missing created_at in patch source metadata")
    require(feed.get("description"), "missing release description in patch source metadata")
    require("signature_download_url" in feed, "missing signature_download_url key")
    require(version == latest.get("version") == listing.get("version"),
            "metadata, latest.json and patch list disagree about their versions")
    asset = f"patches-{version}.mpp"
    url = f"{BASE}/releases/download/v{version}/{asset}"
    require(feed.get("download_url") == url, "patches-bundle.json points to wrong asset")
    require(latest.get("tag") == f"v{version}", "latest.json has incorrect tag")
    require(latest.get("asset") == asset, "latest.json has incorrect asset filename")
    require(re.fullmatch("[a-fA-F0-9]{64}", latest.get("sha256", "")) is not None,
            "missing/invalid published bundle SHA256")
    patches = listing.get("patches")
    require(isinstance(patches, list) and len(patches) >= 1,
            "missing patch descriptions/compatibility list")
    for entry in patches:
        require(entry.get("name") and entry.get("description"),
                "one or more patches are missing name/description")
    print(f"PASS: source metadata, latest.json and {len(patches)} patch records agree on v{version}")
    return version, asset, latest["sha256"].lower()


def remote(version, asset, expected_sha):
    req = urllib.request.Request(
        API + "/releases/tags/v" + version,
        headers={"Accept": "application/vnd.github+json",
                 "User-Agent": "morphe-source-metadata-health",
                 **({"Authorization": "Bearer " + os.environ["GH_TOKEN"]}
                    if os.environ.get("GH_TOKEN") else {})},
    )
    try:
        with urllib.request.urlopen(req, timeout=25) as response:
            release = json.load(response)
    except urllib.error.HTTPError as error:
        raise SystemExit(f"ERROR: GitHub Release v{version} unavailable: HTTP {error.code}") from error
    require(release.get("tag_name") == f"v{version}" and
            not release.get("draft") and not release.get("prerelease"),
            "release is missing, draft, prerelease or incorrectly tagged")
    matching = [a for a in release.get("assets", []) if a.get("name") == asset]
    require(len(matching) == 1, f"missing public downloadable release asset {asset}")
    obj = matching[0]
    require(obj.get("size", 0) > 1024 and obj.get("state") == "uploaded",
            "release bundle empty or not completely uploaded")
    digest = obj.get("digest")
    require(digest == "sha256:" + expected_sha,
            f"published asset checksum disagrees: expected {expected_sha}, got {digest}")
    print(f"PASS: GitHub Release v{version} publishes the exact verified asset {asset}")


if __name__ == "__main__":
    ver, name, sha = local()
    if "--remote" in sys.argv:
        remote(ver, name, sha)

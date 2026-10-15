#!/usr/bin/env python3
"""Check Chrome's pinned Trichrome dependency and optionally install the matching APK.

Requires Android SDK aapt2 and apksigner. --install also requires adb.
This never downloads APKs, changes Chrome's dependency, or replaces system apps.
"""
import argparse
import re
import subprocess
import sys

FIELDS = re.compile(r"([A-Za-z][A-Za-z0-9]*)='([^']*)'")
SIGNER_SHA256 = re.compile(
    r"Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F:]+)", re.I
)


class CheckError(Exception):
    pass


def records(badging, record_type):
    prefix = record_type + ":"
    parsed = []
    for line in badging.splitlines():
        if not line.strip().startswith(prefix):
            continue
        pairs = FIELDS.findall(line)
        entry = dict(pairs)
        # AAPT2 can emit multiple certDigest values for signing rotation.
        if record_type == "uses-static-library":
            entry["certDigests"] = [
                value for key, value in pairs if key == "certDigest"
            ]
        parsed.append(entry)
    return parsed


def trichrome_requirement(chrome_badging):
    matches = [
        entry for entry in records(chrome_badging, "uses-static-library")
        if "trichrome" in entry.get("name", "").lower()
    ]
    if len(matches) > 1:
        raise CheckError("Chrome declares multiple Trichrome dependencies.")
    return matches[0] if matches else None


def normalized_digest(value):
    digest = value.replace(":", "").lower()
    if not re.fullmatch(r"[0-9a-f]{64}", digest):
        raise CheckError("Invalid SHA-256 certificate digest: " + value)
    return digest


def validate_library(requirement, library_badging, certificate_output):
    libraries = records(library_badging, "static-library")
    if not libraries:
        raise CheckError("The supplied APK does not declare a static library.")
    if not any(
        item.get("name") == requirement.get("name")
        and item.get("version") == requirement.get("version")
        and item.get("versionMajor", "0") == requirement.get("versionMajor", "0")
        for item in libraries
    ):
        found = ", ".join(
            f"{item.get('name', '?')} v{item.get('version', '?')}"
            for item in libraries
        )
        raise CheckError(
            "Wrong Trichrome library name/version. Chrome requires "
            f"{requirement.get('name')} v{requirement.get('version')} "
            f"(versionMajor={requirement.get('versionMajor', '0')}); "
            f"provided: {found}. Obtain the matching library for THIS Chrome APK."
        )

    expected_values = requirement.get("certDigests") or [
        requirement.get("certDigest")
    ]
    expected = {
        normalized_digest(value) for value in expected_values if value
    }
    if not expected:
        raise CheckError("Chrome's Trichrome dependency has no certificate digest.")
    signed = {normalized_digest(d) for d in SIGNER_SHA256.findall(certificate_output)}
    if not signed:
        raise CheckError("Could not read a signer SHA-256 digest from apksigner.")
    if expected.isdisjoint(signed):
        raise CheckError(
            "Trichrome certificate does not match Chrome's pinned certDigest. "
            "Do not re-sign the Trichrome Library APK; use its matching original."
        )


def command(*args):
    try:
        result = subprocess.run(
            args, capture_output=True, text=True, check=False
        )
    except FileNotFoundError as error:
        raise CheckError(
            f"Required tool not found: {error.filename}. "
            "Install Android SDK build-tools (aapt2, apksigner) and adb."
        ) from error
    if result.returncode:
        raise CheckError(
            f"Command failed: {' '.join(args)}\n"
            + (result.stderr.strip() or result.stdout.strip())
        )
    return result.stdout


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--chrome", required=True, help="Patched/signed Chrome base APK")
    parser.add_argument("--library", help="Original, matching Trichrome Library APK")
    parser.add_argument(
        "--split", action="append", default=[],
        help="Additional patched Chrome split APK; repeat for each split"
    )
    parser.add_argument("--install", action="store_true", help="Install using adb after checks")
    parser.add_argument("--aapt2", default="aapt2")
    parser.add_argument("--apksigner", default="apksigner")
    parser.add_argument("--adb", default="adb")
    args = parser.parse_args(argv)

    requirement = trichrome_requirement(
        command(args.aapt2, "dump", "badging", args.chrome)
    )
    if requirement is None:
        if args.library:
            raise CheckError(
                "Chrome does not declare a Trichrome static-library dependency; "
                "do not install an unrelated library."
            )
        print("No Trichrome static-library dependency. Checking APK signature.")
    else:
        name = requirement.get("name", "?")
        version = requirement.get("version", "?")
        print(f"Chrome needs {name}, library version {version}.")
        if not args.library:
            raise CheckError(
                "Supply --library with the EXACT matching Trichrome Library APK. "
                "The Chrome APK alone cannot install without its required library. "
                "Do not delete uses-static-library from the manifest."
            )
        library_badging = command(args.aapt2, "dump", "badging", args.library)
        certificate_output = command(
            args.apksigner, "verify", "--print-certs", args.library
        )
        validate_library(requirement, library_badging, certificate_output)
        print("Trichrome library name, version, and signing certificate match.")

    for apk in [args.chrome, *args.split]:
        command(args.apksigner, "verify", "--print-certs", apk)
    print("Chrome APK signature(s) verified.")

    if args.install:
        if args.library:
            print("Installing matching Trichrome Library first...")
            print(command(args.adb, "install", "-r", args.library).strip())
        chrome_apks = [args.chrome, *args.split]
        print("Installing patched Chrome...")
        if len(chrome_apks) > 1:
            print(command(args.adb, "install-multiple", "-r", *chrome_apks).strip())
        else:
            print(command(args.adb, "install", "-r", args.chrome).strip())
        print("Install completed. Chrome launch/runtime still requires testing.")
    else:
        print("Checks passed. Add --install to install the library then Chrome via adb.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except CheckError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        sys.exit(1)

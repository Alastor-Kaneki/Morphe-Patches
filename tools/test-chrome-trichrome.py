#!/usr/bin/env python3
"""Tests for Chrome/Trichrome package verification; no APKs or device needed."""
import importlib.util
import pathlib
import unittest
from unittest.mock import patch

HERE = pathlib.Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location(
    "chrome_trichrome", HERE / "install-chrome-trichrome.py"
)
MOD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MOD)

DIGEST = "a" * 64
WRONG_DIGEST = "b" * 64
CHROME = (
    "package: name='com.android.chrome.morphe' versionCode='123'\n"
    f"uses-static-library: name='org.chromium.trichromelibrary' "
    f"version='150123' versionMajor='0' certDigest='{DIGEST}'\n"
)
LIBRARY = (
    "package: name='org.chromium.trichromelibrary' versionCode='123'\n"
    "static-library: name='org.chromium.trichromelibrary' "
    "version='150123' versionMajor='0'\n"
)
SIGNED = f"Signer #1 certificate SHA-256 digest: {DIGEST}\n"


class ChromeTrichromeTest(unittest.TestCase):
    def test_detects_pinned_library(self):
        entry = MOD.trichrome_requirement(CHROME)
        self.assertEqual(entry["version"], "150123")
        self.assertEqual(entry["name"], "org.chromium.trichromelibrary")

    def test_works_without_trichrome_for_monolithic_build(self):
        self.assertIsNone(MOD.trichrome_requirement("package: name='com.android.chrome'\n"))

    def test_exact_match(self):
        MOD.validate_library(MOD.trichrome_requirement(CHROME), LIBRARY, SIGNED)

    def test_version_mismatch(self):
        with self.assertRaisesRegex(MOD.CheckError, "Wrong Trichrome"):
            MOD.validate_library(
                MOD.trichrome_requirement(CHROME),
                LIBRARY.replace("version='150123'", "version='150124'"),
                SIGNED
            )

    def test_library_name_mismatch(self):
        with self.assertRaisesRegex(MOD.CheckError, "Wrong Trichrome"):
            MOD.validate_library(
                MOD.trichrome_requirement(CHROME),
                LIBRARY.replace("org.chromium.trichromelibrary", "other.library"),
                SIGNED
            )

    def test_signer_mismatch(self):
        with self.assertRaisesRegex(MOD.CheckError, "certificate does not match"):
            MOD.validate_library(
                MOD.trichrome_requirement(CHROME),
                LIBRARY,
                SIGNED.replace(DIGEST, WRONG_DIGEST)
            )

    def test_colon_delimited_digest(self):
        entry = MOD.trichrome_requirement(CHROME)
        entry["certDigests"] = [
            ":".join(DIGEST[i:i + 2] for i in range(0, 64, 2))
        ]
        MOD.validate_library(entry, LIBRARY, SIGNED)

    def test_multiple_pinned_certificates(self):
        chrome = CHROME.replace(
            "certDigest='", f"certDigest='{WRONG_DIGEST}' certDigest='"
        )
        entry = MOD.trichrome_requirement(chrome)
        self.assertEqual(len(entry["certDigests"]), 2)
        MOD.validate_library(entry, LIBRARY, SIGNED)

    def test_no_missing_requirement_bypass(self):
        with patch.object(MOD, "command", return_value=CHROME):
            with self.assertRaisesRegex(MOD.CheckError, "Supply --library"):
                MOD.main(["--chrome", "chrome.apk"])

    def test_install_order(self):
        calls = []

        def fake_command(*args):
            calls.append(args)
            if args[:3] == ("aapt2", "dump", "badging"):
                return CHROME if args[3] == "chrome.apk" else LIBRARY
            if args[0] == "apksigner":
                return SIGNED
            return "Success\n"

        with patch.object(MOD, "command", side_effect=fake_command):
            self.assertEqual(
                MOD.main([
                    "--chrome", "chrome.apk", "--library", "trichrome.apk",
                    "--install"
                ]),
                0
            )
        self.assertLess(
            calls.index(("adb", "install", "-r", "trichrome.apk")),
            calls.index(("adb", "install", "-r", "chrome.apk"))
        )

    def test_split_install(self):
        calls = []

        def fake_command(*args):
            calls.append(args)
            if args[:3] == ("aapt2", "dump", "badging"):
                return CHROME if args[3] == "chrome.apk" else LIBRARY
            if args[0] == "apksigner":
                return SIGNED
            return "Success\n"

        with patch.object(MOD, "command", side_effect=fake_command):
            MOD.main([
                "--chrome", "chrome.apk", "--library", "trichrome.apk",
                "--split", "config.arm64.apk", "--install"
            ])
        self.assertIn(
            ("adb", "install-multiple", "-r", "chrome.apk", "config.arm64.apk"),
            calls
        )


if __name__ == "__main__":
    unittest.main()

"""Every library the app ships is listed on the Licences page (#131). Standard library only."""
import os
import re
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOG = os.path.join(ROOT, "gradle", "libs.versions.toml")
LICENCES = os.path.join(ROOT, "shared", "src", "commonMain", "kotlin", "io", "github", "nanunic9379",
                        "rideplan", "personal", "Licences.kt")

# Only used by the tests, never shipped in the app.
TEST_ONLY = {"org.jetbrains.kotlin:kotlin-test"}


def catalog_modules(text):
    section = text.split("[libraries]", 1)[1].split("\n[", 1)[0]
    return re.findall(r'module\s*=\s*"([^"]+)"', section)


class LicencesTest(unittest.TestCase):
    def test_every_shipped_library_is_listed(self):
        with open(CATALOG, encoding="utf-8") as f:
            modules = catalog_modules(f.read())
        with open(LICENCES, encoding="utf-8") as f:
            listed = set(re.findall(r'"([a-z0-9]+(?:\.[a-z0-9]+)+)"\)', f.read()))
        self.assertTrue(modules)
        def covered(group):
            return any(group == g or group.startswith(g + ".") for g in listed)
        missing = sorted(m for m in modules if m not in TEST_ONLY and not covered(m.split(":")[0]))
        self.assertEqual([], missing, "add these to Licences.libraries in Licences.kt")


if __name__ == "__main__":
    unittest.main()

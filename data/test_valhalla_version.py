import unittest
from unittest.mock import patch

import valhalla_version


class ValhallaVersionTest(unittest.TestCase):
    def test_catalog_versions_match_supported_library_engine_pair(self):
        mobile, engine = valhalla_version.versions()
        self.assertEqual("0.6.6", mobile)
        self.assertEqual("3.9.2", engine)

    def test_unknown_mobile_version_fails_instead_of_building_incompatible_data(self):
        with patch.dict(valhalla_version.MOBILE_ENGINE_VERSIONS, {}, clear=True):
            with self.assertRaisesRegex(ValueError, "No Valhalla engine mapping"):
                valhalla_version.versions()

    def test_engine_version_must_be_updated_with_mobile_library(self):
        with self.assertRaisesRegex(ValueError, "update both versions together"):
            valhalla_version.validate_versions("0.6.6", "3.10.0")


if __name__ == "__main__":
    unittest.main()

"""Keep routing data builds on the Valhalla version used by the app."""

from __future__ import annotations

import argparse
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "gradle" / "libs.versions.toml"

# valhalla-mobile releases bundle a specific Valhalla engine. Add a pair here only after
# verifying the engine version shipped by that library release.
MOBILE_ENGINE_VERSIONS = {
    "0.6.6": "3.9.2",
}


def validate_versions(mobile: str, engine: str) -> tuple[str, str]:
    expected_engine = MOBILE_ENGINE_VERSIONS.get(mobile)
    if expected_engine is None:
        raise ValueError(f"No Valhalla engine mapping is recorded for valhalla-mobile {mobile}")
    if engine != expected_engine:
        raise ValueError(
            f"valhalla-mobile {mobile} bundles Valhalla {expected_engine}, "
            f"but valhalla-engine is {engine}; update both versions together"
        )
    return mobile, engine


def versions() -> tuple[str, str]:
    catalog = tomllib.loads(CATALOG.read_text(encoding="utf-8"))
    configured = catalog["versions"]
    return validate_versions(configured["valhalla-mobile"], configured["valhalla-engine"])


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="validate app engine/library compatibility")
    parser.add_argument("--github-env", action="store_true", help="print GitHub Actions environment assignment")
    args = parser.parse_args()
    _, engine = versions()
    if args.github_env:
        print(f"VALHALLA_VERSION={engine}")
    elif args.check:
        print(f"valhalla-mobile and Valhalla engine versions agree ({engine})")
    else:
        parser.error("choose --check or --github-env")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

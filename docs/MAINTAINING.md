# Maintainer guide

This guide records the release and road-data procedures that are currently implemented in the
repository. The workflow files and GitHub Actions settings remain the source of truth if a command
or policy changes.

## Changes and pull requests

- Start from an issue, make a branch, and open a pull request (see [CONTRIBUTING.md](../CONTRIBUTING.md)).
- Check the required CI jobs on the pull request and wait for a human approval if GitHub requests
  one. Do not bypass required reviews or status checks. Use the merge method offered by the
  repository settings.
- For app changes, test the affected flow on the Android emulator; use a real phone for release
  candidate field testing. Record the steps and results in the pull request.

## Build and publish road data

Road data has two workflows and two release paths:

| Data | Workflow | Trigger | Published assets |
|---|---|---|---|
| Italy | `Routing data` (`.github/workflows/routing-data.yml`) | Actions > Routing data > Run workflow, monthly schedule, or relevant data/workflow changes on `main` | Release `routing-data` |
| Other configured areas | `Routing data areas` (`.github/workflows/routing-areas.yml`) | Actions > Routing data areas > Run workflow, monthly schedule, or relevant area/workflow changes on `main` | One `routing-<id>` Release per area, then `routing-index` |

To build an area manually, enter its comma-separated `id` from `data/routing-areas.json` in the
workflow's `areas` input. For example, the currently configured Alps area is:

```sh
gh workflow run routing-areas.yml -f areas=alps
```

For several configured areas, pass their IDs separated by commas. An empty input only refreshes
the index. Italy (`it`) is listed in `routing-areas.json` for the index, but it is built only by
`routing-data.yml`; do not pass it to the areas workflow.

To start the Italy pipeline manually:

```sh
gh workflow run routing-data.yml
```

The workflows fetch the OpenStreetMap extract, build Valhalla tiles with the pinned engine, split
the tiles into cells, add fuel and road-note data, check Release limits, and upload the resulting
assets. The areas workflow also republishes `routing-index/areas.json`. A push to `main` that only
changes an area configuration refreshes the index; run the workflow manually to build the area.

Before changing the pipeline, run the local checks used by CI:

```sh
python3 -m unittest discover -s data
python3 data/valhalla_version.py --check
```

`data/routing_data.py` documents the input and output arguments for `source`, `poly`, `cells`, and
`stale`; `data/routing_areas.py` documents `matrix`, `config`, `release`, and `index`.

## Diagnose a failed data run

1. Open Actions and select **Routing data** or **Routing data areas**. Open the failed run and read
   the first failed step in the `Valhalla data in grid cells` job or the relevant `Area <id>` job.
2. Check the log around the failed command. Common gates include an OSM download/checksum,
   `osmium` filtering/export, Valhalla tile building, manifest/cell generation, and the pre-publish
   Release-size/file-count checks in `data/routing_data.py`.
3. For a scheduled run, check for the open **Data build failed** issue. The report job creates that
   issue or comments on the existing one with a link to the run. Manual failures do not create that
   report issue; use the Actions run URL directly.
4. If an area build fails, the `Index of the areas` job still runs when planning succeeded. It
   indexes areas whose Releases already contain `cells.json`; it does not make a failed area
   available. Fix the cause and rerun that area, then confirm its Release and the `routing-index`
   asset.
5. After a successful run, inspect the target Release for `cells.json` and the referenced packs,
   fuel lists, and `road-notes.json`. Italy's workflow publishes to `routing-data`; other areas use
   the Release name configured in `data/routing-areas.json`.

The scheduled workflows run monthly on the first day at 02:00 UTC for Italy and 03:00 UTC for
other areas. GitHub disables repository schedules after 60 days without repository activity; if a
schedule is disabled, re-enable it in the corresponding Actions workflow page.

## Android release numbering and signing

Every app-affecting push to `main` starts `.github/workflows/release.yml`. It checks out full Git
history and sets the version code to the number of commits on `main` plus 100 when
`RIDEPLAN_OFFICIAL_RELEASE=true`. The version name and GitHub Release tag are `0.1.N` and `v0.1.N`,
where `N = commits + 100`. The release workflow builds GitHub-distribution APKs for each ABI and
publishes the update manifest. Documentation, data files, routing-data workflows, and other paths
listed in `paths-ignore` do not trigger an APK release; the workflow can also be run manually.

The signing key is supplied only through GitHub Actions secrets. The workflow currently reads:

- `RIDEPLAN_KEYSTORE_B64`: base64-encoded JKS signing key. If absent, the workflow warns and
  skips publishing a Release.
- `RIDEPLAN_KEYSTORE_PASSWORD` — keystore password.
- `RIDEPLAN_KEY_ALIAS` — signing key alias.
- `RIDEPLAN_KEY_PASSWORD` — signing key password.

`RIDEPLAN_KEYSTORE_FILE` is a temporary runner environment variable pointing to the decoded key;
it is not a GitHub secret. The workflow removes the temporary file after building. Never commit a
key or password. A debug-signed APK cannot update an installation signed with the Release key.

## Before calling a build stable

Use [RELEASES.md](RELEASES.md) for the v1.0 criteria and run the complete
[field-test checklist](field-test.md) on a real phone. At minimum, confirm:

- All P1 and P2 issues for the planned release are closed; the release candidate has completed at
  least two weeks of riding with no open P1 bug.
- Italy and Alps route data can be downloaded and used, including a ride across area boundaries.
- Offline map readiness, airplane-mode navigation and off-route recomputation work.
- Navigation continues with the screen off; record battery use, heat, crashes and ANRs.
- Sharing and import preserve the expected privacy behavior.
- The current monthly data builds succeeded for the areas required by the release.
- Update an existing Release installation to the candidate and verify the in-app update.

Write the field-test report on issue #63, include phone model and Android version, attach logcat
for crashes, and open one issue per blocker. Do not call the build stable while the v1.0 criteria
in `docs/RELEASES.md` remain unmet.

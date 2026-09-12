# Releasing

## Branches

| Branch | Channel | App on the phone | Published as |
| --- | --- | --- | --- |
| `master` | stable | Pie Launcher | release, tagged `v<version>` |
| `dev` | beta | Pie Launcher Beta | pre-release, tagged `v<version>-beta.<build>` |
| `feature/*`, `fix/*`, `ci/*` | none | - | nothing, CI only |

The beta build carries the `.beta` application ID suffix, its own name and
an orange launcher icon, so it installs next to the stable build instead
of replacing it. The two do not share settings; use **Preferences >
Export settings** and **Import settings** to copy a setup between them.

## Workflows

`ci.yml` runs `lintDebug`, builds the debug APK and checks the beta
variant on every push to the branches above and on every pull request.

`release.yml` builds a signed APK and publishes it as a GitHub release.

On `master` it reads `versionName` from `app/build.gradle` and publishes
only if no tag `v<versionName>` exists yet, so ordinary commits never
create a release.

On `dev` it publishes every push as `<versionName>-beta.<workflow run
number>`. That run number is also the `versionCode`, because it only ever
grows.

## Setup

Every release has to be signed with the same key or Android refuses to
install the update.

```sh
sh tools/create-keystore.sh
```

Keep `release.jks` and its password backed up, and do not commit them.
Then add these under **Settings > Secrets and variables > Actions**:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | the base64 line printed by the script |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | `pielauncher`, unless you changed `ALIAS` |
| `KEY_PASSWORD` | the key password |

The release workflow fails before building if any are missing.

## Publishing

A beta: push to `dev`.

A stable version:

1. Add a `## <version>` section to `CHANGELOG.md`.
2. Bump `versionCode` and `versionName` in `app/build.gradle`.
3. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
4. Merge `dev` into `master`.

To rebuild an existing release, run the workflow from the **Actions** tab
with **force** enabled.

## Installing

Add the repository URL in [Obtainium](https://github.com/ImranR98/Obtainium).
For the beta, add the same URL again with **Include prereleases** on and
**Filter APKs by regular expression** set to `Beta`; Obtainium keys apps
by package name, so both entries coexist.

Each release lists its signing certificate SHA-256, which Obtainium can
pin.

The stable build keeps upstream's application ID but is signed with a
different key, so it will not install over a copy from Google Play or
F-Droid. The beta build has its own application ID and is unaffected.

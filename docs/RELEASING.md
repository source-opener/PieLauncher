# Releasing

## Branches

| Branch | Channel | App on the phone | Published as |
| --- | --- | --- | --- |
| `master` | stable | Pie Launcher | release, tagged `v<upstream>.<fork>` |
| `dev` | beta | Pie Launcher Beta | pre-release, tagged `v<upstream>.<fork>-beta.<build>` |
| `feature/*`, `fix/*`, `ci/*` | none | - | nothing, CI on their pull request |

The beta build carries the `.beta` application ID suffix, its own name and
an orange launcher icon, so it installs next to the stable build instead
of replacing it. The two do not share settings; use **Preferences >
Export settings** and **Import settings** to copy a setup between them.

## Workflows

`ci.yml` runs `lintDebug`, builds the debug APK and checks the beta
variant on every pull request. Pushes are not built, so a branch is
verified once, when it is proposed for merge.

`release.yml` builds a signed APK and publishes it as a GitHub release.

On `master` it publishes only if the composed version (see
[Versioning](#versioning)) has no tag yet, so ordinary commits never
create a release.

On `dev` it publishes every push, appending `-beta.<workflow run number>`
to the same version.

## Versioning

`app/build.gradle` keeps upstream's `versionCode` and `versionName`
untouched, so merging upstream never conflicts on them. The fork's own
revision lives in [`fork-version`](../fork-version), a file upstream will
never have, and the workflow composes the published version from both:

    upstream 1.28.0 (code 75) + fork-version 1  ->  1.28.0.1, code 75001

So a release of this repository is always visibly distinct from the
upstream release it is built from, and it is clear which upstream version
it carries.

Bump `fork-version` to publish a stable release without an upstream
version change. After merging an upstream release there is nothing to do:
its higher `versionCode` already raises the composed one, so
`1.29.0.1` follows `1.28.0.1` on its own.

Keep `fork-version` below 1000, which is the room the composed
`versionCode` leaves for it.

Betas append `-beta.<workflow run number>`, and use the run number alone
as their `versionCode`, since the beta is a separate app with its own
ladder.

A beta names the version it leads *to*, not the one it was built from. It
sits ahead of stable, so if the composed version is already released the
workflow takes the next fork revision: with `1.28.0.1` out, betas are
`1.28.0.2-beta.<n>`. Their notes list everything that differs from the
last stable release rather than only what changed since the previous
beta, so any single beta describes itself in full.

Publishing a beta deletes the older ones, keeping the number set by
`KEEP` in the workflow. Only pre-releases are ever removed, so a stable
release cannot be pruned.

Stable notes list the commits since the previous stable release, not a
`CHANGELOG.md` section: that file belongs to upstream and says nothing
about what this fork added.

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

1. Bump `fork-version`, unless this carries a new upstream release.
2. Merge `dev` into `master`.

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

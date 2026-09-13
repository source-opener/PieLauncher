# Working in this repository

This file is guidance for Claude Code sessions. It is not part of the app.

**It lives only on `feature/ai-dev`.** A session loads `CLAUDE.md` from the
branch it checks out, so start the session with `feature/ai-dev` as its
source or none of this applies. Once loaded it stays in context for the
whole session, including after you switch branches to work.

**Never merge this file into `dev` or `master`.** If you branched work off
`feature/ai-dev`, delete `CLAUDE.md` in your first commit on that branch.

## What this is

A fork of an upstream Android project. Upstream keeps releasing; this fork
adds its own features on top and publishes its own signed builds. Read
`docs/RELEASING.md` for the full release setup.

## Branches

| Branch | Role |
| --- | --- |
| `master` | stable channel |
| `dev` | beta channel, features land here first |
| `feature/*`, `fix/*`, `ci/*` | work branches, one merge request each |

Work goes to a branch, then a merge request into `dev`. `dev` merges into
`master` for a stable release.

## Releases

Both channels publish as **drafts**. Nothing is live until a human presses
Publish. Do not change that.

Versions are composed in CI from upstream's untouched `versionCode` and
`versionName` plus `fork-version`, so pulling upstream never conflicts on
those lines. Leave upstream's values alone. A stable release without an
upstream version change needs `fork-version` bumped first, or the workflow
finds the existing tag and silently publishes nothing.

Release notes are built from **commit subjects**, so a commit subject is
user-facing text. Write it as a line someone would want to read in a
release.

## Working style

- Commit messages: a few words, imperative.
- No AI or assistant attribution anywhere: not in commits, branch names,
  merge request bodies, code, or comments.
- Comments only where the code cannot say it itself. No comment restating
  the next line. Keep diffs minimal.
- Open merge requests. Never merge them. Never enable auto-merge.
- If a branch collects commits that cancel out, squash before it merges, or
  both subjects appear in a release describing work that no longer exists.
- State in every merge request what you actually verified and what you did
  not.

## Verification

This is where it goes wrong. Most of these are mistakes already made here.

- When editing files programmatically, **assert that every intended change
  matched**. A replacement that silently matches nothing leaves the file
  stale while the script reports success.
- Do not assume a helper exists. Grep for it.
- Do not assume an API is available: check it against `minSdk`. `List.sort`
  is API 24 and compiles fine, then crashes on older devices.
- A new database table must be created in **both** the fresh-install path
  and the upgrade path. Getting the upgrade path wrong only breaks for
  users upgrading, never for you testing a fresh install.
- Execute logic rather than reasoning about it: run the string transform on
  real input, run the shell snippet against real history, extract a
  workflow step from the YAML and run it.
- **Never edit or disable a test to make CI pass.** If a test fails,
  either the change is wrong or the test does not belong in that pipeline.
  Say which.
- When adapting existing CI, keep its triggers. Upstream may run a job only
  for certain paths on purpose; widening that inherits failures that were
  never meant to gate the change.
- Check how the project handles translations before adding a user-facing
  string. If it uses a platform such as Weblate, add English only. Check
  whether lint treats `MissingTranslation` as an error and follow what the
  project already does.
- If you cannot build locally, say so plainly rather than implying you
  verified more than you did.

## Ask before

- Anything that deletes or rewrites published history, releases or tags.
- Generating a signing key. It is the app's permanent identity and must
  only exist on the user's machine. Give the commands; never run them or
  ask for the key.
- Widening scope beyond what was asked.

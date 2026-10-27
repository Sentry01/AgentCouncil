# Contributing

## Layout

| Path | Purpose |
|---|---|
| `skills/agentcouncil/SKILL.md` | Source of truth: routing, model roster, dispatch and output rules |
| `skills/agentcouncil/references/` | Phase prompts, loaded only for the chosen path |
| `com.github.copilot/agents/agentcouncil.agent.md` | Thin agent that loads the skill |
| `plugin.json` | Agent Plugins 1.0 manifest |
| `.github/plugin/marketplace.json` | Lets this repository act as a plugin marketplace |
| `evals/routing.json` | Routing test cases |
| `scripts/validate.mjs` | Static checks, run in CI |
| `scripts/eval-routing.mjs` | Routing eval, run locally |
| `scripts/release-notes.mjs` | Builds release notes from `CHANGELOG.md` |
| `scripts/bump-version.mjs` | Sets the release version everywhere and dates the changelog |
| `.github/workflows/release.yml` | Publishes a GitHub release when the version changes |

## Making a change

1. Edit the skill. Keep protocol logic in `SKILL.md` and `references/`; the agent should stay a thin wrapper.
2. Load your working copy without installing it: `copilot --plugin-dir .`, then try a prompt such as `quick council: ...`.
3. Run `node scripts/validate.mjs`.
4. If you changed Steps 1 to 4 (routing), add cases to `evals/routing.json` and run `node scripts/eval-routing.mjs --runs 2`. It needs Copilot CLI and `gh auth login`, makes one model call per run, and uses a temporary `COPILOT_HOME` so your personal instructions and skills do not affect the result. Try `--model` with a model from another family too, since routing runs on whatever model the user's session uses.
5. Add a line to the Unreleased section of `CHANGELOG.md`.

Documentation style: plain ASCII punctuation, no emoji, no em dashes. `validate.mjs` enforces this. Never write a dollar sign directly before a digit in skill files, because skill loaders expand it as an argument placeholder.

## Updating models

1. Check [Supported AI models](https://docs.github.com/en/copilot/reference/ai-models/supported-models) and the [Copilot changelog](https://github.blog/changelog/) for retirements.
2. Edit the roster table in `SKILL.md` (Step 6). Keep one family per seat.
3. Run `node scripts/validate.mjs`. It fails if the README, agent or references still name a model that is not in the roster.
4. Run one light council end to end with `copilot --plugin-dir .` and check the footer.

## Releasing

Marketplace installs are pinned to a release tag: the plugin entry in `.github/plugin/marketplace.json` has `"source": {"source": "github", "repo": "Sentry01/AgentCouncil", "ref": "vX.Y.Z"}`. Merging to `main` without a version bump does not change what users get; publishing a release does.

1. In your PR, run `node scripts/bump-version.mjs X.Y.Z` (or `patch`, `minor`, `major`). It sets the version in `plugin.json`, sets `metadata.version`, the plugin entry `version` and `source.ref` in `marketplace.json`, and moves the Unreleased changelog notes under a `## [X.Y.Z] - YYYY-MM-DD` heading. Use semver; a version with a suffix such as `2.1.0-rc.1` is published as a GitHub prerelease, but marketplace users still receive it, so test prereleases with `copilot --plugin-dir .` before merging.
2. Run `node scripts/validate.mjs` (it fails if any of those fields disagree) and preview the notes with `node scripts/release-notes.mjs`.
3. Merge to `main`. The Release workflow (`.github/workflows/release.yml`) sees the new version in `plugin.json`, validates, creates the `vX.Y.Z` tag on the merge commit and publishes a GitHub release with the changelog section as notes. Until the tag exists, `copilot plugin update` reports that the ref is missing and leaves the installed version in place. If a run fails, fix the cause and re-run it from the Actions tab (it has a manual trigger); it skips versions that are already released.

Do not move or delete a published tag; installs of that version resolve it.

Users pick up the release with `copilot plugin marketplace update agentcouncil` and `copilot plugin update agentcouncil@agentcouncil`. Listings that pin a tag, such as an awesome-copilot external entry, need their ref bumped separately.

# Changelog

All notable changes to this project are documented here. Versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Release workflow: merging a version bump to `main` tags it and publishes a GitHub release, with notes built from this changelog by `scripts/release-notes.mjs`.
- `scripts/bump-version.mjs` sets a new version in every manifest and moves the Unreleased notes under it.

### Changed

- Marketplace installs are pinned to the release tag (`vX.Y.Z`) instead of `main`, so `copilot plugin update` delivers published releases only. `validate.mjs` checks that the tag matches `plugin.json`, and CI now checks that release notes build.

## [2.0.0] - 2026-10-17

### Breaking

- The plugin, skill and marketplace are now named `agentcouncil`. The name `agent-council` is already used by a different plugin in the awesome-copilot marketplace.
- When installed as a plugin, the agent ID is `agentcouncil:agentcouncil` (`copilot --agent agentcouncil:agentcouncil`). `--agent AgentCouncil` only worked for manually copied files.
- `plugin.json` uses the Agent Plugins 1.0 format. The agent moved to `com.github.copilot/agents/`.
- Explicit invocations (a council prefix such as `council:`, or the agent) always convene. Simple tasks run the new light path instead of being answered directly.

### Added

- Light effort path: one concise pass per seat plus a short synthesis (4 subagent calls).
- Context brief sent to every seat. Seats are told to work read-only.
- Anonymized, reordered inputs for every orchestrator call. Adversarial triage now runs on the orchestrator seat instead of the session model.
- Per-seat fallback lists, a family-diversity rule, user model overrides and a footer that discloses the models that actually ran.
- Routing evals (`evals/routing.json`, `scripts/eval-routing.mjs`), a validation script (`scripts/validate.mjs`) and a CI workflow.
- `.github/plugin/marketplace.json`, so this repository is its own plugin marketplace.
- LICENSE (MIT), CONTRIBUTING.md and this changelog.

### Changed

- Default models: Alpha `claude-opus-5.5`, Beta `gpt-6.1-sol`, Gamma `gemini-4-argon`, Orchestrator `claude-opus-5.5`. The previous defaults (`claude-opus-4.6`, `gpt-5.4`, `gemini-3.1-pro`) are retired or superseded.
- Mode routing checks the prefix first, then the user's intent, instead of matching keywords as substrings. "VS Code" and "the challenge is" no longer trigger adversarial mode, and choose-between and go/no-go questions now route to adversarial.
- Domain rules: data model and service API design is architecture, not code.
- Confidence labels must state what they rest on. The orchestrator weights evidence, not labels.
- The collaborative improve round always ends with a Tensions section, and unadopted first-draft ideas reach the synthesis.
- `SKILL.md` is a short router. Phase prompts live in `references/` and are loaded only for the chosen path.
- The agent is a thin wrapper that loads the skill. Its tools are reduced to read, search, agent, web, todo and skill.
- Documentation rewritten in plain style.

### Removed

- The duplicated protocol in the agent file.
- Manual install instructions that copied files into `~/.copilot`.

## [1.0.0] - 2026-09-16

- First plugin release: `plugin.json`, collaborative and adversarial modes, complexity gate, domain detection, confidence labels and partial-consensus attacks. Earlier history (from 2026-02-09) is in the git log.

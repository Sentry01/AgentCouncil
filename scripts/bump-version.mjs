#!/usr/bin/env node
// Prepares a release: sets the version in plugin.json and marketplace.json
// (including the release tag that installs are pinned to) and moves the
// Unreleased changelog notes under a new version heading.
// Usage: node scripts/bump-version.mjs <X.Y.Z | major | minor | patch>

import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const paths = { plugin: join(root, "plugin.json"), market: join(root, ".github/plugin/marketplace.json"), changelog: join(root, "CHANGELOG.md") };
const die = (msg) => {
  console.error(msg);
  process.exit(1);
};
const readJson = (p) => JSON.parse(readFileSync(p, "utf8"));
const writeJson = (p, data) => writeFileSync(p, `${JSON.stringify(data, null, 2)}\n`);

const arg = process.argv[2];
if (!arg) die("Usage: node scripts/bump-version.mjs <X.Y.Z | major | minor | patch>");

const plugin = readJson(paths.plugin);
const current = plugin.version;
let version = arg;
if (["major", "minor", "patch"].includes(arg)) {
  if (current.includes("-")) die(`${current} is a prerelease; pass an explicit version instead of ${arg}.`);
  const [major, minor, patch] = current.split(".").map(Number);
  version = { major: `${major + 1}.0.0`, minor: `${major}.${minor + 1}.0`, patch: `${major}.${minor}.${patch + 1}` }[arg];
}
if (!/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/.test(version)) die(`Not a semver version: ${version}`);
if (version === current) die(`plugin.json is already at ${version}.`);

const lines = readFileSync(paths.changelog, "utf8").split("\n");
if (lines.some((l) => l.startsWith(`## [${version}]`))) die(`CHANGELOG.md already has a ${version} section.`);
const start = lines.findIndex((l) => l.startsWith("## [Unreleased]"));
if (start < 0) die('CHANGELOG.md has no "## [Unreleased]" section.');
const next = lines.findIndex((l, i) => i > start && l.startsWith("## ["));
if (!lines.slice(start + 1, next < 0 ? undefined : next).join("").trim()) die("The Unreleased section of CHANGELOG.md is empty. Add release notes first.");
const date = new Date().toLocaleDateString("en-CA");
lines.splice(start + 1, 0, "", `## [${version}] - ${date}`);
writeFileSync(paths.changelog, lines.join("\n"));

plugin.version = version;
writeJson(paths.plugin, plugin);

const market = readJson(paths.market);
const entry = market.plugins.find((p) => p.name === plugin.name);
if (!entry) die(`marketplace.json has no plugins[] entry named ${plugin.name}`);
market.metadata.version = version;
entry.version = version;
entry.source.ref = `v${version}`;
writeJson(paths.market, market);

console.log(`Bumped ${current} to ${version}. Installs will follow tag v${version} once the release is published.
Next: node scripts/validate.mjs && node scripts/release-notes.mjs, then open a PR and merge it.`);

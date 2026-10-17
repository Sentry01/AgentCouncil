#!/usr/bin/env node
// Prints GitHub release notes for a version: install and update commands, then
// the matching CHANGELOG.md section. Fails if the section is missing or empty.
// Usage: node scripts/release-notes.mjs [version] [--no-install]
// The version defaults to the one in plugin.json.

import { readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const args = process.argv.slice(2);
const version = args.find((a) => !a.startsWith("--")) || JSON.parse(readFileSync(join(root, "plugin.json"), "utf8")).version;

const lines = readFileSync(join(root, "CHANGELOG.md"), "utf8").split("\n");
const start = lines.findIndex((l) => l.startsWith(`## [${version}]`));
if (start < 0) {
  console.error(`CHANGELOG.md has no "## [${version}]" section.`);
  process.exit(1);
}
const rest = lines.slice(start + 1);
const end = rest.findIndex((l) => l.startsWith("## ["));
const section = (end < 0 ? rest : rest.slice(0, end)).join("\n").trim();
if (!section) {
  console.error(`CHANGELOG.md section for ${version} is empty.`);
  process.exit(1);
}

const install = `## Install or update

\`\`\`bash
# First install
copilot plugin marketplace add Sentry01/AgentCouncil
copilot plugin install agentcouncil@agentcouncil

# Update an existing install
copilot plugin marketplace update agentcouncil
copilot plugin update agentcouncil@agentcouncil
\`\`\`

`;

process.stdout.write(`${args.includes("--no-install") ? "" : install}${section.replace(/^### /gm, "## ")}\n`);

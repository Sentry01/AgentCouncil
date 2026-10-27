#!/usr/bin/env node
// Validates the plugin: manifests, skill and agent frontmatter, model roster
// consistency, changelog entry, relative links and documentation style.
// Rules mirror the Agent Plugins 1.0 schema and github/awesome-copilot validators.
// Usage: node scripts/validate.mjs

import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const errors = [];
const fail = (file, msg) => errors.push(`${relative(root, file) || file}: ${msg}`);
const read = (p) => readFileSync(p, "utf8");
const readJson = (p) => {
  try {
    return JSON.parse(read(p));
  } catch (e) {
    fail(p, `invalid JSON: ${e.message}`);
    return {};
  }
};

const SCHEMA = "https://agent-plugins.org/schemas/1.0.0/plugin.schema.json";
const PLUGIN_FIELDS = new Set(["$schema", "name", "version", "description", "author", "homepage", "repository", "license", "keywords", "extensions"]);
const PLUGIN_NAME = /^(?!.*(?:--|\.\.))[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?$/;
const SKILL_NAME = /^[a-z0-9-]{1,64}$/;
// Strict SemVer (no build metadata); with the .lock check below, v<version> is always a valid Git tag.
const SEMVER_ID = "(?:0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*)";
const SEMVER = new RegExp(`^(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)(?:-${SEMVER_ID}(?:\\.${SEMVER_ID})*)?$`);
const MODEL_ID = /\b(?:claude|gpt|gemini|grok)-[a-z0-9]+(?:[.-][a-z0-9]+)*\b/g;
const SHIPPED_DOCS = ["README.md", "CONTRIBUTING.md", "CHANGELOG.md"];

function frontmatter(file) {
  const text = read(file);
  const m = text.match(/^---\n([\s\S]*?)\n---\n/);
  if (!m) {
    fail(file, "missing YAML frontmatter");
    return { raw: {}, data: {} };
  }
  const raw = {};
  const data = {};
  for (const line of m[1].split("\n")) {
    const kv = line.match(/^([A-Za-z][\w-]*):\s*(.*)$/);
    if (!kv) continue;
    raw[kv[1]] = kv[2];
    data[kv[1]] = kv[2].replace(/^(['"])(.*)\1$/, "$2").replace(/''/g, "'");
  }
  return { raw, data };
}

function walk(dir, out = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const p = join(dir, entry.name);
    if (entry.isDirectory()) walk(p, out);
    else out.push(p);
  }
  return out;
}

// plugin.json
const pluginPath = join(root, "plugin.json");
const plugin = readJson(pluginPath);
if (plugin.$schema !== SCHEMA) fail(pluginPath, `$schema must be ${SCHEMA}`);
for (const key of Object.keys(plugin)) if (!PLUGIN_FIELDS.has(key)) fail(pluginPath, `field not allowed by Agent Plugins 1.0: ${key}`);
if (!PLUGIN_NAME.test(plugin.name || "") || plugin.name.length > 64) fail(pluginPath, "name must be lowercase letters, digits, dots or hyphens, 1-64 chars");
if (!SEMVER.test(plugin.version || "") || plugin.version.endsWith(".lock")) fail(pluginPath, "version must be semver and usable as a Git tag");
if (!plugin.description || plugin.description.length > 500) fail(pluginPath, "description must be 1-500 chars");
if (!plugin.license) fail(pluginPath, "license is required");
if (!plugin.author?.name) fail(pluginPath, "author.name is required");
const keywords = plugin.keywords || [];
if (keywords.length > 10) fail(pluginPath, "at most 10 keywords");
for (const k of keywords) if (!/^[a-z0-9-]{1,30}$/.test(k)) fail(pluginPath, `keyword "${k}" must be lowercase-hyphen, 1-30 chars`);
if (!existsSync(join(root, "LICENSE"))) fail(root, "LICENSE file is missing");

// marketplace.json
const marketPath = join(root, ".github/plugin/marketplace.json");
const market = readJson(marketPath);
const entry = (market.plugins || []).find((p) => p.name === plugin.name);
if (!market.name || !market.owner?.name) fail(marketPath, "name and owner.name are required");
if (!entry) fail(marketPath, `no plugins[] entry named ${plugin.name}`);
else {
  if (entry.version !== plugin.version) fail(marketPath, `plugin version ${entry.version} != plugin.json ${plugin.version}`);
  if (!entry.description) fail(marketPath, "plugin entry needs a description");
  // Installs are pinned to the release tag, so users only receive published releases.
  const repo = (plugin.repository || "").replace(/^https:\/\/github\.com\//, "");
  const src = entry.source || {};
  if (src.source !== "github" || src.repo !== repo || src.ref !== `v${plugin.version}` || src.path || src.sha) {
    fail(marketPath, `plugin source must be {"source": "github", "repo": "${repo}", "ref": "v${plugin.version}"} so installs follow the release tag`);
  }
}
if (market.metadata?.version !== plugin.version) fail(marketPath, `metadata.version must equal plugin.json version ${plugin.version}`);

// skills
const skillsDir = join(root, "skills");
const skillDirs = existsSync(skillsDir) ? readdirSync(skillsDir, { withFileTypes: true }).filter((d) => d.isDirectory()) : [];
if (skillDirs.length === 0) fail(skillsDir, "no skills found");
const rosterModels = new Set();
const shipped = [];
for (const d of skillDirs) {
  const dir = join(skillsDir, d.name);
  const skillFile = join(dir, "SKILL.md");
  if (!readdirSync(dir).includes("SKILL.md")) {
    fail(dir, "missing SKILL.md (exact case)");
    continue;
  }
  const { data } = frontmatter(skillFile);
  if (data.name !== d.name) fail(skillFile, `name "${data.name}" must equal folder name "${d.name}"`);
  if (!SKILL_NAME.test(data.name || "")) fail(skillFile, "name must be lowercase-hyphen, 1-64 chars");
  const desc = data.description || "";
  if (desc.length < 10 || desc.length > 1024) fail(skillFile, `description must be 10-1024 chars (is ${desc.length})`);
  if (!desc.startsWith("Use when")) fail(skillFile, 'description should start with "Use when"');
  const body = read(skillFile);
  for (const ref of body.matchAll(/`(references\/[\w.-]+\.md)`/g)) {
    if (!existsSync(join(dir, ref[1]))) fail(skillFile, `referenced file not found: ${ref[1]}`);
  }
  for (const row of body.split("\n").filter((l) => /^\|\s*(Alpha|Beta|Gamma|Orchestrator)\s*\|/.test(l))) {
    for (const id of row.match(MODEL_ID) || []) rosterModels.add(id);
  }
  for (const f of walk(dir)) {
    if (statSync(f).size > 5 * 1024 * 1024) fail(f, "bundled asset larger than 5 MB");
    if (f.endsWith(".md")) shipped.push(f);
  }
}
if (rosterModels.size === 0) fail(skillsDir, "no model roster table found in SKILL.md");

// agents
const agentsDir = join(root, "com.github.copilot/agents");
const agentFiles = existsSync(agentsDir) ? readdirSync(agentsDir).filter((f) => f.endsWith(".agent.md")) : [];
if (agentFiles.length === 0) fail(agentsDir, "no agents found");
for (const f of agentFiles) {
  const file = join(agentsDir, f);
  shipped.push(file);
  if (!/^[a-z0-9-]+\.agent\.md$/.test(f)) fail(file, "agent filename must be lowercase-hyphen with .agent.md");
  const { raw, data } = frontmatter(file);
  if (!data.description) fail(file, "description is required");
  else if (!/^'.*'$/.test(raw.description)) fail(file, "description must be wrapped in single quotes");
  if (!data.model) fail(file, "model is required");
  else if (!rosterModels.has(data.model)) fail(file, `model ${data.model} is not in the SKILL.md roster`);
  if (!data.name) fail(file, "name is required");
}

// model IDs everywhere must come from the roster
for (const f of SHIPPED_DOCS.map((p) => join(root, p)).filter((p) => p !== join(root, "CHANGELOG.md") && existsSync(p)).concat(shipped)) {
  const text = read(f);
  for (const id of new Set(text.match(MODEL_ID) || [])) {
    if (!rosterModels.has(id)) fail(f, `model ID ${id} is not in the SKILL.md roster`);
  }
}

// changelog
const changelog = join(root, "CHANGELOG.md");
if (!existsSync(changelog)) fail(root, "CHANGELOG.md is missing");
else if (!new RegExp(`^## \\[?${plugin.version.replace(/\./g, "\\.")}\\]?`, "m").test(read(changelog))) {
  fail(changelog, `no entry for version ${plugin.version}`);
}

// style, line endings, loader-unsafe text, relative links
const styleFiles = shipped.concat(SHIPPED_DOCS.map((p) => join(root, p)).filter(existsSync));
for (const f of styleFiles) {
  const text = read(f);
  if (text.includes("\r\n")) fail(f, "use LF line endings");
  text.split("\n").forEach((line, i) => {
    if (/[\u2014\u2013\u2026\u2190-\u21FF]/.test(line)) fail(f, `line ${i + 1}: use ASCII instead of em/en dashes, ellipses or arrows`);
    if (/\p{Extended_Pictographic}/u.test(line)) fail(f, `line ${i + 1}: no emoji`);
  });
  if (shipped.includes(f) && /\$\d/.test(text)) fail(f, "a dollar sign followed by a digit is expanded by skill loaders; rewrite it");
  for (const m of text.matchAll(/\]\((?!https?:|#|mailto:)([^)\s]+)\)/g)) {
    const target = m[1].split("#")[0];
    if (target && !existsSync(join(dirname(f), target))) fail(f, `broken relative link: ${m[1]}`);
  }
}

if (errors.length) {
  console.error(`Validation failed with ${errors.length} error(s):`);
  for (const e of errors) console.error(`  - ${e}`);
  process.exit(1);
}
console.log(`OK: plugin ${plugin.name} v${plugin.version}, ${skillDirs.length} skill(s), ${agentFiles.length} agent(s), roster ${[...rosterModels].join(", ")}`);

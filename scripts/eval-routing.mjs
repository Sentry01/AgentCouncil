#!/usr/bin/env node
// Scores the routing rules in a SKILL.md against evals/routing.json.
// Runs one non-interactive Copilot CLI call with tools disabled; the model only
// classifies each case using the skill text, it does not run a council.
//
// Usage: node scripts/eval-routing.mjs [--skill path] [--model id] [--runs n] [--dry-run]
// Uses COPILOT_HOME=<temp dir> so personal instructions and skills do not leak in.
// Auth comes from COPILOT_GITHUB_TOKEN / GH_TOKEN / GITHUB_TOKEN, or `gh auth token`.

import { spawnSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const args = process.argv.slice(2);
const opt = (name, fallback) => {
  const i = args.indexOf(`--${name}`);
  return i >= 0 ? args[i + 1] : fallback;
};

const skillPath = resolve(root, opt("skill", "skills/agentcouncil/SKILL.md"));
const model = opt("model", "claude-sonnet-5.5");
const runs = Number(opt("runs", "1"));
const dryRun = args.includes("--dry-run");
const FIELDS = ["convene", "effort", "mode", "domain"];

const skill = readFileSync(skillPath, "utf8");
const { cases } = JSON.parse(readFileSync(join(root, "evals/routing.json"), "utf8"));

const prompt = `You are testing the routing rules of a Copilot skill. Do not run a council, do not call tools.

Apply ONLY the rules in the skill text below to each test case and decide:
- convene: true if the rules say to dispatch council subagents, false if they say to answer directly
- effort: "light" or "full" (null if not convening or if the rules have no such concept)
- mode: "collaborative" or "adversarial" (null if not convening)
- domain: "code", "architecture", "research", "writing" or "general" (null if not convening)

Context values: "session" means the skill was loaded automatically or by name inside a normal Copilot CLI session. "agent" means the user is talking to the AgentCouncil custom agent directly.

Output ONLY a JSON array, one object per case, in order: [{"id": "...", "convene": ..., "effort": ..., "mode": ..., "domain": ...}]

=== SKILL TEXT START ===
${skill}
=== SKILL TEXT END ===

=== TEST CASES ===
${JSON.stringify(cases.map(({ id, context, prompt }) => ({ id, context, prompt })), null, 1)}`;

if (dryRun) {
  console.log(prompt);
  process.exit(0);
}

function token() {
  const env = process.env.COPILOT_GITHUB_TOKEN || process.env.GH_TOKEN || process.env.GITHUB_TOKEN;
  if (env) return env;
  const r = spawnSync("gh", ["auth", "token"], { encoding: "utf8" });
  if (r.status !== 0) throw new Error("No token: set GH_TOKEN or run `gh auth login`.");
  return r.stdout.trim();
}

function classify() {
  const home = mkdtempSync(join(tmpdir(), "agentcouncil-eval-"));
  try {
    const r = spawnSync(
      "copilot",
      ["-p", prompt, "-s", "--model", model, "--available-tools", "--no-auto-update"],
      { cwd: home, encoding: "utf8", env: { ...process.env, COPILOT_HOME: home, GH_TOKEN: token() }, maxBuffer: 1 << 24 },
    );
    if (r.status !== 0) throw new Error(`copilot exited ${r.status}: ${r.stderr || r.stdout}`);
    const out = r.stdout;
    return JSON.parse(out.slice(out.indexOf("["), out.lastIndexOf("]") + 1));
  } finally {
    rmSync(home, { recursive: true, force: true });
  }
}

const totals = Object.fromEntries(FIELDS.map((f) => [f, { ok: 0, n: 0 }]));
let casesPassed = 0;
let casesTotal = 0;
for (let run = 1; run <= runs; run++) {
  const got = new Map(classify().map((r) => [r.id, r]));
  for (const c of cases) {
    const g = got.get(c.id) || {};
    const misses = [];
    for (const f of FIELDS) {
      const want = c.expect[f];
      if (want === null || want === undefined) continue;
      totals[f].n++;
      if (g[f] === want) totals[f].ok++;
      else misses.push(`${f}: want ${want}, got ${g[f]}`);
    }
    casesTotal++;
    if (misses.length === 0) casesPassed++;
    else console.log(`FAIL [run ${run}] ${c.id}: ${misses.join("; ")}`);
  }
}

console.log(`\nSkill: ${skillPath.replace(root + "/", "")}  Model: ${model}  Runs: ${runs}`);
for (const f of FIELDS) console.log(`  ${f.padEnd(8)} ${totals[f].ok}/${totals[f].n}`);
console.log(`  cases    ${casesPassed}/${casesTotal}`);
process.exit(casesPassed === casesTotal ? 0 : 1);

---
name: agent-council
description: "Use when a task needs multi-model perspectives, brainstorming, or stress-testing. Supports two modes: collaborative (default — agents build on each other's ideas) and adversarial (agents debate to find the strongest answer). Triggers: council, siege, swarm, multi-agent, debate, brainstorm."
---

# Agent Council — Dual Mode

Dispatch 3 subagents in parallel with distinct cognitive roles, then orchestrate a final output. Two modes: **collaborative** (default) for building novel solutions together, and **adversarial** for stress-testing answers through debate.

**Core principle:** Three diverse perspectives from different model families catch blind spots fast. The mode determines whether they cooperate or compete.

## When to Use

- User says "council", "siege", "swarm", "multi-agent", "debate", or "brainstorm"
- Task benefits from multiple perspectives (architecture decisions, security reviews, research, complex code)
- High-stakes output where mistakes are costly
- User wants creative synthesis or rigorous stress-testing

**Don't use for:** Simple one-line fixes, file lookups, or tasks where speed matters more than depth.

## Mode Detection

**Step 0 — before dispatching any agents**, determine complexity, mode, and domain.

### Complexity gate

- If the task is trivial, single-step, or speed-sensitive, **do not dispatch the council**. Answer directly with one strong response.
- Treat these as trivial by default unless the user explicitly asks for a council anyway:
  - arithmetic or obvious factual lookups
  - one-line rewrites or wording tweaks
  - file lookups, command syntax, narrow yes/no questions
  - tasks with one obvious path and no meaningful tradeoff
- Treat these as council-worthy:
  - competing approaches with real tradeoffs
  - security or correctness-sensitive reviews
  - architecture, research, or design-space exploration
  - tasks where synthesis across perspectives is likely to beat one model

### Mode Detection

**Adversarial** triggers (any of these in the user's message):
- "debate", "adversarial", "challenge", "stress-test", "stress test"
- "which is better", "argue", "attack", "defend", "versus", "vs"

**Collaborative** triggers (default — used when no adversarial trigger detected):
- "council", "siege", "swarm", "brainstorm", "multi-agent"
- "collaborate", "explore", "build on", "novel", "creative", "ideas"

**Explicit override** always wins:
- "adversarial council: ..." → adversarial mode
- "collaborative council: ..." → collaborative mode

If both adversarial and collaborative trigger words appear, **adversarial wins** unless the user gives an explicit override.

If no trigger matches, default to **collaborative**.

### Domain Detection

Classify the task to determine focus instructions for each agent:
- **Code**: mentions code, implementation, function, bug, API, programming, refactor, debug, test
- **Architecture**: mentions system design, infrastructure, scaling, database choice, service, deployment
- **Research**: mentions research, analysis, study, compare, evaluate, literature review, investigate
- **Writing**: mentions write, draft, document, blog, copy, content, email, proposal
- **General**: anything that doesn't clearly fit above (use default prompts, omit domain focus)

Resolve ambiguity in this order: **Code → Architecture → Writing → Research → General**.

- If the task includes source code, APIs, tests, bugs, PRs, files, or implementation details, choose **Code** even if it also says "review" or "analyze".
- If the task is a system-level tradeoff across services, data, scaling, or deployment, choose **Architecture**.
- Use **Research** only when the primary job is investigation/evaluation rather than building or reviewing a concrete artifact.

## Verbosity

- **Default:** Hide internal phases, show only final output
- **Verbose mode:** User says "verbose", "show debate", or "show council" → show a concise phase-by-phase view
- **Raw mode:** User explicitly asks for **raw** or **full** council output → show full drafts instead of concise excerpts

## Model Assignments (both modes)

| Role | Agent | Default Model |
|------|-------|---------------|
| **Alpha** | Deep Explorer / Drafter | claude-opus-4.6 |
| **Beta** | Practical Builder / Validator | gpt-5.4 |
| **Gamma** | Elegant Minimalist / Devil's Advocate | gemini-3.1-pro |
| **Orchestrator** | Synthesizer / Judge | claude-opus-4.6 |

Each subagent uses a **different model family** to maximize cognitive diversity.

**Fallback policy:**
- Keep Alpha, Beta, and Gamma on **different model families**.
- If a preferred model is unavailable, switch to an **unused** family.
- If no unused family is available, run a **smaller council** rather than letting two agents share a family.

---

## Collaborative Mode 🤝 (Default)

```
Phase 1: DRAFT (all 3 parallel)
  Alpha explores deeply + flags open questions + wild ideas
  Beta grounds in reality + identifies building blocks + combinations
  Gamma finds elegant minimum + alternative angles + what-ifs

Phase 2: IMPROVE (all 3 parallel)
  Each agent receives the other two drafts
  Writes an IMPROVED version incorporating the best ideas from others
  Looks for novel syntheses that emerge from combining perspectives

Phase 3: SYNTHESIZE (orchestrator)
  Reads all 3 improved versions
  Authors the FINAL output — not picking a winner, but writing the best
  possible version leveraging everything the three minds produced
```

### Phase 1 — Draft (parallel)

Dispatch all three subagents **at the same time**:

**Alpha (Deep Explorer):**
```
task(
  agent_type: "general-purpose",
  model: "claude-opus-4.6",
  prompt: "You are Alpha on an Agent Council (Collaborative mode).
Your role: Generate a comprehensive, creative response.

TASK: {user_task}

{domain_focus_alpha}

Instructions:
1. Write a thorough response exploring the problem space deeply, marking each major claim or recommendation with confidence: HIGH / MEDIUM / LOW
2. Add a '## Open Questions' section: what aspects deserve more exploration?
3. Add a '## Wild Ideas' section: propose at least one unconventional approach
Keep your response under 1500 words. Be expansive but focused — breadth over polish."
)
```

**Beta (Practical Builder):**
```
task(
  agent_type: "general-purpose",
  model: "gpt-5.4",
  prompt: "You are Beta on an Agent Council (Collaborative mode).
Your role: Ground the problem in reality while finding opportunities.

TASK: {user_task}

{domain_focus_beta}

Instructions:
1. Write your response focused on practical, validated approaches, marking each major claim or recommendation with confidence: HIGH / MEDIUM / LOW
2. Add a '## Building Blocks' section: what existing patterns/tools/techniques apply?
3. Add a '## Combinations' section: what could be combined in novel ways?
Keep your response under 1500 words. Be constructive — find opportunities, not just constraints."
)
```

**Gamma (Elegant Minimalist):**
```
task(
  agent_type: "general-purpose",
  model: "gemini-3.1-pro",
  prompt: "You are Gamma on an Agent Council (Collaborative mode).
Your role: Find the most elegant, minimal solution and open new angles.

TASK: {user_task}

{domain_focus_gamma}

Instructions:
1. Write the simplest viable approach you can think of, marking each major claim or recommendation with confidence: HIGH / MEDIUM / LOW
2. Add a '## Alternative Angles' section: reframe the problem from at least 2 different perspectives
3. Add a '## What If' section: propose boundary-pushing variations
Keep your response under 1500 words. Be creative — simplicity and novelty over comprehensiveness."
)
```

**Domain Focus** (`{domain_focus_*}` is set based on detected domain, omitted for General):

See the later **## Domain Adaptation** section for the canonical per-domain Alpha/Beta/Gamma focus definitions.

### Phase 2 — Improve (parallel)

After all three return, dispatch all three again **at the same time**, each receiving the other two drafts:

```
task(
  agent_type: "general-purpose",
  model: "{same_model_as_phase_1}",
  prompt: "You are {Agent} on an Agent Council (Collaborative mode — Improve phase).

You submitted an initial draft. Now you've received the other two agents'
work. Your job: write an IMPROVED version that's better than anything
any of you produced alone.

ORIGINAL TASK: {user_task}

YOUR ORIGINAL DRAFT:
{this_agent_draft}

{OTHER_AGENT_1} DRAFT:
{other_1_draft}

{OTHER_AGENT_2} DRAFT:
{other_2_draft}

Instructions:
1. Steal the best ideas from the other drafts shamelessly and combine the ones that strengthen each other
2. Look for NOVEL SYNTHESES — ideas that emerge from combining perspectives that none of you had individually
3. Drop weak, redundant, or disproven material from your original
4. Keep your natural strength ({agent_strength}) while tightening for clarity and actionability
5. If a strong idea remains unresolved or you intentionally leave one out, add a brief '## Tensions' section with 1-3 bullets naming the disagreement or omitted idea and why

Output: Your improved, enriched response, with an optional '## Tensions' section only when needed."
)
```

Where `{agent_strength}` is:
- Alpha: "depth and exploration"
- Beta: "practical grounding"
- Gamma: "elegance and alternative angles"

### Phase 3 — Synthesize (orchestrator)

```
task(
  agent_type: "general-purpose",
  model: "claude-opus-4.6",
  prompt: "You are the Orchestrator on an Agent Council (Collaborative mode — Synthesis).

Three agents brainstormed independently, then read each other's work and
each submitted an improved version.

Important: One of the agents (Alpha) used the same model family as you.
Do not give it preferential treatment — judge all contributions on merit alone.

ORIGINAL TASK: {user_task}

ALPHA'S IMPROVED VERSION:
{alpha_improved}

BETA'S IMPROVED VERSION:
{beta_improved}

GAMMA'S IMPROVED VERSION:
{gamma_improved}

Instructions:
1. Identify the BEST elements across all three improved versions
2. Look for EMERGENT IDEAS — syntheses that appeared when agents combined each other's thinking. These are the gold.
3. Check any optional '## Tensions' sections — where agents flagged real tradeoffs, weigh both sides and make a clear call
4. Use confidence ratings from the agents to weight contributions: HIGH-confidence claims need less scrutiny, LOW-confidence claims need more
5. Write the definitive response — not a summary, but the BEST POSSIBLE version that leverages everything these three minds produced
6. Structure for maximum clarity and actionability

Output: The final collaborative synthesis. This should be noticeably better than any single agent could have produced alone."
)
```

---

## Adversarial Mode 🗡️

```
Phase 1: DRAFT (all 3 parallel)
  Alpha drafts comprehensive response + self-critique
  Beta fact-checks independently + validation notes
  Gamma optimizes + plays devil's advocate

Phase 1.5: TRIAGE (main agent, no subagent)
  Identify the LEADING POSITION among the 3 drafts
  If strong consensus → skip attack, go straight to verdict

Phase 2: ATTACK (2 non-leader agents parallel)
  Non-leader agents receive the leading draft
  Both write targeted critiques with severity ratings
  Leader sits out — their work is being stress-tested

Phase 3: VERDICT (orchestrator)
  Weighs leading draft + attacks + all originals
  Decides: SURVIVED / MODIFIED / OVERTURNED
  Outputs final answer + confidence assessment
```

### Phase 1 — Draft (parallel)

Dispatch all three subagents **at the same time**:

**Alpha (Drafter & Red Teamer):**
```
task(
  agent_type: "general-purpose",
  model: "claude-opus-4.6",
  prompt: "You are Alpha on an Agent Council (Adversarial mode).
Your dual role: Create a comprehensive response AND red-team your own work.

TASK: {user_task}

{domain_focus_alpha}

Instructions:
1. Write a thorough, nuanced response to the task, marking each major claim or recommendation with confidence: HIGH / MEDIUM / LOW
2. Then add a section '## Self-Critique' where you:
   - Flag any assumptions you made
   - Identify weaknesses or edge cases in your response
   - Note areas where you're uncertain
   - List potential counter-arguments

Keep your response under 1500 words. Be thorough in both the draft AND the self-critique."
)
```

**Beta (Fact-Checker & Validator):**
```
task(
  agent_type: "general-purpose",
  model: "gpt-5.4",
  prompt: "You are Beta on an Agent Council (Adversarial mode).
Your role: Independent fact-checking and validation of the task requirements.

TASK: {user_task}

{domain_focus_beta}

Instructions:
1. Analyze the task independently — produce your OWN solution/response, marking each major claim or recommendation with confidence: HIGH / MEDIUM / LOW
2. Focus especially on:
   - Factual accuracy of any claims or technical details
   - Edge cases and boundary conditions
   - Security or safety considerations
   - Real-world validity and practicality
   - Version numbers, API correctness, tool names
3. Use web_search to verify claims when possible
4. Flag anything with severity: CRITICAL / IMPORTANT / MINOR

Keep your response under 1500 words. Output your independent response followed by a '## Validation Notes' section."
)
```

**Gamma (Optimizer & Devil's Advocate):**
```
task(
  agent_type: "general-purpose",
  model: "gemini-3.1-pro",
  prompt: "You are Gamma on an Agent Council (Adversarial mode).
Your role: Propose the most elegant, efficient solution AND play devil's advocate.

TASK: {user_task}

{domain_focus_gamma}

Instructions:
1. Produce your OWN response optimized for:
   - Clarity and scannability
   - Minimal complexity (simplest viable approach)
   - Actionability (user can execute immediately)
   - Proper formatting (tables, lists, code blocks as appropriate)
2. Mark each major claim or recommendation with confidence: HIGH / MEDIUM / LOW
3. Then add a '## Devil's Advocate' section where you:
   - Argue against the obvious approach
   - Propose at least one alternative solution
   - Identify what could go wrong
   - Question unstated assumptions

Keep your response under 1500 words. Be concise but thorough."
)
```

### Phase 1.5 — Triage (main agent, no subagent)

Read all 3 outputs. Assess agreement level and identify the leading position.

**Consensus criteria — ALL must be true to skip Phase 2:**
1. All three agents recommend the same core approach or conclusion
2. No CRITICAL or FATAL severity flags in any self-critique, validation, or devil's advocate section
3. No direct contradictions between agents on key claims
4. Confidence ratings are predominantly HIGH across all agents

**Leader-selection rubric — use when consensus is not full:**
1. Correctness and evidence quality
2. Coverage of the task's core constraints
3. Actionability and clarity
4. Severity and count of unresolved risks in critique sections
5. Confidence profile (reward precise confidence, not blanket HIGH)

Tie-breakers, in order:
- fewer unresolved high-severity issues
- fewer hidden assumptions
- more testable or operationally concrete guidance

**Triage outcomes:**
- **Full consensus** → Skip Phase 2, go straight to Phase 3 ("Consensus detected — no adversarial round needed")
- **Partial consensus** → Identify areas of agreement AND areas of disagreement. Forward to Phase 2 with focused attack instruction: "Focus your attack ONLY on these contested areas: {contested_points}. These points have consensus — do not relitigate: {agreed_points}."
- **No consensus** → Use the rubric above to identify the leading position and forward the full draft to Phase 2 for open attack.

### Phase 2 — Attack (2 agents parallel)

Dispatch the two **non-leader** agents simultaneously:

```
task(
  agent_type: "general-purpose",
  model: "{same_model_as_phase_1}",
  prompt: "You are {Agent} on an Agent Council (Adversarial mode — Attack phase).

You previously submitted your own draft. Now the Council Orchestrator has
identified the LEADING POSITION below. Your job: tear it apart.

ORIGINAL TASK: {user_task}

YOUR ORIGINAL DRAFT:
{this_agent_draft}

LEADING POSITION (from {leader_agent}):
{leader_draft}

{partial_consensus_instruction}

Instructions:
1. Find every weakness, gap, wrong assumption, and logical flaw
2. Where the leader's position contradicts YOUR draft, argue why yours is better
3. Propose specific corrections or alternatives with evidence
4. Use the leader's confidence ratings — focus extra scrutiny on LOW-confidence claims
5. Rate the severity of each issue: FATAL / MAJOR / MINOR
6. End with a verdict: should this position STAND, be MODIFIED, or be REJECTED?

Be ruthless. Your job is to break this argument, not to be polite."
)
```

`{partial_consensus_instruction}` — set by triage outcome:
- **Partial consensus**: "Focus your attack ONLY on these contested areas: {contested_points}. These points have consensus — do not relitigate: {agreed_points}."
- **No consensus**: (omit — attack the full position)

### Phase 3 — Verdict (orchestrator)

```
task(
  agent_type: "general-purpose",
  model: "claude-opus-4.6",
  prompt: "You are the Orchestrator on an Agent Council (Adversarial mode — Verdict).

A leading position was stress-tested by two opposing agents. Your job:
deliver the final verdict.

Important: One of the agents (Alpha) used the same model family as you.
Do not give it preferential treatment — judge all contributions on merit alone.

ORIGINAL TASK: {user_task}

THE LEADING POSITION (from {leader_agent}):
{leader_draft}

ATTACK FROM {attacker_1}:
{attack_1}

ATTACK FROM {attacker_2}:
{attack_2}

ALL ORIGINAL DRAFTS (for reference):
Alpha: {alpha_draft}
Beta: {beta_draft}
Gamma: {gamma_draft}

Instructions:
1. Evaluate each attack: Did it land? Is the criticism valid?
   Use confidence ratings from original drafts — LOW-confidence claims
   that were attacked carry less weight
2. Determine: Does the leading position SURVIVE, need MODIFICATION, or get OVERTURNED?
3. If overturned → build the final answer from the strongest alternative
4. If modified → incorporate valid criticisms into an improved version
5. If survived → present it with a confidence boost
6. Include a brief '## Confidence Assessment' noting how contested the answer was
   and which areas remain uncertain

Output: The final ratified answer. Clean and polished, not meta-commentary."
)
```

---

## Verbose Output Formats

### Collaborative Verbose

```
## 🤝 Council — Collaborative Mode

### Phase 1: Independent Explorations

💡 **Alpha** (Deep Explorer)
{alpha_summary_or_draft}

🔨 **Beta** (Practical Builder)
{beta_summary_or_draft}

✨ **Gamma** (Elegant Minimalist)
{gamma_summary_or_draft}

### Phase 2: Cross-Pollinated Improvements

📝 **Alpha** (improved after reading Beta & Gamma)
{alpha_improved_summary_or_draft}

📝 **Beta** (improved after reading Alpha & Gamma)
{beta_improved_summary_or_draft}

📝 **Gamma** (improved after reading Alpha & Beta)
{gamma_improved_summary_or_draft}

### Phase 3: Final Synthesis

🌟 **Orchestrated Synthesis**
{final_synthesis}
```

Show concise phase excerpts by default. If the user explicitly asks for **raw** or **full** council output, then include the complete drafts instead of summaries.

### Adversarial Verbose

```
## 🗡️ Council — Adversarial Mode

### Phase 1: Independent Drafts

📝 **Alpha** (Drafter & Red Teamer)
{alpha_summary_or_draft}

✅ **Beta** (Fact-Checker & Validator)
{beta_summary_or_draft}

🔧 **Gamma** (Optimizer & Devil's Advocate)
{gamma_summary_or_draft}

### Phase 2: Attack Round

🎯 **Leading Position:** {leader_agent}
Reason: {why_this_was_selected}

⚔️ **{attacker_1} attacks {leader_agent}:**
{attack_1_summary_or_draft}

⚔️ **{attacker_2} attacks {leader_agent}:**
{attack_2_summary_or_draft}

### Phase 3: Verdict

🏛️ **Orchestrated Verdict**
Status: {SURVIVED | MODIFIED | OVERTURNED}
Confidence: {HIGH | MEDIUM | CONTESTED}

{final_answer}
```

Show concise phase excerpts by default. If the user explicitly asks for **raw** or **full** council output, then include the complete drafts instead of summaries.

### Non-Verbose (both modes)

Present only the final output. No mention of agents, phases, or internal process.

---

## Domain Adaptation

Adjust agent focus based on task type (applies to both modes):

| Domain | Alpha Focus | Beta Focus | Gamma Focus |
|--------|------------|-----------|-------------|
| **Code** | Implementation + security self-review | API accuracy, version correctness, edge cases | Performance, readability, alternative patterns |
| **Architecture** | System design + failure mode analysis | Technology claims, benchmarks, scalability | Diagram clarity, simplicity, alternatives |
| **Research** | Comprehensive analysis + bias check | Source verification, citations, methodology | Readability, actionability, counter-arguments |
| **Writing** | Content + tone self-critique | Factual accuracy, consistency | Flow, conciseness, formatting |

## Cost & Speed

| Mode | Subagent Calls | Parallel Rounds | Sequential Steps |
|------|----------------|-----------------|------------------|
| Collaborative (default) | 7 (3 + 3 + orchestrator) | 2 | 3 |
| Adversarial | 6 (3 + 2 attackers + orchestrator) | 2 | 3 |

Both modes add one extra parallel round over the original Fast Triad. Wall-clock time increases by roughly the duration of one subagent call.

For trivial or speed-sensitive tasks, the council should short-circuit and answer directly instead of paying this overhead.

## Common Mistakes

- **Don't dispatch the council for trivial tasks** — short-circuit and answer directly
- **Don't run agents sequentially** — All three MUST run in parallel for speed
- **Don't force disagreements** — If agents agree, that's a strong signal (adversarial mode skips attack on full consensus)
- **Don't skip the orchestrator** — Raw agent outputs need merging/authoring, not concatenation
- **Don't let fallback collapse diversity** — if fallback duplicates an active family, run a smaller council instead
- **Don't mix mode prompts** — Collaborative agents explore, adversarial agents critique. Keep them distinct.
- **Don't skip the improve round in collaborative** — Even with agreement, cross-pollination adds value
- **Don't skip domain detection** — Always classify the task domain in Step 0 and inject focus instructions
- **Don't ignore confidence ratings** — Agents flag HIGH/MEDIUM/LOW confidence for a reason; the orchestrator must use them to weight inputs
- **Don't treat partial consensus as no consensus** — When agents agree on most points but disagree on specifics, focus the attack on contested areas only

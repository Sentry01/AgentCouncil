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

**Step 0 — before dispatching any agents**, determine the mode:

**Adversarial** triggers (any of these in the user's message):
- "debate", "adversarial", "challenge", "stress-test", "stress test"
- "which is better", "argue", "attack", "defend", "versus", "vs"

**Collaborative** triggers (default — used when no adversarial trigger detected):
- "council", "siege", "swarm", "brainstorm", "multi-agent"
- "collaborate", "explore", "build on", "novel", "creative", "ideas"

**Explicit override** always wins:
- "adversarial council: ..." → adversarial mode
- "collaborative council: ..." → collaborative mode

If no trigger matches, default to **collaborative**.

## Verbosity

- **Default:** Hide internal phases, show only final output
- **Verbose mode:** User says "verbose", "show debate", or "show council" → show each agent's output with phase headers

## Model Assignments (both modes)

| Role | Agent | Default Model | Fallback |
|------|-------|---------------|----------|
| **Alpha** | Deep Explorer / Drafter | claude-opus-4.6 | gpt-5.3-codex |
| **Beta** | Practical Builder / Validator | gpt-5.3-codex | gemini-3-pro-preview |
| **Gamma** | Elegant Minimalist / Devil's Advocate | gemini-3-pro-preview | claude-opus-4.6 |
| **Orchestrator** | Synthesizer / Judge | claude-opus-4.6 | gpt-5.3-codex |

Each subagent uses a **different model family** to maximize cognitive diversity.

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

Instructions:
1. Write a thorough response exploring the problem space deeply
2. Add a '## Open Questions' section: what aspects deserve more exploration?
3. Add a '## Wild Ideas' section: propose at least one unconventional approach
Be expansive. This is brainstorming — breadth over polish."
)
```

**Beta (Practical Builder):**
```
task(
  agent_type: "general-purpose",
  model: "gpt-5.3-codex",
  prompt: "You are Beta on an Agent Council (Collaborative mode).
Your role: Ground the problem in reality while finding opportunities.

TASK: {user_task}

Instructions:
1. Write your response focused on practical, validated approaches
2. Add a '## Building Blocks' section: what existing patterns/tools/techniques apply?
3. Add a '## Combinations' section: what could be combined in novel ways?
Be constructive. Find opportunities, not just constraints."
)
```

**Gamma (Elegant Minimalist):**
```
task(
  agent_type: "general-purpose",
  model: "gemini-3-pro-preview",
  prompt: "You are Gamma on an Agent Council (Collaborative mode).
Your role: Find the most elegant, minimal solution and open new angles.

TASK: {user_task}

Instructions:
1. Write the simplest viable approach you can think of
2. Add a '## Alternative Angles' section: reframe the problem from at least 2 different perspectives
3. Add a '## What If' section: propose boundary-pushing variations
Be creative. Simplicity and novelty over comprehensiveness."
)
```

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
1. Steal the best ideas from the other drafts shamelessly
2. Combine approaches that complement each other
3. Look for NOVEL SYNTHESES — ideas that emerge from combining perspectives
   that none of you had individually
4. Drop anything from your original that the others' work revealed as weak
5. Keep your natural strength ({agent_strength}) but enrich it

Output: Your improved, enriched response. Not a meta-commentary about
what you borrowed — just the best version you can produce."
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
each submitted an improved version. You have incredibly rich raw material.

ORIGINAL TASK: {user_task}

ALPHA'S IMPROVED VERSION:
{alpha_improved}

BETA'S IMPROVED VERSION:
{beta_improved}

GAMMA'S IMPROVED VERSION:
{gamma_improved}

Instructions:
1. Identify the BEST elements across all three improved versions
2. Look for EMERGENT IDEAS — syntheses that appeared when agents combined
   each other's thinking. These are the gold.
3. Write the definitive response — not a summary, but the BEST POSSIBLE
   version that leverages everything these three minds produced
4. If any agent proposed something truly novel, make sure it's not lost
5. Structure for maximum clarity and actionability

Output: The final collaborative synthesis. This should be noticeably better
than any single agent could have produced alone."
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

Instructions:
1. Write a thorough, nuanced response to the task
2. Then add a section '## Self-Critique' where you:
   - Flag any assumptions you made
   - Identify weaknesses or edge cases in your response
   - Note areas where you're uncertain
   - List potential counter-arguments

Be thorough in both the draft AND the self-critique."
)
```

**Beta (Fact-Checker & Validator):**
```
task(
  agent_type: "general-purpose",
  model: "gpt-5.3-codex",
  prompt: "You are Beta on an Agent Council (Adversarial mode).
Your role: Independent fact-checking and validation of the task requirements.

TASK: {user_task}

Instructions:
1. Analyze the task independently — produce your OWN solution/response
2. Focus especially on:
   - Factual accuracy of any claims or technical details
   - Edge cases and boundary conditions
   - Security or safety considerations
   - Real-world validity and practicality
   - Version numbers, API correctness, tool names
3. Use web_search to verify claims when possible
4. Flag anything with severity: CRITICAL / IMPORTANT / MINOR

Output your independent response followed by a '## Validation Notes' section."
)
```

**Gamma (Optimizer & Devil's Advocate):**
```
task(
  agent_type: "general-purpose",
  model: "gemini-3-pro-preview",
  prompt: "You are Gamma on an Agent Council (Adversarial mode).
Your role: Propose the most elegant, efficient solution AND play devil's advocate.

TASK: {user_task}

Instructions:
1. Produce your OWN response optimized for:
   - Clarity and scannability
   - Minimal complexity (simplest viable approach)
   - Actionability (user can execute immediately)
   - Proper formatting (tables, lists, code blocks as appropriate)
2. Then add a '## Devil's Advocate' section where you:
   - Argue against the obvious approach
   - Propose at least one alternative solution
   - Identify what could go wrong
   - Question unstated assumptions

Be concise but thorough."
)
```

### Phase 1.5 — Triage (main agent, no subagent)

Read all 3 outputs. Identify the **leading position** — the draft that is strongest overall. If all 3 are in strong agreement, **skip Phase 2** and proceed directly to Phase 3 ("Consensus detected — no adversarial round needed").

Otherwise, note which agent produced the leading position and forward it to the other two for attack.

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

Instructions:
1. Find every weakness, gap, wrong assumption, and logical flaw
2. Where the leader's position contradicts YOUR draft, argue why yours is better
3. Propose specific corrections or alternatives with evidence
4. Rate the severity of each issue: FATAL / MAJOR / MINOR
5. End with a verdict: should this position STAND, be MODIFIED, or be REJECTED?

Be ruthless. Your job is to break this argument, not to be polite."
)
```

### Phase 3 — Verdict (orchestrator)

```
task(
  agent_type: "general-purpose",
  model: "claude-opus-4.6",
  prompt: "You are the Orchestrator on an Agent Council (Adversarial mode — Verdict).

A leading position was stress-tested by two opposing agents. Your job:
deliver the final verdict.

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
2. Determine: Does the leading position SURVIVE, need MODIFICATION, or get OVERTURNED?
3. If overturned → build the final answer from the strongest alternative
4. If modified → incorporate valid criticisms into an improved version
5. If survived → present it with a confidence boost
6. Include a brief '## Confidence Assessment' noting how contested the answer was

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
{alpha_draft}

🔨 **Beta** (Practical Builder)
{beta_draft}

✨ **Gamma** (Elegant Minimalist)
{gamma_draft}

### Phase 2: Cross-Pollinated Improvements

📝 **Alpha** (improved after reading Beta & Gamma)
{alpha_improved}

📝 **Beta** (improved after reading Alpha & Gamma)
{beta_improved}

📝 **Gamma** (improved after reading Alpha & Beta)
{gamma_improved}

### Phase 3: Final Synthesis

🌟 **Orchestrated Synthesis**
{final_synthesis}
```

### Adversarial Verbose

```
## 🗡️ Council — Adversarial Mode

### Phase 1: Independent Drafts

📝 **Alpha** (Drafter & Red Teamer)
{alpha_draft}

✅ **Beta** (Fact-Checker & Validator)
{beta_draft}

🔧 **Gamma** (Optimizer & Devil's Advocate)
{gamma_draft}

### Phase 2: Attack Round

🎯 **Leading Position:** {leader_agent}
Reason: {why_this_was_selected}

⚔️ **{attacker_1} attacks {leader_agent}:**
{attack_1}

⚔️ **{attacker_2} attacks {leader_agent}:**
{attack_2}

### Phase 3: Verdict

🏛️ **Orchestrated Verdict**
Status: {SURVIVED | MODIFIED | OVERTURNED}
Confidence: {HIGH | MEDIUM | CONTESTED}

{final_answer}
```

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

## Common Mistakes

- **Don't run agents sequentially** — All three MUST run in parallel for speed
- **Don't force disagreements** — If agents agree, that's a strong signal (adversarial mode skips attack on consensus)
- **Don't skip the orchestrator** — Raw agent outputs need merging/authoring, not concatenation
- **Don't use for trivial tasks** — Still has overhead; use proportionally
- **Don't mix mode prompts** — Collaborative agents explore, adversarial agents critique. Keep them distinct.
- **Don't skip the improve round in collaborative** — Even with agreement, cross-pollination adds value

# Agent Council — Dual Mode Design

## Problem

The current Fast Triad runs in a single mode where each agent has a fixed role and the orchestrator merges outputs. This works well for general cross-validation, but two distinct use cases need different agent dynamics:

1. **Adversarial** — Stress-test an answer through debate to find the strongest position
2. **Collaborative** — Build together to discover novel solutions to complex problems

## Approach

Add two explicit modes to the council, both built on the same 3-agent + orchestrator foundation. Both use the same model assignments. Only prompts and phase flow change between modes.

**Collaborative is the default mode.** Adversarial is triggered by specific keywords or explicit override.

---

## Mode Architecture

| Aspect | Adversarial 🗡️ | Collaborative 🤝 |
|--------|-----------------|-------------------|
| **Goal** | Stress-test → find the strongest answer | Build together → find a novel synthesis |
| **Phases** | 3: Draft → Attack → Verdict | 3: Draft → Improve → Synthesize |
| **Agent relationship** | Competitors | Co-authors |
| **Orchestrator role** | Judge (picks winner, resolves conflicts) | Author (writes final version from enriched inputs) |
| **Default?** | No | Yes |

### Model Assignments (unchanged, both modes)

| Role | Model | Fallback |
|------|-------|----------|
| Alpha | claude-opus-4.6 | gpt-5.3-codex |
| Beta | gpt-5.3-codex | gemini-3-pro-preview |
| Gamma | gemini-3-pro-preview | claude-opus-4.6 |
| Orchestrator | claude-opus-4.6 | gpt-5.3-codex |

### Mode Detection

```
ADVERSARIAL triggers:
  "debate", "adversarial", "challenge", "stress-test",
  "which is better", "argue", "attack", "defend"

COLLABORATIVE triggers (default):
  "council", "siege", "swarm", "brainstorm",
  "collaborate", "explore", "build on", "novel", "creative", "ideas"

Explicit override:
  "adversarial council: ..." or "collaborative council: ..."
```

---

## Adversarial Mode 🗡️

### Flow

```
┌─────────────────────────────────────────────────────────────┐
│ ADVERSARIAL MODE                                            │
│                                                             │
│ Phase 1: DRAFT (parallel)                                   │
│   Alpha ──┐                                                 │
│   Beta  ──┼── All 3 draft independently with self-critique  │
│   Gamma ──┘                                                 │
│                                                             │
│ Phase 1.5: TRIAGE (orchestrator, lightweight — no subagent) │
│   Main agent picks the LEADING POSITION                     │
│   If strong consensus → skip to verdict (no attack needed)  │
│                                                             │
│ Phase 2: ATTACK (2 agents parallel)                         │
│   Non-leader agents receive the leading draft               │
│   Both write targeted critiques/attacks                     │
│   Leader agent sits out — their work is being tested        │
│                                                             │
│ Phase 3: VERDICT (orchestrator subagent)                    │
│   Weighs: leading draft + attacks + all originals           │
│   Decides: SURVIVED / MODIFIED / OVERTURNED                 │
│   Outputs: final answer + confidence assessment             │
└─────────────────────────────────────────────────────────────┘
```

### Phase 1 — Draft

Same as current Fast Triad. All 3 agents draft independently with self-critique sections.

### Phase 1.5 — Triage

The main agent (not a subagent) reads all 3 outputs and identifies the strongest position. If all 3 are in strong agreement, it **skips Phase 2** ("consensus detected — no adversarial round needed") and proceeds directly to verdict.

### Phase 2 — Attack

Two non-leader agents receive the leading draft and attack it:

```
"You are {Agent} on an Agent Council (Adversarial mode — Attack phase).

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
```

### Phase 3 — Verdict

```
"You are the Orchestrator on an Agent Council (Adversarial mode — Verdict).

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
```

---

## Collaborative Mode 🤝

### Flow

```
┌─────────────────────────────────────────────────────────────┐
│ COLLABORATIVE MODE (default)                                │
│                                                             │
│ Phase 1: DRAFT (parallel)                                   │
│   Alpha ──┐                                                 │
│   Beta  ──┼── All 3 draft independently                     │
│   Gamma ──┘   Exploratory, expansive prompts                │
│                                                             │
│ Phase 2: IMPROVE (parallel)                                 │
│   Alpha ──┐   Each receives ALL other outputs               │
│   Beta  ──┼── Writes IMPROVED version incorporating         │
│   Gamma ──┘   the best ideas from others                    │
│                                                             │
│ Phase 3: SYNTHESIZE (orchestrator subagent)                 │
│   Reads all 3 improved versions                             │
│   Writes the FINAL output — authoring, not judging          │
│   Highlights novel combinations and emergent ideas          │
└─────────────────────────────────────────────────────────────┘
```

### Phase 1 — Draft

Prompts shift from adversarial self-critique to exploratory ideation:

**Alpha:**
```
"You are Alpha on an Agent Council (Collaborative mode).
Your role: Generate a comprehensive, creative response.

TASK: {user_task}

Instructions:
1. Write a thorough response exploring the problem space deeply
2. Add a '## Open Questions' section: what aspects deserve more exploration?
3. Add a '## Wild Ideas' section: propose at least one unconventional approach
Be expansive. This is brainstorming — breadth over polish."
```

**Beta:**
```
"You are Beta on an Agent Council (Collaborative mode).
Your role: Ground the problem in reality while finding opportunities.

TASK: {user_task}

Instructions:
1. Write your response focused on practical, validated approaches
2. Add a '## Building Blocks' section: what existing patterns/tools/techniques apply?
3. Add a '## Combinations' section: what could be combined in novel ways?
Be constructive. Find opportunities, not just constraints."
```

**Gamma:**
```
"You are Gamma on an Agent Council (Collaborative mode).
Your role: Find the most elegant, minimal solution and open new angles.

TASK: {user_task}

Instructions:
1. Write the simplest viable approach you can think of
2. Add a '## Alternative Angles' section: reframe the problem from at least 2 different perspectives
3. Add a '## What If' section: propose boundary-pushing variations
Be creative. Simplicity and novelty over comprehensiveness."
```

### Phase 2 — Improve

All 3 agents receive the other two agents' drafts and write an improved version:

```
"You are {Agent} on an Agent Council (Collaborative mode — Improve phase).

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
```

Where `{agent_strength}` is:
- Alpha: "depth and exploration"
- Beta: "practical grounding"
- Gamma: "elegance and alternative angles"

### Phase 3 — Synthesize

```
"You are the Orchestrator on an Agent Council (Collaborative mode — Synthesis).

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
```

---

## Verbose Output Format

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

### Non-Verbose (both modes)

Just the final answer, no indication of internal process.

---

## Cost & Speed Comparison

| Mode | Subagent Calls | Parallel Rounds | Sequential Steps |
|------|----------------|-----------------|------------------|
| Current Fast Triad | 4 (3 + orchestrator) | 1 | 2 |
| Adversarial | 6 (3 + 2 attackers + orchestrator) | 2 | 3 |
| Collaborative | 7 (3 + 3 improvers + orchestrator) | 2 | 3 |

Both new modes add one extra parallel round. Wall-clock time increases by roughly the duration of one subagent call, not the sum of all of them.

---

## Implementation Notes

- Mode detection happens in the skill's preamble, before dispatching agents
- **Domain detection** also happens in the preamble — classifies the task as code/architecture/research/writing/general and injects domain-specific focus instructions into Phase 1 prompts
- The triage step in adversarial mode (picking the leader) is done by the main agent, not a subagent — keeps it fast
- Consensus-skip in adversarial mode avoids wasting compute when all agents agree — but requires **all 4 criteria** (same approach, no critical flags, no contradictions, high confidence)
- **Partial consensus** in adversarial mode: when agents agree on most points, the attack phase focuses only on contested areas rather than the full position
- No consensus-skip in collaborative mode — the improve round adds value even with agreement
- Both modes support the same `verbose` / `show debate` trigger for transparency
- **Confidence signaling**: all agents annotate major claims with HIGH/MEDIUM/LOW confidence; the orchestrator uses these to weight inputs and focus scrutiny
- **Disagreement surfacing** (collaborative): the improve phase explicitly asks agents to flag disagreements rather than silently dropping conflicting ideas
- **Dropped idea tracking** (collaborative): agents list significant ideas they chose not to incorporate, giving the orchestrator a chance to rescue lost value
- **Orchestrator bias mitigation**: the orchestrator prompt explicitly warns against favoring Alpha (same model family) — judges all contributions on merit
- **Original drafts passed to orchestrator** (collaborative): the synthesizer receives both improved and original versions, preventing good ideas from being lost during cross-pollination
- **Word limit** (1500 words per agent): keeps context window pressure manageable without degrading response quality

## Performance Notes

All improvements are prompt-level changes within existing phases. No new sequential steps were added:
- Collaborative: still 3 phases (Draft → Improve → Synthesize), 2 parallel rounds
- Adversarial: still 3 phases (Draft → Triage → Attack → Verdict), 2 parallel rounds
- Wall-clock time unchanged: ~2 sequential subagent calls regardless of improvements
- The 1500-word soft cap on Phase 1 actually improves Phase 2 latency by reducing input token count

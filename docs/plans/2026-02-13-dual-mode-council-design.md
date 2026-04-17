# Agent Council — Dual Mode Design

## Problem

The current Fast Triad runs in a single mode where each agent has a fixed role and the orchestrator merges outputs. This works well for general cross-validation, but two distinct use cases need different agent dynamics:

1. **Adversarial** — stress-test an answer through debate to find the strongest position
2. **Collaborative** — build together to discover novel solutions to complex problems

The design also has to stay **fast and token-economic**. If the council spends too much context on intermediate material or runs on trivial tasks, it loses the advantage of parallelism.

## Approach

Add two explicit modes to the council, both built on the same 3-agent + orchestrator foundation. Both use the same default model assignments. Only prompts and phase flow change between modes.

**Collaborative is the default mode.** Adversarial is triggered by specific keywords or explicit override.

Three design rules keep the system efficient:
- **Fast-path trivial tasks** — short-circuit and answer directly instead of dispatching a council
- **One extra round only** — no recursive revision loops
- **Lean synthesis** — in collaborative mode, the orchestrator reads the three improved drafts, not all six drafts

---

## Mode Architecture

| Aspect | Adversarial 🗡️ | Collaborative 🤝 |
|--------|-----------------|-------------------|
| **Goal** | Stress-test → find the strongest answer | Build together → find a novel synthesis |
| **Phases** | 3: Draft → Attack → Verdict | 3: Draft → Improve → Synthesize |
| **Agent relationship** | Competitors | Co-authors |
| **Orchestrator role** | Judge (picks winner, resolves conflicts) | Author (writes final version from enriched inputs) |
| **Default?** | No | Yes |

### Model Assignments

| Role | Model |
|------|-------|
| Alpha | claude-opus-4.6 |
| Beta | gpt-5.4 |
| Gamma | gemini-3.1-pro |
| Orchestrator | claude-opus-4.6 |

**Fallback policy:** preserve model-family diversity. If a seat's preferred model is unavailable, use an unused family. If that is impossible, run a smaller council instead of duplicating a family.

### Routing Rules

#### Complexity gate

Short-circuit and answer directly when the task is trivial, single-step, or speed-sensitive:
- arithmetic or obvious factual lookups
- one-line rewrites
- file lookups and command syntax
- narrow questions with one obvious path

Dispatch the council when the task has meaningful tradeoffs, correctness risk, or synthesis value.

#### Mode detection

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

If both adversarial and collaborative trigger words appear, adversarial wins unless the user gives an explicit override.

#### Domain detection

Use this precedence order to avoid ambiguous routing:

**Code → Architecture → Writing → Research → General**

- If the task includes source code, APIs, tests, bugs, PRs, files, or implementation details, classify it as **Code** even if it also says "review" or "analyze".
- If the task is a system-level tradeoff across services, data, scaling, or deployment, classify it as **Architecture**.
- Use **Research** only when the main job is investigation/evaluation rather than building or reviewing a concrete artifact. In this context, "review" means literature-style review, not code review.

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
│   Main agent scores drafts with a fixed rubric              │
│   If strong consensus → skip to verdict                     │
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

Same 3-agent parallel draft structure as before, but confidence is folded into the main writing instruction instead of repeated as a separate prompt step.

### Phase 1.5 — Triage

The main agent reads all 3 outputs and identifies the strongest position. If all 3 are in strong agreement, it **skips Phase 2** ("consensus detected — no adversarial round needed") and proceeds directly to verdict.

When consensus is not full, use this rubric:
1. Correctness and evidence quality
2. Coverage of the task's core constraints
3. Actionability and clarity
4. Severity and count of unresolved risks in critique sections
5. Confidence profile

Tie-breakers:
- fewer unresolved high-severity issues
- fewer hidden assumptions
- more testable or operationally concrete guidance

### Phase 2 — Attack

Two non-leader agents receive the leading draft and attack it. On **partial consensus**, the orchestrator narrows the attack to only the genuinely contested points so the council does not waste tokens relitigating settled ground.

### Phase 3 — Verdict

The verdict prompt remains evidence-driven: it weighs the leading draft, the attacks, and the original drafts, then decides whether the leader survives, needs modification, or gets overturned.

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

Prompts remain role-specific and exploratory. Confidence stays, but it is folded into the primary writing instruction rather than consuming a separate numbered instruction.

### Phase 2 — Improve

All 3 agents receive the other two agents' drafts and write an improved version. The improve prompt is intentionally slimmer:
1. steal the best ideas
2. combine complementary approaches
3. drop weak or redundant material
4. keep the agent's natural strength
5. add an optional brief `## Tensions` section only when a strong idea remains unresolved or was intentionally left out

This preserves the benefits of cross-pollination without forcing every agent to emit multiple meta-sections.

### Phase 3 — Synthesize

The orchestrator receives only:
- Alpha improved
- Beta improved
- Gamma improved

It does **not** receive all three original drafts by default. That was richer, but too expensive. The optional `## Tensions` notes are the lightweight escape hatch for rescuing genuinely valuable disagreements or omitted ideas.

---

## Output Modes

### Default

Final answer only. No internal process.

### Verbose

Show the council as a **concise phase-by-phase view**:
- draft summaries
- improve/attack summaries
- final synthesis or verdict

Only show full raw drafts when the user explicitly asks for **raw** or **full** council output. This keeps terminal output readable and prevents verbose mode from exploding into multi-thousand-word dumps.

---

## Domain Adaptation

| Domain | Alpha Focus | Beta Focus | Gamma Focus |
|--------|------------|-----------|-------------|
| Code | Implementation + security self-review | API accuracy, versions, edge cases | Performance, readability, alternatives |
| Architecture | System design + failure modes | Tech claims, benchmarks, scalability | Diagram clarity, simplicity, alternatives |
| Research | Comprehensive analysis + bias check | Source verification, citations | Readability, actionability, counter-arguments |
| Writing | Content + tone self-critique | Factual accuracy, consistency | Flow, conciseness, formatting |

---

## Implementation Notes

- Mode detection happens in the skill/agent preamble, before dispatching agents
- **Complexity gate** also happens in the preamble — the council should not run on trivial tasks
- **Domain detection** uses an explicit precedence order to avoid ambiguous routing
- The triage step in adversarial mode (picking the leader) is done by the main agent, not a subagent — keeps it fast
- Consensus-skip in adversarial mode avoids wasting compute when all agents agree — but requires **all 4 criteria**
- **Partial consensus** in adversarial mode narrows the attack phase to contested areas
- No consensus-skip in collaborative mode — the improve round still adds value
- **Confidence signaling** is retained, but folded into the main authoring instruction to reduce prompt clutter
- **Disagreement surfacing** in collaborative mode is now conditional via optional `## Tensions`, not required meta-output
- **Orchestrator bias mitigation** remains explicit because the orchestrator shares a model family with Alpha

## Performance Notes

All improvements are prompt-level changes within the existing flow. No new sequential rounds were added:
- Collaborative: still 3 phases (Draft → Improve → Synthesize), with 2 parallel agent rounds before synthesis
- Adversarial: still 2 parallel rounds before the final verdict; triage is a lightweight gate
- Wall-clock time is unchanged in the council path
- Token economy improves because:
  - trivial tasks short-circuit
  - collaborative synthesis no longer ingests six drafts
  - improve prompts emit less mandatory meta-text
  - verbose mode defaults to concise summaries instead of full transcripts

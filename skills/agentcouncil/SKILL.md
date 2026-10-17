---
name: agentcouncil
description: 'Use when the user asks for a council, debate, brainstorm, stress-test, or second opinions from multiple AI models, or when a high-stakes decision, architecture choice, security review, or comparison of competing approaches should be checked by more than one model family. Trigger prefixes: "council:", "debate:", "brainstorm:", "stress-test:", "adversarial council:", "collaborative council:", "quick council:", "full council:".'
license: MIT
compatibility: 'Requires a client whose task or subagent tool accepts a per-call model, such as GitHub Copilot CLI.'
---

# AgentCouncil

Three subagents on three different model families (Alpha, Beta, Gamma) answer independently, interact once, and an orchestrator subagent writes the final answer from anonymized inputs.

- **Collaborative mode** (default): the seats read each other's drafts and improve them; the orchestrator synthesizes.
- **Adversarial mode**: the orchestrator picks the leading draft, the other two seats attack it, the orchestrator rules.
- **Light effort**: one concise pass per seat plus a short synthesis (4 subagent calls).
- **Full effort**: drafts, one interaction round, and a final orchestrator call (5 to 7 subagent calls).

Follow Steps 1 to 7 in order.

## Step 1: Convene or answer directly

The council is **explicitly invoked** when any of these is true:

- The message starts with a council prefix, optionally preceded by `verbose` or `raw`: `council:`, `brainstorm:`, `debate:`, `stress-test:`, `adversarial council:`, `collaborative council:`, `quick council:`, `light council:`, `full council:`, `deep council:`.
- The user asks for a council, for several models, or for a second opinion from other models.
- You are running as the AgentCouncil custom agent.

When explicitly invoked, always convene. Never answer directly first; simple tasks use light effort.

Otherwise the skill was selected automatically. Answer directly, without subagents, when the task is trivial: arithmetic, a factual lookup, command syntax, a file lookup, a one-line edit or rewrite, a narrow yes/no question, or anything with one obvious path and no real tradeoff. After a direct answer, add one line: `For a multi-model answer, ask again with "council: ...".` For anything else, convene.

## Step 2: Effort

- **light**: the user says `quick` or `light`, or the council was explicitly invoked for a task that Step 1 would call trivial.
- **full**: the user says `full`, `deep` or `thorough`, or the task has real tradeoffs, correctness or security risk, or is architecture, research, design or substantial writing.
- If still unclear, use full.

## Step 3: Mode

Apply the first rule that matches:

1. **Prefix.** `debate:`, `stress-test:` and `adversarial council:` mean adversarial. `council:`, `brainstorm:`, `collaborative council:` and effort variants such as `quick council:` or `full council:` mean collaborative.
2. **Intent.** Adversarial when the user wants to choose between named options, validate or attack an existing proposal, plan or artifact, or get a go/no-go verdict. Collaborative when the user wants ideas, options, a design, a plan or an explanation generated. If both appear, use the primary intent of the message.
3. **Default.** Collaborative.

Words are hints, not triggers. Read them in the sense the user meant: "VS Code" is a product, not a comparison, and "the main challenge is" is not a request to debate.

Also record the output style now: **verbose** if the user wrote `verbose`, `show council` or `show debate`; **raw** if they wrote `raw`; otherwise **default**. Collect each seat's key points as phases finish if the style is verbose or raw.

## Step 4: Domain

Pick the first that fits. The domain sets each seat's focus line.

1. **code**: the task centers on a concrete code artifact (source, diff, test, stack trace, config file) or asks for an implementation or bug fix.
2. **architecture**: system-level design or tradeoffs, including services, data models, service APIs, infrastructure, scaling, deployment and technology choices.
3. **writing**: the deliverable is prose, such as a post, email, document, proposal or announcement.
4. **research**: the main job is investigating or evaluating evidence, literature or options, with no concrete artifact to build.
5. **general**: anything else.

| Domain | Alpha focus | Beta focus | Gamma focus |
|---|---|---|---|
| code | Implementation and security self-review | API and version correctness, edge cases | Performance, readability, alternative patterns |
| architecture | System design and failure modes | Technology claims, benchmarks, scaling limits | Simplicity, operability, alternatives |
| research | Comprehensive analysis and bias check | Source verification and citations | Actionability and counter-arguments |
| writing | Content and tone self-critique | Factual accuracy and consistency | Flow, concision, formatting |
| general | none | none | none |

## Step 5: Context brief

Subagents see nothing except the prompt you send. Before dispatching, write a context brief of at most 400 words:

- **Goal**: what the user is trying to achieve and what a good answer looks like.
- **Constraints**: stated requirements, scale, deadlines, stack and preferences.
- **Material**: the code, diff, text or data the task is about. Paste short excerpts verbatim. For long material, give absolute file paths with line ranges so subagents can read them.
- **Already decided**: decisions made earlier in the conversation, and anything out of scope.
- **Working directory**: absolute path.

Keep the brief neutral. Do not hint at a preferred answer. Always pass the user's task text verbatim as well.

## Step 6: Seats, models and dispatch

This roster is the single source of truth for model IDs.

| Seat | Family | Default model | Fallbacks, in order |
|---|---|---|---|
| Alpha | Anthropic | `claude-opus-5.5` | `claude-sonnet-5.5` |
| Beta | OpenAI | `gpt-6.1-sol` | `gpt-5.5` |
| Gamma | Google | `gemini-4-argon` | `grok-4.7` (xAI), `gemini-3.8-flash` |
| Orchestrator | any | `claude-opus-5.5` | `claude-sonnet-5.5`, then the current session model |

**Diversity.** Alpha, Beta and Gamma must run on three different families. The orchestrator may share a family with a seat because it only sees anonymized inputs.

**User overrides.** If the user names models for the seats (for example "council with sonnet, gpt-5.5 and gemini flash"), map them to available model IDs and use them instead of the defaults, keeping the three families distinct.

**Failures.** If a call fails, times out or returns an invalid or unavailable model error, retry that seat once on its next fallback. When a seat's list is exhausted, use any available model from a family that is not already seated. If none is left, continue with the remaining seats and disclose it in the footer. Never substitute silently and never seat two families twice. If fewer than two seats succeed, stop and tell the user which models failed. Each seat keeps the model it actually ran on for every later phase.

**Dispatch.** Call the `task` tool with every required field: `agent_type: "general-purpose"`, `model: <seat model>`, `name` (a short id such as `alpha-draft` or `orchestrator-synthesis`), `description` (3 to 5 words) and `prompt`. Send all calls of a phase in parallel, in a single response, and wait for all of them before starting the next phase. Run at most one improve or attack round.

**Seat header.** Every Alpha, Beta and Gamma prompt starts with:

```
You are {Alpha|Beta|Gamma} on AgentCouncil ({mode} mode, {phase}).
Work read-only: you may read files and search the web, but do not edit files, run commands that change state, or post anything anywhere.
Mark each major claim HIGH, MEDIUM or LOW confidence and say what it rests on: a source, a check you ran, or reasoning only.

CONTEXT BRIEF:
{context_brief}

TASK (verbatim from the user):
{task}

FOCUS: {this seat's focus from the Step 4 table; omit this line for general}
```

**Anonymization.** Before every orchestrator call:

- Relabel the seat outputs as Response A, B and C in an order where A is not Alpha. Vary the order between councils.
- Remove seat names, role names, model names and self-references such as "as Claude".
- Keep the mapping to yourself. Use it only to route attacks, for verbose output and for the footer.

## Step 7: Run the protocol

Read exactly one reference file from this skill's directory and follow it:

- light effort, either mode: `references/light.md`
- full effort, collaborative: `references/collaborative.md`
- full effort, adversarial: `references/adversarial.md`

## Output

**Default.** Show the final answer from the last orchestrator call verbatim. Do not rewrite, shorten or add commentary to it. Then add one footer line:

```
AgentCouncil: {mode}, {effort}. Seats: Alpha {model}, Beta {model}, Gamma {model}. Orchestrator: {model}.
```

When a fallback was used, append `Fallback: {seat} {failed model} unavailable, used {model}.` When a seat was dropped, append `Ran with 2 seats: {seat} unavailable.`

**Verbose.** Before the final answer, show every phase that ran:

```
## Phase 1: {phase name}
**Alpha ({model})**
- {2 to 4 bullets with the seat's key points}
**Beta ({model})**
- ...
**Gamma ({model})**
- ...
## Phase 2: ...
## Final answer
{orchestrator answer, verbatim}
{footer}
```

In adversarial mode, also show the triage result (consensus level, leading seat, reason), each attacker's verdict, and the ruling (SURVIVED, MODIFIED or OVERTURNED).

**Raw.** Same layout as verbose, with each seat's complete output instead of bullets.

## Rules

- Never skip the orchestrator, and never concatenate drafts as the answer.
- Never run more than one improve or attack round.
- Do not force disagreement. Agreement between different model families is a signal.
- Keep collaborative and adversarial prompts separate.
- Subagents never modify files or external systems.
- Cost: light 4 subagent calls; full collaborative 7; full adversarial 7, or 5 when triage finds full consensus.

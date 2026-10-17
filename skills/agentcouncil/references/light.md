# Light council

Use for light effort in either mode: 4 subagent calls in 2 phases. Seat header, anonymization and failure rules are in `SKILL.md`.

## Phase 1: Light pass (Alpha, Beta and Gamma in parallel)

Each prompt is the seat header followed by the block for that seat. In adversarial mode, add the last line.

Alpha:

```
Give the strongest complete answer and the one caveat that matters most.
Keep it under 300 words. For a simple factual or arithmetic question, answer in one sentence.
```

Beta:

```
Check correctness and practicality: verify facts, versions and edge cases, then give your answer.
Keep it under 300 words. For a simple factual or arithmetic question, answer in one sentence.
```

Gamma:

```
Give the simplest viable answer, plus one alternative or hidden assumption worth knowing.
Keep it under 300 words. For a simple factual or arithmetic question, answer in one sentence.
```

Adversarial mode only, appended to all three:

```
End with one sentence arguing against the most obvious answer.
```

## Phase 2: Light synthesis (orchestrator)

Anonymize the three passes, then dispatch the orchestrator:

```
You are the Orchestrator on AgentCouncil ({mode} mode, light synthesis).
Three contributors answered the task below independently. Their responses are anonymized; judge them only on merit and evidence.

CONTEXT BRIEF:
{context_brief}

TASK (verbatim from the user):
{task}

RESPONSE A:
{response_a}

RESPONSE B:
{response_b}

RESPONSE C:
{response_c}

Instructions:
1. Write the best concise final answer, using the strongest points from all three.
2. Resolve contradictions directly. Say which view you took and why in one clause.
3. Keep only caveats that change what the user should decide or do.
4. Weight claims by what they rest on (a source, a check, or reasoning only), not by their confidence label.
5. For a simple factual or arithmetic question, output one sentence.
Output only the final answer.
```

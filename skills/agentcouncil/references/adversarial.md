# Full adversarial council

Draft, triage, attack, verdict: 7 subagent calls in 4 phases, or 5 when triage finds full consensus. Seat header, anonymization and failure rules are in `SKILL.md`.

## Phase 1: Draft (Alpha, Beta and Gamma in parallel)

Each prompt is the seat header (phase: draft) followed by the block for that seat.

Alpha, Drafter and Red Teamer:

```
Role: Drafter and Red Teamer.
1. Write a thorough, nuanced response.
2. Add "## Self-Critique": your assumptions, weaknesses, edge cases, uncertainties and the strongest counter-arguments.
Keep it under 1500 words.
```

Beta, Fact-Checker and Validator:

```
Role: Fact-Checker and Validator.
1. Produce your own independent response.
2. Focus on factual accuracy, edge cases, security and safety, real-world validity, and correctness of versions, APIs and tool names. Verify claims with web search where possible.
3. Add "## Validation Notes": each issue tagged CRITICAL, IMPORTANT or MINOR.
Keep it under 1500 words.
```

Gamma, Optimizer and Devil's Advocate:

```
Role: Optimizer and Devil's Advocate.
1. Produce your own response, optimized for clarity, minimal complexity and immediate actionability.
2. Add "## Devil's Advocate": argue against the obvious approach, propose at least one alternative, say what could go wrong, and question unstated assumptions.
Keep it under 1500 words.
```

## Phase 2: Triage (orchestrator)

Anonymize the three drafts as Response A, B and C. Keep these labels for the rest of this council. Dispatch:

```
You are the Orchestrator on AgentCouncil (adversarial mode, triage).
Work read-only: do not edit files, run commands that change state, or post anything anywhere.
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

Decide the consensus level:
- full: all three reach the same core conclusion, no unresolved CRITICAL issue is raised in any critique or validation section, and there is no material contradiction on key claims.
- partial: the same core conclusion, but material disagreement on specific points.
- none: different core conclusions.

Pick the leading response by, in order: correctness and evidence quality; coverage of the task's core constraints; actionability and clarity; severity and number of unresolved risks; calibrated confidence (precise labels beat blanket HIGH). Tie-breakers: fewer unresolved high-severity issues, then fewer hidden assumptions, then more testable guidance.

Output exactly:
CONSENSUS: full | partial | none
LEADER: A | B | C
AGREED:
- {point}
CONTESTED:
- {point}
REASON: {one sentence}
```

- **full**: skip Phase 3 and run Phase 4 without critiques.
- **partial** or **none**: run Phase 3.

## Phase 3: Attack (the two non-leader seats in parallel)

Map the leader label back to its seat. The other two seats attack, each on the model it used in Phase 1. Prompt: the seat header (phase: attack), then:

```
You wrote the draft below. Another contributor's response was selected as the leading position. Your job is to break it.

YOUR DRAFT:
{own_draft}

LEADING POSITION:
{leader_response}

{partial consensus only: Attack only these contested points: {contested}. Do not relitigate these agreed points: {agreed}.}

Instructions:
1. Find every weakness, gap, wrong assumption and logical flaw.
2. Where the leading position contradicts your draft, argue why yours is better, with evidence.
3. Propose specific corrections or alternatives.
4. Put extra scrutiny on LOW-confidence and unsupported claims.
5. Tag each issue FATAL, MAJOR or MINOR.
6. End with one verdict: STAND, MODIFY or REJECT.
Be direct, not polite. Do not invent problems: if the position holds, say so.
```

## Phase 4: Verdict (orchestrator)

Label the two attacks Critique 1 and Critique 2, with seat and model names removed. Dispatch:

```
You are the Orchestrator on AgentCouncil (adversarial mode, verdict).
Work read-only: do not edit files, run commands that change state, or post anything anywhere.
A leading position was stress-tested. Deliver the final ruling. All inputs are anonymized; judge them only on merit and evidence.

CONTEXT BRIEF:
{context_brief}

TASK (verbatim from the user):
{task}

LEADING POSITION (Response {leader_label}):
{leader_response}

CRITIQUE 1:
{critique_1, or "None: triage found full consensus."}

CRITIQUE 2:
{critique_2, or omit}

ALL RESPONSES FOR REFERENCE:
Response A: {response_a}
Response B: {response_b}
Response C: {response_c}

Instructions:
1. For each critique point, decide whether it lands. Weight by evidence, not by confidence labels or tone.
2. Rule SURVIVED, MODIFIED or OVERTURNED. If overturned, build the answer from the strongest alternative. If modified, fold in the valid criticism.
3. Write the final answer, clean and ready to use.
4. End with "## Confidence Assessment": the ruling, how contested the answer was, and what remains uncertain.
The user never sees the individual responses or critiques. Do not mention Response A, B or C, Critique 1 or 2, contributors or seats. Refer to "the leading answer" or "a critique" if you must.
Output only the final answer.
```

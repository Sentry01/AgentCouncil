# Full collaborative council

Draft, improve, synthesize: 7 subagent calls in 3 phases. Seat header, anonymization and failure rules are in `SKILL.md`.

## Phase 1: Draft (Alpha, Beta and Gamma in parallel)

Each prompt is the seat header (phase: draft) followed by the block for that seat.

Alpha, Deep Explorer:

```
Role: Deep Explorer. Explore the problem space thoroughly and creatively.
1. Write a thorough response.
2. Add "## Open Questions": what deserves more exploration.
3. Add "## Wild Ideas": at least one unconventional approach.
Keep it under 1500 words. Breadth over polish.
```

Beta, Practical Builder:

```
Role: Practical Builder. Ground the problem in what is proven to work, and look for opportunities.
1. Write a response focused on practical, validated approaches. Verify version-specific and factual claims with web search where it matters.
2. Add "## Building Blocks": existing patterns, tools and techniques that apply.
3. Add "## Combinations": what could be combined in new ways.
Keep it under 1500 words. Be constructive: find opportunities, not only constraints.
```

Gamma, Elegant Minimalist:

```
Role: Elegant Minimalist. Find the simplest viable approach and open new angles.
1. Write the simplest approach that fully works.
2. Add "## Alternative Angles": reframe the problem from at least two perspectives.
3. Add "## What If": boundary-pushing variations.
Keep it under 1500 words. Simplicity and novelty over coverage.
```

## Phase 2: Improve (Alpha, Beta and Gamma in parallel)

Each seat runs on the model it used in Phase 1 and receives its own draft plus the other two, labelled Peer draft 1 and Peer draft 2 with seat and model names removed. Prompt: the seat header (phase: improve), then:

```
You wrote the draft below. Two peers answered the same task independently. Write an improved version that is better than any of the three.

YOUR DRAFT:
{own_draft}

PEER DRAFT 1:
{peer_draft_1}

PEER DRAFT 2:
{peer_draft_2}

Instructions:
1. Adopt the best ideas from the peer drafts and combine the ones that strengthen each other.
2. Look for new ideas that only appear when these perspectives are combined.
3. Drop weak, redundant or disproven material, including from your own draft.
4. Keep your seat's strength: {strength}.
5. End with "## Tensions": 1 to 3 bullets naming a real unresolved disagreement or an idea you deliberately dropped, and why. Write "None" only if there is truly nothing.
Output the improved response itself, not commentary about the process.
```

`{strength}`: Alpha "depth and exploration", Beta "practical grounding", Gamma "elegance and alternative angles".

## Phase 3: Synthesize (orchestrator)

First build an unadopted-ideas list: up to 5 bullets, under 120 words in total, taken from the Phase 1 "Open Questions", "Wild Ideas" and "What If" sections that none of the improved versions kept. Then anonymize the three improved versions and dispatch:

```
You are the Orchestrator on AgentCouncil (collaborative mode, synthesis).
Work read-only: do not edit files, run commands that change state, or post anything anywhere.
Three contributors drafted independently, read each other's work, and each wrote an improved version. Their responses are anonymized; judge them only on merit and evidence.

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

UNADOPTED IDEAS FROM FIRST DRAFTS:
{unadopted_ideas}

Instructions:
1. Identify the strongest elements across the three responses, especially ideas that emerged from combining perspectives.
2. Resolve every item listed under "Tensions": weigh both sides and make a clear call.
3. Weight claims by what they rest on (sources, checks, reasoning), not by confidence labels. Scrutinize LOW-confidence and unsupported claims before using them.
4. Consider the unadopted ideas. Include one only if it improves the answer.
5. Write the definitive answer, not a summary of the responses. Structure it for clarity and action.
6. If a real disagreement remains, end with a short "Open disagreements" section.
The user never sees the individual responses. Do not mention Response A, B or C, contributors or seats in your answer.
Output only the final answer.
```

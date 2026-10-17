---
description: Turn a new requirement (a spec file) into a reviewed implementation plan. 
   Reads the spec, analyzes the code with subagents, drafts a plan in plan mode, lets the human review it, 
   then saves it as a Markdown file an agent can implement. 
   Use when the user asks to "analyze a requirement", "plan a spec", or "plan a new feature".
argument-hint: <path-to-spec>
allowed-tools: Read, Grep, Glob, Agent, EnterPlanMode, ExitPlanMode, Write
disallowed-tools: Edit, Bash, PowerShell
---
Turn the requirement in `$ARGUMENTS` into an implementation plan. Follow these steps in order and do not skip ahead.
This skill is read-only with one exception: the plan file in step 5.
Never edit source code, run shell commands, or change any other file.

# Steps:

1. **Read the spec.** Read `$ARGUMENTS` (specs live in `docs/specs/`). Restate the requirement in a few lines.
   If anything is ambiguous, ask the human before continuing.

2. **Analyze the code.** Launch one or more `Explore` subagents (in parallel
   when areas are independent, e.g. backend vs. frontend) to explore the codebase. Each returns a short
   list of findings: the files and methods likely to be impacted, and any existing pattern to follow. Wait until agents finish.

3. **Enter plan mode.** Before drafting, make sure plan mode is on.
   - If you are already in plan mode, continue to step 4.
   - Otherwise, tell the human: "Exploration is done. You can switch to plan
     mode yourself (Shift+Tab until the indicator shows 'plan mode on'), or
     reply 'go' and I'll enter it myself." Then stop and wait for their reply.
   - If they switch it on or tell you to continue, proceed to step 4. If they
     say 'go' and plan mode is still off, call `EnterPlanMode`.
   - Guidelines:
     - List of sequential steps that need to be executed to implement the feature
     - Use real code snippets to facilitate plan review and implementation

4. **Human review.** Present the plan and stop. Wait for the human suggestions or
   fixes and update the plan until they approve it.

5. **Write the plan file.** Save the approved plan to
   `docs/plans/<spec-name>.plan.md` (same name as the spec, `.spec.md` replaced by
   `.plan.md`). Write it so another agent can implement it without further context.
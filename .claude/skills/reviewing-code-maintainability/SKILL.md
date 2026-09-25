---
description: Clean-code and maintainability review of the current branch's changes,
  applying a software architect's eye at the code level (naming, method/class size,
  duplication, coupling, module boundary and dependency-direction violations, error
  handling, testability, consistency with existing patterns). Use when the user asks
  for a "maintainability review", "clean-code check", an "architecture review
  perspective" on a diff, or a pre-merge code review of a branch or PR.
argument-hint: [base-branch]
allowed-tools: Bash, Read, Grep, Glob
context: fork
---

Review the changes on the current branch against $0 for maintainability and
clean code. The goal is to catch what will make this code harder to
understand, change, or test six months from now, before it is merged.

If no base branch was given, use `main`.

Review only code that was added or changed in this branch. Do not flag
pre-existing problems unless the change makes them worse.

Read [review-criteria.md](review-criteria.md) to understand what to include in the review.

Do not comment on formatting or anything a linter or formatter would catch.
Do not invent findings to fill the sections. If the change is clean, say so.

Be concise. Format output in the following sections:

**Should fix before merge**
Issues that will cause real maintenance pain. For each: `file:line`, what
the problem is, why it matters, and a concrete suggested fix. If none,
write "None".

**Worth improving**
Smaller issues, or ones that are reasonable to defer. Same format as above,
one line each where possible. Limit these suggestions to maximum 5 to keep them
actionable. If none, write "None".
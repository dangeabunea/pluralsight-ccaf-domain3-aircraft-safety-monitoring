---
description: Review changes made to the codebase for maintainability and clean code
argument-hint: [base-branch]
allowed-tools: Bash, Read, Grep, Glob
---

Review the changes on the current branch against the base branch for
maintainability and clean code. The goal is to catch what will make this code
harder to understand, change, or test six months from now, before it is merged.

Base branch argument: "$ARGUMENTS"
If that value is empty, use `main`.

Review only code that was added or changed in this branch. Do not flag
pre-existing problems unless the change makes them worse.

Look for:

- **Naming and intent**: names that hide or mislead about what the code does;
  logic that needs a comment only because the code isn't clear
- **Size and responsibility**: methods or classes doing more than one job;
  deep nesting; long parameter lists; mixed levels of abstraction
- **Complexity**: speculative generality, needless indirection, magic
  numbers or strings, dead code, commented-out code

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

#!/usr/bin/env python3
"""
Pre-push gate — runs before any 'git push' Bash command.

For backend worktrees: runs Maven tests + PMD analysis and blocks on failure.
For frontend worktrees (detected by presence of web-app/): skips Maven and
prints a reminder to run ng test locally. Frontend CI validates Angular tests.
"""

import os
import re
import subprocess
import sys


def main():
    tool_input = os.environ.get("TOOL_INPUT", "")

    if "git push" not in tool_input:
        sys.exit(0)

    # Extract worktree path from: git -C <path> push ...
    match = re.search(r"-C\s+(\S+)", tool_input)
    worktree = match.group(1) if match else ""

    if worktree and os.path.isdir(os.path.join(worktree, "web-app")):
        print("[Pre-Push Gate] Frontend worktree detected — Maven gate skipped.")
        print('[Pre-Push Gate] Reminder: run "ng test --watch=false" locally before pushing.')
        sys.exit(0)

    print("[Pre-Push Gate] Running tests and PMD analysis before push...")
    result = subprocess.run(["mvn", "-B", "test", "pmd:check", "pmd:cpd-check", "-q"])
    if result.returncode == 0:
        print("[Pre-Push Gate] All gates passed")
        sys.exit(0)
    else:
        print("[Pre-Push Gate] Gate FAILED — fix failing tests or PMD violations first.")
        sys.exit(1)


if __name__ == "__main__":
    main()

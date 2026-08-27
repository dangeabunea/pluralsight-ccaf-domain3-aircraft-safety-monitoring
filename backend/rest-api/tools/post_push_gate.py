#!/usr/bin/env python3
"""
Post-push gate — fires after any 'git push' Bash command.

Reads the last commit message from the correct worktree (not the main repo)
and either outputs the PR Review Gate message or skips for maintenance commits.
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
    worktree = match.group(1) if match else "."

    result = subprocess.run(
        ["git", "-C", worktree, "log", "-1", "--format=%s"],
        capture_output=True,
        text=True,
    )
    last_msg = result.stdout.strip() if result.returncode == 0 else ""

    if re.match(r"^(wip|fixup|chore):", last_msg):
        print(f"[CI Poll Gate] Skipped — commit classified as maintenance: {last_msg}")
    else:
        print(
            "[CI Poll Gate] Git push detected. Poll the PR's CI check status using "
            "mcp__github__pull_request_read every 30 seconds (up to 10 attempts). "
            "When mergeable_state is 'clean': Low risk (Phase 3 tier) → call "
            "mcp__github__merge_pull_request immediately and autonomously; "
            "Medium or High risk → post the PR link to the human and wait for manual merge. "
            "Tell the user what is happening at each poll attempt."
        )


if __name__ == "__main__":
    main()

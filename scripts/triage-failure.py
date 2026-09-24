#!/usr/bin/env python3
import sys
import os
import json
import urllib.request

def build_prompt(stack_trace: str, diff: str) -> str:
    return f"""You are helping a developer understand why a test or application failed.

Below is the failure output (stack trace / error) and the git diff of
recent changes. Analyze them together and answer:

1. What is the most likely root cause? Pay special attention to the
   deepest "Caused by:" line in the stack trace — it is usually the
   most specific and diagnostic clue, even if earlier lines look more
   generic. Name the specific exception type and what it implies.
2. Which specific lines in the diff (if any) are implicated? If the
   diff is empty or unrelated, say so explicitly rather than assuming
   the failure is environmental by default.
3. Is this likely a real bug, a flaky/environmental issue, or a
   configuration/naming mismatch (e.g. a hostname, port, or credential
   that doesn't match between two places)?
4. One concrete, specific next step — reference the exact hostname,
   variable, or value involved if one appears in the trace.

Be concise and specific. Do not restate the stack trace back verbatim.

--- FAILURE OUTPUT ---
{stack_trace}

--- GIT DIFF ---
{diff}
"""

def call_claude(prompt: str) -> str:
    api_key = os.environ.get("ANTHROPIC_API_KEY")
    if not api_key:
        print("Error: ANTHROPIC_API_KEY is not set.", file=sys.stderr)
        sys.exit(1)

    payload = json.dumps({
        "model": "claude-sonnet-4-6",
        "max_tokens": 1000,
        "messages": [{"role": "user", "content": prompt}]
    }).encode("utf-8")

    req = urllib.request.Request(
        "https://api.anthropic.com/v1/messages",
        data=payload,
        headers={
            "Content-Type": "application/json",
            "x-api-key": api_key,
            "anthropic-version": "2023-06-01",
        },
        method="POST",
    )

    with urllib.request.urlopen(req) as response:
        result = json.loads(response.read().decode("utf-8"))
        return "".join(
            block["text"] for block in result.get("content", [])
            if block.get("type") == "text"
        )

def main():
    if len(sys.argv) != 3:
        print("Usage: triage-failure.py <stack_trace_file> <diff_file>", file=sys.stderr)
        sys.exit(1)

    with open(sys.argv[1], "r") as f:
        stack_trace = f.read()
    with open(sys.argv[2], "r") as f:
        diff = f.read()

    prompt = build_prompt(stack_trace, diff)
    analysis = call_claude(prompt)
    print(analysis)

if __name__ == "__main__":
    main()
---
name: code-review-checklist
description: Walk through a structured checklist for reviewing a pull request
---
# Code Review Checklist

Review a pull request's diff against a consistent checklist instead of an
ad-hoc read-through.

1. Check correctness: does the logic match the stated intent?
2. Check test coverage: are the new/changed code paths actually tested?
3. Check for missed edge cases and error handling.
4. Check naming, readability, and whether comments explain "why" not "what".
5. Fill in `templates/review-checklist.md` and return the result.

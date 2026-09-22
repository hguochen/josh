---
name: commit-message-writer
description: Draft a conventional commit message from a summary of code changes
---
# Commit Message Writer

Turn a description of what changed into a well-formed commit message.

1. Identify the type of change (feat, fix, refactor, docs, test, chore).
2. Write a short, imperative-mood summary line (under ~70 characters).
3. Add a body explaining *why* the change was made, not just what changed.
4. Fill in `templates/commit-message.md` and return the result.

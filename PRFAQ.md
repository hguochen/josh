# JOSH: Just Our Skills Hub — Press Release & FAQ

## Press Release

**JOSH Launches: A Shared Catalog for Every AI-Assisted Developer's Skills**

*Publish, discover, and reuse AI-assistant skills across the org — no more copy-pasting prompts.*

[LOCATION] — [DATE]

- **What's launching:** JOSH, a shared catalog for reusable AI-assistant skills — bug-report templates, commit-message writers, code-review checklists, and more
- **Problem:** skills trapped in individual developers' heads and local files; no way to search whether one already exists, no version confidence, no team-wide reuse
- **Solution:** publish a skill in one command; every version stored immutably. Ask your AI assistant a natural-language question, get back the matching skill, checksum-verified
- **Personal-first workflow:** publish privately, then explicitly promote to the shared catalog when ready — like a private fork before merging to main
- **Leadership quote:** "We built JOSH because the best AI-assistant instructions were trapped in individual developers' heads and local files. JOSH makes the team's collective skill-writing effort reusable, the same way a shared code repo makes code reusable." — Head of Developer Platform
- **Developer experience:** ask your assistant a question, get a skill in seconds; publish your own with one command from its folder
- **Testimonial:** "I used to keep a folder of prompt snippets. Now I publish once, and the whole team — including future me on a new laptop — finds it by asking. When I improved my commit-message skill, everyone got the update just by asking for the latest version." — senior engineer, platform team
- **Get started:** connect your AI assistant to the JOSH MCP server and ask if a skill exists, or publish your own — see the README

## Frequently Asked Questions

### Customer FAQs

- **Publish a skill?** Ask your assistant to publish your skill folder via `publish_skill` — returns name, version, checksum
- **Find a skill?** Ask a natural-language question like "is there a skill for X?" — searches shared + your own private skills
- **Publish under an existing name?** Creates a new version, never overwrites; full history stays visible
- **Keep a skill private?** Yes — publish with `visibility=private`; only you can find/retrieve it until you promote it
- **Concurrent publish/promote conflicts?** Clean error asking you to retry — no corruption, no crash
- **Size limit?** 5MB — skills are meant to be small and text-based
- **Delete or unpublish?** Not today — append-only by design

### Stakeholder FAQs

- **Infrastructure?** Self-contained: embedded SQLite + local files, no external dependencies. Single machine, ~200 developers
- **Security?** No authentication yet — author is a self-reported, unverified name. Fine for a trusted closed group, but "private" isn't real access control yet. Top priority before wider rollout
- **Uptime?** Weekly consistent backups; no automatic failover (HA) yet
- **Can content be deleted?** No — append-only by design. Nothing disappears, but nothing published by mistake can be removed either
- **Roadmap?** Real authentication, rate limiting, structured logging + health monitoring, tag-based search (e.g. finance vs. engineering)
- **Different from an assistant's built-in skills feature?** JOSH is a separate, team-shared catalog — not a single-user local feature

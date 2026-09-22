# JOSH: Just Our Skills Hub — Press Release & FAQ

## Press Release

**JOSH Launches: A Shared Catalog for Every AI-Assisted Developer's Skills**

*Developers can now publish, discover, and reuse AI-assistant skills across the entire engineering organization — no more copy-pasting prompts between teammates.*

[LOCATION] — [DATE] — Today the engineering organization is launching JOSH (Just Our Skills Hub), a shared catalog that lets any developer publish reusable AI-assistant "skills" — structured instructions like bug-report templates, commit-message writers, or code-review checklists — so every other developer can discover and use them directly through their own AI assistant, with no file handoffs and no copy-pasting.

Until now, a developer who built a useful AI-assistant skill had no way to share it beyond pasting it into a team channel or a wiki page that quickly went stale. There was no single place to search for "does a skill for this already exist," no way to know if you were using the latest version of something a teammate wrote, and no way for the team to build on each other's work. As more developers rely on AI assistants for daily work, this gap has meant the same instructions get reinvented, inconsistently, across dozens of individual developer setups.

JOSH solves this with a lightweight, self-contained catalog service any AI assistant can talk to directly. A developer packages a skill and publishes it with one command; JOSH stores every version immutably, so nothing is ever silently overwritten and prior versions stay retrievable. Other developers ask their own assistant a natural-language question — "is there a skill for writing bug reports?" — and get back the matching skill, verified byte-for-byte identical to what was published, ready to use immediately. A developer can also keep a skill private to themselves first, in their own personal collection, and explicitly promote it to the shared catalog only once they're happy with it — the same "try it privately, then share it" workflow developers already know from version control.

"We built JOSH because the best AI-assistant instructions on our team were trapped in individual developers' heads and local files," said the Head of Developer Platform. "Every team was solving the same discovery problem — knowing whether a good skill already existed and finding it — on their own. JOSH makes the team's collective skill-writing effort visible and reusable, the same way a shared code repository makes the team's code reusable."

For a developer, using JOSH feels invisible: they simply ask their AI assistant a question in plain language, and if a matching skill exists, it's fetched and ready to use in seconds — no separate app to open, no manual download, no version confusion. A developer who wants to share something they've built runs one command from their own skill's folder, and it's instantly searchable by every other developer's assistant.

"I used to keep a folder of prompt snippets I'd copy into whatever tool I was using that week," said a senior engineer on the platform team. "Now I just publish it to JOSH once, and anyone on the team — including future me on a different laptop — can find it by asking. When I improved my commit-message skill last month, everyone using it got the update automatically just by asking for the latest version."

JOSH is available today for every developer in the organization. To get started, connect your AI assistant to the JOSH MCP server and ask it whether a skill already exists for what you're working on — or publish your own in one command. See the project README for setup instructions.

## Frequently Asked Questions

### Customer FAQs

1. **How do I publish a skill to JOSH?**
   From your AI assistant, ask it to publish your skill's folder. The `publish_skill` tool zips your `SKILL.md` and any supporting files and sends them to the catalog; you get back the skill's name, version number, and a checksum confirming exactly what was stored.

2. **How do I find a skill someone else published?**
   Ask your AI assistant a natural-language question, like *"is there a skill for X?"* It searches the shared catalog — and your own private skills — and returns matches with their description and latest version.

3. **What happens if I publish a skill under a name that already exists?**
   JOSH never overwrites anything. Publishing under an existing name creates a new version; every prior version stays retrievable by number, and the full history is visible to everyone.

4. **Can I keep a skill private while I'm still working on it?**
   Yes. Publish with visibility set to private, and only you can find or retrieve it until you explicitly promote it into the shared catalog.

5. **What happens if two people publish or promote the same skill name at the same moment?**
   JOSH detects the conflict and returns a clear message asking you to retry, rather than silently corrupting data or crashing.

6. **Is there a size limit on what I can publish?**
   Yes — skills are meant to be small, text-based instructions (a manifest plus a few supporting files), and uploads are capped at 5MB.

7. **Can I delete or unpublish a skill?**
   Not today. JOSH is deliberately append-only — nothing is ever overwritten or removed — so double-check a skill before publishing it, private or shared.

### Stakeholder FAQs

1. **What is JOSH built on, and does it require new infrastructure?**
   JOSH is a self-contained service: an embedded SQLite database plus local file storage, with no external dependencies. It runs on a single machine today and is sized for roughly 200 developers.

2. **Is JOSH secure? Who can see what?**
   Today, JOSH has no authentication — a user identifies themselves with a self-reported name that is not verified. This is acceptable for the trusted, closed developer group JOSH launches to, but it's a known limitation: the "private" collections feature is not yet backed by real access control. Closing this gap is our top priority before any wider rollout.

3. **What happens if JOSH goes down?**
   JOSH takes a full, consistent backup on a weekly schedule. Automatic failover to a second instance (high availability) is not yet built — see the roadmap below.

4. **Can content ever be deleted from JOSH?**
   No — JOSH is deliberately append-only: nothing is ever overwritten or deleted, including prior versions and private skills. This guarantees nothing a developer depends on ever disappears, but it also means there's currently no way to remove something published by mistake.

5. **What's next on the roadmap?**
   Real authentication and access control (making the "private" promise enforceable), rate limiting, structured logging and health monitoring, and tag-based search so different teams — for example finance versus engineering — can browse the skills relevant to them.

6. **How is this different from an AI assistant's own built-in skills or prompts feature?**
   JOSH is a separate, team-shared catalog — distinct from any AI assistant's own local, single-user skills or prompts feature. JOSH exists specifically so skills can be discovered and reused across the whole team, not just by the person who wrote them.

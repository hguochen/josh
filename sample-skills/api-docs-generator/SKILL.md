---
name: api-docs-generator
description: Generate API reference documentation from function signatures and comments
---
# API Docs Generator

Turn a function or endpoint signature plus its surrounding comments into a
reference documentation entry.

1. Extract the name, parameters, and return type.
2. Pull the description from existing comments, or ask for one if missing.
3. Note any error cases mentioned in the code.
4. Fill in `templates/api-doc-entry.md` and return the result.

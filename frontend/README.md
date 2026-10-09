# React + TypeScript + Vite

## Caregiver workspace

The caregiver routes provide schedule/work-pack reads, absence and spot-check responses,
own incident reports, check-in and one-time task results. See
[`../docs/caregiver/cg04-cg03-handoff.md`](../docs/caregiver/cg04-cg03-handoff.md)
for integration rules, API boundaries and demonstration guidance.

Check-in uses server-provided eligibility and GPS or an explicitly labelled manual
location note. Only DONE tasks count as completed tasks; handling all tasks does not
complete the Visit. An incident pauses open work. Evidence upload, vital signs,
check-out and SYS03 missed-check-in scheduling are not included yet.

Commands are not automatically retried. After an unknown incident result, open own
reports in a new tab so the original page retains its immutable command key and draft.
For validation run `npm run lint`, `npm run test:coverage` and `npm run build`.

This template provides a minimal setup to get React working in Vite with HMR and some Oxlint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the Oxlint configuration

If you are developing a production application, we recommend enabling type-aware lint rules by installing `oxlint-tsgolint` and editing `.oxlintrc.json`:

```json
{
  "$schema": "./node_modules/oxlint/configuration_schema.json",
  "plugins": ["react", "typescript", "oxc"],
  "options": {
    "typeAware": true
  },
  "rules": {
    "react/rules-of-hooks": "error",
    "react/only-export-components": ["warn", { "allowConstantExport": true }]
  }
}
```

See the [Oxlint rules documentation](https://oxc.rs/docs/guide/usage/linter/rules) for the full list of rules and categories.

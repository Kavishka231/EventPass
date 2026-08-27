# EventPass Frontend

EventPass is a React and TypeScript web application for the existing EventPass API. The frontend currently contains the production application foundation and reusable visual design system; customer, organizer, and administrator product flows will be implemented in later feature commits.

The frontend consumes the backend under `/api/v1`. Backend authorization, availability, inventory, booking and payment state, and server-calculated pricing remain authoritative.

## Current implementation

- React 19, TypeScript, Vite, and React Router application foundation
- Strict TypeScript project references and production source maps
- ESLint and Prettier configuration
- Environment-driven API base URL and local backend proxy
- Locally bundled Manrope variable font
- Light-first design tokens with compatible dark-surface overrides
- Responsive typography, spacing, radius, elevation, focus, and motion foundations
- Reusable buttons, form controls, surfaces, status feedback, dialog, toast, and layout primitives
- A temporary component-preview route at `/`

The preview is not the EventPass homepage. It exists only to verify the shared visual foundation until product pages replace it.

See [DESIGN.md](DESIGN.md) for the implemented visual language and component guidance.

## Local development

Copy `.env.example` to `.env.local` when local values need to differ from the safe defaults, then run:

```shell
npm install
npm run dev
```

`VITE_API_BASE_URL` defaults to `/api/v1`. During development, Vite proxies `/api` requests to `DEV_PROXY_TARGET`, which defaults to `http://localhost:8080`. No credentials belong in frontend environment files; every `VITE_` value is public browser configuration.

## Verification

```shell
npm run format:check
npm run lint
npm run typecheck
npm run build
```

Automated frontend testing is planned but is not configured yet. There is currently no `npm test` script.

## Source structure

```text
frontend/
|-- src/
|   |-- app/          Application composition
|   |-- components/   Reusable layout and UI primitives
|   |-- lib/          Framework-independent helpers and environment access
|   |-- pages/        Route-level screens
|   |-- routes/       Router configuration
|   `-- styles/       Tokens, base rules, components, and responsive layout
|-- .env.example
|-- eslint.config.js
|-- package.json
|-- tsconfig*.json
`-- vite.config.ts
```

Future feature folders should be added only when their product flows are implemented. Do not create fake API services or duplicate backend business rules in the browser.

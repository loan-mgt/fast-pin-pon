# Frontend (React 19 + Vite)

Opinionated starting point for the frontend (`front/`) that pairs React 19, the React Compiler-friendly toolchain, Tailwind CSS v4 (via the Vite plugin), and a lightweight shadcn-style component layer.

## Getting started

```bash
cd front
npm install
npm run dev
```

The dev server listens on `http://localhost:5173/` and benefits from Vite HMR plus React Compiler-friendly hydration.

## Production build

```bash
npm run build
npm run preview
```

`npm run build` creates the static bundle in `dist/`, and `npm run preview` lets you locally verify the production output.

## Docker

A production-ready Dockerfile already exists under `front/Dockerfile`.

```bash
docker build -t fast-pin-pon-frontend ./front
docker run --rm -p 8080:80 fast-pin-pon-frontend
```

The multi-stage build compiles the app with Node 20, then serves the static files through nginx on port 80.

## Highlights

- React 19 + Vite 7 with `@vitejs/plugin-react`
- Tailwind CSS v4 running entirely as a Vite plugin, no additional CLI wiring
- Minimal `components/ui` primitives inspired by https://ui.shadcn.com/
- Reusable `cn` helper for composable className logic
- Dockerfile that mirrors the same build output used in CI/deployments

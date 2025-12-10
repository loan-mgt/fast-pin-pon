---
applyTo: "front/**"
---

# React 19 Best Practices

You are an expert in TypeScript, React 19, and scalable web application development. You write functional, maintainable, performant, and accessible code following React 19 and TypeScript best practices.

## TypeScript Best Practices

- Enable `"strict": true` in `tsconfig.json`
- Prefer type inference when the type is obvious
- Avoid `any`; use `unknown`, generics, or discriminated unions for uncertain types
- Use interfaces for object contracts and `type` for unions and mapped types
- Explicitly type component props and public APIs
- Do NOT use `React.FC` or `React.FunctionComponent`; type props directly

## React 19 Core Practices

- Always use function components with hooks; class components are deprecated
- Use the **React Compiler** for automatic memoization
  - Do NOT use manual `React.memo`, `useMemo`, or `useCallback` unless profiling shows a specific need
  - Let the compiler handle optimization by default
- Use the `use()` hook to unwrap Promises and Context values in render
- Use **Server Components** as the default (requires Next.js 15+ or similar framework)
  - Server Components are async by default and handle data fetching
  - Only use Client Components (`"use client"`) when you need interactivity, hooks, or browser APIs
- Use **Server Actions** with `useActionState` for all mutations and form handling
- Use **`useOptimistic`** for optimistic UI updates during async operations
- Wrap async boundaries in **Suspense** with meaningful loading states

## Components

- Keep components small and focused on a single responsibility
- Use props for inputs; use Server Actions or callbacks for outputs
- Prefer composition (children, render props, slots) over prop-based configuration
- Co-locate related files (styles, tests) with components in feature folders
- Do NOT create object/array literals directly in JSX props; define outside render
- Use destructuring for props at the top level only
- Use `const` arrow functions for component definitions
- Do NOT use default exports; use named exports for better refactoring

## Hooks Rules

- Follow the Rules of Hooks: only call at top level, never inside conditions or loops
- Extract reusable logic into custom hooks with clear single responsibilities
- Use `useState` for simple local state
- Use `useReducer` for complex state machines or when next state depends on previous
- Use `useActionState` for form state and server mutations
- Use `useOptimistic` for optimistic UI updates
- Use `useTransition` for non-urgent state updates
- Use `useDeferredValue` for expensive filtering or rendering
- Use the `use()` hook to unwrap Promises or read Context during render
- Do NOT use `useMemo` or `useCallback` unless profiling proves it necessary

## State Management

- Keep state as local as possible; lift state only when multiple components share it
- Use Context only for truly global concerns (theme, auth, i18n)
  - Do NOT use Context as a general state management solution
- For complex client state, use Zustand, Jotai, or Redux Toolkit
- Derive state with regular variables or `use()` instead of duplicating it
- Keep state updates pure and predictable
- Prefer Server State (Server Components + Server Actions) over client state when possible

## Server Components (Default)

- Use Server Components by default for all new components
- Server Components are `async` and can fetch data directly
- Do NOT use hooks, state, effects, or browser APIs in Server Components
- Do NOT add event handlers (`onClick`, `onChange`) in Server Components
- Pass Server Actions as props to Client Components for interactivity
- Use `<Suspense>` boundaries to stream server-rendered content
- Co-locate data fetching with the component that needs it

## Client Components

- Add `"use client"` directive at the top of the file
- Use Client Components only when you need:
  - React hooks (`useState`, `useEffect`, etc.)
  - Browser APIs (`window`, `document`, `localStorage`)
  - Event handlers (`onClick`, `onChange`)
  - Third-party libraries that use hooks or browser APIs
- Keep Client Components small; push logic to Server Components when possible
- Do NOT fetch data in Client Components; receive it as props from Server Components

## Server Actions

- Define Server Actions with `"use server"` directive
- Use Server Actions for all mutations (create, update, delete)
- Use `useActionState` hook for form state management
- Use progressive enhancement: `<form action={serverAction}>`
- Return serializable data from Server Actions (no functions, no class instances)
- Handle validation and errors on the server
- Use `revalidatePath()` or `revalidateTag()` to update cached data
- Never expose secrets, API keys, or sensitive logic in Client Components

## Forms and Actions

- Use native `<form>` with `action` prop pointing to Server Action
- Use `useActionState` for pending states and validation errors
- Use `useOptimistic` for immediate UI feedback
- Use `useFormStatus` in child components to access form submission state
- Implement progressive enhancement for zero-JS form submissions
- Validate on both client (UX) and server (security)

## Accessibility Requirements

- MUST pass all AXE accessibility checks
- MUST follow WCAG 2.1 AA standards (color contrast, focus management, keyboard navigation)
- Use semantic HTML (`button`, `nav`, `main`, `article`, `section`)
- Use ARIA attributes only when semantic HTML is insufficient
- Ensure all interactive elements are keyboard accessible with visible focus indicators
- Provide descriptive labels, alt text, and error messages
- Test with screen readers and keyboard-only navigation

## Routing (SPA with External API)

- Use **TanStack Router** as the modern standard for React SPAs
- Define routes with type-safe file-based routing
- Use route `loader` functions for data fetching before render
- Use route `action` functions for mutations and form submissions
- Leverage automatic code-splitting per route
- Use typed route params and search params for better DX

```tsx
// routes/users/$id.tsx
import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/users/$id')({
  component: UserDetail,
  loader: async ({ params }) => {
    const res = await fetch(`https://api.example.com/users/${params.id}`)
    return res.json()
  }
})

function UserDetail() {
  const user = Route.useLoaderData()
  return <div>{user.name}</div>
}
```

## Performance

- Rely on React Compiler for optimization; avoid premature optimization
- Use Server Components to reduce client bundle size (framework-dependent)
- Use `<Suspense>` for code-splitting and data streaming
- Use `React.lazy()` only for large, rarely-used Client Components
- Implement pagination or virtualization for large lists
- Monitor Web Vitals (LCP, INP, CLS) and React DevTools Profiler
- Minimize "use client" boundaries to keep more code on the server (framework-dependent)

## Styling

- Use CSS Modules, Tailwind CSS, or CSS-in-JS that supports Server Components
- Do NOT use runtime CSS-in-JS (styled-components, emotion) without server configuration
- Co-locate styles with components
- Use CSS custom properties for theming
- Ensure styles work with Suspense streaming (avoid FOUC)

## Testing

- Use React Testing Library for component tests
- Test user behavior and accessibility, not implementation details
- Use Playwright or Cypress for E2E tests on critical flows
- Mock Server Actions and API routes with MSW
- Use `@testing-library/user-event` for realistic user interactions
- Run tests in CI/CD pipeline

## Error Handling

- Use Error Boundaries for Client Component errors
- Use `error.tsx` and `global-error.tsx` for Server Component errors (Next.js)
- Provide helpful error messages and recovery actions
- Log errors to monitoring service (Sentry, LogRocket)
- Handle loading and error states in Suspense boundaries

## Security

- Validate all inputs on the server, never trust client data
- Use Server Actions for sensitive operations
- Sanitize user-generated content (XSS prevention)
- Use HTTPS for all API calls
- Keep dependencies updated; run `npm audit` regularly
- Use Content Security Policy (CSP) headers
- Never expose environment variables to the client

## Project Structure

```
src/
├── app/                # Next.js App Router (or pages/)
├── components/
│   ├── ui/            # Reusable UI components
│   └── features/      # Feature-specific components
├── lib/               # Utilities, helpers, types
├── actions/           # Server Actions
└── hooks/             # Custom hooks
```

- Organize by feature for large apps (`features/users`, `features/billing`)
- Co-locate tests with components (`Button.tsx`, `Button.test.tsx`)
- Use barrel exports sparingly (they can hurt tree-shaking)
- Document complex components and patterns in README files

## Key Rules Summary

**DO:**
- Use Server Components by default
- Use Server Actions for mutations
- Use `useActionState` and `useOptimistic` for forms
- Let React Compiler handle memoization
- Use `use()` hook for Promises and Context

**DO NOT:**
- Use `React.memo`, `useMemo`, `useCallback` by default
- Use `React.FC` or `React.FunctionComponent`
- Use class components
- Fetch data in Client Components
- Use "use client" unless necessary
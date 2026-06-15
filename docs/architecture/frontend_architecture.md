# Frontend Architecture Decisions

This document outlines the architectural patterns and technology choices for the Project Meridian React frontend.

## 1. Technology Stack

*   **Framework:** React (Vite)
*   **Language:** TypeScript
*   **Styling:** Tailwind CSS + Radix UI (Primitives) / shadcn/ui
*   **Data Fetching:** TanStack Query (React Query)
*   **Routing:** React Router DOM (v6+)

## 2. State Management

*   **Server State:** Handled entirely by **TanStack Query**. We do not sync server data into global stores (like Redux or Zustand). React Query handles caching, deduplication, and background refetching.
*   **Client/UI State:**
    *   *Local:* `useState` / `useReducer` for component-level state (e.g., dropdowns, form inputs).
    *   *Global:* **Zustand** for lightweight, cross-component UI state (e.g., active theme, sidebar toggle) if context becomes too cumbersome.
*   **Form State:** React Hook Form, paired with Zod for schema validation.

## 3. Authentication & Token Storage

*   **Strategy:** JWT (Access + Refresh Tokens).
*   **Storage:**
    *   **Access Token:** Stored in memory and managed by the authentication layer. It is never persisted to localStorage or sessionStorage.
    *   **Refresh Token:** Stored in an `HttpOnly`, `Secure`, `SameSite=Strict` cookie managed by the backend.
*   **Flow:**
    1.  User logs in. Backend returns Access Token in JSON payload and sets Refresh Token cookie.
    2.  Frontend stores Access Token in memory and attaches it to `Authorization: Bearer <token>` for API requests via an Axios interceptor.
    3.  Expired access tokens are refreshed transparently through the refresh endpoint. The frontend retries the original request after obtaining a new token.

## 4. API Error Handling

*   **Centralized Interceptor:** An Axios interceptor catches all error responses.
*   **Standardized Format:** The backend strictly follows RFC 7807 (Problem Details).
*   **User Feedback:**
    *   `400/422`: Mapped to form field errors via React Hook Form if applicable.
    *   `401/403`: Triggers forced logout or "Access Denied" view.
    *   `500+`: Displays a generic "System Error" toast notification (using e.g., Sonner).

## 5. Loading & Error States

*   **Loading:** Use Skeleton loaders for data fetching (paired with `isPending` from React Query) rather than generic spinners, to avoid layout shift.
*   **Errors:** Use Error Boundaries (via `react-error-boundary`) at the route level to catch rendering errors and display a graceful fallback UI without crashing the whole app.

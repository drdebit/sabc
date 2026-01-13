# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SABC ("Consolidated Paesan") is a full-stack Clojure/ClojureScript choose-your-own-adventure game with user authentication and persistent game state.

## Build & Development Commands

### Frontend (ClojureScript)
```bash
npm run dev      # Start shadow-cljs watch mode (serves at http://localhost:8080)
npm run release  # Production build
npm run clean    # Remove compiled output and cache
```

### Backend (Clojure)
```bash
clojure -M:run   # Start Pedestal server on port 5000
clojure -M:repl  # Start REPL with nREPL/CIDER support
clojure -M:dev   # Development mode with extra source paths
```

### Full Development Setup
Run both concurrently:
1. `npm run dev` - Frontend with hot reload
2. `clojure -M:run` - Backend API server

## Architecture

### Tech Stack
- **Backend:** Pedestal (HTTP), Datomic (in-memory database), tools.logging
- **Frontend:** Reagent (React wrapper), cljs-http (HTTP client)
- **Build:** shadow-cljs (CLJS compilation), tools.deps (CLJ dependencies)

### Code Organization
```
src/clj/sabc/          # Backend
├── config.clj         # Centralized configuration (db-uri, port, CORS origins)
├── db.clj             # Database connection management (single source of truth)
├── schema.clj         # Datomic schema definitions
├── story.clj          # Story data, Datomic queries, game logic
├── rest.clj           # Pedestal routes, handlers, auth interceptor
└── core.clj           # Server lifecycle, initialization

src/cljs/sabc/client/  # Frontend
├── config.cljs        # API base URL configuration
├── communicate.cljs   # Centralized app-state atom, API client functions
└── app.cljs           # UI components
```

### Key Patterns

**Backend Authentication:**
- PIN validation via `auth-interceptor` on protected routes
- PIN sent in `x-pin` header from frontend
- Routes `/update-loc`, `/new-game`, `/resume-game` require auth

**Frontend State:**
- Single `app-state` atom in `communicate.cljs` holds all state
- Keys: `:user`, `:pin`, `:pin-verified`, `:location`, `:add-user-response`, `:error`, `:loading`
- Access via `(comm/get-state :key)` and `(comm/set-state! :key value)`

**Error Handling:**
- Backend: try-catch around Datomic transactions, proper HTTP status codes
- Frontend: errors stored in `:error` state, displayed via `error-banner` component

### Data Flow
1. User enters email → Backend creates user + game in Datomic with 4-digit PIN
2. Frontend stores PIN and sends it in `x-pin` header on subsequent requests
3. User navigates story via choices → Backend validates PIN, updates game state
4. Frontend re-renders with centralized Reagent state

## Configuration

Environment variables (with defaults):
- `PORT` - Server port (default: 5000)
- `SABC_DB_URI` - Datomic URI (default: "datomic:mem://sabc-db")
- `SABC_ALLOWED_ORIGINS` - Comma-separated CORS origins (default: "http://localhost:8080,http://localhost:5000")

## Important Notes

- Database is in-memory - data resets on server restart
- Input validation on email format and location tags
- Logging via `clojure.tools.logging` (uses SLF4J simple backend)

# ChemVantage React SPA Modernization

This branch converts the Homework assignment to a single-page React application.

## Architecture

### Backend (Java)

- **SpaJwt.java**: Issues and validates JWT tokens with rotating nonce for API security
- **SpaRequest.java**: Utility for detecting JSON requests, parsing JSON bodies, and extracting Bearer tokens
- **Homework.java** (updated): Dual-mode servlet that returns HTML (legacy) or JSON (SPA mode)
  - Detects `Accept: application/json` or `Content-Type: application/json`
  - Returns `{ ok, html, jwt, assignmentType, error }` for SPA clients
  - JWT rotated on every API response to refresh nonce

### Frontend (React)

- **Location**: `frontend/react_app/`
- **Build output**: `src/main/webapp/react/app/` (served statically by App Engine)
- **Entry**: `/spa_homework.html` redirects to `/react/app/index.html?sig=...`
- **State**: JWT stored in-memory (not localStorage), sent as `Authorization: Bearer <jwt>` on each API call
- **App container**: Minimal React component with:
  - JWT state (rotated on each response)
  - Assignment type state
  - `apiCall(endpoint, params)` function for backend communication
  - Render container for HTML from backend

## Development Workflow

### Backend

```bash
# Compile and run locally (Spring Boot embedded Tomcat)
mvn clean package exec:java

# Deploy to Google Cloud
mvn clean package appengine:deploy
```

### Frontend

```bash
cd frontend/react_app

# Install dependencies
npm install

# Dev server (proxies /Homework to localhost:8080)
npm start

# Production build (outputs to src/main/webapp/react/app)
npm run build
```

## Security

- JWT tokens stored in memory (not localStorage) to prevent XSS sniffing
- JWT sent as `Authorization: Bearer <token>` header
- Nonce rotated on every API response
- Tokens expire after 90 minutes
- Backend validates JWT signature and issuer on every request

## Migration Path

This pattern can be replicated for the other 7 assignment types:
1. Add JSON mode to the servlet (`SpaRequest.isJsonRequest`)
2. Return `{ ok, html, jwt, assignmentType }` for JSON requests
3. Create React app in `frontend/<assignment_type>/`
4. Configure `BUILD_PATH` in `package.json` to output to `src/main/webapp/react/<assignment_type>/`
5. Add static handler to `app.yaml`
6. Create entry redirect HTML

## Files Changed

- `src/main/java/org/chemvantage/SpaJwt.java` (new)
- `src/main/java/org/chemvantage/SpaRequest.java` (new)
- `src/main/java/org/chemvantage/Homework.java` (updated doGet/doPost for JSON mode)
- `frontend/react_app/src/App.js` (new SPA)
- `frontend/react_app/package.json` (custom BUILD_PATH)
- `src/main/webapp/spa_homework.html` (new router)
- `src/main/appengine/app.yaml` (static /react/app handler)
- `.gitignore` (ignore node_modules, build artifacts)

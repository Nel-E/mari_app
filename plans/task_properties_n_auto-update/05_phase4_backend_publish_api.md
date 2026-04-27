# Phase 4 — Backend Publish API (RPi Docker)

**Depends on:** nothing (can be built in parallel with Phases 0–3, but must land before Phase 5).
**Unblocks:** Phases 5 and 6 (phone + watch fetch from this API).

## Goal

A small, portable FastAPI service that publishes phone and watch APK updates from a single channel (`prod`), with two slots (`stable` and `beta`). Runs on the RPi inside a Docker container via `docker-compose`. No database — the source of truth is JSON files on disk, served behind the API.

## Rationale

User decision: one channel, two branches (`beta`, `release`), one backend, phone toggle "Receive debug builds". The API follows the contract proven by `bfi_dev/bfi_prod`.

## Host & Paths

- RPi, Docker container.
- Bind mount: `/home/mari/mari_updates/` → `/data/app_updates/` (inside container).
- Container-listen port: `8080`. Host port: `8080`.
- Reverse proxy (optional) via whatever is already on the RPi; not required for LAN-only use.

## API Contract

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/app-update/latest?track=<stable\|beta>&component=<phone\|watch>` | Returns `latest.json` for the requested slot. |
| `GET` | `/api/app-update/releases?track=&component=&after_version_code=` | Returns release notes newer than `after_version_code`. |
| `GET` | `/api/app-update/artifacts/{track}/{component}/{file_name}` | Streams the APK bytes. |
| `GET` | `/healthz` | Returns `200 OK` with `{"status":"ok"}`. |

### Auth

Minimal shared-secret. All endpoints require header `X-Mari-Token: <token>`. Token is read from `MARI_API_TOKEN` env var. Empty env var = auth disabled (local dev only — log a warning on startup).

### `latest.json` shape (phone)

```json
{
  "component": "phone",
  "track": "debug",
  "package_name": "com.mari.app",
  "version_code": 101400,
  "version_name": "1.0.1.4-beta",
  "file_name": "mari-phone-1.0.1.4-beta.apk",
  "file_size_bytes": 32564411,
  "sha256": "bd6508580b5d5bd38bf2f3f44564a8579739782e8b253626a211bc67a410cc5b",
  "released_at": "2026-04-22T14:00:00Z",
  "notification_title": "Mari 1.0.1.4-debug available",
  "notification_text": "Deadline reminders, daily nudge.",
  "changelog": "- Deadline reminders\n- Daily nudge\n- Unique task names",
  "min_installed_version_code": 100000
}
```

### Release-note shape

Identical to `bfi_dev` structure — features / upgrades / fixes lists, sequentially numbered `releases/NNNN__<version>.json`.

## Filesystem Layout

```
/data/app_updates/
├── phone/
│   ├── release/
│   │   ├── latest.json
│   │   ├── mari-phone-<ver>.apk
│   │   └── releases/
│   │       └── 0001__1.0.1.0.json
│   └── debug/
│       ├── latest.json
│       ├── mari-phone-<ver>-beta.apk
│       └── releases/
└── watch/
    ├── release/
    └── debug/
```

## Service Implementation

### Tech stack
- Python 3.12, FastAPI, Uvicorn, `pydantic-settings` for env vars.
- No database. Pure filesystem-backed.
- `pytest` + `httpx` for tests.

### Files (new)

```
backend_api/
├── Dockerfile
├── docker-compose.yml
├── pyproject.toml               # or requirements.txt
├── README.md
├── mari_api/
│   ├── __init__.py
│   ├── main.py                  # FastAPI app factory
│   ├── settings.py              # env vars, paths, token
│   ├── routes/
│   │   ├── __init__.py
│   │   ├── latest.py
│   │   ├── releases.py
│   │   ├── artifacts.py
│   │   └── health.py
│   ├── auth.py                  # dependency that checks X-Mari-Token
│   └── models.py                # pydantic models for latest/release
└── tests/
    ├── conftest.py              # fixtures for temp data dir
    ├── test_auth.py
    ├── test_latest.py
    ├── test_releases.py
    ├── test_artifacts.py
    └── test_health.py
```

### Dockerfile sketch

```dockerfile
FROM python:3.12-slim
WORKDIR /app
COPY pyproject.toml README.md ./
COPY mari_api ./mari_api
RUN pip install --no-cache-dir .
ENV PYTHONUNBUFFERED=1 \
    MARI_DATA_DIR=/data/app_updates \
    MARI_API_TOKEN=""
EXPOSE 8080
CMD ["uvicorn", "mari_api.main:app", "--host", "0.0.0.0", "--port", "8080"]
```

### docker-compose sketch

```yaml
services:
  mari-api:
    build: .
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      MARI_API_TOKEN: ${MARI_API_TOKEN:-}
    volumes:
      - /home/mari/mari_updates:/data/app_updates:ro
    healthcheck:
      test: ["CMD", "curl", "-fs", "http://localhost:8080/healthz"]
      interval: 30s
      timeout: 5s
      retries: 3
```

> **Note:** mount is `:ro` so the API cannot overwrite published artifacts. The **publish scripts** (see `08_release_workflow.md`) write to the host path directly — they don't go through the API.

### Request validation

- `track ∈ `{release, debug}` else `400`.` else `400`.
- `component` ∈ `{phone, watch}` else `400`.
- `file_name` — regex `^[A-Za-z0-9._-]+$`, plus confine resolved path inside the expected `<component>/<track>/` dir (path traversal guard). Deny `..`.
- `after_version_code` — non-negative int.
- Missing or mismatched token → `401`.

### Streaming artifacts

Use `FileResponse` with `media_type="application/vnd.android.package-archive"`, `Content-Length` from `os.stat`. Set `Cache-Control: public, max-age=3600` (safe because SHA256 is in `latest.json`).

## Tests (RED before GREEN)

1. `test_health` — `/healthz` returns 200 with `{"status":"ok"}`.
2. `test_auth`:
   - Missing token header → 401.
   - Wrong token → 401.
   - Correct token → 200.
   - Empty `MARI_API_TOKEN` env → auth disabled, 200 without header (and log warning verified).
3. `test_latest`:
   - `track=stable&component=phone` returns the fixture JSON verbatim.
   - Missing file → 404.
   - Unknown track → 400.
4. `test_releases`:
   - `after_version_code=0` → all releases sorted ascending.
   - `after_version_code=999999` → empty list.
5. `test_artifacts`:
   - Happy path streams bytes matching SHA256 in `latest.json`.
   - Path traversal (`..`) → 400.
   - Missing file → 404.
   - Filename regex violation → 400.

## Validation Gate

- [ ] `pytest` in `backend_api/` all green.
- [ ] Build image: `docker compose build mari-api` succeeds.
- [ ] Run locally: `docker compose up mari-api`, `curl -H "X-Mari-Token: $TOK" http://localhost:8080/api/app-update/latest?track=stable&component=phone` returns the fixture.
- [ ] RPi deploy: repo synced, `cd /home/mari/mari_app/backend_api && docker compose up -d mari-api`, container healthcheck reports healthy, `/healthz` reachable from another LAN device.
- [ ] File a sample `latest.json` manually, publish a dummy APK (any 1 MB blob), phone `curl` fetches and the SHA256 matches.

## Security & Ops Notes

- Token is not rotated automatically — document rotation process in `backend_api/README.md`.
- LAN-only by default. If exposing externally, add HTTPS (Caddy or Traefik) + IP allowlist.
- Log format: structured JSON, no token or payload bodies. File size and SHA256 are safe to log.

## Exit Criteria

API running on RPi, healthcheck green, end-to-end fetch verified from another device.
Commit sequence (in `backend_api/`):
1. `feat(api): scaffold FastAPI app with healthcheck and shared-token auth`
2. `feat(api): latest + releases + artifacts endpoints`
3. `chore(docker): Dockerfile and compose for mari-api`
4. `docs(api): deployment and token rotation notes`

---

## Implementation Progress

- [ ] `not implemented` `backend_api/` directory scaffolded with `pyproject.toml` / `requirements.txt`
- [ ] `not implemented` `mari_api/main.py` FastAPI app factory
- [ ] `not implemented` `mari_api/settings.py` env vars (`MARI_DATA_DIR`, `MARI_API_TOKEN`)
- [ ] `not implemented` `mari_api/auth.py` shared-token dependency (warn-only when token empty)
- [ ] `not implemented` `mari_api/models.py` Pydantic models for `latest.json` and release notes
- [ ] `not implemented` `routes/health.py` — `/healthz` endpoint
- [ ] `not implemented` `routes/latest.py` — `/api/app-update/latest` endpoint
- [ ] `not implemented` `routes/releases.py` — `/api/app-update/releases` endpoint
- [ ] `not implemented` `routes/artifacts.py` — `/api/app-update/artifacts/{track}/{component}/{file_name}` streaming endpoint with path-traversal guard
- [ ] `not implemented` `Dockerfile` and `docker-compose.yml` with `:ro` bind mount
- [ ] `not implemented` `tests/test_health.py`, `test_auth.py`, `test_latest.py`, `test_releases.py`, `test_artifacts.py` — all green
- [ ] `not implemented` Docker image builds on RPi (`docker compose build mari-api`)
- [ ] `not implemented` Container deployed on RPi, healthcheck green, reachable from LAN device

## Functional Requirements / Key Principles

- All endpoints require `X-Mari-Token` header; missing or wrong token returns `401`. When `MARI_API_TOKEN` env var is empty the server logs a warning and disables auth (local dev only).
- `track` must be `stable` or `beta`; `component` must be `phone` or `watch`; any other value returns `400`.
- Artifact `file_name` is validated against `^[A-Za-z0-9._-]+$` and the resolved path is confined inside `<data_dir>/<component>/<track>/`; path traversal (`..`) returns `400`.
- The API mounts the updates directory read-only (`:ro`); it never writes to the filesystem — publish scripts write directly to the host path.
- `GET /api/app-update/artifacts/...` streams the file with `Content-Length` from `os.stat` and `Cache-Control: public, max-age=3600`; SHA-256 integrity is the client's responsibility (verified against `latest.json`).
- `/healthz` returns `200 {"status":"ok"}` with no auth required; used by Docker healthcheck.
- Structured JSON logs never include the token value, request bodies, or APK content.

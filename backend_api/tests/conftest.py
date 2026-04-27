import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from mari_api.main import create_app
from mari_api.settings import Settings, get_settings

TOKEN = "test-secret-token"


def make_settings(tmp_path: Path, token: str = TOKEN) -> Settings:
    return Settings(data_dir=tmp_path, api_token=token)


def make_client(settings: Settings) -> TestClient:
    app = create_app()
    app.dependency_overrides[get_settings] = lambda: settings
    return TestClient(app, raise_server_exceptions=True)


@pytest.fixture()
def data_dir(tmp_path: Path) -> Path:
    return tmp_path


@pytest.fixture()
def settings(data_dir: Path) -> Settings:
    return make_settings(data_dir)


@pytest.fixture()
def client(settings: Settings) -> TestClient:
    return make_client(settings)


@pytest.fixture()
def auth_headers() -> dict[str, str]:
    return {"X-Mari-Token": TOKEN}


def write_latest(base: Path, component: str, track: str, payload: dict) -> None:
    d = base / component / track
    d.mkdir(parents=True, exist_ok=True)
    (d / "latest.json").write_text(json.dumps(payload))


def write_release(base: Path, component: str, track: str, seq: str, payload: dict) -> None:
    d = base / component / track / "releases"
    d.mkdir(parents=True, exist_ok=True)
    (d / f"{seq}.json").write_text(json.dumps(payload))


SAMPLE_LATEST = {
    "component": "phone",
    "track": "release",
    "package_name": "com.mari.app",
    "version_code": 100000,
    "version_name": "1.0.0.0",
    "file_name": "mari-phone-1.0.0.0.apk",
    "file_size_bytes": 1024,
    "sha256": "abc123",
    "released_at": "2026-04-22T14:00:00Z",
    "notification_title": "Mari 1.0.0.0 available",
    "notification_text": "Initial release.",
    "changelog": "- Initial release",
    "min_installed_version_code": 0,
}

SAMPLE_RELEASE = {
    "version_code": 100000,
    "version_name": "1.0.0.0",
    "released_at": "2026-04-22T14:00:00Z",
    "features": ["Initial release"],
    "upgrades": [],
    "fixes": [],
}

from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from tests.conftest import TOKEN, make_client, make_settings, write_latest, SAMPLE_LATEST


def _client_with_token(data_dir: Path, token: str) -> TestClient:
    settings = make_settings(data_dir, token=token)
    write_latest(data_dir, "phone", "stable", SAMPLE_LATEST)
    return make_client(settings)


def test_missing_token_returns_401(data_dir):
    client = _client_with_token(data_dir, TOKEN)
    r = client.get("/api/app-update/latest?track=stable&component=phone")
    assert r.status_code == 401


def test_wrong_token_returns_401(data_dir):
    client = _client_with_token(data_dir, TOKEN)
    r = client.get(
        "/api/app-update/latest?track=stable&component=phone",
        headers={"X-Mari-Token": "wrong"},
    )
    assert r.status_code == 401


def test_correct_token_returns_200(data_dir):
    client = _client_with_token(data_dir, TOKEN)
    r = client.get(
        "/api/app-update/latest?track=stable&component=phone",
        headers={"X-Mari-Token": TOKEN},
    )
    assert r.status_code == 200


def test_empty_token_env_disables_auth(data_dir, caplog):
    import logging
    settings = make_settings(data_dir, token="")
    write_latest(data_dir, "phone", "stable", SAMPLE_LATEST)
    client = make_client(settings)
    with caplog.at_level(logging.WARNING):
        r = client.get("/api/app-update/latest?track=stable&component=phone")
    assert r.status_code == 200

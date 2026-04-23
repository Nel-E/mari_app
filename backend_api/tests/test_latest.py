from tests.conftest import SAMPLE_LATEST, write_latest


def test_latest_returns_fixture_json(client, auth_headers, data_dir):
    write_latest(data_dir, "phone", "stable", SAMPLE_LATEST)
    r = client.get("/api/app-update/latest?track=stable&component=phone", headers=auth_headers)
    assert r.status_code == 200
    body = r.json()
    assert body["version_code"] == SAMPLE_LATEST["version_code"]
    assert body["component"] == "phone"
    assert body["track"] == "stable"


def test_missing_latest_returns_404(client, auth_headers):
    r = client.get("/api/app-update/latest?track=stable&component=phone", headers=auth_headers)
    assert r.status_code == 404


def test_unknown_track_returns_422(client, auth_headers):
    r = client.get("/api/app-update/latest?track=nightly&component=phone", headers=auth_headers)
    assert r.status_code == 422


def test_unknown_component_returns_422(client, auth_headers):
    r = client.get("/api/app-update/latest?track=stable&component=tablet", headers=auth_headers)
    assert r.status_code == 422

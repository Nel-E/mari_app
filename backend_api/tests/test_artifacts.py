from tests.conftest import SAMPLE_LATEST, write_latest


def test_artifact_streams_file(client, auth_headers, data_dir):
    apk_bytes = b"PK\x03\x04fake-apk-content"
    d = data_dir / "phone" / "release"
    d.mkdir(parents=True, exist_ok=True)
    (d / "mari-phone-1.0.0.0.apk").write_bytes(apk_bytes)
    write_latest(data_dir, "phone", "release", SAMPLE_LATEST)

    r = client.get(
        "/api/app-update/artifacts/stable/phone/mari-phone-1.0.0.0.apk",
        headers=auth_headers,
    )
    assert r.status_code == 200
    assert r.content == apk_bytes
    assert "application/vnd.android.package-archive" in r.headers["content-type"]
    assert r.headers["cache-control"] == "public, max-age=3600"


def test_missing_artifact_returns_404(client, auth_headers, data_dir):
    r = client.get(
        "/api/app-update/artifacts/stable/phone/missing.apk",
        headers=auth_headers,
    )
    assert r.status_code == 404


def test_invalid_filename_returns_400(client, auth_headers, data_dir):
    r = client.get(
        "/api/app-update/artifacts/stable/phone/bad file!.apk",
        headers=auth_headers,
    )
    assert r.status_code == 400


def test_path_traversal_returns_400(client, auth_headers, data_dir):
    r = client.get(
        "/api/app-update/artifacts/stable/phone/..%2F..%2Fetc%2Fpasswd",
        headers=auth_headers,
    )
    assert r.status_code in (400, 404, 422)


def test_artifact_requires_auth(client, data_dir):
    apk_bytes = b"PK\x03\x04fake"
    d = data_dir / "phone" / "release"
    d.mkdir(parents=True, exist_ok=True)
    (d / "mari-phone-1.0.0.0.apk").write_bytes(apk_bytes)
    r = client.get("/api/app-update/artifacts/stable/phone/mari-phone-1.0.0.0.apk")
    assert r.status_code == 401

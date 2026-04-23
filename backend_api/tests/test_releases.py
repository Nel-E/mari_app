from tests.conftest import SAMPLE_RELEASE, write_release


def _rel(version_code: int) -> dict:
    return {**SAMPLE_RELEASE, "version_code": version_code, "version_name": f"1.0.{version_code}.0"}


def test_after_zero_returns_all_releases(client, auth_headers, data_dir):
    write_release(data_dir, "phone", "stable", "0001__1.0.0.0", _rel(100000))
    write_release(data_dir, "phone", "stable", "0002__1.0.1.0", _rel(100100))
    r = client.get(
        "/api/app-update/releases?track=stable&component=phone&after_version_code=0",
        headers=auth_headers,
    )
    assert r.status_code == 200
    codes = [n["version_code"] for n in r.json()]
    assert codes == [100000, 100100]


def test_after_version_filters_older(client, auth_headers, data_dir):
    write_release(data_dir, "phone", "stable", "0001__1.0.0.0", _rel(100000))
    write_release(data_dir, "phone", "stable", "0002__1.0.1.0", _rel(100100))
    r = client.get(
        "/api/app-update/releases?track=stable&component=phone&after_version_code=100000",
        headers=auth_headers,
    )
    assert r.status_code == 200
    codes = [n["version_code"] for n in r.json()]
    assert codes == [100100]


def test_after_large_version_returns_empty(client, auth_headers, data_dir):
    write_release(data_dir, "phone", "stable", "0001__1.0.0.0", _rel(100000))
    r = client.get(
        "/api/app-update/releases?track=stable&component=phone&after_version_code=999999",
        headers=auth_headers,
    )
    assert r.status_code == 200
    assert r.json() == []


def test_missing_releases_dir_returns_empty(client, auth_headers):
    r = client.get(
        "/api/app-update/releases?track=stable&component=phone",
        headers=auth_headers,
    )
    assert r.status_code == 200
    assert r.json() == []

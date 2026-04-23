def test_healthz_returns_200(client):
    r = client.get("/healthz")
    assert r.status_code == 200
    assert r.json() == {"status": "ok"}


def test_healthz_requires_no_token(client):
    r = client.get("/healthz")
    assert r.status_code == 200

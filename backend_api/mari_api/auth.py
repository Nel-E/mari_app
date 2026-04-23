from fastapi import Depends, Header, HTTPException
from fastapi.security import APIKeyHeader

from mari_api.settings import Settings, get_settings

_api_key_header = APIKeyHeader(name="X-Mari-Token", auto_error=False)


def require_token(
    token: str | None = Depends(_api_key_header),
    settings: Settings = Depends(get_settings),
) -> None:
    if not settings.auth_enabled():
        return
    if not token or token != settings.api_token:
        raise HTTPException(status_code=401, detail="Invalid or missing X-Mari-Token")

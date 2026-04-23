import re
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import FileResponse

from mari_api.auth import require_token
from mari_api.settings import Settings, get_settings

router = APIRouter()

Track = Literal["stable", "beta"]
Component = Literal["phone", "watch"]

_SAFE_FILENAME = re.compile(r"^[A-Za-z0-9._-]+$")


@router.get(
    "/api/app-update/artifacts/{track}/{component}/{file_name}",
    dependencies=[Depends(require_token)],
)
def get_artifact(
    track: Track,
    component: Component,
    file_name: str,
    settings: Settings = Depends(get_settings),
) -> FileResponse:
    if not _SAFE_FILENAME.match(file_name):
        raise HTTPException(status_code=400, detail="Invalid file name")

    base = (settings.data_dir / component / track).resolve()
    candidate = (base / file_name).resolve()

    if not str(candidate).startswith(str(base)):
        raise HTTPException(status_code=400, detail="Path traversal not allowed")

    if not candidate.is_file():
        raise HTTPException(status_code=404, detail="Artifact not found")

    return FileResponse(
        path=candidate,
        media_type="application/vnd.android.package-archive",
        headers={"Cache-Control": "public, max-age=3600"},
    )

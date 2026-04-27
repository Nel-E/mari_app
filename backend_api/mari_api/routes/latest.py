import json
from pathlib import Path
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException

from mari_api.auth import require_token
from mari_api.models import LatestRelease
from mari_api.settings import Settings, get_settings

router = APIRouter()

Track = Literal["release", "debug"]
Component = Literal["phone", "watch"]


@router.get("/api/app-update/latest", dependencies=[Depends(require_token)])
def get_latest(
    track: Track,
    component: Component,
    settings: Settings = Depends(get_settings),
) -> LatestRelease:
    path = settings.data_dir / component / track / "latest.json"
    if not path.is_file():
        raise HTTPException(status_code=404, detail="latest.json not found")
    return LatestRelease.model_validate(json.loads(path.read_text()))

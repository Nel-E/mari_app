import json
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException

from mari_api.auth import require_token
from mari_api.models import ReleaseNote
from mari_api.settings import Settings, get_settings

router = APIRouter()

Track = Literal["stable", "beta"]
Component = Literal["phone", "watch"]


@router.get("/api/app-update/releases", dependencies=[Depends(require_token)])
def get_releases(
    track: Track,
    component: Component,
    after_version_code: int = 0,
    settings: Settings = Depends(get_settings),
) -> list[ReleaseNote]:
    releases_dir = settings.data_dir / component / track / "releases"
    if not releases_dir.is_dir():
        return []
    notes: list[ReleaseNote] = []
    for f in sorted(releases_dir.iterdir()):
        if not f.suffix == ".json":
            continue
        note = ReleaseNote.model_validate(json.loads(f.read_text()))
        if note.version_code > after_version_code:
            notes.append(note)
    return notes

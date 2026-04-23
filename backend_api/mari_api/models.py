from datetime import datetime

from pydantic import BaseModel


class LatestRelease(BaseModel):
    component: str
    track: str
    package_name: str
    version_code: int
    version_name: str
    file_name: str
    file_size_bytes: int
    sha256: str
    released_at: datetime
    notification_title: str
    notification_text: str
    changelog: str
    min_installed_version_code: int


class ReleaseNote(BaseModel):
    version_code: int
    version_name: str
    released_at: datetime
    features: list[str] = []
    upgrades: list[str] = []
    fixes: list[str] = []

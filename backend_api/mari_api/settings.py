import logging
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

logger = logging.getLogger(__name__)


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="MARI_")

    data_dir: Path = Path("/data/app_updates")
    api_token: str = ""

    def auth_enabled(self) -> bool:
        return bool(self.api_token)


_settings: Settings | None = None


def get_settings() -> Settings:
    global _settings
    if _settings is None:
        _settings = Settings()
        if not _settings.auth_enabled():
            logger.warning("MARI_API_TOKEN is empty — authentication disabled (local dev only)")
    return _settings

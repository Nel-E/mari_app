import logging
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

logger = logging.getLogger(__name__)

DEFAULT_DATA_DIR = Path(__file__).resolve().parents[1] / "data" / "app_updates"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="MARI_")

    data_dir: Path = DEFAULT_DATA_DIR
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

import logging
import logging.config

from fastapi import FastAPI

from mari_api.routes import artifacts, health, latest, releases
from mari_api.settings import get_settings

logging.config.dictConfig({
    "version": 1,
    "formatters": {"json": {"format": '{"time":"%(asctime)s","level":"%(levelname)s","name":"%(name)s","msg":"%(message)s"}'}},
    "handlers": {"console": {"class": "logging.StreamHandler", "formatter": "json"}},
    "root": {"level": "INFO", "handlers": ["console"]},
})


def create_app() -> FastAPI:
    get_settings()  # trigger startup warning if token empty
    application = FastAPI(title="Mari Update API", version="1.0.0")
    application.include_router(health.router)
    application.include_router(latest.router)
    application.include_router(releases.router)
    application.include_router(artifacts.router)
    return application


app = create_app()

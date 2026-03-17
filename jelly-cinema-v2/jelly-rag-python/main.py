from app.config import get_settings
from app.main import app


if __name__ == "__main__":
    import uvicorn

    settings = get_settings()
    uvicorn.run("main:app", host="0.0.0.0", port=settings.service_port, reload=False)

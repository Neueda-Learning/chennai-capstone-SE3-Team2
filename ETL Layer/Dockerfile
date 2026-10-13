# =====================================================================
# The analytics ETL: reads trading_system_db (public schema) as the read-only
# role and writes the DuckDB star schema. One run, then it exits.
#
#   docker compose run --rm etl            (from the repository root)
#
# Connection and output paths come from the environment (DB_*,
# ANALYTICS_DB_*, DUCKDB_PATH): docker-compose.yml sets them.
# =====================================================================
FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY pyproject.toml ./
COPY src ./src
RUN pip install --no-cache-dir .
RUN useradd --create-home etl && mkdir -p /data && chown etl /data
USER etl
CMD ["python", "-m", "etl.pipeline", "run"]

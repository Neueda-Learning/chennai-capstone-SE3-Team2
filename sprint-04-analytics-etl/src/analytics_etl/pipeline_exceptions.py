from __future__ import annotations


class PipelineError(Exception):
    """Base for every failure the pipeline raises deliberately."""

    mode = "unknown"
    stage = "unknown"
    fatal = False

    def __init__(self, message: str, *, symbol: str | None = None):
        super().__init__(message)
        self.symbol = symbol

    def log_fields(self) -> str:
        return (
            f"stage={self.stage} mode={self.mode} "
            f"symbol={self.symbol or '?'} fatal={str(self.fatal).lower()}"
        )


# --------------------------------------------------------------------
# Extract
# --------------------------------------------------------------------
class ExtractError(PipelineError):
    stage = "extract"


class QuotaExhaustedError(ExtractError):
    """HTTP 429 - the daily quota is gone."""

    mode = "quota_exhausted"
    fatal = True

    def __init__(
        self,
        message: str,
        *,
        symbol: str | None = None,
        retry_after: str | None = None,
        retry_after_seconds: float | None = None,
        resets_at=None,
    ):
        super().__init__(message, symbol=symbol)
        self.retry_after = retry_after
        self.retry_after_seconds = retry_after_seconds
        self.resets_at = resets_at


class BadRequestError(ExtractError):
    """4xx other than 429 - the request itself is wrong."""

    mode = "bad_request"

    def __init__(
        self,
        message: str,
        *,
        symbol: str | None = None,
        status_code: int | None = None,
        body: str | None = None,
    ):
        super().__init__(message, symbol=symbol)
        self.status_code = status_code
        self.body = body


class ServiceUnreachableError(ExtractError):
    """Connection error or timeout, after all retries."""

    mode = "unreachable"

    def __init__(
        self, message: str, *, symbol: str | None = None, attempts: int = 0
    ):
        super().__init__(message, symbol=symbol)
        self.attempts = attempts


class UpstreamServiceError(ExtractError):
    """5xx - the service is broken, the request was fine."""

    mode = "upstream_error"

    def __init__(
        self,
        message: str,
        *,
        symbol: str | None = None,
        status_code: int | None = None,
    ):
        super().__init__(message, symbol=symbol)
        self.status_code = status_code


class MalformedResponseError(ExtractError):
    """HTTP 200 whose body will not parse as JSON."""

    mode = "malformed_response"


class MissingCredentialError(ExtractError):
    """No API key configured."""

    mode = "missing_credential"
    fatal = True


# --------------------------------------------------------------------
# Transform
# --------------------------------------------------------------------
class TransformError(PipelineError):
    stage = "transform"


class MalformedEnvelopeError(TransformError):
    """The response shape is unusable - nothing can be salvaged."""

    mode = "malformed_envelope"


# --------------------------------------------------------------------
# Load
# --------------------------------------------------------------------
class LoadError(PipelineError):
    stage = "load"


class OutputUnavailableError(LoadError):
    """Cannot create or write the output directory."""

    mode = "output_unavailable"
    fatal = True


class SchemaDriftError(LoadError):
    """Dataframe columns do not match the header already in the CSV."""

    mode = "schema_drift"
    fatal = True


class LoadWriteError(LoadError):
    """The append failed and was rolled back."""

    mode = "load_write_failed"
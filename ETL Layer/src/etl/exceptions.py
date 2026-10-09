from __future__ import annotations


class ETLError(Exception):
    pass


class ExtractionError(ETLError):
    pass


class LoadError(ETLError):
    pass

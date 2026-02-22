"""
Resilience helpers for retry and circuit breaker control.
"""
import logging
import threading
import time
from typing import Any, Callable, Dict, Optional, TypeVar

from tenacity import (
    before_sleep_log,
    retry,
    retry_if_exception,
    stop_after_attempt,
    wait_fixed,
)

from config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()
T = TypeVar("T")


class CircuitBreakerOpenError(RuntimeError):
    """Raised when an operation is blocked by an open circuit breaker."""


class CircuitBreaker:
    """Simple thread-safe circuit breaker."""

    def __init__(self, failure_threshold: int, recovery_timeout_seconds: int):
        self.failure_threshold = max(1, failure_threshold)
        self.recovery_timeout_seconds = max(1, recovery_timeout_seconds)
        self._failure_count = 0
        self._opened_at: Optional[float] = None
        self._lock = threading.Lock()

    def before_call(self, operation_name: str) -> None:
        with self._lock:
            if self._opened_at is None:
                return
            elapsed = time.monotonic() - self._opened_at
            if elapsed >= self.recovery_timeout_seconds:
                # Half-open: allow a new probe call.
                self._opened_at = None
                self._failure_count = 0
                return
            raise CircuitBreakerOpenError(
                f"Circuit breaker is open for operation '{operation_name}'"
            )

    def record_success(self) -> None:
        with self._lock:
            self._failure_count = 0
            self._opened_at = None

    def record_failure(self) -> None:
        with self._lock:
            self._failure_count += 1
            if self._failure_count >= self.failure_threshold:
                self._opened_at = time.monotonic()


_breaker_lock = threading.Lock()
_breakers: Dict[str, CircuitBreaker] = {}


def _get_breaker(operation_name: str) -> CircuitBreaker:
    with _breaker_lock:
        breaker = _breakers.get(operation_name)
        if breaker is None:
            breaker = CircuitBreaker(
                failure_threshold=settings.circuit_breaker_threshold,
                recovery_timeout_seconds=settings.circuit_breaker_timeout,
            )
            _breakers[operation_name] = breaker
        return breaker


def _should_retry(exc: BaseException) -> bool:
    return not isinstance(exc, CircuitBreakerOpenError)


def run_with_resilience(operation_name: str, func: Callable[..., T], *args: Any, **kwargs: Any) -> T:
    """
    Execute an operation with retry and circuit-breaker protection.
    """
    breaker = _get_breaker(operation_name)

    @retry(
        reraise=True,
        stop=stop_after_attempt(max(1, settings.retry_max_attempts)),
        wait=wait_fixed(max(0.0, settings.retry_wait_seconds)),
        retry=retry_if_exception(_should_retry),
        before_sleep=before_sleep_log(logger, logging.WARNING),
    )
    def _invoke() -> T:
        breaker.before_call(operation_name)
        try:
            result = func(*args, **kwargs)
            breaker.record_success()
            return result
        except Exception:
            breaker.record_failure()
            raise

    return _invoke()

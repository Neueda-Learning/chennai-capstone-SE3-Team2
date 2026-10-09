package com.yellow.trade.advice;

import java.util.List;

/**
 * The signals for what an account holds and watches (openapi/advice.yaml),
 * holdings first. Truncated past AccountAdviceService.MAX_STOCKS.
 */
public record AccountAdvice(long accountId, List<AdviceItem> items, boolean truncated, String disclaimer) {
}

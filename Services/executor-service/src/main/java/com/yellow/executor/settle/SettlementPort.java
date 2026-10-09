package com.yellow.executor.settle;

import com.yellow.executor.fill.FillDecision;
import com.yellow.executor.fill.OrderSnapshot;

/** Writes a decision down, once. THE SEAM BETWEEN STORY 610 AND STORY 611. */
public interface SettlementPort {

    SettlementResult settle(FillDecision decision, OrderSnapshot order);
}

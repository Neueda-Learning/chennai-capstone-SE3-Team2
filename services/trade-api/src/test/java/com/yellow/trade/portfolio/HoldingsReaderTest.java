package com.yellow.trade.portfolio;

import com.yellow.trade.mappers.PositionMapper;
import com.yellow.trade.mappers.PositionRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldingsReaderTest {

    @Mock private PositionMapper positions;

    private static PositionRow row(String symbol, String quantity) {
        PositionRow row = new PositionRow();
        row.setSymbol(symbol);
        row.setQuantity(new BigDecimal(quantity));
        return row;
    }

    @Test
    @DisplayName("what an account holds, for advice: quantity above zero, each symbol once, in symbol order")
    void held() {
        when(positions.findByAccountId(3L)).thenReturn(List.of(row("TCS.NS", "4"), row("ITC.NS", "2"), row("MRF.NS", "0"),
                row("TCS.NS", "1")));

        assertThat(new HoldingsReader(positions).heldSymbols(3), contains("ITC.NS", "TCS.NS"));
    }
}

package com.yellow.trade.advice;

import com.yellow.enums.Reason;
import com.yellow.exceptions.InstrumentNotFoundException;
import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.InstrumentRow;
import com.yellow.trade.marketdata.PricingUnavailableException;
import com.yellow.trade.portfolio.api.Holdings;
import com.yellow.trade.security.AccountAccess;
import com.yellow.trade.security.AccountNotReachableException;
import com.yellow.trade.watchlists.api.WatchedInstruments;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountAdviceServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T04:30:00Z");

    @Mock private AdviceService advice;
    @Mock private Holdings holdings;
    @Mock private WatchedInstruments watched;
    @Mock private InstrumentMapper instruments;
    @Mock private AccountAccess access;

    private AccountAdviceService service() {
        return new AccountAdviceService(advice, holdings, watched, instruments, access, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void listed(String symbol, String type) {
        InstrumentRow row = new InstrumentRow();
        row.setSymbol(symbol);
        row.setInstrumentType(type);
        row.setTradable(true);
        lenient().when(instruments.findBySymbol(symbol)).thenReturn(row);
    }

    private static Signal buy(String symbol) {
        return new Signal(symbol, Direction.BUY, 64, Methodology.NAME, "The 20-day average is 2.0% above the 50-day and RSI is 61, so the trend is up.",
                new SignalFigures(new BigDecimal("102.0"), new BigDecimal("100.0"), new BigDecimal("61.00"), new BigDecimal("103.00"), NOW, 80),
                NOW, AdviceService.DISCLAIMER);
    }

    @Test
    @DisplayName("every stock held or watched, each once, held first; one both held and watched says so")
    void heldAndWatched() {
        when(holdings.heldSymbols(3)).thenReturn(List.of("ITC.NS", "SUMCEM"));
        when(watched.watchedSymbols(3)).thenReturn(List.of("TCS.NS", "ITC.NS"));
        List.of("ITC.NS", "SUMCEM", "TCS.NS").forEach(s -> {
            listed(s, "STOCK");
            when(advice.signal(s)).thenReturn(buy(s));
        });

        AccountAdvice result = service().forAccount(3);

        assertThat(result.accountId(), is(3L));
        assertThat(result.items().stream().map(AdviceItem::symbol).toList(), contains("ITC.NS", "SUMCEM", "TCS.NS"));
        assertThat(result.items().stream().map(i -> i.held() + "/" + i.watched()).toList(),
                contains("true/true", "true/false", "false/true"));
        assertThat(result.items().get(0).signal().direction(), is(Direction.BUY));
        assertThat(result.items().get(0).signal().reason(), containsString("so the trend is up"));
        assertThat(result.truncated(), is(false));
        assertThat(result.disclaimer(), is(AdviceService.DISCLAIMER));
    }

    @Test
    @DisplayName("a fund is listed with no signal, saying why; its signal is never asked for")
    void fund() {
        when(holdings.heldSymbols(3)).thenReturn(List.of("122639"));
        when(watched.watchedSymbols(3)).thenReturn(List.of());
        listed("122639", "MF");

        AdviceItem item = service().forAccount(3).items().get(0);

        assertThat(item.signal().direction(), is(nullValue()));
        assertThat(item.signal().strength(), is(nullValue()));
        assertThat(item.signal().reason(), is("A fund is priced once a day at its NAV, and this method needs daily candles, so there is no signal."));
        verify(advice, never()).signal(anyString());
    }

    @Test
    @DisplayName("prices that cannot be read are no signal for that stock, not an error for the list")
    void unavailable() {
        when(holdings.heldSymbols(3)).thenReturn(List.of("ITC.NS", "MERSTL"));
        when(watched.watchedSymbols(3)).thenReturn(List.of("TCS.NS"));
        listed("ITC.NS", "STOCK");
        listed("MERSTL", "STOCK");
        listed("TCS.NS", "STOCK");
        when(advice.signal("ITC.NS")).thenThrow(new PricingUnavailableException("no Fauxnance request available"));
        when(advice.signal("MERSTL")).thenThrow(new InstrumentNotFoundException("MERSTL", Reason.NOT_TRADABLE));
        when(advice.signal("TCS.NS")).thenReturn(buy("TCS.NS"));

        List<AdviceItem> items = service().forAccount(3).items();

        assertThat(items.get(0).signal().direction(), is(nullValue()));
        assertThat(items.get(0).signal().reason(), is("Its prices could not be read just now, so there is no signal; it is tried again on the next read."));
        assertThat(items.get(1).signal().reason(), is("It is no longer traded, so there is no signal."));
        assertThat(items.get(2).signal().direction(), is(Direction.BUY));
    }

    @Test
    @DisplayName("too little history is no signal, as the methodology says, and the list passes it on")
    void tooLittleHistory() {
        when(holdings.heldSymbols(3)).thenReturn(List.of("NEWCO.NS"));
        when(watched.watchedSymbols(3)).thenReturn(List.of());
        listed("NEWCO.NS", "STOCK");
        Signal none = new Signal("NEWCO.NS", null, null, Methodology.NAME,
                "Only 32 days of prices: a 50-day average needs 50, so there is no signal yet.",
                new SignalFigures(new BigDecimal("101.0"), null, new BigDecimal("55.00"), new BigDecimal("102.00"), NOW, 32),
                NOW, AdviceService.DISCLAIMER);
        when(advice.signal("NEWCO.NS")).thenReturn(none);

        assertThat(service().forAccount(3).items().get(0).signal().direction(), is(nullValue()));
    }

    @Test
    @DisplayName("another customer's account is ACC-403 before anything is read")
    void otherAccount() {
        doThrow(new AccountNotReachableException()).when(access).requireOwn(4L);

        assertThrows(AccountNotReachableException.class, () -> service().forAccount(4));
        verifyNoInteractions(holdings, watched, advice);
    }

    @Test
    @DisplayName("past 30 stocks the list stops, holdings first, and says so: each one costs price-service quota")
    void capped() {
        List<String> held = IntStream.range(0, 5).mapToObj(i -> "H" + i).toList();
        List<String> watch = IntStream.range(0, 40).mapToObj(i -> "W" + i).toList();
        when(holdings.heldSymbols(3)).thenReturn(held);
        when(watched.watchedSymbols(3)).thenReturn(watch);
        held.forEach(s -> listed(s, "STOCK"));
        watch.forEach(s -> listed(s, "STOCK"));
        lenient().when(advice.signal(anyString())).thenAnswer(call -> buy(call.getArgument(0)));

        AccountAdvice result = service().forAccount(3);

        assertThat(result.items(), hasSize(AccountAdviceService.MAX_STOCKS));
        assertThat(result.items().get(0).symbol(), is("H0"));
        assertThat(result.items().get(5).symbol(), is("W0"));
        assertThat(result.truncated(), is(true));
        verify(advice, never()).signal("W25");
    }
}

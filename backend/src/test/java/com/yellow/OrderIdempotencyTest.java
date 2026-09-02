package com.yellow;

import com.yellow.dto.PlaceOrderRequest;
import com.yellow.entities.Account;
import com.yellow.entities.Instrument;
import com.yellow.enums.AccountStatus;
import com.yellow.enums.AssetClass;
import com.yellow.enums.OrderSide;
import com.yellow.exceptions.DuplicateOrderException;
import com.yellow.repositories.InMemoryAccountRepository;
import com.yellow.repositories.InMemoryInstrumentRepository;
import com.yellow.repositories.InMemoryOrderRepository;
import com.yellow.repositories.InMemoryPositionRepository;
import com.yellow.services.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderIdempotencyTest {

	private InMemoryOrderRepository orders;
	private OrderService service;

	@BeforeEach
	void setUp() {
		InMemoryAccountRepository accounts = new InMemoryAccountRepository();
		accounts.save(new Account(1L, "REF-1", 100L, new BigDecimal("100000.00"),
				BigDecimal.ZERO, AccountStatus.ACTIVE, 1));

		InMemoryInstrumentRepository instruments = new InMemoryInstrumentRepository();
		instruments.save(new Instrument(2L, "AAPL", "Apple Inc",
				AssetClass.EQUITY, "USD", true));

		orders = new InMemoryOrderRepository();
		service = new OrderService(accounts, instruments,
				new InMemoryPositionRepository(), orders);
	}

	private PlaceOrderRequest request() {
		return new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 10,
				new BigDecimal("100.00"), "key-1234");
	}

	@Test
	void replayOfTheSameKeyIsRefused() {
		service.placeOrder(request());

		assertThrows(DuplicateOrderException.class, () -> service.placeOrder(request()));
		assertEquals(1, orders.count());
	}

	@Test
	void onlyOneOfManyConcurrentRequestsWithTheSameKeyIsAccepted() throws Exception {
		int threads = 16;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch startTogether = new CountDownLatch(1);

		AtomicInteger accepted = new AtomicInteger();
		AtomicInteger refusedAsDuplicate = new AtomicInteger();
		AtomicInteger refusedOtherwise = new AtomicInteger();

		List<Future<?>> futures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			futures.add(pool.submit(() -> {
				try {
					startTogether.await();
					service.placeOrder(request());
					accepted.incrementAndGet();
				} catch (DuplicateOrderException expected) {
					refusedAsDuplicate.incrementAndGet();
				} catch (Throwable unexpected) {
					refusedOtherwise.incrementAndGet();
				}
			}));
		}

		startTogether.countDown();
		for (Future<?> future : futures) {
			future.get(10, TimeUnit.SECONDS);
		}
		pool.shutdown();

		assertEquals(1, accepted.get(),
				"exactly one request may win the race");
		assertEquals(threads - 1, refusedAsDuplicate.get(),
				"every loser must be refused as a duplicate, not with a raw runtime error");
		assertEquals(0, refusedOtherwise.get());
		assertEquals(1, orders.count(),
				"exactly one order may reach the store");
	}
}

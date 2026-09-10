package com.yellow.trade.persistence;

import com.yellow.entities.Order;
import com.yellow.exceptions.DuplicateOrderException;
import com.yellow.repositories.OrderRepository;
import com.yellow.trade.mappers.OrderMapper;
import com.yellow.trade.mappers.OrderRow;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Binds the domain's OrderRepository port to Postgres, and is where rule 8
 * actually gets decided.
 *
 * The domain asks existsByAccountAndKey before it builds an order, which is
 * the seam that makes rule 8 testable in Sprint 5 with no database. That check
 * is necessary and not sufficient: two requests carrying the same key can both
 * read "no" before either writes. The authority is
 * uq_orders_client_idempotency_key, built in Sprint 3, and it is enforced
 * below by letting the insert fail and translating the failure -- not by
 * checking harder first.
 */
@Repository
public class MyBatisOrderRepository implements OrderRepository {

    private final OrderMapper orderMapper;

    public MyBatisOrderRepository(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public boolean existsByAccountAndKey(Long accountId, String idempotencyKey) {
        if (accountId == null || idempotencyKey == null) {
            return false;
        }
        // Deliberately a targeted read rather than a scan: it exists to give
        // the common case a clean ORD-409 with the ordering rule 8 specifies,
        // ahead of rules that would otherwise report a different code first.
        return orderMapper.findByAccountId(accountId, null, null, null).stream()
                .anyMatch(row -> idempotencyKey.equals(row.getIdempotencyKey()));
    }

    @Override
    public Order save(Order order) {
        OrderRow row = RowMapping.toRow(order);
        try {
            int affected = orderMapper.insert(row);
            if (affected != 1) {
                // Not reachable through a constraint -- that throws. This
                // catches a statement that silently matched nothing.
                throw new IllegalStateException(
                        "order insert affected " + affected + " rows, expected 1");
            }
        } catch (DuplicateKeyException e) {
            // The unique index refused it: another request won the race with
            // the same key. Same code the pre-check would have produced, so
            // the caller cannot tell which path answered -- which is the
            // point. The existing order's id is not read back, because
            // reporting it would leak another request's identifier.
            throw new DuplicateOrderException(order.idempotencyKey(), null);
        }
        return order;
    }

    @Override
    public Optional<Order> findById(UUID orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        OrderRow row = orderMapper.findById(orderId);
        return Optional.ofNullable(row).map(RowMapping::toOrder);
    }
}

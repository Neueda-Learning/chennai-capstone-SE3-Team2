# Historical Trade Data Design

## 1. Purpose

The current `orders` table represents the client's request to buy or
sell an instrument. It stores the order lifecycle, but it does not
represent the details of an individual execution.

Trade execution is handled by an external execution service. Therefore,
historical trade data should be retained separately from the current
order state.

The purpose of the historical trade data is to preserve what actually
happened during execution and provide a reliable source for future
reporting and data extraction.

## 2. Retention Grain

The historical trade record should be stored at the **execution grain**:
one row represents one execution of an order.

An execution record should retain information such as:

- `execution_id` — unique identifier for the execution.
- `order_id` — order that resulted in the execution.
- `client_id` — client associated with the order.
- `instrument_id` — instrument that was traded.
- `executed_quantity` — quantity executed.
- `execution_price` — actual price at which the trade was executed.
- `executed_at` — timestamp of the execution.
- `execution_reference` — identifier supplied by the external execution
  service.

The execution record should retain the values needed to reconstruct what
was actually traded rather than relying only on the current values in
`orders` or `position`.

An order may result in one or more executions in a general trading
system. Each execution is therefore retained independently. The current
Sprint design does not implement partial or multiple executions, but
the historical design allows for this future extension.

## 3. Population

The external execution service is responsible for executing the order.

The population flow is:

1. The client submits an order.
2. The platform stores the order in `orders`.
3. The order is sent to the external execution service.
4. The external service executes the order.
5. The external service returns the execution details.
6. The platform records the execution in historical trade storage.

The historical record should be populated from the execution response,
not calculated later from `orders` or `position`.

The `order_id` and `execution_reference` provide traceability between the
platform's order and the external execution.

The historical execution record is append-oriented. Once an execution
has been recorded, it should not normally be modified or deleted.

## 4. Incremental Extraction

Sprint 7 should extract only new historical executions rather than
scanning the complete trade history on every run.

Each execution record contains an `executed_at` timestamp and a unique
`execution_id`. These fields can be used as an incremental extraction
cursor.

For example, the extraction can maintain the last successfully
processed execution timestamp and ID:

```text
WHERE executed_at > :last_timestamp
   OR (
        executed_at = :last_timestamp
        AND execution_id > :last_execution_id
      )
ORDER BY executed_at, execution_id
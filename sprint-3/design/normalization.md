# Normalization Notes

1. ER Diagram → Tables

The ER diagram was converted into four main tables based on the main domain entities:

1) CLIENT — stores information about each client.
2) INSTRUMENT — stores information about each security/stock available for trading.
3) ORDER — stores every order placed by a client.
4) POSITION — stores the current holdings of a client for each instrument.

Relationships

1) A CLIENT can place many ORDERS -> client_id is a foreign key in ORDER.
2) An INSTRUMENT can appear in many ORDERS -> instrument_id is a foreign key in ORDER.
3) A CLIENT can hold multiple INSTRUMENTS, and an instrument can be held by multiple clients -> represented through POSITION.
4) POSITION has a unique (client_id, instrument_id) combination so that a client has only one current position for a particular instrument.

2. Normalisation

The tables are designed up to 3NF.

CLIENT

Each row represents one client and all non-key attributes describe that client.

client_id -> name, email, phone_number, demat_id, pan

No repeating groups or partial/transitive dependencies.

INSTRUMENT

Each row represents one security.

instrument_id -> ticker, isin

The isin is also unique because it uniquely identifies a security.

ORDER

Each row represents one order placed by a client.

order_id → client_id, instrument_id, side, order_type, price, quantity, status, date_placed

Client and instrument details are not duplicated in the order table; they are referenced using foreign keys.

POSITION

Each row represents the current holding of one client in one instrument.

The (client_id, instrument_id) combination identifies the position, and the quantity/average price depend on that combination.

3. Design Decisions

1) ORDER stores the order and its current status; a separate failed-order table is not required.
2) A separate ORDER_HISTORY table is not required because historical status tracking is outside the current scope.
3) POSITION stores the current state rather than recalculating holdings from past orders every time.
4) Indexes were added mainly for common queries such as retrieving a client's recent orders and finding orders for an instrument.
5) The actual trade execution is assumed to be handled by an external service, so individual executions are outside the scope of this database.

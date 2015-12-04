-- name: ddl-keyrun-transaction-table!
CREATE TABLE IF NOT EXISTS keyrun_transaction (
  tx_hash varchar(64) primary key,
  mined boolean DEFAULT false,
  friendly_value varchar(64),
  data varchar(40),
  sort_time timestamp DEFAULT CURRENT_TIMESTAMP
)

-- name: sql-upsert-keyrun-transaction!
INSERT OR REPLACE INTO keyrun_transaction (tx_hash, mined, data, friendly_value, sort_time)
VALUES (:tx_hash, :mined, :data, :friendly_value, :sort_time)

-- name: sql-insert-keyrun-transaction!
INSERT INTO keyrun_transaction (tx_hash, mined, data, friendly_value, sort_time)
VALUES (:tx_hash, :mined, :data, :friendly_value, :sort_time)

-- name: sql-get-keyrun-transaction
SELECT * FROM keyrun_transaction WHERE tx_hash = :tx_hash LIMIT 1

-- name: sql-get-keyrun-transactions
SELECT * FROM keyrun_transaction ORDER BY sort_time DESC

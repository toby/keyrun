-- name: ddl-keyrun-transaction-table!
CREATE TABLE IF NOT EXISTS keyrun_transaction (
  tx_hash varchar(64) primary key,
  mined boolean DEFAULT false,
  value integer,
  data varchar(40),
  sort_time timestamp DEFAULT CURRENT_TIMESTAMP
)

-- name: sql-upsert-keyrun-transaction!
INSERT OR REPLACE INTO keyrun_transaction (tx_hash, mined, data, value)
VALUES (:tx_hash, :mined, :data, :value)

-- name: sql-insert-keyrun-transaction!
INSERT INTO keyrun_transaction (tx_hash, mined, data, value)
VALUES (:tx_hash, :mined, :data, :value)

-- name: sql-get-keyrun-transaction
SELECT * FROM keyrun_transaction WHERE tx_hash = :tx_hash LIMIT 1

-- name: sql-get-keyrun-transactions-old
SELECT * FROM keyrun_transaction ORDER BY sort_time DESC

-- name: sql-get-keyrun-transactions
SELECT data, count(tx_hash) AS tx_hashes, sum(value) AS value, sum(mined) AS mined
FROM keyrun_transaction
GROUP BY data
ORDER BY value DESC

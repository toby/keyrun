-- name: ddl-keyrun-transaction-table!
CREATE TABLE IF NOT EXISTS keyrun_transaction (
  id bigserial primary key,
  data varchar(40),
  tx_hash varchar(64),
  friendly_value varchar(64),
  update_time timestamp,
  from_address varchar(21)
)

-- name: save-keyrun-transaction!
INSERT INTO keyrun_transaction (data, tx_hash, friendly_value, update_time, from_address)
VALUES (:data, :tx_hash, :friendly_value, :update_time, :from_address)

-- name: sql-get-keyrun-transactions
SELECT * FROM keyrun_transactions ORDER BY id DESC

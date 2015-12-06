(ns keyrun.db
  (:require [clojure.tools.logging :as log]
            [clojure.java.jdbc :as jdbc]
            [yesql.core :refer [defqueries]]
            [com.stuartsierra.component :refer [Lifecycle]]))

(defrecord KeyrunTransaction [tx-hash mined data value sort-time])

(defprotocol KeyrunTransactionStore
  (insert-keyrun-transaction [this tx])
  (upsert-keyrun-transaction [this tx])
  (get-keyrun-transaction [this tx-hash])
  (get-keyrun-transactions [this])
  (get-btih-transactions [this btih])
  )

(defprotocol DBAdmin
  (create-db [this])
  (drop-db [this]))

(defqueries "keyrun/sql/keyrun.sql")

(defrecord SQLiteDB [spec]
  Lifecycle
  (start [this]
    (log/info "Ensuring SQLite table structure" spec)
    (let [db-file (:subname spec)]
      (if (.exists (clojure.java.io/as-file db-file))
        (log/info "Found SQLite DB:" db-file)
        (.create-db this))
      this))
  (stop [this] this)

  DBAdmin
  (create-db [this]
    (log/info "Creating SQLite DB:" (str "./" (:subname spec)))
    (try
      (let [con {:connection spec}]
        (ddl-keyrun-transaction-table! nil con))
      (catch Exception e
        (log/error (.getMessage e)))))
  (drop-db [this]
    (log/info "Drop DB")
    ; TODO delete file
    )

  KeyrunTransactionStore
  (insert-keyrun-transaction [this tx]
    (sql-insert-keyrun-transaction! tx {:connection spec}))
  (upsert-keyrun-transaction [this tx]
    (sql-upsert-keyrun-transaction! tx {:connection spec}))
  (get-keyrun-transaction [this tx-hash]
    )
  (get-keyrun-transactions [this]
    (let [transactions (sql-get-keyrun-transactions {} {:connection spec})]
      ;(map #(update-in % [:mined] (partial not= 0)) transactions)
      transactions
      ))
  (get-btih-transactions [this btih]
    (sql-get-btih-transactions {:btih btih} {:connection spec})))

(defn get-sqlite-db [filename]
  (SQLiteDB. {:classname "org.sqlite.JDBC"
              :subprotocol "sqlite"
              :subname filename}))

(ns keyrun.db
  (:require [clojure.tools.logging :as log]
            [clojure.java.jdbc :as jdbc]
            [yesql.core :refer [defqueries]]
            [com.stuartsierra.component :refer [Lifecycle]]))

(defprotocol KeyrunTransactionStore
  (save-btih-transaction [this tx])
  (get-btih-transaction [this tx-hash])
  (get-btih-transactions [this]))

(defrecord KeyrunTransaction [data tx-hash friendly-value update-time from-address])

(defprotocol DBAdmin
  (create-db [this])
  (drop-db [this]))

(defqueries "keyrun/sql/schema.sql")

(defrecord SQLiteDB [spec]
  Lifecycle
  (start [this]
    (log/info "Loading SQL queries")
    (log/info "Ensuring SQLite table structure" spec)
    (let [db-file (:subname spec)]
      (if (.exists (clojure.java.io/as-file db-file))
        (log/info "Found SQLite DB:" db-file)
        (.create-db this))
      this))
  (stop [this] this)

  DBAdmin
  (create-db [this]
    (log/info "Create DB")
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
  (save-btih-transaction [this tx]
    (save-keyrun-transaction! tx {:connection spec}))
  (get-btih-transaction [this tx-hash]
    )
  (get-btih-transactions [this]
    (sql-get-keyrun-transactions {} {:connection spec})))

(defn get-sqlite-db [filename]
  (SQLiteDB. {:classname "org.sqlite.JDBC"
              :subprotocol "sqlite"
              :subname filename}))

(defrecord InMemoryDB [state]
  KeyrunTransactionStore
  (save-btih-transaction [this tx]
    (swap! state
           (fn [txs {:keys [tx-hash] :as t}]
             (if (not (contains? txs tx-hash))
               (assoc txs tx-hash t)
               txs))
           tx))
  (get-btih-transaction [this tx-hash]
    (get @state tx-hash))
  (get-btih-transactions [this]
    (map second @state)))

(defn get-memory-db []
  (InMemoryDB. (atom {})))

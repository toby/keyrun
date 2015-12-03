(ns keyrun.db
  (:require [clojure.tools.logging :as log]
            [clojure.java.jdbc :as jdbc]
            [yesql.core :refer [defqueries]]
            [com.stuartsierra.component :refer [Lifecycle]]))

(defprotocol KeyrunTransactionStore
  (add-keyrun-transaction [this tx])
  (get-keyrun-transaction [this tx-hash])
  (get-keyrun-transactions [this]))

(defrecord KeyrunTransaction [data tx-hash friendly-value update-time from-address])

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
  (add-keyrun-transaction [this tx]
    (sql-save-keyrun-transaction! tx {:connection spec}))
  (get-keyrun-transaction [this tx-hash]
    )
  (get-keyrun-transactions [this]
    (sql-get-keyrun-transactions {} {:connection spec})))

(defn get-sqlite-db [filename]
  (SQLiteDB. {:classname "org.sqlite.JDBC"
              :subprotocol "sqlite"
              :subname filename}))

(defrecord InMemoryDB [state]
  KeyrunTransactionStore
  (add-keyrun-transaction [this tx]
    (swap! state
           (fn [txs {:keys [tx-hash] :as t}]
             (if (not (contains? txs tx-hash))
               (assoc txs tx-hash t)
               txs))
           tx))
  (get-keyrun-transaction [this tx-hash]
    (get @state tx-hash))
  (get-keyrun-transactions [this]
    (map second @state)))

(defn get-memory-db []
  (InMemoryDB. (atom {})))

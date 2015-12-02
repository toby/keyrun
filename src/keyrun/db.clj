(ns keyrun.db
  (:require [clojure.tools.logging :as log]
            [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]))

(defprotocol KeyrunTransactionStore
  (save-btih-transaction [this tx])
  (get-btih-transaction [this tx-hash])
  (get-btih-transactions [this]))

(defrecord SQLiteDB [spec]
  component/Lifecycle
  (start [this]
    (log/info "Ensuring SQLite table structure")
    (let [connection (jdbc/get-connection spec)
          connection-meta (.getMetaData connection)
          tables (.getTables connection-meta nil nil nil nil)]
      (log/info "TABLES" tables)
      ))
  (stop [this])
  KeyrunTransactionStore
  (save-btih-transaction [this tx]
    )
  (get-btih-transaction [this tx-hash]
    )
  (get-btih-transactions [this]
    ))

(defn create-sqlite-db [filename]
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

(defn create-memory-db []
  (InMemoryDB. (atom {})))

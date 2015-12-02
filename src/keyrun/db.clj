(ns keyrun.db
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]))

(defprotocol KeyrunTransactionStore
  (save-btih-transaction [this tx])
  (get-btih-transaction [this tx-hash])
  (get-btih-transactions [this]))

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

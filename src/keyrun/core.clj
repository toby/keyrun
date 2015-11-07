(ns keyrun.core
  (:gen-class)
  (:require [clojure.tools.logging :as log])
  (:import
    (org.bitcoinj.core NetworkParameters Address ECKey PeerGroup BlockChain)
    (org.bitcoinj.store SPVBlockStore)
    (org.bitcoinj.net.discovery DnsDiscovery)
    (org.bitcoinj.params TestNet3Params RegTestParams MainNetParams)
    ))

(defn usage []
  (println "Usage: address-to-send-back-to [regtest|testnet]"))

(defmulti network-params identity)

(defmethod network-params "testnet" [_]
  (TestNet3Params/get))

(defmethod network-params "regtest" [_]
  (RegTestParams/get))

(defmethod network-params :default [_]
  (MainNetParams/get))

(defmulti file-prefix class)

(defmethod file-prefix TestNet3Params [_]
  "testnet")

(defmethod file-prefix RegTestParams [_]
  "regtest")

(defmethod file-prefix MainNetParams [_]
  "production")

(defn bitcoin-address->Address [address params]
  (try
    (Address. params address)
    (catch Exception e
      (log/error "Bad address:" (.getMessage e)))
    ))

(defn bitcoin-address->ECKey [address params]
  (try
    (ECKey. nil (.getBytes address))
    (catch Exception e
      (log/error "Bad address:" (.getMessage e)))))

; default namespace key: 1GzjTsqp3LASxLsEd1vsKiDHTuPa2aYm5G

(defn -main
  "Starting a key.run server"
  [& [address network-type]]
  (if (or (= "help" address) (nil? address))
    (usage)
    (let [params (network-params network-type)
          network-prefix (file-prefix params)
          namespace-address (bitcoin-address->ECKey address params) ; TODO check nil
          blockstore-file (clojure.java.io/file (str "./" network-prefix ".blockstore"))
          blockstore (SPVBlockStore. params blockstore-file) ; TODO load checkpoint
          blockchain (BlockChain. params blockstore)
          peer-group (PeerGroup. params blockchain)
          ]
      (log/info "Namespace address:" (.toString namespace-address))
      (log/info "Starting peer group...")
      (doto peer-group
        (.setUserAgent "key.run", "0.1")
        (.addPeerDiscovery (DnsDiscovery. params))
        ; (.setFastCatchupTime) ; TODO set to start of key.run
        (.start)
        (.downloadBlockChain))
      ;(println "New key:" (.getPrivateKeyAsWiF (ECKey.) params))
      )))

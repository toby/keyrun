(ns keyrun.core
  (:gen-class)
  (:require [clojure.tools.logging :as log])
  (:import
    (org.bitcoinj.core NetworkParameters
                       Address
                       ECKey
                       PeerGroup
                       BlockChain
                       Utils
                       PeerFilterProvider
                       BloomFilter
                       AbstractPeerEventListener
                       AbstractBlockChainListener)
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

(defn string->Address [address params]
  (try
    (Address. params address)
    (catch Exception e
      (log/error "Bad address:" (.getMessage e)))
    ))

(defn address-peer-filter [addresses]
  (reify
    PeerFilterProvider
    (beginBloomFilterCalculation [this]
      (log/info "Begin bloom filter calculation"))
    (endBloomFilterCalculation [this]
      (log/info "End bloom filter calculation"))
    (getBloomFilter [this size falsePositiveRate nTweak]
      (log/info "get bloom filter")
      (let [bloom (BloomFilter. size falsePositiveRate nTweak)]
        (doseq [address addresses]
          (.insert bloom (.getHash160 address)))
        bloom))
    (getBloomFilterElementCount [this] (count addresses))
    (getEarliestKeyCreationTime [this] 0)
    (isRequiringUpdateAllBloomFilter [this] false)))

(defn peer-event-listener [params]
  (proxy [AbstractPeerEventListener] []
    (onTransaction [peer transaction]
      (log/info "PEER TRANSACTION:" (.getHash transaction))
      (doseq [output (.getOutputs transaction)]
        (log/info "OUTPUT value:"  (.getValue output))
        (log/info "OUTPUT:"  (.toString output)))
      )))

(defn blockchain-event-listener [params]
  (proxy [AbstractBlockChainListener] []
    (receiveFromBlock [transaction block block-type relativity-offset]
      (log/info "BLOCK TRANSACTION:" (.getHash transaction))
      (doseq [output (.getOutputs transaction)]
        (log/info "OUTPUT value:"  (.getValue output))
        (log/info "OUTPUT:"  (.toString output)))
      )))

; default namespace key: 1GzjTsqp3LASxLsEd1vsKiDHTuPa2aYm5G

(defn -main
  "Starting a key.run server"
  [& [address network-type]]
  (if (or (= "help" address) (nil? address))
    (usage)
    (let [params (network-params network-type)
          network-prefix (file-prefix params)
          namespace-address (string->Address address params) ; TODO check nil
          peer-filter (address-peer-filter [namespace-address])
          peer-listener (peer-event-listener params)
          blockchain-listener (blockchain-event-listener params)
          blockstore-file (clojure.java.io/file (str "./" network-prefix ".blockstore"))
          blockstore (SPVBlockStore. params blockstore-file) ; TODO load checkpoint
          blockchain (BlockChain. params blockstore)
          peer-group (PeerGroup. params blockchain)
          ]

      (.addListener blockchain blockchain-listener)

      (log/info "Namespace address:" (.toString namespace-address))

      (log/info "Starting peer group...")
      (doto peer-group
        (.setUserAgent "key.run", "0.1")
        (.addPeerDiscovery (DnsDiscovery. params))
        (.addPeerFilterProvider peer-filter)
        (.addEventListener peer-listener)
        ; (.setFastCatchupTime) ; TODO set to start of key.run
        (.start)
        (.downloadBlockChain))

      (while (not= "q" (clojure.string/lower-case (read-line))))

      )))

(def k "1GzjTsqp3LASxLsEd1vsKiDHTuPa2aYm5G")
(def params (network-params :default))
(def ka (string->Address k params))

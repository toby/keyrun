(ns keyrun.bitcoin
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component])
  (:import
    (org.bitcoinj.core NetworkParameters
                       Address
                       Transaction
                       PeerGroup
                       BlockChain
                       Utils
                       PeerFilterProvider
                       BloomFilter
                       AbstractPeerEventListener
                       AbstractBlockChainListener
                       DownloadProgressTracker)
    (org.bitcoinj.store SPVBlockStore)
    (org.bitcoinj.net.discovery DnsDiscovery)
    (org.bitcoinj.params TestNet3Params RegTestParams MainNetParams)
    (org.bitcoin.protocols.payments Protos$PaymentRequest Protos$PaymentDetails Protos$Output)
    (org.bitcoinj.script ScriptBuilder ScriptOpCodes)
    (com.google.protobuf ByteString)
    ))

(defrecord BitcoinServer [network-type namespace-address])

(defprotocol BitcoinMultiNet
  (network-params [this] "Return the correct bitcoinj network params object.")
  (file-prefix [this] "Return a file prefix for this network type."))

(defn make-payment-request [address message]
  (let [min-output-amount (-> Transaction/MIN_NONDUST_OUTPUT (.getValue))
        address-script (ScriptBuilder/createOutputScript address)
        address-output (-> (Protos$Output/newBuilder)
                           (.setAmount min-output-amount)
                           (.setScript (ByteString/copyFrom (.getProgram address-script)))
                           (.build))
        data-script (ScriptBuilder/createOpReturnScript (.getBytes message))
        data-output (-> (Protos$Output/newBuilder)
                           (.setAmount min-output-amount)
                           (.setScript (ByteString/copyFrom (.getProgram data-script)))
                           (.build))
        payment-details (-> (Protos$PaymentDetails/newBuilder)
                            (.addAllOutputs [address-output data-output])
                            (.setTime (System/currentTimeMillis))
                            (.setPaymentUrl "http://key.run:9090/kr/message/payment")
                            (.setMemo "key.run transaction")
                            (.build))
        payment-request (-> (Protos$PaymentRequest/newBuilder)
                            (.setSerializedPaymentDetails (.toByteString payment-details))
                            (.setSignature (ByteString/copyFrom (.getBytes "none")))
                            (.build))]
    payment-request))

(defn string->Address [address params]
  (try
    (Address. params address)
    (catch Exception e
      (log/error "Bad address:" (.getMessage e)))))

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
    (isTransactionRelevant [transaction]
      (log/info "relevant?" transaction))
    (notifyTransactionIsInBlock [tx-hash block block-type relativity-offset]
      (log/info "TRANSACTION" tx-hash "is in block" block))
    (receiveFromBlock [transaction block block-type relativity-offset]
      (log/info "BLOCK TRANSACTION:" (.getHash transaction))
      (doseq [output (.getOutputs transaction)]
        (log/info "OUTPUT value:"  (.getValue output))
        (log/info "OUTPUT:"  (.toString output)))
      )))

(defn download-progress-tracker []
  (proxy [DownloadProgressTracker] []
    (onChainDownloadStarted [peer blocks-left]
      (log/info "Start download of" blocks-left "from blockchain."))
    (doneDownload []
      (log/info "Done downloading blocks."))
    (progress [percent blocks-so-far date]
      (log/info "Downloaded" (str percent "%") "of" blocks-so-far "blocks."))
    (onBlocksDownloaded [peer block filtered-block blocks-left]
      (when (and (not= 0 blocks-left) (= 0 (mod blocks-left 1000)))
        (log/info blocks-left "blocks left."))
      )))

(defmulti params-for-string identity)
(defmethod params-for-string "testnet" [_] (TestNet3Params/get))
(defmethod params-for-string "regtest" [_] (RegTestParams/get))
(defmethod params-for-string :default [_] (MainNetParams/get))

(extend-type BitcoinServer
  BitcoinMultiNet
  (network-params [this]
    (params-for-string (:network-type this)))
  (file-prefix [this]
    (condp = (:network-type this)
      "testnet" "testnet"
      "regtest" "regtest"
      "production"))
  component/Lifecycle
  (start [this]
    (log/info "Starting bitcoin")
    (let [params (network-params this)
          network-prefix (file-prefix this)
          address (string->Address (:namespace-address this) params) ; TODO check nil
          peer-filter (address-peer-filter [address])
          peer-listener (peer-event-listener params)
          blockchain-listener (blockchain-event-listener params)
          blockstore-file (clojure.java.io/file (str "./" network-prefix ".blockstore"))
          blockstore (SPVBlockStore. params blockstore-file) ; TODO load checkpoint
          blockchain (BlockChain. params blockstore)
          peer-group (PeerGroup. params blockchain)]
      (.addListener blockchain blockchain-listener)
      (log/info "Starting peer group...")
      (doto peer-group
        (.setUserAgent "key.run", "0.1")
        (.clearEventListeners)
        (.addEventListener peer-listener)
        (.addEventListener (download-progress-tracker))
        (.addPeerDiscovery (DnsDiscovery. params))
        (.addPeerFilterProvider peer-filter)
        ; (.setFastCatchupTime) ; TODO set to start of key.run
        (.start)
        (.downloadBlockChain))
      (log/info "Done downloading blockchain")
      this))
  (stop [this]
    (log/info "Stopping bitcoin")
    this))
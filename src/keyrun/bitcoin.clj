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

(defprotocol BitcoinMultiNet
  (network-params [this] "Return the correct bitcoinj network params object.")
  (file-prefix [this] "Return a file prefix for this network type."))

(defn make-payment-request [address message]
  (let [min-output-amount (-> Transaction/MIN_NONDUST_OUTPUT (.getValue))
        min-output-amount (* 2 min-output-amount)
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
                            ;(.setPaymentUrl "http://key.run:9090/kr/message/payment")
                            (.setMemo (str "key.run `magnet:?xt=urn:btih:" message "`"))
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
    (getBloomFilter [this size falsePositiveRate nTweak]
      (let [bloom (BloomFilter. size falsePositiveRate nTweak)]
        (doseq [address addresses]
          (.insert bloom (.getHash160 address)))
        bloom))
    (getBloomFilterElementCount [this] (count addresses))
    (getEarliestKeyCreationTime [this] 0)
    (isRequiringUpdateAllBloomFilter [this] false)
    (beginBloomFilterCalculation [this])
    (endBloomFilterCalculation [this])))

(defn- extract-keyrun-data [script-chunks]
  (let [result (reduce (fn [{:keys [last-chunk] :as v} script-chunk]
                         (if (and (not (nil? last-chunk))
                                  (.equalsOpCode (:last-chunk v) 106)
                                  (= 40 (count (.data script-chunk))))
                           (-> v
                               (assoc :last-chunk script-chunk)
                               (assoc :data (String. (.data script-chunk))))
                           (assoc v :last-chunk script-chunk)))
                       {:last-chunk nil :data nil}
                       script-chunks)]
    (:data result)))

(defn get-keyrun-output [output]
  (-> {}
      (assoc :value (.getValue (.getValue output)))
      (assoc :data (extract-keyrun-data (.getChunks (.getScriptPubKey output))))))

(defn get-keyrun-transaction [transaction]
  (let [outputs (map get-keyrun-output (.getOutputs transaction))
        keyrun-output (first (filter :data outputs))]
    ; TODO use KeyrunTransaction record
    (when keyrun-output
      (merge keyrun-output
             {:tx_hash (.getHashAsString transaction)
              :mined (not (.isPending transaction))}))))

(defn handle-transaction [db transaction upsert]
  (let [keyrun-transaction (get-keyrun-transaction transaction)]
    (when keyrun-transaction
      (if upsert
        (.upsert-keyrun-transaction db keyrun-transaction)
        (try
          (.insert-keyrun-transaction db keyrun-transaction)
          (catch Exception e
            (log/info "Transaction" (:tx_hash keyrun-transaction) "already exists."))))
      (log/info "Transaction" (:tx_hash keyrun-transaction) "Pending?" (.isPending transaction))
      keyrun-transaction)))

(defn peer-event-listener [db params]
  (proxy [AbstractPeerEventListener] []
    (onTransaction [peer transaction]
      (log/info "Found peer group transaction")
      (handle-transaction db transaction false))))

(defn blockchain-event-listener [db params]
  (proxy [AbstractBlockChainListener] []
    (isTransactionRelevant [transaction]
      (log/info "Found blockchain transaction")
      (if (some? (handle-transaction db transaction true))
        true
        false))
    (notifyTransactionIsInBlock [tx-hash block block-type relativity-offset]
      (log/info "Transaction" tx-hash "is in block" block))
    (receiveFromBlock [transaction block block-type relativity-offset]
      (log/info "receiveFromBlock" (.getHashAsString transaction)))))

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

(defrecord BitcoinServer [network-type namespace-address db])

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
    (log/info "Connecting to Bitcoin network")
    (let [params (network-params this)
          network-prefix (file-prefix this)
          address (string->Address (:namespace-address this) params) ; TODO check nil
          peer-filter (address-peer-filter [address])
          peer-listener (peer-event-listener (:db this) params)
          blockchain-listener (blockchain-event-listener (:db this) params)
          blockstore-file (clojure.java.io/file (str "./" network-prefix ".blockstore"))
          blockstore (SPVBlockStore. params blockstore-file) ; TODO load checkpoint
          blockchain (BlockChain. params blockstore)
          peer-group (PeerGroup. params blockchain)]
      (.addListener blockchain blockchain-listener)
      (log/info "Starting peer group...")
      (doto peer-group
        (.setUserAgent "key.run", "0.1")
        ;(.clearEventListeners)
        (.addEventListener peer-listener)
        ;(.addEventListener (download-progress-tracker))
        (.addPeerDiscovery (DnsDiscovery. params))
        (.addPeerFilterProvider peer-filter)
        ;(.setFastCatchupTimeSecs 1446379200) ; near the birth of key.run
        (.start)
        (.downloadBlockChain))
      (log/info "Done downloading blockchain")
      this))
  (stop [this]
    (log/info "Disconnect from Bitcoin network")
    this))

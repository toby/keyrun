(ns keyrun.core
  (:gen-class)
  (:import
    (org.bitcoinj.core NetworkParameters Address ECKey)
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

(defmethod file-prefix TestNet3Params [-]
  "forwarding-service-testnet")

(defmethod file-prefix RegTestParams [-]
  "forwarding-service-regtest")

(defmethod file-prefix MainNetParams [-]
  "forwarding-service")

(defn parse-address [s params]
  (try
    (Address. params s)
    (catch Exception e
      (println "Bad address:" (.getMessage e)))
    ))

(defn -main
  "Starting a key.run server"
  [& [address network-type]]
  (if (or (= "help" address) (nil? address))
    (usage)
    (let [params (network-params network-type)
          fp (file-prefix params)
          forwarding-address (parse-address address params)
          ]
      (println "Forwarding address:" (.toString forwarding-address))
      (println "New key:" (.getPrivateKeyAsWiF (ECKey.) params))
      )))

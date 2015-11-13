(ns keyrun.core
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [keyrun.bitcoin :as bitcoin]
            [keyrun.ring :refer [wrap-logging-basic wrap-root-index binary-response]]
            [com.stuartsierra.component :as component]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.response :refer [response header content-type not-found]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.resource :refer [wrap-resource]]
            ))

(defn usage []
  (println "Usage: namespace-address [regtest|testnet]"))

(defmulti router :uri)

(defmethod router "/kr/message/payreq" [request]
  (log/info "PAYMENT REQUEST" (:params request))
  (let [payment-request (bitcoin/make-payment-request "1GzjTsqp3LASxLsEd1vsKiDHTuPa2aYm5G")]
    (binary-response (.toByteArray payment-request) "application/bitcoin-paymentrequest")))

(defmethod router "/kr/message/pay" [request]
  (log/info "PAYMENT" (:params request))
  (-> (response nil)
      (header "content-type" "application/bitcoin-payment")))

(defmethod router "/kr/message/payack" [request]
  (log/info "PAYMENT ACK" (:params request))
  (-> (response nil)
      (header "content-type" "application/bitcoin-paymentack")))

(defmethod router :default [request]
  (not-found "404 - That's not here!"))

(def default-app (-> router
                     (wrap-resource "public")
                     wrap-root-index
                     wrap-keyword-params
                     wrap-params
                     wrap-logging-basic))

(defrecord WebServer [app port]
  component/Lifecycle
  (start [this]
    (log/info "Starting web server")
    (run-jetty app {:port (Integer. port)}))
  (stop [this]
    (log/info "Stopping web server")))

(defn new-system [network-type namespace-address port]
  (component/system-map
    :bitcoin-server
    (bitcoin/->BitcoinServer network-type namespace-address)
    :web-server
    (component/using (WebServer. default-app port) [:bitcoin-server])))

(defn start-keyrun [network-type namespace-address port]
  (try
    (log/info "Starting key.run")
    (log/info "Namespace address:" (.toString namespace-address))
    (component/start (new-system network-type namespace-address port))
    (catch Exception e
      (log/error (.getMessage e)))))

; default namespace key: 1GzjTsqp3LASxLsEd1vsKiDHTuPa2aYm5G

(defn -main
  "Starting a key.run server"
  [& [namespace-address network-type]]
  (if (or (= "help" namespace-address) (nil? namespace-address))
    (usage)
    (do
      (start-keyrun network-type namespace-address 9090)
      (while (not= "q" (clojure.string/lower-case (read-line)))))))


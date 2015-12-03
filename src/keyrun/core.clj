(ns keyrun.core
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [keyrun.bitcoin :refer :all]
            [keyrun.db :as db]
            [keyrun.ring :refer [wrap-logging-basic wrap-root-index binary-response]]
            [com.stuartsierra.component :as component]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.response :refer [response header content-type not-found]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.resource :refer [wrap-resource]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [clostache.parser :refer [render-resource]]
            [clojure.java.io :as io]
            ))

(def host-name (or (System/getenv "KEYRUN_HOST") (.getCanonicalHostName (java.net.InetAddress/getLocalHost))))

(defn usage []
  (println "Usage: namespace-address [regtest|testnet]"))

(def templates-dir "templates/")

(defn render-page
  ([template data] (render-page template data nil))
  ([template data partials]
   (render-resource
     (str templates-dir template)
     data
     (reduce #(assoc %1 %2 (-> (str templates-dir (name %2) ".html")
                               io/resource
                               slurp))
             {}
             partials))))

(defn router [bitcoin-server]
  (defroutes app
    (GET "/index.html" []
         (render-page "index.html" {:transactions (-> bitcoin-server :db (.get-btih-transactions))
                                    :namespace-address (:namespace-address bitcoin-server) }))
    (GET "/kr/message/payreq" request
          (log/info "PAYMENT REQUEST" (:params request))
          (let [params (:params request)
                to-address (string->Address (:namespace-address bitcoin-server) (network-params bitcoin-server))
                payment-request (make-payment-request to-address (:message params))]
            (binary-response (.toByteArray payment-request) "application/bitcoin-paymentrequest")))

    (POST "/kr/message/pay" request
          (log/info "PAYMENT" (:params request))
          (-> (response nil)
              (header "content-type" "application/bitcoin-payment")))

    (POST "/kr/message/payack" request
          (log/info "PAYMENT ACK" (:params request))
          (-> (response nil)
              (header "content-type" "application/bitcoin-paymentack")))

    (route/not-found "404 - That's not here!")))

(defn default-app [bitcoin-server]
  (-> (router bitcoin-server)
      (wrap-resource "public")
      wrap-root-index
      wrap-keyword-params
      wrap-params
      wrap-logging-basic))

(defrecord WebServer [port network-type bitcoin-server]
  component/Lifecycle
  (start [this]
    (log/info "Starting web server")
    (log/info "Namespace address:" (-> bitcoin-server :namespace-address))
    (run-jetty (default-app bitcoin-server) {:port (Integer. port)}))
  (stop [this]
    (log/info "Stopping web server")))

(defn new-system [network-type namespace-address port]
  (component/system-map
    :db
    (db/get-sqlite-db "keyrun.db")
    :bitcoin-server
    (component/using (map->BitcoinServer {:network-type network-type
                                          :namespace-address namespace-address})
                     [:db])
    :web-server
    (component/using (map->WebServer {:port port
                                      :network-type network-type})
                     [:bitcoin-server])))

(defn start-keyrun [network-type namespace-address port]
  (try
    (log/info "Starting key.run")
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


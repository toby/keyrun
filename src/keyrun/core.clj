(ns keyrun.core
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [keyrun.bitcoin :refer :all]
            [keyrun.web :refer [render-page map->WebServer Routing]]
            [keyrun.db :as db]
            [com.stuartsierra.component :as component]
            [ring.util.response :refer [response header content-type not-found]]
            [keyrun.ring :refer [binary-response]]
            [compojure.route :as route]
            [compojure.core :refer [defroutes GET POST]]
            )
  (:import [keyrun.web WebServer]))

(extend-type WebServer
  Routing
  (define-router [this]
    (let [bitcoin-server (:bitcoin-server this)]
      (defroutes app-router
        (GET "/index.html" []
             (render-page "index.html" {:transactions (-> bitcoin-server :db (.get-keyrun-transactions))
                                        :namespace-address (:namespace-address bitcoin-server)}
                          [:header :footer]))
        (GET "/btih/:btih" [btih]
             (let [transactions (-> bitcoin-server :db (.get-btih-transactions btih))]
               (render-page "btih.html" {:btih (-> transactions first :data)
                                         :transactions transactions
                                         :namespace-address (:namespace-address bitcoin-server)}
                            [:header :footer])))
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

        (route/not-found "404 - That's not here!")))))

(defn new-system [network-type namespace-address port]
  (component/system-map
    :db
    (db/get-sqlite-db "keyrun.db")

    :bitcoin-server
    (component/using (map->BitcoinServer {:network-type network-type
                                          :namespace-address namespace-address})
                     [:db])

    :web-server
    (component/using (map->WebServer {:port port})
                     [:bitcoin-server])))

(defn start-keyrun [network-type namespace-address port]
  (try
    (log/info "Starting key.run")
    (component/start (new-system network-type namespace-address port))
    (catch Exception e
      (log/error (.getMessage e)))))

(defn -main
  "Starting a key.run server"
  [& [namespace-address network-type]]
  (let [namespace-address (or namespace-address "1NUysE6fnJNhiUGJbWE2wP8T7AFCtRAVs4")
        port (Integer. (or (System/getenv "KEYRUN_HTTP_PORT") 9090))]
    (start-keyrun network-type namespace-address port)
    (while (not= "q" (clojure.string/lower-case (read-line))))))


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
            [compojure.core :refer [routes GET POST]])
  (:import [keyrun.web WebServer]))

(extend-type WebServer
  Routing
  (get-router [this]
    (let [bitcoin-server (:bitcoin-server this)
          db (-> this :bitcoin-server :db)
          namespace-address (:namespace-address bitcoin-server)]
      (routes
        (GET "/index.html" []
             (render-page "index.html" {:transactions (.get-keyrun-transactions db)
                                        :namespace-address namespace-address}
                          [:header :footer]))

        (GET "/btih/:btih" [btih]
             (let [transactions (.get-btih-transactions db btih)]
               (render-page "btih.html" {:btih (-> transactions first :data)
                                         :transactions transactions
                                         :namespace-address namespace-address}
                            [:header :footer])))

        (GET "/kr/message/payreq" request
             (log/info "PAYMENT REQUEST" (:params request))
             (let [params (:params request)
                   to-address (string->Address namespace-address (network-params bitcoin-server))
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

(defn new-system [namespace-address port network-type]
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

(defn start-keyrun [namespace-address port network-type]
  (try
    (component/start (new-system namespace-address port network-type))
    (catch Exception e
      (log/error (.getMessage e)))))

(defn -main [& [namespace-address]]
  (let [namespace-address (or namespace-address "1NUysE6fnJNhiUGJbWE2wP8T7AFCtRAVs4")
        port (Integer. (or (System/getenv "KEYRUN_HTTP_PORT") 9090))
        network-type (or (System/getenv "KEYRUN_BITCOIN_NETWORK") "production")]
    (start-keyrun namespace-address port network-type)
    (while (not= "q" (clojure.string/lower-case (read-line))))))


(ns keyrun.web
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [clostache.parser :refer [render-resource]]
            [keyrun.ring :refer [wrap-logging-basic wrap-root-index]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.resource :refer [wrap-resource]]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [ring.adapter.jetty :refer [run-jetty]]
            ))

(def templates-dir "templates/")

(defn render-page
  ([template data] (render-page template data nil))
  ([template data partials]
   (render-resource
     (str templates-dir template)
     (merge data {:commas (fn [t] (fn [f] (format "%,d" (Integer. (f t)))))})
     (reduce #(assoc %1 %2 (-> (str templates-dir (name %2) ".html")
                               io/resource
                               slurp))
             {}
             partials))))

(defprotocol Routing
  (get-router [this]))

(defn default-app [router]
  (-> router
      (wrap-resource "public")
      wrap-root-index
      wrap-keyword-params
      wrap-params
      wrap-logging-basic))

(defrecord WebServer [port bitcoin-server]
  component/Lifecycle
  (start [this]
    (log/info "Starting web server")
    (run-jetty (default-app (get-router this)) {:port (Integer. port)}))
  (stop [this]
    (log/info "Stopping web server")))


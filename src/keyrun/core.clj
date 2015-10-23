(ns keyrun.core
  (:gen-class)
  (:require [bitcoin.core :as bc]
    ))

(defn -main
  "Starting a key.run server"
  [& args]
  (println "Enter your namespace Bitcoin address:"))

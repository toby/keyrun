(defproject keyrun "0.3.1-SNAPSHOT"
  :description "key.run server"
  :url "http://key.run"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.slf4j/slf4j-log4j12 "1.7.12"]
                 [log4j/log4j "1.2.17"]
                 [com.stuartsierra/component "0.3.0"]
                 [ring "1.4.0"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [org.clojure/java.jdbc "0.4.2"]
                 [org.xerial/sqlite-jdbc "3.7.2"]
                 [yesql "0.5.1"]
                 [org.bitcoinj/bitcoinj-core "0.13.3"]
                 [compojure "1.4.0"]
                 [de.ubercode.clostache/clostache "1.4.0"]]
  :main ^:skip-aot keyrun.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

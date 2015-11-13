(ns keyrun.ring
  (:require [clojure.tools.logging :as log]
            [ring.util.response :refer [response header content-type]]))

(defn binary-response [b-array content-type-header]
  (-> (response (new java.io.ByteArrayInputStream b-array))
      (content-type content-type-header)
      (header "Content-Length" (count b-array))))

(defn wrap-root-index [handler]
  (fn [{:keys [uri] :as request}]
    (if (or (= "" uri)
            (= "/" uri))
      (handler (assoc request :uri "/index.html"))
      (handler request))))

(defn- get-remote-host [headers remote-addr]
  (or (get headers "x-forwarded-for") remote-addr))

(defn wrap-logging-basic [handler]
  (fn [{:keys [headers remote-addr request-method uri] :as request}]
    (let [remote (get-remote-host headers remote-addr)]
      (log/info remote (clojure.string/upper-case (name request-method)) uri (get headers "user-agent"))
      (handler request))))

(defn wrap-logging-combined [handler]
  (fn [{:keys [headers remote-addr request-method uri] :as request}]
    (try
      (let [host (get-remote-host headers remote-addr)
            ident "-"
            user-name "-"
            response-time (.format (java.text.SimpleDateFormat. "dd/MMM/yyyy:HH:mm:ss Z") (java.util.Date.))
            response-time (str "[" response-time "]")
            request-string (str "\"" (-> request-method name clojure.string/upper-case) " " uri " HTTP/1.1\"")
            referer (or (get headers "referer") "-")
            referer (if-not (= "-" referer) (str "\"" referer "\"") referer)
            user-agent (or (get headers "user-agent") "-")
            user-agent (if-not (= "-" user-agent) (str "\"" user-agent "\"") user-agent)
            {:keys [headers status] :as response} (handler request)
            content-length (or (get headers "Content-Length") "-")]
        (log/info host ident user-name response-time request-string status content-length referer user-agent)
        response)
      (catch Exception e
        (log/error (.getMessage e))))))

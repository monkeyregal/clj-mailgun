(ns mailgun-test
  (:require [cheshire.core :as json]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [io.pedestal.http :as server]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.ring-middlewares :as ring-middle]
            [io.pedestal.http.route.definition :refer [defroutes expand-routes]]
            [io.pedestal.interceptor.helpers :as interceptor]
            [mailgun :as mg]
            [mailgun.webhooks :as wh]
            [ring.util.response :as ring-resp]))

;; bind *out* for repl logging of logging fixen :s
(defn general-hook [request]
  (log/info request)
 ;;
  (log/info "Is valid? " (:valid-hook? request))

  (ring-resp/response "ok"))

(interceptor/defon-request keywordize-form [request]
  (cond-> request
    (:form-params request)
    (update-in [:form-params] clojure.walk/keywordize-keys)
    (:multipart-params request)
    (update-in [:multipart-params] clojure.walk/keywordize-keys)
    (:params request)
    (update-in [:params] clojure.walk/keywordize-keys)))

(def multipart-interceptor (ring-middle/multipart-params))
(def body-params-interceptor (body-params/body-params (body-params/default-parser-map :json-options {:key-fn keyword})))

(defn make-hook-validator-interceptor
  "creates interceptor that injects mailgun component to pedestal's context"
  [mailgun]
  (interceptor/on-request
   ::valid-mailgun-hook-injector
   (fn [request]
     (let [payload (or (:form-params request) (:params request))]
       (assoc request :valid-hook? (wh/is-valid? mailgun payload))))))

(defn make-webhook-routes [mailgun]
  (let [hook-validator (make-hook-validator-interceptor mailgun)]
    (expand-routes
     `[[["/" ^:interceptors [body-params-interceptor multipart-interceptor keywordize-form]
        ["/hook" {:post [:hook general-hook]} ^:interceptors [~hook-validator]]]]])))

(defn make-webhook-service [routes]
  {:env :dev
   ::server/routes routes
   ::server/type :jetty
   ::server/port 8080})

(defn start-server [server]
  (server/start server))

(defn stop-server [server]
  (when server
    ((:stop server))
    (server/stop server))
  server)

(defn create-server []
  (let [mailgun (mg/create)
        routes (make-webhook-routes mailgun)
        service (make-webhook-service routes)
        server (server/create-server service #())
        server (assoc server :stop #(mg/close mailgun))]
    server))

(defn webhook-bindings [host]
  {:bounce (str host "/hook")
   :deliver (str host "/hook")
   :drop (str host "/hook")
   :spam (str host "/hook")
   :unsubscribe (str host "/hook")
   :click (str host "/hook")
   :open (str host "/hook")})

(defn initialize-mailgun []
  (let [mailgun (mg/create "sandbox574bd4355310413c8dcdae96d02cbb6e.mailgun.org")
        current (:webhooks (wh/all mailgun))
        req-bindings (webhook-bindings "http://mg.0x7.be")]
    (doseq [[binding url] req-bindings]
      (if (contains? current binding)
        (wh/update mailgun binding url)
        (wh/create mailgun binding url)))
    (mg/close mailgun)))

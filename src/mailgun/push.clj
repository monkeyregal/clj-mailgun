(ns mailgun.push
  (:require [clojure.tools.logging :as log]
            [io.pedestal.http :as server]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.ring-middlewares :as ring-middle]
            [io.pedestal.http.route.definition :refer [defroutes expand-routes]]
            [io.pedestal.interceptor.helpers :as interceptor]
            [mailgun [core :as core] [webhooks :as hooks] [routes :as routes]]))

(defonce webhook-config
  {:bounce "/bounced"
   :deliver "/delivered"
   :drop "/dropped"
   :spam "/spammed"
   :unsubscribe "/unsubscribed"
   :click "/clicked"
   :open "/opened"})

(defonce routes-hook-config
  {:incoming "/incoming"})

(defn routes-config [hook-host domain]
  {:catch-all
   {:priority 10
    :filter (routes/recipient-filter (str ".*@" domain))
    :actions [(routes/store-action)
              (routes/forward-action (str hook-host "/incoming"))
              (routes/stop-action)]}})

(deftype LoggingConsumer []
    core/Consumer
  (push [_ type data]
    (log/info type " - " (:Message-Id data))))

(interceptor/defon-request ^:private keywordize-form-interceptor [request]
  (cond-> request
    (:form-params request)
    (update-in [:form-params] clojure.walk/keywordize-keys)
    (:multipart-params request)
    (update-in [:multipart-params] clojure.walk/keywordize-keys)
    (:params request)
    (update-in [:params] clojure.walk/keywordize-keys)))

(def ^:private multipart-interceptor (ring-middle/multipart-params))
(def ^:private body-params-interceptor (body-params/body-params))

(defn ^:private create-validation-interceptor
  "creates interceptor that validates if mailgun sent the payload"
  [mailgun]
  (interceptor/on-request
   ::payload-validator
   (fn [request]
     (let [payload (or (:form-params request) (:params request))]
       (assoc request :valid-hook? (hooks/is-valid? mailgun payload))))))

(defn ^:private consumer [type consumers]
  (interceptor/handler
   ::consumer
   (fn [request]
     (when (:valid-hook? request)
       (doseq [c consumers]
         (core/push c type (or (:params request) (:form-params request)))))
     {:status 200
      :body "ok"
      :headers {"Content-Type" "text/plain"}})))

(defn ^:private create-webhook-routes [mailgun consumers]
  (let [hook-validator (create-validation-interceptor mailgun)]
    (expand-routes
     `[[["/" ^:interceptors [body-params-interceptor multipart-interceptor
                             keywordize-form-interceptor ~hook-validator]
         ;; webhooks
         ~@(map (fn [[k v]] [v {:post [k (consumer k consumers)]}])
                webhook-config)
         ;; route hooks
         ~@(map (fn [[k v]] [v {:post [k (consumer k consumers)]}])
                routes-hook-config)]]])))

(defn create-webhook-service [routes port]
  {:env :dev
   ::server/routes routes
   ::server/type :jetty
   ::server/port port})

(defn start-server [server]
  (server/start server))

(defn stop-server [server]
  (when server
    ((:stop server))
    (server/stop server))
  server)

(defn create-server
  ([mailgun port] (create-server mailgun port #()))
  ([mailgun port on-stop]
   (let [routes (create-webhook-routes mailgun [(->LoggingConsumer)])
         service (create-webhook-service routes port)
         server (server/create-server service #())
         server (assoc server :stop on-stop)]
     server)))

  ;;"http://mg.0x7.be"
(defn configure-webhooks
  ([mailgun hook-host]
   (let [current @(hooks/all mailgun)
         reqs (doall (map (fn [[hook path]]
                            (if (contains? current hook)
                              (hooks/update mailgun hook (str hook-host path))
                              (hooks/create mailgun hook (str hook-host path))))
                          webhook-config))]
     (doall (map deref reqs)))))

(defn configure-routes
  ([{domain :domain :as mailgun} hook-host]
   (let [required (routes-config hook-host domain)
         current @(routes/all mailgun)
         for-delete (map :id (filter #(.startsWith (:description %) domain) current))]
     ;; create first
     (->> (map (fn [[k v]] (apply routes/create
                                 mailgun
                                 (str domain k)
                                 (:priority v)
                                 (:filter v)
                                 (:actions v)))
               required)
          doall
          (map deref)
          doall)
     ;; then delete old
     (->> (map #(routes/delete mailgun %) for-delete)
          doall
          (map deref)
          doall))))

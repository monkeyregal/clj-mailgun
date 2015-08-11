(ns mailgun
  (:require [clojure.tools.logging :as log]
            [environ.core :refer [env]]
            [http.async.client :as http]
            [io.pedestal.http :as server]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.ring-middlewares :as ring-middle]
            [io.pedestal.http.route.definition :refer [defroutes expand-routes]]
            [io.pedestal.interceptor.helpers :as interceptor]
            [mailgun [webhooks :as hooks] [routes :as routes]]
            [ring.util.response :as ring-resp]))

(defn create
  ([] (create (env :mailgun-domain)))
  ([domain] (create domain (env :mailgun-private) (env :mailgun-public)))
  ([domain private-key public-key]
   {:private-key private-key
    :public-key public-key
    :domain domain
    :client (http/create-client :auth {:type :basic :user "api" :password private-key})
    :public-client (http/create-client :auth {:type :basic :user "api" :password public-key})}))

(defn close [{:keys [client public-client] :as mailgun}]
  (.close client)
  (.close public-client)
  mailgun)

(defonce webhook-config
  {:bounce "/bounced"
   :deliver "/delivered"
   :drop "/dropped"
   :spam "/spammed"
   :unsubscribe "/unsubscribed"
   :click "/clicked"
   :open "/opened"})

(defonce routes-webhook-config
  {:forward "/incoming"
   :store "/stored"})

(defn routes-config [hook-host domain]
  {:catch-all
   {:priority 10
    :filter (routes/recipient-filter (str ".*@" domain))
    :actions [(routes/store-action (str hook-host "/stored"))
              (routes/forward-action (str hook-host "/incoming"))
              (routes/stop-action)]}})

(defprotocol EventReceiver
  (new-event [_ type payload]))

(defprotocol MailReceiver
  (new-mail [_ from to subject date body-txt body-html]))

(defn general-hook [request]
  (log/info request)
 ;;
  (log/info "Is valid? " (:valid-hook? request))

  (ring-resp/response "ok"))

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

(defn ^:private create-hook-validator-interceptor
  "creates interceptor that injects mailgun component to pedestal's context"
  [mailgun]
  (interceptor/on-request
   ::valid-mailgun-hook-injector
   (fn [request]
     (let [payload (or (:form-params request) (:params request))]
       (assoc request :valid-hook? (hooks/is-valid? mailgun payload))))))

(defn ^:private create-webhook-routes [mailgun]
  (let [hook-validator (create-hook-validator-interceptor mailgun)]
    (expand-routes
     `[[["/" ^:interceptors [body-params-interceptor multipart-interceptor
                             keywordize-form-interceptor ~hook-validator]
         ~@(map (fn [[k v]] [v {:post [(keyword k) 'general-hook]}])
                webhook-config)
         ~@(map (fn [[k v]] [v {:post [(keyword k) 'general-hook]}])
                routes-webhook-config)]]])))

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

(defn create-server [port]
  (let [mailgun (create)
        routes (create-webhook-routes mailgun)
        service (create-webhook-service routes port)
        server (server/create-server service #())
        server (assoc server :stop #(close mailgun))]
    server))

  ;;"http://mg.0x7.be"
(defn configure-webhooks
  ([hook-host] (let [mg (create)]
                 (configure-webhooks mg hook-host)
                 (close mg)))
  ([mailgun hook-host]
   (let [current @(hooks/all mailgun)
         reqs (doall (map (fn [[hook path]]
                            (if (contains? current hook)
                              (hooks/update mailgun hook (str hook-host path))
                              (hooks/create mailgun hook (str hook-host path))))
                          webhook-config))]
     (doall (map deref reqs)))))

(defn configure-routes
  ([hook-host]
   (let [mg (create)]
     (configure-routes mg hook-host)
     (close mg)))
  ([{domain :domain :as mailgun} hook-host]
   (let [required (routes-config hook-host domain)
         current @(routes/all mailgun)
         delete (map :id (filter #(.startsWith (:description %) domain) current))
         del-actions (doall (map #(routes/delete mailgun %) delete))]
     (doall (map deref del-actions))
     (->> (map (fn [[k v]] (apply routes/create
                                 mailgun
                                 (str domain k)
                                 (:priority v)
                                 (:filter v)
                                 (:actions v)))
               required)
          doall
          (map deref)
          doall))))

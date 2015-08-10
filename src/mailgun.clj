(ns mailgun
  (:require [environ.core :refer [env]]
            [http.async.client :as http]))

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

(ns mailgun.routes
  (:require [mailgun.core :as core]))

(defn recipient-filter [pattern]
  (format "match_recipient(\"%s\")" pattern))

(defn header-filter [header pattern]
  (format "match_header(\"%s\",\"%s\")" header pattern))

(defn catch-all-filter []
  "catch_all()")

(defn forward-action [email-or-endpoint]
  (format "forward(\"%s\")" email-or-endpoint))

(defn store-action
  ([] "store(notify=\"\")")
  ([notification-endpoint]
   (format "store(notify=\"%s\")" notification-endpoint)))

(defn stop-action []
  "stop()")

(defn all [mailgun]
  (core/get mailgun "routes"))

(defn for-id [mailgun id]
  (core/get mailgun (str "routes/" id)))

(defn create [mailgun description priority filter actions]
  (let [action (if (sequential? actions) actions [actions])]
    (core/post mailgun "routes" :body {:description description
                                     :priority priority
                                     :expression filter
                                     :action actions})))

(defn delete [mailgun id]
  (core/delete mailgun (str "routes/" id)))

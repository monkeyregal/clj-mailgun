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
  ([] "store()")
  ([notification-endpoint]
   (format "store(notify=\"%s\")" notification-endpoint)))

(defn stop-action []
  "stop()")

(defn all [mailgun]
  (core/on-result (core/get mailgun (core/api-url "routes"))
                  :items))

(defn for-id [mailgun id]
  (core/on-result (core/get mailgun (core/api-url "routes" id))
                  :route))

(defn create [mailgun description priority filter & actions]
  (let [actions-v (into [] actions)]
    (core/on-result (core/post mailgun (core/api-url "routes")
                          :query {:description description
                                 :priority priority
                                 :expression filter
                                 :action actions-v
                                  })
                    :route)))

(defn delete [mailgun id]
  (core/on-result (core/delete mailgun (core/api-url "routes" id))))

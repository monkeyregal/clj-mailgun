(ns mailgun.stats
  (:require [mailgun.core :as core]))

(defn for-event [{:keys [domain] :as mailgun} event]
  (core/on-result (core/get mailgun (core/api-url domain "stats") :query {:event (name event)})))

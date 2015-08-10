(ns mailgun.stats
  (:require [mailgun.core :as core]))

(defn for-event [{:keys [domain] :as mailgun} event]
  (core/get mailgun (str domain "/stats") :query {:event (name event)}))

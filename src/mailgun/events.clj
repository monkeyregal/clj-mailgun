(ns mailgun.events
  (:require [mailgun.core :as core]))

(defonce event-types [:accepted :rejected :delivered :failed :opened :clicked :unsubscribed
                      :complained :stored])

;(defn ^:private)

(defn ^:private load-events [mailgun url & params]
  (let [result (apply core/get mailgun url params)]
    (core/on-result result
                    (fn [r] (when (not= 0 (count (:items r)))
                             (concat (:items r) (lazy-seq
                                                 @(load-events mailgun (-> r :paging :next))))))
                    (fn [_]))))

(defn ^:private paged-result [mailgun url query]
  (let [events (load-events mailgun url :query (assoc query :limit 300))
        eager (seque 900 @events)]
    eager))

(defn all [{:keys [domain] :as mailgun}]
  (paged-result mailgun (core/api-url domain "events") {}))

(defn for-event [{:keys [domain] :as mailgun} event]
  (paged-result  mailgun (core/api-url domain "events") {:event (name event)}))

(defn for-id [{:keys [domain] :as mailgun} id]
  (paged-result mailgun (core/api-url domain "events") {:message-id id}))

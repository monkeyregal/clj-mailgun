(ns mailgun.message
  (:refer-clojure :exclude [send])
  (:require [mailgun.core :as core]))

(defn ^:private from-param [from]
  (if (map? from)
    (str (:name from) " <" (:email from) ">")
    from))

;; {:id "<id@domain.org>", :message "Queued. Thank you."}

(defn send [{:keys [domain] :as mailgun} from to subject text html & user-vars]
  {:pre [(even? (count user-vars))]}
  (let [ukv (->> (partition 2 user-vars) (mapcat (fn [[k v]] [(str "v:" (name k)) v])))]
    (letfn [(grep-message-id [response] (second (re-find #"^.*<([^<>]*)>.*$"  (:id response))))]
      (core/on-result (core/post mailgun (core/api-url domain "messages")
                                 :body (apply assoc {:from (from-param from)
                                                     :to to
                                                     :subject subject
                                                     :text text
                                                     :html html
                                                     } ukv))
                      grep-message-id))))

(defn stored [{:keys [domain] :as mailgun} message-key]
  (core/sync (core/get mailgun (core/api-url "domains" domain "messages" message-key))))

;; {:address "foo@baz.com", :did_you_mean nil, :is_valid true, :parts {:display_name nil, :domain "baz.com", :local_part "foo"}}

(defn valid-email-address? [mailgun email-address]
  (core/on-result
   (core/public-get mailgun (core/api-url "address/validate") :query {:address email-address} )
  :is_valid))

;; {:parsed ["foo@baz.com"], :unparseable ["foo@@baz.com"]}

(defn parse [mailgun & addresses]
  (core/on-result (core/public-get mailgun (core/api-url "address/parse") :query {:addresses (clojure.string/join "," addresses)})))

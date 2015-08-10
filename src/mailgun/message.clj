(ns mailgun.message
  (:refer-clojure :exclude [send])
  (:require [mailgun.core :as core]))

(defn ^:private from-param [from]
  (if (map? from)
    (str (:name from) " <" (:email from) ">")
    from))

(defn send [{:keys [domain] :as mailgun} from to subject text html]
  (core/post mailgun (str domain "/messages") :body {:from (from-param from)
                                               :to to
                                               :subject subject
                                               :text text
                                               :html html}))

(defn validate [mailgun email-address]
  (core/public-get mailgun "address/validate" :query {:address email-address} ))

(defn parse [mailgun & addresses]
  (core/public-get mailgun "address/parse" :query {:addresses (clojure.string/join "," addresses)}))

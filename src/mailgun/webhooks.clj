(ns mailgun.webhooks
  (:refer-clojure :exclude [update])
  (:require [mailgun.core :as core])
  (:import [org.apache.commons.codec.binary Hex]
           [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec]))

(declare hmac-sha-256)

(defn ^:private webhook-path [mg & more]
  (str "domains/" (:domain mg) "/webhooks" (when (seq more) (str "/" (clojure.string/join "/" more)))))

(def all-names [:bounce :deliver :drop :spam :unsubscribe :click :open])

(defn all [mailgun]
  (core/get mailgun (webhook-path mailgun)))

(defn info [mailgun hook-name]
  (core/get mailgun (webhook-path mailgun (name hook-name))))

(defn create [mailgun hook-name endpoint]
  (core/post mailgun (webhook-path mailgun) :body {:id (name hook-name) :url endpoint}))

(defn update [mailgun hook-name endpoint]
  (core/put mailgun (webhook-path mailgun (name hook-name)) :body {:url endpoint}))

(defn delete [mailgun hook-name]
  (core/delete mailgun (webhook-path mailgun (name hook-name))))

(defn is-valid? [mailgun payload]
  (let [input (str (:timestamp payload) (:token payload))
        digest (hmac-sha-256 (:private-key mailgun) input)]
    (= digest (:signature payload))))

(defn hmac-sha-256
  [key-seq byte-seq]
  (let [hmac-key (SecretKeySpec. (byte-array (map byte key-seq)) "HmacSHA256")
        hmac (doto (Mac/getInstance "HmacSHA256") (.init hmac-key))
        digest (.doFinal hmac (byte-array (map byte byte-seq)))]
    (Hex/encodeHexString digest)))

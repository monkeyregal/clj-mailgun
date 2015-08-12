(ns mailgun.core
  (:refer-clojure :exclude [get sync])
  (:require [http.async.client :as http]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]))

(defonce api-base "https://api.mailgun.net/v3/")

(defprotocol Consumer
  (push [_ type payload])
  (pop  [_ type payload]))

(defn ^:private request [client request-fn
                      default-headers url
                      {:keys [headers] :or {headers {}} :as params}]
  (let [_ (log/debug "Requesting: " url " with params: " params)
        params (assoc params :headers (merge default-headers headers))
        resp (apply request-fn client url (apply concat params))]
    resp))

(defn ^:private to-json [resp]
  (binding [cheshire.parse/*use-bigdecimals?* true]
      (json/parse-string (http/string resp) true)))

(defn on-result
  "Returns a promise which delivers either (on-success body-as-json-map) for 200
  or (on-error Throwable) for not 200"
  ([response] (on-result response identity identity))
  ([response on-success] (on-result response on-success identity))
  ([response on-success on-error]
   (let [result-promise (promise)]
     (future
       (if-let [error (http/error response)]
         (deliver result-promise (on-error error))
         (let [status (http/status response)
               _ (http/await response)]
           (if (not= 200 (:code status))
             (deliver result-promise (on-error (Throwable. (str status))))
             (deliver result-promise (on-success (to-json response)))))))
     result-promise)))

(defn sync [response]
  @(on-result response identity identity))

(defmacro ^:private generate-method
"Generates request fn with name type"
([type] `(generate-method ~type {}))
([type default-headers]
 (let [fn-name (symbol (name type))
       fn-name-public (symbol (str "public-" (name type)))
       request-fn (symbol "http" (.toUpperCase (name type)))]
   `(do
     (defn ~fn-name [~'mailgun ~'request & ~'params]
        (request ~'(:client mailgun) ~request-fn ~default-headers ~'request ~'params))
     (defn ~fn-name-public [~'mailgun ~'request & ~'params]
        (request ~'(:public-client mailgun) ~request-fn ~default-headers ~'request ~'params))))))

(generate-method :get)
(generate-method :post {:Content-Type "application/x-www-form-urlencoded"})
(generate-method :put {:Content-Type "application/x-www-form-urlencoded"})
(generate-method :delete)

(defn api-url [& path]
  (str api-base (when (seq path) (clojure.string/join "/" path))))

(defn multipart-string [name value]
  {:type :string
   :name name
   :value (str value)})

(defn map-structure
  [m]
  (let [f-seq (fn [v]
                (if-let [fst (first v)]
                  (if (map? fst)
                    (conj (empty v) (into {} (map-structure fst)))
                    (conj (empty v) (symbol (.getSimpleName (type fst)))))
                  (empty v)))
        f-map (fn [[k v]]
            (if (map? v)
              [k v]
              (if (sequential? v)
                [k (f-seq v)]
                [k (symbol (.getSimpleName (type v)))])))]
    ;; only apply to maps
    (clojure.walk/postwalk (fn [x]
                             (if (map? x) (into {} (map f-map x))
                               x)) m)))

(ns mailgun.core
  (:refer-clojure :exclude [get])
  (:require [http.async.client :as http]
            [cheshire.core :as json]))

(defonce api-base "https://api.mailgun.net/v3/")

(defn ^:private request [client request-fn
                      default-headers request
                      {:keys [headers] :or {headers {}} :as params}]
  (let [params (assoc params :headers (merge default-headers headers))
        url (str api-base request)
        resp (apply request-fn client url (apply concat params))
       status (http/status resp)]
   (http/await resp)
   (json/parse-string (http/string resp) true)))

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

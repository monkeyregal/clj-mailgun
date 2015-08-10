(defproject clj-mailgun "0.1.0-SNAPSHOT"
  :description "Clojure mailgun binding"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[ch.qos.logback/logback-classic "1.1.3"]
                 [cheshire "5.5.0"]
                 [commons-codec "1.10"]
                 [environ "1.0.0"]
                 [http.async.client "0.6.0"]
                 [org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.slf4j/slf4j-api "1.7.12"]]
  :profiles {:dev
             {:resource-paths ["config"]
              :dependencies [[io.pedestal/pedestal.jetty "0.4.0"]
                             [io.pedestal/pedestal.service "0.4.0" :exclusions [org.slf4j/slf4j-api]]]}})

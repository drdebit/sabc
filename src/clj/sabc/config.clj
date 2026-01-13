(ns sabc.config
  (:require [environ.core :refer [env]]))

;; Database
(def db-uri (or (env :sabc-db-uri) "datomic:mem://sabc-db"))

;; Server
(def port (Integer. (or (env :port) 5000)))

;; CORS - comma-separated list of allowed origins
(def allowed-origins
  (if-let [origins (env :sabc-allowed-origins)]
    (set (clojure.string/split origins #","))
    #{"http://localhost:8080" "http://localhost:5000"}))

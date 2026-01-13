(ns sabc.db
  (:require [datomic.api :as d]
            [sabc.config :as config]))

;; Database connection - single source of truth
(defonce conn-atom (atom nil))

(defn ensure-db! []
  "Ensures database exists and returns connection."
  (when-not @conn-atom
    (d/create-database config/db-uri)
    (reset! conn-atom (d/connect config/db-uri)))
  @conn-atom)

(defn conn []
  "Returns the database connection, ensuring it exists."
  (ensure-db!))

(defn db []
  "Returns the current database value."
  (d/db (conn)))

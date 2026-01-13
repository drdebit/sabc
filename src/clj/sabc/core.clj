(ns sabc.core
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [sabc.config :as config]
            [sabc.schema :as schema]
            [sabc.story :as story]
            [sabc.rest :as rest]))

(defonce server (atom nil))

(defn init-db! []
  "Initialize database with schema and story data."
  (log/info "Initializing database...")
  (schema/install-schema!)
  (story/load-story!)
  (log/info "Database initialized."))

(defn start []
  (when-not @server
    (init-db!)
    (reset! server (rest/start-server))
    (log/info "Server started on port" config/port)))

(defn stop []
  (when @server
    (rest/stop-server @server)
    (reset! server nil)
    (log/info "Server stopped")))

(defn -main
  "Start the SABC server."
  [& args]
  (start)
  (log/info "SABC server running. Press Ctrl+C to stop."))

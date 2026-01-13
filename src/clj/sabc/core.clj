(ns sabc.core
  (:gen-class)
  (:require [datomic.api :as d]
            [sabc.rest :as rest]))

(def test-player {:name "player1"
                  :inventory #{:socks :sneakers}
                  :cooldude true})

(defn current-player []
  test-player)

(def can-do {:walk {:s "walk"
                    :r ['(true? (get (current-player) :cooldude))]}
             :run {:s "run!"
                   :r ['(contains? (get (current-player) :inventory) :sneakers)]}})

(defn check-restrictions [p a]
  (every? identity (map eval (get-in can-do [a :r]))))

(defonce server (atom nil))

(defn start []
  (when-not @server
    (reset! server (rest/start-server))
    (println "Server started on port 5000")))

(defn stop []
  (when @server
    (rest/stop-server @server)
    (reset! server nil)
    (println "Server stopped")))

(defn -main
  "Start the SABC server."
  [& args]
  (start)
  (println "SABC server running. Press Ctrl+C to stop."))

(ns sabc.rest
  (:gen-class)
  (:require [sabc.schema :as schema]
            [io.pedestal.http :as http]
            [environ.core :refer [env]]
            [datomic.api :as d]
            [sabc.story :as story]))

;; Utilties
(defn make-pin []
  (apply str (take 4 (repeatedly #(rand-int 10)))))
(defn uuid [] (.toString (java.util.UUID/randomUUID)))

;; Datomic
(def db-uri "datomic:mem://sabc-db")
(def conn (d/connect db-uri))

(defn new-game [{{email :email} :path-params}]
  (let [uuid (java.util.UUID/randomUUID)]
    (do @(d/transact conn [{:game/uuid uuid :game/location {:db/id [:story/tag :init]}}])
        @(d/transact conn [{:db/id [:user/email email] :user/current-game
                              {:db/id [:game/uuid uuid]}}])
        {:status 200 :body (story/entry-data :init)})))

(defn add-user [{{email :email} :path-params :as response}]
  (let [db (d/db conn)
        pin (make-pin)
        existing-pins (d/q '[:find ?pin
                    :in $ ?email
                     :where [?e :user/email ?email]
                     [?e :user/pin ?pin]]
                    db email)]
    (cond
      (empty? existing-pins) (do @(d/transact conn [{:user/email email
                                                     :user/pin pin}])
                                 {:status 200
                                  :body {:added? true
                                         :pin pin
                                         :location (:body (new-game response))
                                         :message (str "User " email " successfully added.")}})
      :else {:status 200 :body {:added? false
                                :pin (ffirst existing-pins)
                                :location  (story/entry-data
                                            (or (ffirst (d/q '[:find ?tag
                                                               :in $ ?email
                                                               :where [?e :user/email ?email]
                                                               [?e :user/current-game ?game]
                                                               [?game :game/location ?entry]
                                                               [?entry :story/tag ?tag]] db email)) :init))
                                :message (str "User " email " already exists.")}})))

(defn update-location [{{email :email
                         loc :loc} :path-params}]
  (let [db (d/db conn)
        loc (if (nil? loc) :init (keyword loc))
        user-game (ffirst (d/q '[:find ?uuid
                                 :in $ ?email
                                 :where [?e :user/email ?email]
                                 [?e :user/current-game ?game]
                                 [?game :game/uuid ?uuid]] db email))]
    @(d/transact conn [{:game/uuid user-game
                        :game/location {:db/id [:story/tag loc]}}])
    {:status 200 :body (story/entry-data loc)}))


(defn resume-game [{{email :email} :path-params :as passed}]
  (let [db (d/db conn)
        loc (ffirst (d/q '[:find ?tag
                           :where [?e :user/email email]
                           [?e :user/current-game ?game]
                           [?game :game/location ?entry]
                           [?entry :story/tag ?tag]] db))]
    {:status 200 :body "I hear you."}))

(defn rand-string [_]
  {:status 200 :body (str (rand-nth ["hi!" "hello!" "yo!" "salut!"]) "\n")})

(def routes #{["/" :get rand-string :route-name :rand-string]
              ["/update-loc/:email" :put update-location :route-name :start-location]
              ["/update-loc/:email/:loc" :put update-location :route-name :update-location]
              ["/add-user/:email" :put add-user :route-name :add-user]
              ["/new-game/:email" :put new-game :route-name :new-game]
              ["/resume-game/:email" :get resume-game :route-name :resume-game]})

(def service-map (-> {::http/routes routes
                      ::http/type   :immutant
                      ::http/host   "0.0.0.0"
                      ::http/join?  false
                      ::http/port (Integer. (or (env :port) 5000))
                      ::http/allowed-origins (constantly true)}))

(defn start-server []
  (-> service-map http/create-server http/start))

(defn stop-server [server]
  (http/stop server))

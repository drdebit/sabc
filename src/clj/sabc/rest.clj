(ns sabc.rest
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [io.pedestal.http :as http]
            [io.pedestal.interceptor :as interceptor]
            [datomic.api :as d]
            [sabc.config :as config]
            [sabc.db :as db]
            [sabc.story :as story]))

;; Utilities
(defn make-pin []
  (apply str (take 4 (repeatedly #(rand-int 10)))))

(defn valid-email? [email]
  "Validates email/username - allows either email format or simple alphanumeric username."
  (and (string? email)
       (>= (count email) 1)
       (re-matches #"^[a-zA-Z0-9._%+-@]+$" email)))

(defn valid-location? [loc]
  (and (string? loc)
       (re-matches #"^[a-zA-Z][a-zA-Z0-9]*$" loc)))

;; Response helpers
(defn json-response [status body]
  {:status status
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string body)})

(defn error-response [status message]
  (json-response status {:error message}))

;; PIN validation
(defn get-user-pin [email]
  (ffirst (d/q '[:find ?pin
                 :in $ ?email
                 :where [?e :user/email ?email]
                 [?e :user/pin ?pin]]
               (db/db) email)))

(defn validate-pin [email provided-pin]
  (let [stored-pin (get-user-pin email)]
    (and stored-pin (= stored-pin provided-pin))))

;; Auth interceptor for protected routes
(def auth-interceptor
  (interceptor/interceptor
   {:name ::auth
    :enter (fn [context]
             (let [email (get-in context [:request :path-params :email])
                   pin (get-in context [:request :headers "x-pin"])]
               (if (or (nil? (get-user-pin email))  ; User doesn't exist yet (for add-user)
                       (validate-pin email pin))
                 context
                 (assoc context :response (error-response 401 "Invalid PIN")))))}))

;; Handlers
(defn create-new-game!
  "Creates a new game for the user and returns the initial location data."
  [email]
  (let [uuid (java.util.UUID/randomUUID)]
    @(d/transact (db/conn) [{:game/uuid uuid :game/location {:db/id [:story/tag :init]}}])
    @(d/transact (db/conn) [{:db/id [:user/email email] :user/current-game
                             {:db/id [:game/uuid uuid]}}])
    (log/info "New game created for user:" email "uuid:" uuid)
    (story/entry-data :init)))

(defn new-game [{{email :email} :path-params}]
  (try
    (json-response 200 (create-new-game! email))
    (catch Exception e
      (log/error e "Failed to create new game for" email)
      (error-response 500 "Failed to create new game"))))

(defn add-user [{{email :email} :path-params}]
  (if-not (valid-email? email)
    (error-response 400 "Invalid email format")
    (try
      (let [pin (make-pin)
            existing-pins (d/q '[:find ?pin
                                 :in $ ?email
                                 :where [?e :user/email ?email]
                                 [?e :user/pin ?pin]]
                               (db/db) email)]
        (if (empty? existing-pins)
          (do
            @(d/transact (db/conn) [{:user/email email :user/pin pin}])
            (log/info "User created:" email)
            (json-response 201 {:added? true
                                :pin pin
                                :location (create-new-game! email)
                                :message (str "User " email " successfully added.")}))
          (let [existing-pin (ffirst existing-pins)
                location (or (ffirst (d/q '[:find ?tag
                                            :in $ ?email
                                            :where [?e :user/email ?email]
                                            [?e :user/current-game ?game]
                                            [?game :game/location ?entry]
                                            [?entry :story/tag ?tag]]
                                          (db/db) email))
                             :init)]
            (log/info "Existing user login:" email)
            (json-response 200 {:added? false
                                :pin existing-pin
                                :location (story/entry-data location)
                                :message (str "User " email " already exists.")}))))
      (catch Exception e
        (log/error e "Failed to add user" email)
        (error-response 500 "Failed to add user")))))

(defn update-location [{{email :email loc :loc} :path-params}]
  (if (and loc (not (valid-location? loc)))
    (error-response 400 "Invalid location format")
    (try
      (let [loc (if (nil? loc) :init (keyword loc))
            user-game (ffirst (d/q '[:find ?uuid
                                     :in $ ?email
                                     :where [?e :user/email ?email]
                                     [?e :user/current-game ?game]
                                     [?game :game/uuid ?uuid]]
                                   (db/db) email))]
        (if user-game
          (do
            @(d/transact (db/conn) [{:game/uuid user-game
                                     :game/location {:db/id [:story/tag loc]}}])
            (log/debug "Location updated for" email "to" loc)
            (json-response 200 (story/entry-data loc)))
          (error-response 404 "User or game not found")))
      (catch Exception e
        (log/error e "Failed to update location for" email)
        (error-response 500 "Failed to update location")))))

(defn resume-game [{{email :email} :path-params}]
  (try
    (let [loc (ffirst (d/q '[:find ?tag
                             :in $ ?email
                             :where [?e :user/email ?email]
                             [?e :user/current-game ?game]
                             [?game :game/location ?entry]
                             [?entry :story/tag ?tag]]
                           (db/db) email))]
      (if loc
        (json-response 200 (story/entry-data loc))
        (error-response 404 "No game found for user")))
    (catch Exception e
      (log/error e "Failed to resume game for" email)
      (error-response 500 "Failed to resume game"))))

;; Routes - auth-interceptor applied to protected routes
(def routes
  #{["/update-loc/:email" :put [auth-interceptor update-location] :route-name :start-location]
    ["/update-loc/:email/:loc" :put [auth-interceptor update-location] :route-name :update-location]
    ["/add-user/:email" :put add-user :route-name :add-user]
    ["/new-game/:email" :put [auth-interceptor new-game] :route-name :new-game]
    ["/resume-game/:email" :get [auth-interceptor resume-game] :route-name :resume-game]})

;; CORS configuration
(defn allowed-origin? [origin]
  (contains? config/allowed-origins origin))

(def service-map
  {::http/routes routes
   ::http/type :immutant
   ::http/host "0.0.0.0"
   ::http/join? false
   ::http/port config/port
   ::http/allowed-origins allowed-origin?})

(defn start-server []
  (log/info "Starting server on port" config/port)
  (log/info "Allowed origins:" config/allowed-origins)
  (-> service-map http/create-server http/start))

(defn stop-server [server]
  (http/stop server))

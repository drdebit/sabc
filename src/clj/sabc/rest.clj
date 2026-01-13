(ns sabc.rest
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [io.pedestal.http :as http]
            [io.pedestal.interceptor :as interceptor]
            [datomic.api :as d]
            [sabc.config :as config]
            [sabc.db :as db]
            [sabc.story :as story]
            [sabc.game-logic :as game-logic]
            [sabc.cases :as cases]
            [sabc.clues :as clues]
            [sabc.suspects :as suspects]))

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

;; Mystery Game Handlers

(defn get-cases [{{email :email} :path-params}]
  (try
    (let [available-cases (game-logic/get-available-cases email)
          progress (game-logic/get-user-progress email)]
      (json-response 200 {:cases available-cases
                          :progress progress}))
    (catch Exception e
      (log/error e "Failed to get cases for" email)
      (error-response 500 "Failed to get cases"))))

(defn start-case [{{email :email case-id :case-id} :path-params}]
  (try
    (let [case-kw (keyword case-id)
          result (game-logic/start-case! email case-kw)
          case-data (cases/get-case-by-id case-kw)]
      (log/info "Started case" case-id "for" email "culprit:" (:culprit-id result))
      (json-response 200 {:game-uuid (str (:game-uuid result))
                          :case case-data}))
    (catch Exception e
      (log/error e "Failed to start case" case-id "for" email)
      (error-response 500 "Failed to start case"))))

(defn get-game-status [{{email :email} :path-params}]
  (try
    (let [game-state (game-logic/get-user-game email)]
      (if game-state
        (let [case-id (get-in game-state [:game/case :case/id])
              case-clues (cases/get-case-clues case-id)
              case-suspects (cases/get-case-suspects case-id)
              can-accuse (game-logic/can-accuse? (:game/uuid game-state))]
          (json-response 200 {:game game-state
                              :clues case-clues
                              :suspects case-suspects
                              :can-accuse can-accuse}))
        (error-response 404 "No active game")))
    (catch Exception e
      (log/error e "Failed to get game status for" email)
      (error-response 500 "Failed to get game status"))))

(defn get-clue [{{email :email clue-id :clue-id} :path-params}]
  (try
    (let [clue-kw (keyword clue-id)
          game-state (game-logic/get-user-game email)
          game-clue-data (when-let [data-str (:game/clue-data game-state)]
                           (read-string data-str))
          ;; Use generated data if available, fall back to static
          clue (if game-clue-data
                 (clues/get-clue-with-generated-data clue-kw game-clue-data)
                 (clues/get-clue-by-id clue-kw))]
      (if clue
        (json-response 200 clue)
        (error-response 404 "Clue not found")))
    (catch Exception e
      (log/error e "Failed to get clue" clue-id)
      (error-response 500 "Failed to get clue"))))

(defn get-hint [{{email :email clue-id :clue-id} :path-params}]
  (try
    (let [clue-kw (keyword clue-id)
          hint (clues/get-hint clue-kw)]
      (if hint
        (json-response 200 {:hint hint})
        (error-response 404 "Hint not found")))
    (catch Exception e
      (log/error e "Failed to get hint for" clue-id)
      (error-response 500 "Failed to get hint"))))

(defn solve-clue [{{email :email clue-id :clue-id} :path-params
                   body :body}]
  (try
    (let [clue-kw (keyword clue-id)
          body-str (if (string? body) body (slurp body))
          {:keys [answer]} (json/parse-string body-str true)
          game-state (game-logic/get-user-game email)
          game-uuid (:game/uuid game-state)
          result (game-logic/attempt-clue! game-uuid clue-kw answer)]
      (log/info "Clue attempt" clue-id "by" email "answer:" answer "correct:" (:correct? result))
      (json-response 200 result))
    (catch Exception e
      (log/error e "Failed to solve clue" clue-id "for" email)
      (error-response 500 "Failed to solve clue"))))

(defn make-accusation [{{email :email suspect-id :suspect-id} :path-params}]
  (try
    (let [suspect-kw (keyword suspect-id)
          game-state (game-logic/get-user-game email)
          game-uuid (:game/uuid game-state)]
      (if (game-logic/can-accuse? game-uuid)
        (let [result (game-logic/make-accusation! game-uuid suspect-kw)]
          (when (:correct? result)
            (game-logic/complete-case! email (get-in game-state [:game/case :case/id])))
          (log/info "Accusation by" email "suspect:" suspect-id "correct:" (:correct? result))
          (json-response 200 result))
        (error-response 400 "Not enough clues to make an accusation")))
    (catch Exception e
      (log/error e "Failed to make accusation for" email)
      (error-response 500 "Failed to make accusation"))))

;; Routes - auth-interceptor applied to protected routes
(def routes
  #{;; Original story routes
    ["/update-loc/:email" :put [auth-interceptor update-location] :route-name :start-location]
    ["/update-loc/:email/:loc" :put [auth-interceptor update-location] :route-name :update-location]
    ["/add-user/:email" :put add-user :route-name :add-user]
    ["/new-game/:email" :put [auth-interceptor new-game] :route-name :new-game]
    ["/resume-game/:email" :get [auth-interceptor resume-game] :route-name :resume-game]
    ;; Mystery game routes
    ["/cases/:email" :get [auth-interceptor get-cases] :route-name :get-cases]
    ["/case/:email/:case-id" :post [auth-interceptor start-case] :route-name :start-case]
    ["/game/:email/status" :get [auth-interceptor get-game-status] :route-name :game-status]
    ["/game/:email/clue/:clue-id" :get [auth-interceptor get-clue] :route-name :get-clue]
    ["/game/:email/clue/:clue-id/hint" :get [auth-interceptor get-hint] :route-name :get-hint]
    ["/game/:email/clue/:clue-id/solve" :post [auth-interceptor solve-clue] :route-name :solve-clue]
    ["/game/:email/accuse/:suspect-id" :post [auth-interceptor make-accusation] :route-name :make-accusation]})

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

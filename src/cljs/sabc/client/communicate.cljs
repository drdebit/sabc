(ns sabc.client.communicate
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [reagent.core :as r]
            [sabc.client.config :as config]))

;; Centralized application state
(defonce app-state
  (r/atom {:user ""
           :pin nil
           :pin-verified false
           :location nil
           :add-user-response nil
           :error nil
           :loading false
           ;; Mystery game state
           :game-mode :story  ; :story or :mystery
           :cases nil
           :progress nil
           :current-game nil
           :current-clue nil
           :accusation-result nil}))

;; State accessors for convenience
(defn get-state [k] (get @app-state k))
(defn set-state! [k v] (swap! app-state assoc k v))
(defn clear-error! [] (set-state! :error nil))

;; HTTP helpers
(defn api-url [path]
  (str config/api-base-url path))

(defn with-auth [opts]
  (let [pin (get-state :pin)]
    (if pin
      (assoc-in opts [:headers "x-pin"] pin)
      opts)))

(defn handle-error [response context]
  (let [status (:status response)
        body (:body response)
        error-msg (or (:error body)
                      (str "Request failed: " context " (status " status ")"))]
    (set-state! :error error-msg)
    (js/console.error context error-msg)
    nil))

(defn success? [response]
  (and response (>= (:status response) 200) (< (:status response) 300)))

;; API functions
(defn update-loc [loc]
  (go
    (set-state! :loading true)
    (clear-error!)
    (let [user (get-state :user)
          path (if (empty? loc)
                 (str "/update-loc/" user)
                 (str "/update-loc/" user "/" loc))
          response (<! (http/put (api-url path)
                                 (with-auth {:with-credentials? false})))]
      (set-state! :loading false)
      (if (success? response)
        (do
          (set-state! :location (:body response))
          (:body response))
        (handle-error response "update location")))))

(defn add-user [user-email]
  (go
    (set-state! :loading true)
    (clear-error!)
    (let [response (<! (http/put (api-url (str "/add-user/" user-email))
                                 {:with-credentials? false}))]
      (set-state! :loading false)
      (if (success? response)
        (let [body (:body response)]
          (set-state! :user user-email)
          (set-state! :add-user-response body)
          (set-state! :location (:location body))
          (set-state! :pin (:pin body))
          body)
        (handle-error response "add user")))))

(defn new-game []
  (go
    (set-state! :loading true)
    (clear-error!)
    (let [user (get-state :user)
          response (<! (http/put (api-url (str "/new-game/" user))
                                 (with-auth {:with-credentials? false})))]
      (set-state! :loading false)
      (if (success? response)
        (do
          (set-state! :location (:body response))
          (:body response))
        (handle-error response "new game")))))

;; Mystery Game API functions

(declare get-game-status)

(defn get-cases []
  (go
    (set-state! :loading true)
    (clear-error!)
    (let [user (get-state :user)
          response (<! (http/get (api-url (str "/cases/" user))
                                 (with-auth {:with-credentials? false})))]
      (set-state! :loading false)
      (if (success? response)
        (let [body (:body response)]
          (set-state! :cases (:cases body))
          (set-state! :progress (:progress body))
          body)
        (handle-error response "get cases")))))

(defn start-case [case-id]
  (go
    (set-state! :loading true)
    (clear-error!)
    (let [user (get-state :user)
          response (<! (http/post (api-url (str "/case/" user "/" (name case-id)))
                                  (with-auth {:with-credentials? false})))]
      (set-state! :loading false)
      (if (success? response)
        (do
          (set-state! :current-game (:body response))
          (set-state! :current-clue nil)
          (set-state! :accusation-result nil)
          ;; Immediately fetch game status
          (<! (get-game-status))
          (:body response))
        (handle-error response "start case")))))

(defn get-game-status []
  (go
    (set-state! :loading true)
    (clear-error!)
    (let [user (get-state :user)
          response (<! (http/get (api-url (str "/game/" user "/status"))
                                 (with-auth {:with-credentials? false})))]
      (set-state! :loading false)
      (if (success? response)
        (do
          (set-state! :current-game (:body response))
          (:body response))
        (handle-error response "get game status")))))

(defn get-clue [clue-id]
  (go
    (set-state! :loading true)
    (clear-error!)
    (let [user (get-state :user)
          response (<! (http/get (api-url (str "/game/" user "/clue/" (name clue-id)))
                                 (with-auth {:with-credentials? false})))]
      (set-state! :loading false)
      (if (success? response)
        (do
          (set-state! :current-clue (:body response))
          (:body response))
        (handle-error response "get clue")))))

(defn get-hint [clue-id]
  (go
    (set-state! :loading true)
    (clear-error!)
    (let [user (get-state :user)
          response (<! (http/get (api-url (str "/game/" user "/clue/" (name clue-id) "/hint"))
                                 (with-auth {:with-credentials? false})))]
      (set-state! :loading false)
      (if (success? response)
        (:hint (:body response))
        (handle-error response "get hint")))))

(defn solve-clue [clue-id answer-index]
  (go
    (set-state! :loading true)
    (clear-error!)
    (let [user (get-state :user)
          response (<! (http/post (api-url (str "/game/" user "/clue/" (name clue-id) "/solve"))
                                  (with-auth {:with-credentials? false
                                              :json-params {:answer answer-index}})))]
      (set-state! :loading false)
      (if (success? response)
        (let [result (:body response)]
          ;; Update game status after solving
          (<! (get-game-status))
          result)
        (handle-error response "solve clue")))))

(defn make-accusation [suspect-id]
  (go
    (set-state! :loading true)
    (clear-error!)
    (let [user (get-state :user)
          response (<! (http/post (api-url (str "/game/" user "/accuse/" (name suspect-id)))
                                  (with-auth {:with-credentials? false})))]
      (set-state! :loading false)
      (if (success? response)
        (do
          (set-state! :accusation-result (:body response))
          (:body response))
        (handle-error response "make accusation")))))

(defn start-investigation []
  "Starts the mystery investigation directly with the first available case."
  (set-state! :game-mode :mystery)
  (set-state! :current-game nil)
  (set-state! :current-clue nil)
  (set-state! :accusation-result nil)
  ;; Start the first case directly
  (start-case :missing-materials))

(defn back-to-story []
  "Returns to story mode."
  (set-state! :game-mode :story)
  (set-state! :current-game nil)
  (set-state! :current-clue nil)
  (set-state! :accusation-result nil))

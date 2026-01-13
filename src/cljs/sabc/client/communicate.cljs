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
           :loading false}))

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

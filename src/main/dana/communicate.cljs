(ns dana.communicate
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require  [cljs-http.client :as http]
             [cljs.core.async :refer [<!]]
             [reagent.core :as r]))

(defonce greet-string (r/atom ""))
(defonce user (r/atom ""))
(defonce location (r/atom ""))
(defonce add-user-response (r/atom nil))

(defn greeting []
  (go (let [response (<! (http/get "http://localhost:5000"
                                   {:with-credentials? false}))]
        (prn (:status response))
        (prn (:body response))
        (reset! greet-string (:body response)))))

(defn update-loc [loc]
  (go (let [response (<! (http/put (str "http://localhost:5000/update-loc/" @user
                                        (cond (empty? loc) "" :else (str "/" loc)))
                                   {:with-credentials? false}))]
        (prn (:status response))
        ;; (prn (:body response))
        (reset! location (:body response))
        (:body response))))

(defn add-user [user-passed]
  (go (let [response (<! (http/put (str "http://localhost:5000/add-user/" user-passed)
                                   {:with-credentials? false}))
            body (:body response)
            location-responded (:location body)]
        (prn (:status response))
        (reset! user user-passed)
        (reset! add-user-response body)
        (reset! location location-responded)
        body)))

(defn new-game []
  (go (let [response (<! (http/put (str "http://localhost:5000/new-game/" @user)
                                   {:with-credentials? false}))]
        (reset! location (:body response)))))

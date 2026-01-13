(ns sabc.client.app
  (:require [sabc.client.communicate :as comm]
            [reagent.core :as r]
            [reagent.dom.client :as rdomc]))

(def title "Consolidated Paesan")

(defn atom-input [value]
  [:input {:type "text" :value @value
           :on-change #(reset! value (-> % .-target .-value))}])

(defn error-banner []
  (when-let [error (comm/get-state :error)]
    [:div.error {:style {:color "red" :padding "10px" :margin "10px 0"}}
     [:strong "Error: "] error
     [:button {:style {:margin-left "10px"}
               :on-click comm/clear-error!} "Dismiss"]]))

(defn loading-indicator []
  (when (comm/get-state :loading)
    [:div.loading {:style {:color "blue" :padding "5px"}}
     "Loading..."]))

(defn username-input []
  (let [typed-user (r/atom "")]
    (fn []
      [:div#username-input
       [:h1 title]
       [error-banner]
       [:p "Please type your CampusID in the box below."]
       [:form {:on-submit (fn [e]
                            (.preventDefault e)
                            (comm/add-user @typed-user))}
        [atom-input typed-user]
        [:button {:type :submit} "Submit"]]
       [loading-indicator]])))

(defn provide-pin []
  [:div
   [:h1 "Please write down your PIN so you can access your game in the future."]
   [error-banner]
   [:p (str "Your PIN is: " (:pin (comm/get-state :add-user-response)))]
   [:input {:type "button" :value "Continue"
            :on-click #(comm/set-state! :pin-verified true)}]])

(defn enter-pin []
  (r/with-let [error-text (r/atom "")
               typed-pin (r/atom "")]
    [:div
     [:h1 "Please login with your previously provided PIN."]
     [error-banner]
     [:p {:style {:color "red"}} @error-text]
     [:form {:on-submit (fn [e]
                          (.preventDefault e)
                          (let [stored-pin (:pin (comm/get-state :add-user-response))]
                            (if (= stored-pin @typed-pin)
                              (comm/set-state! :pin-verified true)
                              (do (reset! error-text "Please try again.")
                                  (reset! typed-pin "")))))}
      [atom-input typed-pin]
      [:button {:type :submit} "Submit"]]
     [loading-indicator]]))

(defn choice-button [[loc title]]
  ^{:key (name loc)}
  [:div [:input {:type "button" :value title
                 :on-click #(comm/update-loc (name loc))}]])

(defn game-time []
  (let [location (comm/get-state :location)]
    [:div
     [:h1 title]
     [error-banner]
     [loading-indicator]
     [:p {:style {:width "50%"}} (:text location)]
     [:div (map choice-button (:parents location))]
     [:div (map choice-button (:children location))]
     [:div [:p [:input {:type "button" :value "New Game"
                        :on-click #(comm/new-game)}]]]]))

(defn app []
  (let [user (comm/get-state :user)
        pin-verified (comm/get-state :pin-verified)
        add-user-response (comm/get-state :add-user-response)
        is-new-user (:added? add-user-response)]
    (cond
      (= user "") [username-input]
      (and (not pin-verified) (not is-new-user)) [enter-pin]
      (not pin-verified) [:div [:h1 title] [provide-pin]]
      :else [game-time])))

(defonce root (rdomc/create-root (.getElementById js/document "root")))

(defn init []
  (.render root (r/as-element [app])))

(defn stop []
  (js/console.log "Stopping..."))

(defn ^:dev/after-load re-render []
  (init))

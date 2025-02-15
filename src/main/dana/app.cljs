(ns dana.app
  (:require ;[goog.dom :as gdom]
            ;[goog.events :as gevents]
   [dana.communicate :as comm]
   ["react" :as react]
   [reagent.core :as r]
   [reagent.dom :as rdom]
   [reagent.dom.client :as rdomc]))

(defonce pin (r/atom false))
(def title "Consolidated Paesan")

(defn atom-input [value]
  [:input {:type "text" :value @value
           :on-change #(reset! value (-> % .-target .-value))}])

(defn change-room []
  (let [room (if (= @comm/location "room1") "room2" "room1")]
    [:input {:type "button" :value (str "Change to " room)
             :on-click #(comm/update-loc room)}]))

(defn username-input []
  (let [typed-user (r/atom "")]
    [:div#username-input
     [:h1 title]
     [:p "Please type your CampusID in the box below."]
     [:form {:on-submit (fn [e]
                          (.preventDefault e)
                          (comm/add-user @typed-user))}
      [atom-input typed-user]
      [:button {:type :submit} "Submit"]]]))

;; (defn username-form []
;;   (let [typed-user (r/atom "")]
;;     [:div#username-form [:h1 "I am a game!"]
;;      [:p "Please type your CampusID in the box below."]
;;      [atom-input typed-user]
;;      [:input {:type "button" :value "Submit"
;;               :on-click #(comm/add-user @typed-user)}]]))

(defn provide-pin []
  [:div [:h1 "Please write down your PIN so you can access your game in the future."]
   [:p (str "Your PIN is: " (:pin @comm/add-user-response))]
   [:input {:type "button" :value "Continue"
                                :on-click #(reset! pin true)}]])

(defn enter-pin []
  (r/with-let [error-text (r/atom "")
               typed-pin (r/atom "")]
    [:div [:h1 "Please login with your previously provided PIN."]
     [:p @error-text]
     [:form {:on-submit (fn [e]
                          (.preventDefault e)
                          (cond
                            (= (:pin @comm/add-user-response) @typed-pin) (reset! pin true)
                            :else (do (reset! error-text "Please try again.")
                                      (reset! typed-pin ""))))}
      [atom-input typed-pin]
      [:button {:type :submit} "Submit"]]]))

(defn choice-button [[loc title]]
  [:div [:input {:type "button" :value title :on-click #(comm/update-loc (name loc))}]])

(defn game-time []
  [:div [:h1 title]
   [:p {:style {:width "50%"}} (:text @comm/location)]
   [:div (map choice-button (:parents @comm/location))]
   [:div (map choice-button (:children @comm/location))]
   [:div [:p [:input {:type "button" :value "New Game"  :on-click #(comm/new-game)}]]]
   ])

(defn app []
  (cond
   (= @comm/user "") [username-input]
   (and (not (true? @pin))
        (not (true?  (:added? @comm/add-user-response)))) [enter-pin]
   (not (true? @pin)) [:div [:h1 title]
                       [provide-pin]]
   :else [game-time]))

(defonce root (rdomc/create-root (.getElementById js/document "root")))
(defn init []
  (.render root (r/as-element [app])))

(defn stop []
  (js/console.log "Stopping..."))

(defn ^:dev/after-load re-render []
  (init))

;; define your app data so that it doesn't get over-written on reload
;; (defn get-app-element []
;;   (gdom/getElement "root"))
;; (def app-element (get-app-element))
;; (def input (gdom/getElement "input"))

;; (defn get-input-value []
;;   (js/String (.. input -value)))

;; (def input-value (gdom/getElement "input-value"))
;; (defn render-new-value [val] 
;;   (gdom/setTextContent input-value val))

;; (defonce is-initialized?
;;   (do
;;     (gevents/listen app-element "click"
;;                     (fn [event]
;;                       (condp = (aget event "target" "id")
;;                         "submit" (comm/greeting)
;;                         nil)))
;;     (add-watch comm/greet-string :counter-watcher 
;;       (fn [key atom old new]
;;         (render-new-value new)))
;;     true))

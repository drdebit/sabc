(ns sabc.client.app
  (:require [sabc.client.communicate :as comm]
            [cljs.core.async :refer [<!]]
            [reagent.core :as r]
            [reagent.dom.client :as rdomc])
  (:require-macros [cljs.core.async.macros :refer [go]]))

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
  (let [location (comm/get-state :location)
        ;; Check if we're at Sofia's briefing (no children = end of story intro)
        at-briefing? (and (seq (:text location))
                          (empty? (:children location)))]
    [:div
     [:h1 title]
     [error-banner]
     [loading-indicator]
     [:div {:style {:width "50%"}} (:text location)]
     [:div (map choice-button (:parents location))]
     [:div (map choice-button (:children location))]
     (when at-briefing?
       [:div {:style {:margin-top "20px" :padding "15px" :background-color "#e8f5e9" :border-radius "5px"}}
        [:input {:type "button"
                 :value "Begin Investigation"
                 :style {:font-size "1.1em" :padding "10px 20px" :background-color "#4CAF50" :color "white" :border "none" :cursor "pointer"}
                 :on-click #(comm/start-investigation)}]])
     [:div {:style {:margin-top "20px"}}
      [:input {:type "button" :value "Restart Story"
               :on-click #(comm/new-game)}]]]))

;; Mystery Game Components

(defn clue-status-badge [clue-id game-state]
  (let [solved-ids (set (map :clue/id (:game/clues-solved game-state)))
        attempted-ids (set (map :clue/id (:game/clues-attempted game-state)))]
    (cond
      (contains? solved-ids clue-id)
      [:span {:style {:color "green" :margin-left "10px"}} "✓ Solved"]
      (contains? attempted-ids clue-id)
      [:span {:style {:color "orange" :margin-left "10px"}} "✗ Attempted"]
      :else
      [:span {:style {:color "blue" :margin-left "10px"}} "? Available"])))

(defn clue-list-item [clue game-state]
  (let [{:keys [clue/id clue/department clue/concept]} clue
        solved-ids (set (map :clue/id (:game/clues-solved game-state)))
        already-solved? (contains? solved-ids id)]
    ^{:key (name id)}
    [:div {:style {:padding "10px"
                   :margin "5px 0"
                   :border "1px solid #ddd"
                   :border-radius "5px"
                   :background-color (if already-solved? "#e8f5e9" "#fff")}}
     [:span [:strong department] " - " (name concept)]
     [clue-status-badge id game-state]
     (when-not already-solved?
       [:input {:type "button"
                :value "Investigate"
                :style {:margin-left "10px"}
                :on-click #(comm/get-clue id)}])]))

(defn suspect-card [suspect can-accuse?]
  (let [{:keys [suspect/id suspect/department suspect/description]} suspect
        suspect-name (:suspect/name suspect)]
    ^{:key (name id)}
    [:div {:style {:border "1px solid #ccc"
                   :padding "15px"
                   :margin "10px 0"
                   :border-radius "5px"
                   :background-color "#fff8e1"}}
     [:h4 suspect-name]
     [:p {:style {:color "#666" :font-style "italic"}} department]
     [:p description]
     (when can-accuse?
       [:input {:type "button"
                :value "Accuse!"
                :style {:background-color "#f44336" :color "white"}
                :on-click #(comm/make-accusation id)}])]))

(defn investigation-hub []
  (let [current-game (comm/get-state :current-game)
        game-state (:game current-game)
        clues (:clues current-game)
        suspects (:suspects current-game)
        can-accuse (:can-accuse current-game)
        case-info (:game/case game-state)
        solved-count (count (:game/clues-solved game-state))
        min-clues (:case/min-clues case-info 2)]
    [:div
     [:h1 title]
     [:h2 (:case/title case-info)]
     [error-banner]
     [loading-indicator]
     [:input {:type "button"
              :value "← Back to Sofia"
              :on-click #(comm/back-to-story)}]

     [:div {:style {:margin "20px 0" :padding "10px" :background-color "#e3f2fd" :border-radius "5px"}}
      [:p [:strong "Investigation Progress"]]
      [:p (str "Clues Solved: " solved-count "/" (count clues))]
      [:p (str "Need " min-clues " clues to make an accusation")]
      (when can-accuse
        [:p {:style {:color "green" :font-weight "bold"}} "You can now make an accusation!"])]

     [:h3 "Clues to Investigate"]
     [:div (map #(clue-list-item % game-state) clues)]

     [:h3 "Suspects"]
     [:div (map #(suspect-card % can-accuse) suspects)]]))

(defn render-data-table [rows]
  "Renders a table from rows (first row can be headers)."
  [:table {:style {:border-collapse "collapse" :width "100%" :margin "10px 0"}}
   [:tbody
    (map-indexed
     (fn [idx row]
       ^{:key idx}
       [:tr {:style {:background-color (if (zero? idx) "#e3f2fd" (if (odd? idx) "#f5f5f5" "#fff"))}}
        (map-indexed
         (fn [cidx cell]
           ^{:key cidx}
           [(if (zero? idx) :th :td)
            {:style {:border "1px solid #ddd" :padding "8px" :text-align "left"
                     :font-weight (if (zero? idx) "bold" "normal")}}
            cell])
         row)])
     rows)]])

(defn render-costs-list [costs]
  "Renders a list of [name, amount] costs."
  [:table {:style {:border-collapse "collapse" :width "100%" :margin "10px 0"}}
   [:tbody
    [:tr {:style {:background-color "#e3f2fd"}}
     [:th {:style {:border "1px solid #ddd" :padding "8px" :text-align "left"}} "Cost Item"]
     [:th {:style {:border "1px solid #ddd" :padding "8px" :text-align "right"}} "Amount"]]
    (map-indexed
     (fn [idx [item amount]]
       ^{:key idx}
       [:tr {:style {:background-color (if (odd? idx) "#f5f5f5" "#fff")}}
        [:td {:style {:border "1px solid #ddd" :padding "8px"}} item]
        [:td {:style {:border "1px solid #ddd" :padding "8px" :text-align "right"}} amount]])
     costs)]])

(defn render-clue-data [data]
  "Renders clue data in appropriate format based on structure."
  [:div {:style {:margin "15px 0" :padding "15px" :background-color "#fff" :border "1px solid #ddd" :border-radius "5px"}}
   (when (:info data)
     [:p {:style {:font-weight "bold" :margin-bottom "10px"}} (:info data)])
   (when (:table data)
     [render-data-table (:table data)])
   (when (:costs data)
     [render-costs-list (:costs data)])
   ;; Notes that may reveal discrepancies (or confirm matching records)
   (when-let [note (or (:ledger-note data) (:po-note data) (:expense-note data) (:budget-note data))]
     [:p {:style {:margin-top "10px" :padding "10px" :background-color "#fff3e0" :border-left "4px solid #ff9800"}}
      [:strong "Records Check: "] note])])

(defn clue-puzzle []
  (let [clue (comm/get-state :current-clue)
        hint-text (r/atom nil)
        result (r/atom nil)]
    (fn []
      (let [{:keys [clue/id clue/narrative clue/data clue/question clue/answers]} clue]
        [:div
         [:h1 title]
         [:h2 "Investigating a Clue"]
         [error-banner]
         [loading-indicator]
         [:input {:type "button"
                  :value "← Back to Investigation"
                  :on-click #(do (comm/set-state! :current-clue nil)
                                 (comm/get-game-status))}]

         [:div {:style {:margin "20px 0" :padding "20px" :background-color "#f5f5f5" :border-radius "10px"}}
          [:p {:style {:font-style "italic"}} narrative]

          (when data
            [render-clue-data data])

          [:p [:strong question]]

          (when @result
            [:div {:style {:padding "10px"
                           :margin "10px 0"
                           :border-radius "5px"
                           :background-color (if (:correct? @result) "#c8e6c9" "#ffcdd2")}}
             (if (:correct? @result)
               [:div
                [:p [:strong "Correct!"]]
                [:p (:result-text @result)]]
               [:p [:strong "Incorrect."] " The trail goes cold here."])])

          (when-not @result
            [:div
             (map-indexed
              (fn [idx answer]
                ^{:key idx}
                [:div {:style {:margin "5px 0"}}
                 [:input {:type "button"
                          :value answer
                          :style {:width "100%" :text-align "left" :padding "10px"}
                          :on-click #(go
                                       (let [r (<! (comm/solve-clue id idx))]
                                         (reset! result r)))}]])
              answers)])

          (when-not @result
            [:div {:style {:margin-top "20px"}}
             [:input {:type "button"
                      :value "Ask Sofia for a Hint"
                      :style {:background-color "#2196F3" :color "white"}
                      :on-click #(go (reset! hint-text (<! (comm/get-hint id))))}]
             (when @hint-text
               [:p {:style {:margin-top "10px" :padding "10px" :background-color "#e3f2fd" :border-radius "5px"}}
                [:strong "Sofia says: "] @hint-text])])]]))))

(defn victory-screen []
  (let [result (comm/get-state :accusation-result)
        culprit (:culprit result)]
    [:div
     [:h1 title]
     [:h2 {:style {:color "green"}} "Case Solved!"]
     [:div {:style {:padding "20px" :background-color "#c8e6c9" :border-radius "10px"}}
      [:p {:style {:font-size "1.2em"}} (:message result)]
      [:p "The culprit was: " [:strong (:suspect/name culprit)]]
      [:p {:style {:font-style "italic"}} (:suspect/description culprit)]]
     [:input {:type "button"
              :value "Return to Sofia"
              :style {:margin-top "20px"}
              :on-click #(comm/back-to-story)}]]))

(defn wrong-accusation-screen []
  (let [result (comm/get-state :accusation-result)]
    [:div
     [:h1 title]
     [:h2 {:style {:color "red"}} "Wrong Accusation!"]
     [:div {:style {:padding "20px" :background-color "#ffcdd2" :border-radius "10px"}}
      [:p (:message result)]
      [:p (str "Wrong accusations so far: " (:wrong-accusations result))]]
     [:input {:type "button"
              :value "Continue Investigating"
              :style {:margin-top "20px"}
              :on-click #(do (comm/set-state! :accusation-result nil)
                             (comm/get-game-status))}]]))

(defn mystery-game []
  (let [current-game (comm/get-state :current-game)
        current-clue (comm/get-state :current-clue)
        accusation-result (comm/get-state :accusation-result)]
    (cond
      ;; Show victory/defeat screen if accusation was made
      (and accusation-result (:correct? accusation-result))
      [victory-screen]

      accusation-result
      [wrong-accusation-screen]

      ;; Show clue puzzle if investigating a clue
      current-clue
      [clue-puzzle]

      ;; Show investigation hub if in a game
      current-game
      [investigation-hub]

      ;; Loading state while game starts
      :else
      [:div
       [:h1 title]
       [loading-indicator]
       [:p "Loading investigation..."]])))

(defn app []
  (let [user (comm/get-state :user)
        pin-verified (comm/get-state :pin-verified)
        add-user-response (comm/get-state :add-user-response)
        is-new-user (:added? add-user-response)
        game-mode (comm/get-state :game-mode)]
    (cond
      (= user "") [username-input]
      (and (not pin-verified) (not is-new-user)) [enter-pin]
      (not pin-verified) [:div [:h1 title] [provide-pin]]
      (= game-mode :mystery) [mystery-game]
      :else [game-time])))

(defonce root (rdomc/create-root (.getElementById js/document "root")))

(defn init []
  (.render root (r/as-element [app])))

(defn stop []
  (js/console.log "Stopping..."))

(defn ^:dev/after-load re-render []
  (init))

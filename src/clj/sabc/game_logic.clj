(ns sabc.game-logic
  (:require [datomic.api :as d]
            [sabc.db :as db]
            [sabc.cases :as cases]
            [sabc.clues :as clues]
            [sabc.suspects :as suspects]
            [sabc.clue-generator :as clue-gen]))

(defn random-culprit [case-id]
  "Randomly selects a culprit from the case's suspects."
  (let [case-suspects (cases/get-case-suspects case-id)]
    (when (seq case-suspects)
      (:suspect/id (rand-nth case-suspects)))))

(defn start-case! [user-email case-id]
  "Starts a new game for the user with the given case. Returns game UUID."
  (let [culprit-id (random-culprit case-id)
        game-uuid (java.util.UUID/randomUUID)
        clue-data (clue-gen/generate-all-clue-data culprit-id)]
    ;; Create the game entity with generated clue data
    @(d/transact (db/conn)
                 [{:game/uuid game-uuid
                   :game/case [:case/id case-id]
                   :game/culprit [:suspect/id culprit-id]
                   :game/wrong-accusations 0
                   :game/solved false
                   :game/clue-data (pr-str clue-data)}])
    ;; Link game to user
    @(d/transact (db/conn)
                 [{:db/id [:user/email user-email]
                   :user/current-game [:game/uuid game-uuid]}])
    {:game-uuid game-uuid
     :case-id case-id
     :culprit-id culprit-id}))

(defn get-game-state [game-uuid]
  "Returns the current state of a game."
  (d/q '[:find (pull ?g [:game/uuid :game/wrong-accusations :game/solved
                         :game/clue-data
                         {:game/case [:case/id :case/title :case/min-clues]}
                         {:game/culprit [:suspect/id]}
                         {:game/clues-solved [:clue/id]}
                         {:game/clues-attempted [:clue/id]}]) .
         :in $ ?uuid
         :where [?g :game/uuid ?uuid]]
       (db/db) game-uuid))

(defn get-user-game [user-email]
  "Gets the current game for a user."
  (let [game-uuid (ffirst (d/q '[:find ?uuid
                                  :in $ ?email
                                  :where [?u :user/email ?email]
                                         [?u :user/current-game ?g]
                                         [?g :game/uuid ?uuid]]
                                (db/db) user-email))]
    (when game-uuid
      (get-game-state game-uuid))))

(defn attempt-clue! [game-uuid clue-id answer-index]
  "Records a clue attempt and returns the result."
  (let [game-state (get-game-state game-uuid)
        culprit-id (get-in game-state [:game/culprit :suspect/id])
        game-clue-data (when-let [data-str (:game/clue-data game-state)]
                         (read-string data-str))
        already-attempted? (some #(= clue-id (:clue/id %))
                                 (:game/clues-attempted game-state))
        result (clues/check-answer clue-id answer-index culprit-id game-clue-data)]
    (when-not already-attempted?
      ;; Record the attempt
      @(d/transact (db/conn)
                   [{:db/id [:game/uuid game-uuid]
                     :game/clues-attempted {:db/id [:clue/id clue-id]}}])
      ;; If correct, also record as solved
      (when (:correct? result)
        @(d/transact (db/conn)
                     [{:db/id [:game/uuid game-uuid]
                       :game/clues-solved {:db/id [:clue/id clue-id]}}])))
    (assoc result
           :already-attempted? already-attempted?
           :clue-id clue-id)))

(defn can-accuse? [game-uuid]
  "Returns true if the player has solved enough clues to make an accusation."
  (let [game-state (get-game-state game-uuid)
        min-clues (get-in game-state [:game/case :case/min-clues] 2)
        solved-count (count (:game/clues-solved game-state))]
    (>= solved-count min-clues)))

(defn make-accusation! [game-uuid suspect-id]
  "Makes an accusation. Returns result with win/lose status."
  (let [game-state (get-game-state game-uuid)
        culprit-id (get-in game-state [:game/culprit :suspect/id])
        correct? (= suspect-id culprit-id)]
    (if correct?
      ;; Correct accusation - mark game as solved
      (do
        @(d/transact (db/conn)
                     [{:db/id [:game/uuid game-uuid]
                       :game/solved true}])
        {:correct? true
         :message "You caught the culprit!"
         :culprit (suspects/get-suspect-by-id culprit-id)})
      ;; Wrong accusation - increment counter
      (do
        @(d/transact (db/conn)
                     [{:db/id [:game/uuid game-uuid]
                       :game/wrong-accusations (inc (:game/wrong-accusations game-state 0))}])
        {:correct? false
         :message "That's not the culprit. Keep investigating!"
         :wrong-accusations (inc (:game/wrong-accusations game-state 0))}))))

(defn complete-case! [user-email case-id]
  "Marks a case as completed for the user and potentially unlocks next difficulty."
  (let [case-data (cases/get-case-by-id case-id)
        difficulty (:case/difficulty case-data)
        current-max (or (ffirst (d/q '[:find ?max
                                        :in $ ?email
                                        :where [?u :user/email ?email]
                                               [?u :user/max-difficulty ?max]]
                                      (db/db) user-email))
                        1)
        new-max (max current-max (inc difficulty))]
    @(d/transact (db/conn)
                 [{:db/id [:user/email user-email]
                   :user/completed-cases {:db/id [:case/id case-id]}
                   :user/max-difficulty new-max}])
    {:completed-case case-id
     :new-max-difficulty new-max}))

(defn get-user-progress [user-email]
  "Returns the user's progress including max difficulty and completed cases."
  (let [result (d/q '[:find (pull ?u [:user/max-difficulty
                                      {:user/completed-cases [:case/id :case/title]}]) .
                      :in $ ?email
                      :where [?u :user/email ?email]]
                    (db/db) user-email)]
    {:max-difficulty (or (:user/max-difficulty result) 1)
     :completed-cases (or (:user/completed-cases result) [])}))

(defn get-available-cases [user-email]
  "Returns cases available to the user based on their progress."
  (let [progress (get-user-progress user-email)
        max-diff (:max-difficulty progress)]
    (cases/get-cases-by-difficulty max-diff)))

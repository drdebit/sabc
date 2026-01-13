(ns sabc.cases
  (:require [datomic.api :as d]
            [sabc.db :as db]))

(def cases
  [{:case/id :missing-materials
    :case/title "The Missing Materials"
    :case/description "Someone at Consolidated Paesan has been cooking the books. The Don noticed a $50,000 discrepancy in the quarterly materials budget. \"Find out who's stealing from me,\" he growls, \"or you'll be swimming with the fishes.\" Sofia will help you investigate each department and uncover the truth through their financial records."
    :case/difficulty 1
    :case/min-clues 2
    :case/suspects [[:suspect/id :vinnie-numbers]
                    [:suspect/id :tony-receipts]
                    [:suspect/id :maria-books]
                    [:suspect/id :sal-percentage]]
    :case/clues [[:clue/id :clue-cost-classification]
                 [:clue/id :clue-cost-behavior]
                 [:clue/id :clue-mixed-costs]
                 [:clue/id :clue-total-cost]]}])

(defonce cases-loaded? (atom false))

(defn load-cases! []
  "Loads case data into the database if not already loaded."
  (when-not @cases-loaded?
    @(d/transact (db/conn) cases)
    (reset! cases-loaded? true)))

(defn get-all-cases []
  "Returns all cases."
  (d/q '[:find [(pull ?e [:case/id :case/title :case/description :case/difficulty :case/min-clues]) ...]
         :where [?e :case/id _]]
       (db/db)))

(defn get-cases-by-difficulty [max-difficulty]
  "Returns cases up to the given difficulty level."
  (d/q '[:find [(pull ?e [:case/id :case/title :case/description :case/difficulty :case/min-clues]) ...]
         :in $ ?max-diff
         :where [?e :case/id _]
                [?e :case/difficulty ?diff]
                [(<= ?diff ?max-diff)]]
       (db/db) max-difficulty))

(defn get-case-by-id [case-id]
  "Returns a case by its keyword id with full suspect and clue info."
  (d/q '[:find (pull ?e [:case/id :case/title :case/description :case/difficulty :case/min-clues
                         {:case/suspects [:suspect/id :suspect/name :suspect/department :suspect/description]}
                         {:case/clues [:clue/id :clue/department :clue/concept]}]) .
         :in $ ?id
         :where [?e :case/id ?id]]
       (db/db) case-id))

(defn get-case-suspects [case-id]
  "Returns the suspects for a case."
  (d/q '[:find [(pull ?s [:suspect/id :suspect/name :suspect/department :suspect/description]) ...]
         :in $ ?case-id
         :where [?c :case/id ?case-id]
                [?c :case/suspects ?s]]
       (db/db) case-id))

(defn get-case-clues [case-id]
  "Returns the clue IDs and departments for a case (not the answers)."
  (d/q '[:find [(pull ?clue [:clue/id :clue/department :clue/concept]) ...]
         :in $ ?case-id
         :where [?c :case/id ?case-id]
                [?c :case/clues ?clue]]
       (db/db) case-id))

(ns sabc.clues
  (:require [datomic.api :as d]
            [sabc.db :as db]))

;; Clues for "The Missing Materials" case (Difficulty 1 - Cost Basics)
;; Each clue tests cost classification/behavior and points to a suspect

(def case1-clues
  [{:clue/id :clue-cost-classification
    :clue/concept :cost-classification
    :clue/department "Production"
    :clue/narrative "Sofia adjusts her spectacles and pulls out a ledger. \"Let's look at the Production department's costs. Understanding which costs change with production volume is key to finding our thief.\""
    :clue/data (pr-str {:costs [["Direct Materials" "$50,000"]
                                ["Factory Rent" "$10,000"]
                                ["Direct Labor" "$35,000"]
                                ["Equipment Depreciation" "$8,000"]
                                ["Packaging Supplies" "$12,000"]]})
    :clue/question "Which of these costs would increase if production doubled?"
    :clue/answers (pr-str ["Factory Rent and Equipment Depreciation"
                           "Direct Materials, Direct Labor, and Packaging Supplies"
                           "Only Direct Materials"
                           "All of the above"])
    :clue/correct-index 1
    :clue/hint "Variable costs change with production volume. Fixed costs stay the same regardless of how many units you make."
    :clue/guilty-text "Correct! You cross-reference the records and find discrepancies. Vinnie's been misclassifying variable costs as fixed, then pocketing the difference. This is your guy!"
    :clue/cleared-text "Correct! You carefully review the records, but everything in Production checks out. Vinnie's books are clean."
    :clue/suspect [:suspect/id :vinnie-numbers]}

   {:clue/id :clue-cost-behavior
    :clue/concept :cost-behavior
    :clue/department "Purchasing"
    :clue/narrative "Sofia leads you to the Purchasing department. \"Let's check these material costs over the past few months.\""
    :clue/data (pr-str {:table [["Month" "Units Produced" "Material Cost"]
                                ["January" "1,000" "$25,000"]
                                ["February" "1,500" "$37,500"]
                                ["March" "2,000" "$50,000"]
                                ["April" "1,200" "$30,000"]]})
    :clue/question "What is the variable cost per unit for materials?"
    :clue/answers (pr-str ["$20 per unit"
                           "$25 per unit"
                           "$30 per unit"
                           "$37.50 per unit"])
    :clue/correct-index 1
    :clue/hint "Divide the total material cost by the number of units for any month. If it's truly variable, the per-unit cost should be the same each month."
    :clue/guilty-text "Correct! $25 per unit. But wait - Tony's approved purchase orders show $30 per unit. He's been pocketing $5 on every unit! You've found your thief!"
    :clue/cleared-text "Correct! $25 per unit, and that matches Tony's purchase orders exactly. Purchasing is clean."
    :clue/suspect [:suspect/id :tony-receipts]}

   {:clue/id :clue-mixed-costs
    :clue/concept :mixed-costs
    :clue/department "Warehouse"
    :clue/narrative "You visit the Warehouse with Sofia. \"Let's analyze the shipping costs,\" she says."
    :clue/data (pr-str {:table [["Month" "Shipments" "Total Shipping Cost"]
                                ["January" "100" "$3,000"]
                                ["February" "150" "$4,000"]
                                ["March" "200" "$5,000"]
                                ["April" "250" "$6,000"]]})
    :clue/question "If shipping has a fixed component plus a variable rate per shipment, what is the fixed cost?"
    :clue/answers (pr-str ["$500"
                           "$1,000"
                           "$1,500"
                           "$2,000"])
    :clue/correct-index 1
    :clue/hint "Use the high-low method: Variable rate = (High cost - Low cost) / (High shipments - Low shipments). Then: Fixed cost = Total cost - (Variable rate × Shipments)"
    :clue/guilty-text "Correct! The fixed cost should be $1,000 with $20 variable per shipment. But Maria's been reporting $1,500 fixed and skimming $500 every month! Case closed!"
    :clue/cleared-text "Correct! Fixed cost is $1,000, variable is $20 per shipment. Maria's records match perfectly. The Warehouse is clean."
    :clue/suspect [:suspect/id :maria-books]}

   {:clue/id :clue-total-cost
    :clue/concept :total-cost
    :clue/department "Sales"
    :clue/narrative "Sofia takes you to review the Sales department budget. \"Sal submitted this cost projection for a new product launch. Let's verify it.\""
    :clue/data (pr-str {:info "Sal's Projection for 5,000 units:"
                        :costs [["Fixed Costs (rent, salaries)" "$20,000"]
                                ["Variable Cost per Unit" "$15"]]
                        :question-context "Sal claims the total cost will be $120,000"})
    :clue/question "What should the actual total cost be for 5,000 units?"
    :clue/answers (pr-str ["$75,000"
                           "$95,000"
                           "$100,000"
                           "$120,000"])
    :clue/correct-index 1
    :clue/hint "Total Cost = Fixed Costs + (Variable Cost per Unit × Number of Units)"
    :clue/guilty-text "Correct! $20,000 + ($15 × 5,000) = $95,000. Sal inflated the projection by $25,000 - he was planning to pocket the difference! You've caught him!"
    :clue/cleared-text "Correct! $95,000 is the right answer. You check Sal's actual submitted budget and it matches. Sales is clean."
    :clue/suspect [:suspect/id :sal-percentage]}])

(defonce clues-loaded? (atom false))

(defn load-clues! []
  "Loads clue data into the database if not already loaded."
  (when-not @clues-loaded?
    @(d/transact (db/conn) case1-clues)
    (reset! clues-loaded? true)))

(defn get-clue-by-id [clue-id]
  "Returns a clue by its keyword id (without correct answer for client).
   Parses data and answers from EDN strings."
  (let [raw (d/q '[:find (pull ?e [:clue/id :clue/concept :clue/department :clue/narrative
                                   :clue/data :clue/question :clue/answers]) .
                   :in $ ?id
                   :where [?e :clue/id ?id]]
                 (db/db) clue-id)]
    (when raw
      (-> raw
          (update :clue/data #(when % (read-string %)))
          (update :clue/answers #(when % (read-string %)))))))

(defn get-clue-with-generated-data [clue-id game-clue-data]
  "Returns a clue with dynamically generated data merged in.
   game-clue-data should be the parsed :game/clue-data map."
  (let [base-clue (d/q '[:find (pull ?e [:clue/id :clue/concept :clue/department :clue/narrative
                                         :clue/hint
                                         {:clue/suspect [:suspect/id :suspect/name]}]) .
                         :in $ ?id
                         :where [?e :clue/id ?id]]
                       (db/db) clue-id)
        generated (get game-clue-data clue-id)]
    (when (and base-clue generated)
      (assoc base-clue
             :clue/data (:data generated)
             :clue/answers (:answers generated)
             :clue/question (:question generated)))))

(defn get-clue-full [clue-id]
  "Returns full clue data including answer (for server-side validation)."
  (d/q '[:find (pull ?e [* {:clue/suspect [:suspect/id]}]) .
         :in $ ?id
         :where [?e :clue/id ?id]]
       (db/db) clue-id))

(defn check-answer [clue-id answer-index culprit-id game-clue-data]
  "Checks if the given answer index is correct using generated clue data.
   Returns neutral result text - doesn't reveal who is guilty."
  (let [clue (get-clue-full clue-id)
        generated (get game-clue-data clue-id)
        ;; Use generated correct-index if available, fall back to static
        correct-index (or (:correct-index generated) (:clue/correct-index clue))
        correct? (= answer-index correct-index)
        clue-suspect-id (get-in clue [:clue/suspect :suspect/id])
        ;; Use generated result-text (neutral feedback)
        result-text (or (:result-text generated) (:clue/guilty-text clue))]
    {:correct? correct?
     :suspect clue-suspect-id
     :result-text (when correct? result-text)}))

(defn get-hint [clue-id]
  "Returns the hint for a clue."
  (:clue/hint (get-clue-full clue-id)))

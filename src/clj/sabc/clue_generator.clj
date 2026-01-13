(ns sabc.clue-generator
  "Generates randomized but mathematically consistent clue data for each game.
   Guilty suspects have discrepancies in their numbers; innocent suspects have clean books.")

(defn rand-between [min max]
  (+ min (rand-int (- max min))))

(defn rand-multiple [min max multiple]
  "Returns a random number between min and max that's a multiple of 'multiple'."
  (* multiple (rand-between (quot min multiple) (quot max multiple))))

(defn generate-wrong-answers [correct others]
  "Shuffles correct answer with wrong options."
  (let [all-answers (shuffle (cons correct others))]
    {:answers all-answers
     :correct-index (.indexOf all-answers correct)}))

;; Cost Classification - variable vs fixed costs
;; Suspect: Vinnie (Production)
(defn generate-cost-classification [guilty?]
  (let [direct-materials (rand-multiple 30000 70000 5000)
        factory-rent (rand-multiple 8000 15000 1000)
        direct-labor (rand-multiple 25000 45000 5000)
        equipment-depreciation (rand-multiple 5000 12000 1000)
        packaging-supplies (rand-multiple 8000 18000 2000)
        costs [["Direct Materials" (str "$" (format "%,d" direct-materials))]
               ["Factory Rent" (str "$" (format "%,d" factory-rent))]
               ["Direct Labor" (str "$" (format "%,d" direct-labor))]
               ["Equipment Depreciation" (str "$" (format "%,d" equipment-depreciation))]
               ["Packaging Supplies" (str "$" (format "%,d" packaging-supplies))]]
        {:keys [answers correct-index]}
        (generate-wrong-answers
         "Direct Materials, Direct Labor, and Packaging Supplies"
         ["Factory Rent and Equipment Depreciation"
          "Only Direct Materials"
          "All of the above"])
        ;; If guilty, show that Vinnie's ledger has these costs classified wrong
        ledger-note (if guilty?
                      "Vinnie's internal ledger shows Direct Materials and Direct Labor as FIXED costs."
                      nil)]
    {:data {:costs costs
            :ledger-note ledger-note}
     :answers answers
     :correct-index correct-index
     :question "Which of these costs would increase if production doubled?"
     :result-text "Correct! Direct Materials, Direct Labor, and Packaging Supplies are variable costs - they increase with production volume. Factory Rent and Equipment Depreciation are fixed costs."}))

;; Cost Behavior - calculating variable cost per unit
;; Suspect: Tony (Purchasing)
(defn generate-cost-behavior [guilty?]
  (let [cost-per-unit (rand-between 20 35)
        inflated-cost (+ cost-per-unit 5)
        units [1000 1500 2000 1200]
        months ["January" "February" "March" "April"]
        table (vec (cons ["Month" "Units Produced" "Material Cost"]
                         (map (fn [month u]
                                [month
                                 (format "%,d" u)
                                 (str "$" (format "%,d" (* u cost-per-unit)))])
                              months units)))
        correct-str (str "$" cost-per-unit " per unit")
        wrong-costs [(- cost-per-unit 5) inflated-cost (+ cost-per-unit 10)]
        {:keys [answers correct-index]}
        (generate-wrong-answers
         correct-str
         (map #(str "$" % " per unit") wrong-costs))
        ;; If guilty, Tony's purchase orders show a higher price
        po-note (if guilty?
                  (str "Tony's approved purchase orders show materials at $" inflated-cost " per unit.")
                  (str "Tony's approved purchase orders show materials at $" cost-per-unit " per unit."))]
    {:data {:table table
            :po-note po-note}
     :answers answers
     :correct-index correct-index
     :cost-per-unit cost-per-unit
     :question "What is the variable cost per unit for materials?"
     :result-text (str "Correct! The variable cost is $" cost-per-unit " per unit. You can verify this by dividing any month's material cost by units produced.")}))

;; Mixed Costs - high-low method to find fixed cost
;; Suspect: Maria (Warehouse)
(defn generate-mixed-costs [guilty?]
  (let [fixed-cost (rand-multiple 800 1500 100)
        inflated-fixed (+ fixed-cost 500)
        variable-rate (rand-between 15 25)
        shipments [100 150 200 250]
        months ["January" "February" "March" "April"]
        table (vec (cons ["Month" "Shipments" "Total Shipping Cost"]
                         (map (fn [month s]
                                [month
                                 (str s)
                                 (str "$" (format "%,d" (+ fixed-cost (* s variable-rate))))])
                              months shipments)))
        correct-str (str "$" (format "%,d" fixed-cost))
        wrong-fixed [inflated-fixed (- fixed-cost 300) (+ fixed-cost 1000)]
        {:keys [answers correct-index]}
        (generate-wrong-answers
         correct-str
         (map #(str "$" (format "%,d" %)) wrong-fixed))
        ;; If guilty, Maria's expense reports show inflated fixed costs
        expense-note (if guilty?
                       (str "Maria's expense reports claim the fixed shipping cost is $" (format "%,d" inflated-fixed) " per month.")
                       (str "Maria's expense reports claim the fixed shipping cost is $" (format "%,d" fixed-cost) " per month."))]
    {:data {:table table
            :expense-note expense-note}
     :answers answers
     :correct-index correct-index
     :fixed-cost fixed-cost
     :variable-rate variable-rate
     :question "Using the high-low method, what is the fixed cost component of shipping?"
     :result-text (str "Correct! Using the high-low method: Variable rate = ($" (format "%,d" (+ fixed-cost (* 250 variable-rate))) " - $" (format "%,d" (+ fixed-cost (* 100 variable-rate))) ") / (250 - 100) = $" variable-rate " per shipment. Fixed cost = $" (format "%,d" (+ fixed-cost (* 100 variable-rate))) " - ($" variable-rate " x 100) = $" (format "%,d" fixed-cost) ".")}))

;; Total Cost calculation
;; Suspect: Sal (Sales)
(defn generate-total-cost [guilty?]
  (let [fixed-costs (rand-multiple 15000 25000 1000)
        variable-per-unit (rand-between 12 20)
        units (rand-multiple 4000 6000 500)
        actual-total (+ fixed-costs (* variable-per-unit units))
        inflated-total (+ actual-total (rand-multiple 20000 35000 5000))
        ;; If guilty, Sal's budget shows inflated total; if innocent, it matches
        budget-total (if guilty? inflated-total actual-total)
        correct-str (str "$" (format "%,d" actual-total))
        wrong-totals [inflated-total
                      (- actual-total 20000)
                      (+ fixed-costs (* units (+ variable-per-unit 5)))]
        {:keys [answers correct-index]}
        (generate-wrong-answers
         correct-str
         (map #(str "$" (format "%,d" %)) wrong-totals))]
    {:data {:info (str "Projection for " (format "%,d" units) " units:")
            :costs [["Fixed Costs (rent, salaries)" (str "$" (format "%,d" fixed-costs))]
                    ["Variable Cost per Unit" (str "$" variable-per-unit)]]
            :budget-note (str "Sal's submitted budget claims total cost will be $" (format "%,d" budget-total) ".")}
     :answers answers
     :correct-index correct-index
     :actual-total actual-total
     :question (str "What should the actual total cost be for " (format "%,d" units) " units?")
     :result-text (str "Correct! Total Cost = Fixed Costs + (Variable Cost per Unit x Units) = $" (format "%,d" fixed-costs) " + ($" variable-per-unit " x " (format "%,d" units) ") = $" (format "%,d" actual-total) ".")}))

;; Mapping of clue-id to suspect-id
(def clue-suspect-map
  {:clue-cost-classification :vinnie-numbers
   :clue-cost-behavior :tony-receipts
   :clue-mixed-costs :maria-books
   :clue-total-cost :sal-percentage})

(defn generate-all-clue-data [culprit-id]
  "Generates randomized data for all clues in a case.
   Only the culprit's clue will show discrepancies."
  {:clue-cost-classification (generate-cost-classification (= culprit-id :vinnie-numbers))
   :clue-cost-behavior (generate-cost-behavior (= culprit-id :tony-receipts))
   :clue-mixed-costs (generate-mixed-costs (= culprit-id :maria-books))
   :clue-total-cost (generate-total-cost (= culprit-id :sal-percentage))})

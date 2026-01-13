(ns sabc.suspects
  (:require [datomic.api :as d]
            [sabc.db :as db]))

(def suspects
  [{:suspect/id :vinnie-numbers
    :suspect/name "Vincent 'Numbers' Moretti"
    :suspect/department "Production"
    :suspect/description "The Production Manager. A meticulous man who runs the factory floor with an iron fist. He's been with Consolidated Paesan for 20 years and knows every machine by name. Some say he's too attached to the old ways of doing things."
    :suspect/image "vinnie"}

   {:suspect/id :tony-receipts
    :suspect/name "Tony 'Receipts' Calabrese"
    :suspect/department "Purchasing"
    :suspect/description "The Purchasing Agent. A smooth talker who handles all vendor relationships. He always seems to have a new watch or car, which he claims are 'gifts from grateful suppliers.' His desk is suspiciously clean for someone who handles so much paperwork."
    :suspect/image "tony"}

   {:suspect/id :maria-books
    :suspect/name "Maria 'The Books' Fontana"
    :suspect/department "Warehouse"
    :suspect/description "The Warehouse Supervisor. Despite her nickname, she doesn't work in accounting - she just keeps perfect inventory records. Or so she claims. She started in the mailroom and worked her way up. Knows where everything is stored."
    :suspect/image "maria"}

   {:suspect/id :sal-percentage
    :suspect/name "Sal 'Percentage' Rizzo"
    :suspect/department "Sales"
    :suspect/description "The Sales Director. A flashy dresser who brings in big contracts. He's always talking about his commission checks and vacation homes. The sales numbers look great on paper, but some of the deals seem too good to be true."
    :suspect/image "sal"}

   {:suspect/id :gina-benefits
    :suspect/name "Gina 'Benefits' Napolitano"
    :suspect/department "HR"
    :suspect/description "The HR Manager. Handles all employee records, payroll, and benefits. She's known for her 'creative' interpretation of expense policies. Always organizing company events that seem to cost more than budgeted."
    :suspect/image "gina"}])

(defonce suspects-loaded? (atom false))

(defn load-suspects! []
  "Loads suspect data into the database if not already loaded."
  (when-not @suspects-loaded?
    @(d/transact (db/conn) suspects)
    (reset! suspects-loaded? true)))

(defn get-all-suspects []
  "Returns all suspects from the database."
  (d/q '[:find [(pull ?e [:suspect/id :suspect/name :suspect/department :suspect/description :suspect/image]) ...]
         :where [?e :suspect/id _]]
       (db/db)))

(defn get-suspect-by-id [suspect-id]
  "Returns a suspect by their keyword id."
  (d/q '[:find (pull ?e [:suspect/id :suspect/name :suspect/department :suspect/description :suspect/image]) .
         :in $ ?id
         :where [?e :suspect/id ?id]]
       (db/db) suspect-id))

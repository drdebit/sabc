(ns sabc.schema
  (:require [datomic.api :as d]
            [sabc.db :as db]))

(def schema [;; Users
             {:db/ident :user/email
              :db/valueType :db.type/string
              :db/unique :db.unique/identity
              :db/cardinality :db.cardinality/one
              :db/doc "The user's e-mail address."}
             {:db/ident :user/pin
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc "The user's PIN for use in web forms."}
             {:db/ident :user/current-game
              :db/valueType :db.type/ref
              :db/cardinality :db.cardinality/one
              :db/doc "The user's current in-progress game."}
             ;; Story
             {:db/ident :story/id
              :db/valueType :db.type/bigint
              :db/cardinality :db.cardinality/one
              :db/doc "A unique identifier to identify a story entry."}
             {:db/ident :story/tag
              :db/valueType :db.type/keyword
              :db/unique :db.unique/identity
              :db/cardinality :db.cardinality/one
              :db/doc "The tag for the story entry, for querying and display."}
             {:db/ident :story/choose-text
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc "The choose text for the story entry. This is used for buttons leading down the tree."}
             {:db/ident :story/return-text
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc "The return text for the story entry. This is used for buttons leading up the tree."}
             {:db/ident :story/text
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc "The text of the story entry."}
             {:db/ident :story/child
              :db/valueType :db.type/ref
              :db/cardinality :db.cardinality/many
              :db/doc "The story entry's children."}
             {:db/ident :story/entry-transformations
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc "A stringified map representing the entries and attributes transformed by choosing the entry."}
             ;; Games
             {:db/ident :game/id
              :db/valueType :db.type/bigint
              :db/cardinality :db.cardinality/one
              :db/doc "A unique identifier to identify a game."}
             {:db/ident :game/uuid
              :db/valueType :db.type/uuid
              :db/cardinality :db.cardinality/one
              :db/unique :db.unique/identity
              :db/doc "A UUID to resolve game entries."}
             {:db/ident :game/entry-transformations
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc "A transformation to apply to story entries. Represented as a stringified map indexed by entry tags containing a map of attributes and their new values."}
             {:db/ident :game/location
              :db/valueType :db.type/ref
              :db/cardinality :db.cardinality/one
              :db/doc "Reference to the story entry at which the player currently is."}
             {:db/ident :game/case
              :db/valueType :db.type/ref
              :db/cardinality :db.cardinality/one
              :db/doc "Reference to the case being played."}
             {:db/ident :game/culprit
              :db/valueType :db.type/ref
              :db/cardinality :db.cardinality/one
              :db/doc "Reference to the guilty suspect (randomly selected at game start)."}
             {:db/ident :game/clues-solved
              :db/valueType :db.type/ref
              :db/cardinality :db.cardinality/many
              :db/doc "References to clues the player has correctly solved."}
             {:db/ident :game/clues-attempted
              :db/valueType :db.type/ref
              :db/cardinality :db.cardinality/many
              :db/doc "References to clues the player has attempted (right or wrong)."}
             {:db/ident :game/wrong-accusations
              :db/valueType :db.type/long
              :db/cardinality :db.cardinality/one
              :db/doc "Number of wrong accusations made."}
             {:db/ident :game/solved
              :db/valueType :db.type/boolean
              :db/cardinality :db.cardinality/one
              :db/doc "Whether the case has been solved."}
             {:db/ident :game/clue-data
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc "Generated clue data for this game (EDN string)."}

             ;; Cases (Mysteries)
             {:db/ident :case/id
              :db/valueType :db.type/keyword
              :db/unique :db.unique/identity
              :db/cardinality :db.cardinality/one
              :db/doc "Unique identifier for the case."}
             {:db/ident :case/title
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc "Display title of the case."}
             {:db/ident :case/description
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc "Introduction narrative for the case."}
             {:db/ident :case/difficulty
              :db/valueType :db.type/long
              :db/cardinality :db.cardinality/one
              :db/doc "Difficulty level 1-5."}
             {:db/ident :case/suspects
              :db/valueType :db.type/ref
              :db/cardinality :db.cardinality/many
              :db/doc "Suspects involved in this case."}
             {:db/ident :case/clues
              :db/valueType :db.type/ref
              :db/cardinality :db.cardinality/many
              :db/doc "Clues available in this case."}
             {:db/ident :case/min-clues
              :db/valueType :db.type/long
              :db/cardinality :db.cardinality/one
              :db/doc "Minimum clues needed to make an accusation."}

             ;; Suspects
             {:db/ident :suspect/id
              :db/valueType :db.type/keyword
              :db/unique :db.unique/identity
              :db/cardinality :db.cardinality/one
              :db/doc "Unique identifier for the suspect."}
             {:db/ident :suspect/name
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc "Display name of the suspect."}
             {:db/ident :suspect/department
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc "Department the suspect works in."}
             {:db/ident :suspect/description
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc "Character background and description."}
             {:db/ident :suspect/image
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc "Image identifier or URL for the suspect."}

             ;; Clues
             {:db/ident :clue/id
              :db/valueType :db.type/keyword
              :db/unique :db.unique/identity
              :db/cardinality :db.cardinality/one
              :db/doc "Unique identifier for the clue."}
             {:db/ident :clue/concept
              :db/valueType :db.type/keyword
              :db/cardinality :db.cardinality/one
              :db/doc "Accounting concept tested (e.g., :cost-behavior, :cvp)."}
             {:db/ident :clue/narrative
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc "Sofia's explanation/context for the clue."}
             {:db/ident :clue/data
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc "Accounting data to display (EDN string)."}
             {:db/ident :clue/question
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc "The question the player must answer."}
             {:db/ident :clue/answers
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc "Answer options (EDN vector of strings)."}
             {:db/ident :clue/correct-index
              :db/valueType :db.type/long
              :db/cardinality :db.cardinality/one
              :db/doc "Index of the correct answer (0-based)."}
             {:db/ident :clue/hint
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc "Hint Sofia can provide if requested."}
             {:db/ident :clue/guilty-text
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc "Text shown when clue is solved and suspect IS the culprit."}
             {:db/ident :clue/cleared-text
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc "Text shown when clue is solved and suspect is NOT the culprit."}
             {:db/ident :clue/suspect
              :db/valueType :db.type/ref
              :db/cardinality :db.cardinality/one
              :db/doc "The suspect this clue points to when solved."}
             {:db/ident :clue/department
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc "Department where this clue is found."}

             ;; User extensions for progress
             {:db/ident :user/completed-cases
              :db/valueType :db.type/ref
              :db/cardinality :db.cardinality/many
              :db/doc "Cases the user has successfully completed."}
             {:db/ident :user/max-difficulty
              :db/valueType :db.type/long
              :db/cardinality :db.cardinality/one
              :db/doc "Highest difficulty level unlocked (starts at 1)."}])

(defonce schema-installed? (atom false))

(defn install-schema! []
  "Installs the schema if not already installed."
  (when-not @schema-installed?
    @(d/transact (db/conn) schema)
    (reset! schema-installed? true)))

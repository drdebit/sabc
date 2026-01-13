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
              :db/doc "Reference to the story entry at which the player currently is."}])

(defonce schema-installed? (atom false))

(defn install-schema! []
  "Installs the schema if not already installed."
  (when-not @schema-installed?
    @(d/transact (db/conn) schema)
    (reset! schema-installed? true)))

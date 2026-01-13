(ns sabc.story
  (:require [datomic.api :as d]
            [sabc.db :as db]))

;; Utilities
(defn read-structure [s]
  (when (not (empty? s)) (read-string s)))

;; Data
(def text-map {:init {:return-text "Think again."
                      :text [:div [:p "You've just started work at Consolidated Paesan, " [:b "the"] " premiere employment destination for Italian Americans."]
                             [:p "You work in the mailroom. One morning, a short, shifty-looking gentleman approaches you. \"Hey you,\" he says. \"Don Paesan wants to see you.\" Don Paesan is the CEO and Big Boss of Consolidated Paesan. You swallow. What could the Don want to see you about?"]
                             [:p "You take the fancy elevator all the way to the top of Cosa Nostra Tower. When you reach the top a receptionist greets you brightly. \"The Don is waiting for you. Go right in.\" You straighten your jacket and go in."]
                             [:p "Don Paesan is the biggest man you've ever seen, sitting behind an even bigger desk. He shifts a cigar from one corner of his mouth to the other. When he sees you he stands and spreads his arms wide. \"Ah, here you are. Come in, come in.\" You decline the offered drink (it's only 9am) and have a seat across from the Don."]
                             [:p "He leans towards you conspiratorially. \"I run a tight ship. But somewhere in this organization there's a rat. I want you to smell them out.\" He gestures to a corner of the room, and a small woman you hadn't noticed before stands up. She wears thick spectacles. \"Take Sofia here. She knows how things work around here.\" Sofia smiles and nods. \"So,\" the Don fixes you with a penetrating glare. \"Whaddya say?\""]]
                      :links [:initYes :initNo]}
               :initYes {:choose-text "Of course, Don, right away."
                         :text [:div [:p "\"Excellent!\" The Don comes around the desk and claps you on the back, causing you to almost bite your tongue. \"Keep me in the loop. Happy hunting.\""]]
                         :links [:stepFirst]
                         :tf {:init {:return-text "Talk to the boss again."
                                              :text "What do you want?"}
                                       :initYes {:choose-text "Nevermind."}}}
               :initNo {:choose-text "I think maybe you have the wrong guy."
                        :text [:div [:p "The Don's glare turns steely. \"Think again.\""]]}
               :stepFirst {:choose-text "Here we go!"
                           :text [:div [:p "If you're going to find out who's stealing from the Don, you're going to need to know a thing or two about how Consolidated Paesan keeps its records. This is a balance sheet:"]]
                           :table {}}})

(defn datomize-entries [m]
  (mapv (fn [[k v]] (into {} (filter val {:story/tag k
                                          :story/choose-text (:choose-text v)
                                          :story/return-text (:return-text v)
                                          :story/text (str (:text v))
                                          :story/entry-transformations (str
                                                                        (:tf v))})))
        m))

(defn link-entries [m]
  (vec (mapcat (fn [[k v]]
                 (let [links (:links v)]
                  (mapv (fn [c] {:db/id [:story/tag k]
                                  :story/child {:db/id [:story/tag c]}}) links))) m)))

(defonce story-loaded? (atom false))

(defn load-story! []
  "Loads story data into the database if not already loaded."
  (when-not @story-loaded?
    @(d/transact (db/conn) (datomize-entries text-map))
    @(d/transact (db/conn) (link-entries text-map))
    (reset! story-loaded? true)))

(defn child-list [k]
  (->> (d/q '[:find ?tagc ?ctext
              :in $ ?tagp
              :where [?e :story/tag ?tagp]
              [?e :story/child ?child]
              [?child :story/tag ?tagc]
              [?child :story/choose-text ?ctext]]
            (db/db) k)
       (apply vector)
       (mapv (fn [[k v]] {k {:choose-text v}}))))

(defn parent-list [k]
  (->> (d/q '[:find ?tagp ?rtext
              :in $ ?tagc
              :where [?e :story/tag ?tagp]
              [?e :story/child ?child]
              [?child :story/tag ?tagc]
              [?e :story/return-text ?rtext]]
            (db/db) k)
       (apply vector)
       (mapv (fn [[k v]] {k {:return-text v}}))))

(defn apply-transformations [k ms]
  (let [tf (->> (d/q '[:find ?tf
                       :in $ ?tag
                       :where [?e :story/tag ?tag]
                       [?e :story/entry-transformations ?tf]]
                     (db/db) k)
                first
                (apply read-structure))]
    (cond
      (vector? ms) (mapv #(apply-transformations k %) ms)
      :else (into ms (select-keys tf (keys ms))))))

(defn select-one [vr key]
  (vec
   (filter #(not (nil? (second %)))
           (mapv (fn [m]
                   (vec (mapcat (fn [[k v]] [k (get v key)]) m))) vr))))

(defn entry-data [k]
  (->> (d/q '[:find ?text
              :in $ ?tag
              :where [?e :story/tag ?tag]
              [?e :story/text ?text]]
            (db/db) k)
       first
       (apply read-structure)
       (hash-map :text)
       (apply-transformations k)
       (into {:parents (select-one (apply-transformations k (parent-list k)) :return-text)
              :children (select-one (apply-transformations k (child-list k)) :choose-text)})))

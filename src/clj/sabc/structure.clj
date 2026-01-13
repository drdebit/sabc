(ns sabc.structure)

(defn rower [k length gen-f]
  (let [tder (fn [x] [:td x])]
    (into [:tr {:id k}]
          (map tder (gen-f length)))))

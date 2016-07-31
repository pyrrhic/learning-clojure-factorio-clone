(ns proja.utils)

(def tile-size 32)

(defn world->grid [n]
  (/ n tile-size))

(defn grid->world [n]
  (float (* n tile-size)))

(defn dissoc-in
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (assoc m k newmap))
      m)
    (dissoc m k)))
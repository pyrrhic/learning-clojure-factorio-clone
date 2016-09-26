(ns proja.belt-group
  (:require [proja.ecs.core :as ecs]
            [proja.utils :as utils]
            [proja.screens.game :as game]))

(defn neighbor-belt-ids [ecs ent-map ent-id]
  (let [neighbor-buildings-fn (fn [ent-map transform]
                                (let [x     (:x transform)
                                      y     (:y transform)
                                      north (get-in ent-map [(utils/ent-map-key transform 0 1) :building-id])
                                      south (get-in ent-map [(utils/ent-map-key transform 0 -1) :building-id])
                                      east  (get-in ent-map [(utils/ent-map-key transform 1 0) :building-id])
                                      west  (get-in ent-map [(utils/ent-map-key transform -1 0) :building-id])]
                                  (filter #(not (nil? %)) [north south east west])))
        is-belt-fn            (fn [building-id]
                                (if (nil? (ecs/unsafe-component ecs :belt-mover building-id))
                                  false
                                  building-id))]
    (let [neighbor-building-ids (neighbor-buildings-fn ent-map (ecs/component ecs :transform ent-id))]
      (if (empty? neighbor-building-ids)
        "it empty yo"
        (map is-belt-fn neighbor-building-ids)
        ))))

;TODO this is going to break if i ever re-order the systems past the 4th one...
(defn get-all-belt-ids [ecs]
  (-> ecs :systems (nth 3) :qualifying-ents))

(defn belt-facing-this? [this-belt belt]
  {:pre [ ;only one belt per tile allowed. checking for fat fingering.
         (not (and (== (:x this-belt) (:x belt))
                   (== (:y this-belt) (:y belt))))]}
  "this-belt and belt should be transform components.
  this function does not check if the belts are right next to each other."
  (if (== (:y this-belt) (:y belt)) ;same row
    (if (< (:x this-belt) (:x belt))
      (== 270 (:rotation belt))
      (== 90 (:rotation belt)))
    (if (== (:x this-belt) (:x belt)) ;same column
      (if (< (:y this-belt) (:y belt))
        (== 180 (:rotation belt))
        (== 0 (:rotation belt)))
      (throw (IllegalArgumentException. "Should not be passed diagonal belts.
      Assumption is that neighbors are being checked, and neighbors are only
      N/S/E/W, not diagonal.")))))

(defn- either-belt-facing? [this-belt neighbor-belt]
  "this-belt and neighbor-belt should be transforms.
  If this belt feeds into the neighbor belt, or the neighbor belt feeds
  into this belt, and the neighbor belt is not already included in a group
  (the same group as this-belt), return true, else false."
  (or (belt-facing-this? this-belt neighbor-belt)
      (belt-facing-this? neighbor-belt this-belt)))

(defn- vector-contains? [vecc value]
  (not-empty (filter #{value} vecc))
  #_(->> (map #(= % value) vecc)
       (reduce #(or %1 %2))))

(defn- neighbor-in-group? [neighbor-id group-idx groups]
  "this-group will be an int, index in groups vector
   groups is a vector of vector belt-ids."
  (vector-contains? (nth groups group-idx) neighbor-id))

(defn- valid-belt-neighbor? [this-belt-id neighbor-id ecs group-idx groups]
  (boolean (and (either-belt-facing? (ecs/component ecs :transform this-belt-id)
                                     (ecs/component ecs :transform neighbor-id))
                (not (neighbor-in-group? neighbor-id group-idx groups)))))

(defn- valid-belt-neighbors [belt-id current-group groups ecs ent-map]
  (->> (map #(if (valid-belt-neighbor? belt-id % ecs current-group groups)
              %
              nil)
            (neighbor-belt-ids ecs ent-map belt-id))
       (filter #(not (nil? %)))
       (vec)))

(defn- value-idx [vektor value]
  "returns index for the first instance of the value found in vector.
   returns nil if value not found."
  (loop [idx (dec (count vektor))]
    (cond
      (neg? idx)
      nil

      (= value (nth vektor idx))
      idx

      :else
      (recur (dec idx)))))

(defn- remove-value [vector value]
  (let [idx (value-idx vector value)]
    (vec (concat (subvec vector 0 idx)
                 (subvec vector (inc idx) (count vector))))))

;[[vector of the belt ids that are in this group, which is by index, so group 0]
; [another vector, different belt id's. this would be group 1]]

;each pull from open means new group.


;does not work with loops.
;do i care?
;throws a null pointer error.
(defn create-belt-groups [ecs ent-map]
  "do not call if there are no belts. returns nil exception."
  (loop [open (apply vector (get-all-belt-ids ecs))
         current-group nil
         groups []
         intersect []]
    (if (and (nil? current-group) (pos? (count open)))
      (recur (pop open)
             0
             [[(peek open)]]
             [])
      (let [belt-id (peek (nth groups current-group))
            neighs (valid-belt-neighbors belt-id current-group groups ecs ent-map)
            num-neighs (count neighs)]
        (cond
          (zero? num-neighs)
          (cond
            (pos? (count intersect))
            (recur open
                   current-group
                   (update groups current-group #(conj % (peek intersect)))
                   (pop intersect))

            (pos? (count open))
            (recur (pop open)
                   (inc current-group)
                   (conj groups [(peek open)])
                   intersect)

            :else
            groups)

          (== 1 num-neighs)
          (recur (remove-value open (peek neighs))
                 current-group
                 (update groups current-group #(conj % (peek neighs)))
                 intersect)

          (> num-neighs 1)
          (recur (vec (remove (set neighs) open)) ;(remove-value open (peek neighs))
                 current-group
                 (update groups current-group #(conj % (peek neighs)))
                 (into intersect (pop neighs)))))))
  
  #_(loop [open (apply vector (get-all-belt-ids ecs))
         current-group nil
         groups []
         intersect []]
    (if (and (nil? current-group) (pos? (count open)))
      (recur (pop open)
             0
             [[(peek open)]]
             [])
      (let [belt-id (peek (nth groups current-group))
            neighs (valid-belt-neighbors belt-id current-group groups ecs ent-map)
            num-neighs (count neighs)]
        (cond
          (zero? num-neighs)
          (cond
            (pos? (count intersect))
            (recur open
                   current-group
                   (update groups current-group #(conj % (peek intersect)))
                   (pop intersect))

            (pos? (count open))
            (recur (pop open)
                   (inc current-group)
                   (conj groups [(peek open)])
                   intersect)

            :else
            groups)

          (== 1 num-neighs)
          (recur (remove-value open (peek neighs))
                 current-group
                 (update groups current-group #(conj % (peek neighs)))
                 intersect)

          (> num-neighs 1)
          (recur (vec (remove (set neighs) open)) ;(remove-value open (peek neighs))
                 current-group
                 (update groups current-group #(conj % (peek neighs)))
                 (into intersect (pop neighs))))))))

(defn belt-direction [transform]
  "{:x -1 or 0 or 1
    :y -1 or 0 or 1}"
  (case (:rotation transform)
    0 {:x 0, :y 1}
    90 {:x 1, :y 0}
    180 {:x 0, :y -1}
    270 {:x -1, :y 0}))

(defn end-belts [groups ent-map]
  "desc: groups should be a vector of vectors. each child vector has belt ids, and the
         child vector represents a group.
  returns: a vector of belt-ids. the belt-ids represent the 'end belt' for
           that group."
  )

;(defn belt-update-order [group]
;  "group is a vector of belt-ids"
;  (loop [open []
;         closed []]))

#_(loop [g group]
  (if (empty? g)
    nil
    (let [belt-id (first g)
          belt-transform (ecs/component ecs :transform belt-id)
          belt-dir       (belt-direction belt-transform)
          faced-building (get-in ent-map
                                 [(utils/ent-map-key belt-transform
                                                     (:x belt-dir)
                                                     (:y belt-dir))
                                  :building-id])]
      (if (nil? (ecs/unsafe-component ecs :belt-mover faced-building))
        belt-id
        (recur (rest g))
        ))))
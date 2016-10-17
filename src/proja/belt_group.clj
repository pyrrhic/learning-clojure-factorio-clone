(ns proja.belt-group
  (:require [proja.ecs.core :as ecs]
            [proja.utils :as utils]
            [proja.screens.game :as game]))

(defn neighbor-belt-ids [ecs ent-map ent-id]
  (let [neighbor-buildings-fn (fn neighbor-buildings [ent-map transform]
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
        []
        (remove false? (map is-belt-fn neighbor-building-ids))
        ))))

;TODO this is going to break if i ever re-order the systems past the 4th one...
(defn get-all-belt-ids [ecs]
  (-> (filter
        #(= {:and #{:belt-mover}} (:predicates %))
        (-> game/g :ecs :systems))
      first
      :qualifying-ents))

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
  into this belt, return true, else false."
  (or (belt-facing-this? this-belt neighbor-belt)
      (belt-facing-this? neighbor-belt this-belt)))

(defn facing-each-other? [this-belt neighbor-belt]
  (and (belt-facing-this? this-belt neighbor-belt)
       (belt-facing-this? neighbor-belt this-belt)))

(defn- contains-value? [vecc value]
  (boolean (not-empty (filter #{value} vecc)))
  #_(->> (map #(= % value) vecc)
       (reduce #(or %1 %2))))

(defn- neighbor-in-group? [neighbor-id group-idx groups]
  "this-group will be an int, index in groups vector
   groups is a vector of vector belt-ids."
  (contains-value? (nth groups group-idx) neighbor-id))

(defn- valid-belt-neighbor? [this-belt-id neighbor-id ecs group-idx groups]
  (let [this-belt (ecs/component ecs :transform this-belt-id)
        neighbor-belt (ecs/component ecs :transform neighbor-id)]
    (boolean (and (either-belt-facing? this-belt neighbor-belt)
                  (not (facing-each-other? this-belt neighbor-belt))
                  (not (neighbor-in-group? neighbor-id group-idx groups))))))

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
  (if (empty? vector)
    vector
    (try
      (let [idx (value-idx vector value)]
        (if idx
          (vec (concat (subvec vector 0 idx)
                       (subvec vector (inc idx) (count vector))))
          vector))
      (catch NullPointerException npe
        (println [vector value])
        (throw npe))
      )))

;[[vector of the belt ids that are in this group, which is by index, so group 0]
; [another vector, different belt id's. this would be group 1]]
(defn create-belt-groups
  ([ecs ent-map]
    (create-belt-groups ecs ent-map (apply vector (get-all-belt-ids ecs))))
  ([ecs ent-map open-init]
   (if (empty? open-init)
     '()
     (loop [open open-init
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
               ;if there is a loop, the last belt that is looked at will be added twice.
               ;so this is a quick hack way to ensure the belt-ids are unique.
               ;what I should do is change the algorithm to not add the last belt twice if there is a loop.
               (map set groups))

             (== 1 num-neighs)
             (recur (remove-value open (peek neighs))
                    current-group
                    (update groups current-group #(conj % (peek neighs)))
                    intersect)

             (> num-neighs 1)
             (recur (vec (remove (set neighs) open))        ;(remove-value open (peek neighs))
                    current-group
                    (update groups current-group #(conj % (peek neighs)))
                    (into intersect (pop neighs))))))))))

(defn belt-direction [transform]
  "{:x -1 or 0 or 1
    :y -1 or 0 or 1}"
  (case (:rotation transform)
    0 {:x 0, :y 1}
    90 {:x 1, :y 0}
    180 {:x 0, :y -1}
    270 {:x -1, :y 0}))

(defn is-belt? [ent-id ecs]
  (boolean (ecs/unsafe-component ecs :belt-mover ent-id)))

(defn end-belt [group ent-map ecs]
  "end belt-id or nil if it's a loop"
  (->> (for [belt-id group]
         (let [belt-transform (ecs/component ecs :transform belt-id)
               belt-dir (belt-direction belt-transform)
               building-id-faced (get-in ent-map
                                         [(utils/ent-map-key belt-transform (:x belt-dir) (:y belt-dir))
                                          :building-id])]
           (cond
             (nil? building-id-faced)
             belt-id

             (and (is-belt? building-id-faced ecs)
                  (not (contains-value? group building-id-faced)))
             belt-id

             (not (is-belt? building-id-faced ecs))
             belt-id)))
       (remove nil?)
       (#(if (empty? %) nil (first %)))))

(defn belt-in-loop [group ent-map ecs]
  (loop [belt-id (first group)
         visited #{}]
    (let [belt-trans (ecs/component ecs :transform belt-id)
          belt-dir (belt-direction belt-trans)
          neigh-id (get-in ent-map [(utils/ent-map-key belt-trans (:x belt-dir) (:y belt-dir)) :building-id])]
      (if (contains-value? visited neigh-id)
        neigh-id
        (recur neigh-id
               (conj visited neigh-id))))))

(defn starting-belt-id [group ent-map ecs]
  (let [end-belt-id (end-belt group ent-map ecs)]
    (if end-belt-id
      end-belt-id
      (belt-in-loop group
                    ent-map
                    ecs))))

(defn updateable-neighbors [this-belt-id ent-map ecs group closed]
  (let [neighbors (neighbor-belt-ids ecs ent-map this-belt-id)
        neighs-in-group? (map #(contains-value? group %) neighbors)
        neighs-in-group (->> (map (fn [neigh in-group?] (if in-group?
                                                          neigh
                                                          nil))
                                  neighbors
                                  neighs-in-group?)
                             (remove nil?))
        neighs-in-closed? (map #(contains-value? closed %) neighs-in-group)
        neighs-not-in-closed (->> (map (fn [neigh in-closed?] (if in-closed?
                                                               nil
                                                               neigh))
                                       neighs-in-group
                                       neighs-in-closed?)
                                  (remove nil?))
        neighs-facing-this (->> (map (fn [neigh-id]
                                       (if (belt-facing-this? (ecs/component ecs :transform this-belt-id)
                                                              (ecs/component ecs :transform neigh-id))
                                         neigh-id
                                         nil))
                                     neighs-not-in-closed)
                                (remove nil?))]
    neighs-facing-this))

(defn update-order [group ent-map ecs]
  (loop [closed []
         open [(starting-belt-id group ent-map ecs)]]
    (if (empty? open)
      closed
      (let [this-belt-id (peek open)
            neighs (updateable-neighbors this-belt-id ent-map ecs group (into closed open))
            neigh-count (count neighs)]
        (cond
          (zero? neigh-count)
          (recur (conj closed this-belt-id)
                 (pop open))

          (== neigh-count 1)
          (recur (conj closed this-belt-id)
                 (-> (pop open)
                     (conj (first neighs))))

          :else
          (recur (conj closed this-belt-id)
                 (-> (pop open)
                     (into neighs)))
          )))))

(defn remove-belt-ids [ent-map group ecs]
  "removes the belt-ids in the group from the entity map"
  (loop [grp group
         e-map ent-map]
    (if (empty? grp)
      e-map
      (recur (rest grp)
             (assoc-in e-map [(utils/ent-map-key (ecs/component ecs :transform (first grp)))
                              :building-id]
                       nil)))))

(defn get-loop-belt-ids [group ent-map ecs]
  "returns the belt-ids that are in the loop"
  (loop [belt-id (first group)
         visited #{}
         visited-again #{}]
    (cond
      (contains-value? visited-again belt-id)
      visited-again

      (contains-value? visited belt-id)
      (recur (let [belt-trans (ecs/component ecs :transform belt-id)
                   belt-dir (belt-direction belt-trans)
                   neigh-key (utils/ent-map-key (ecs/component ecs :transform belt-id) (:x belt-dir) (:y belt-dir))]
               (get-in ent-map [neigh-key :building-id]))
             visited
             (conj visited-again belt-id))

      :else
      (recur (let [belt-trans (ecs/component ecs :transform belt-id)
                   belt-dir (belt-direction belt-trans)
                   neigh-key (utils/ent-map-key (ecs/component ecs :transform belt-id) (:x belt-dir) (:y belt-dir))]
               (get-in ent-map [neigh-key :building-id]))
             (conj visited belt-id)
             visited-again)
      )))

(defn break-up-loop-groups [belt-groups ent-map ecs]
  (loop [grps belt-groups
         non-loop-grps []
         loop-grps []]
    (if (empty? grps)
      {:non-loop-groups non-loop-grps
       :loop-groups loop-grps}
      ;if does not have a loop
      (if (end-belt (first grps) ent-map ecs)
        (recur (rest grps)
               (conj non-loop-grps (first grps))
               loop-grps)
        (let [grp (first grps)
              loop-belt-ids (get-loop-belt-ids grp ent-map ecs)
              ent-map-wo-loop (remove-belt-ids ent-map loop-belt-ids ecs)]
          (recur (rest grps)
                 (into non-loop-grps (create-belt-groups ecs
                                                        ent-map-wo-loop
                                                        (vec (remove loop-belt-ids grp))))
                 (conj loop-grps loop-belt-ids)))
        ))))

(defn ordered-belt-groups [game]
  (let [ecs (:ecs game)
        ent-map (:entity-map game)
        belt-groups (break-up-loop-groups (create-belt-groups ecs ent-map) ent-map ecs)
        non-loop-groups (:non-loop-groups belt-groups)
        loop-groups (:loop-groups belt-groups)
        non-loop-ordered (for [belt-grp non-loop-groups]
                           (update-order belt-grp ent-map ecs))
        loop-ordered (for [belt-grp loop-groups]
                       (update-order belt-grp ent-map ecs))]
    (assoc game :belt-update-orders non-loop-ordered
                :loop-belt-update-orders loop-ordered)))
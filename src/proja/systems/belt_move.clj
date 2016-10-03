(ns proja.systems.belt-move
  (:require [proja.utils :as utils]
            [proja.ecs.core :as ecs]
            [proja.components.core :as c]
            [proja.belt-group :as belt-group]))

(defn idle [ent-map ecs ent-id]
  (let [transform (ecs/component ecs :transform ent-id)]
    (if (nil? (->> (utils/ent-map-key transform) (get ent-map) :pickupable))
      {:ecs ecs
       :ent-map ent-map}
      {:ecs (ecs/update-component ecs :belt-mover ent-id #(assoc % :state :moving))
       :ent-map ent-map}
      )))

(defn belt-direction [transform]
  "{:x -1 or 0 or 1
    :y -1 or 0 or 1}"
  (case (:rotation transform)
    0 {:x 0, :y 1}
    90 {:x 1, :y 0}
    180 {:x 0, :y -1}
    270 {:x -1, :y 0}))

(defn dist-to-next-tile
  "Will never return 0"
  ([xy dir]
    (dist-to-next-tile xy 0 dir))
  ([xy origin-xy dir]
   (case dir
     ;just return something big.
     0 utils/tile-size
     ;moving to the right/up. the left/top side of each tile is inclusive.
     1 (- utils/tile-size (mod (+ xy origin-xy)
                               utils/tile-size))
     ;moving to the left/down. the left/down side of each tile is exclusive, so dec the tile-size.
     ;otherwise we have off by 1 error.
     -1 (inc (mod (+ xy origin-xy)
                  utils/tile-size)))))

(defn ent-map-pickupable-id [transform belt-dir ent-map]
  (->> (utils/ent-map-key transform (:x belt-dir) (:y belt-dir))
       (get ent-map)
       :pickupable))

(defn move-transform [transform belt-dir]
  (-> transform
      (assoc :x (+ (:x transform)
                   (:x belt-dir)))
      (assoc :y (+ (:y transform)
                   (:y belt-dir)))))

(defn facing-each-other? [belt-dir-1 belt-dir-2]
  (cond
    (and (== (:x belt-dir-1) 1) (== (:x belt-dir-2) -1)) true
    (and (== (:x belt-dir-2) 1) (== (:x belt-dir-1) -1)) true
    (and (== (:y belt-dir-1) 1) (== (:y belt-dir-2) -1)) true
    (and (== (:y belt-dir-2) 1) (== (:y belt-dir-1) -1)) true
    :else false))

;(defn can-move? [transform belt-dir ent-map ecs]
;  "True if the 'neighbor' tile has a belt and is not facing the current belt.
;  And if neighbor has no pickupable, or the pickupable is moving to another tile, also true."
;  (let [neighbor-key (utils/ent-map-key (-> transform :x (utils/world->grid) (#(+ % (:x belt-dir))))
;                                        (-> transform :y (utils/world->grid) (#(+ % (:y belt-dir)))))
;        building-id (-> ent-map (get neighbor-key) :building-id)
;        neighbor-pickupable-id (-> ent-map (get neighbor-key) :pickupable)]
;    (and
;      building-id
;      (ecs/unsafe-component ecs :belt-mover building-id)
;      (not (facing-each-other? belt-dir (belt-direction (ecs/component ecs :transform building-id))))
;      (or (nil? neighbor-pickupable-id)
;          (let [move-to (ecs/unsafe-component ecs :move-to neighbor-pickupable-id)
;                n-p-transform (ecs/component ecs :transform neighbor-pickupable-id)]
;            (if move-to
;              ;the question here is, am i moving to another tile, or still moving into place in existing tile?
;              (or (not= (utils/world->grid (:x n-p-transform)) (:x move-to))
;                  (not= (utils/world->grid (:y n-p-transform)) (:y move-to)))
;              false))))))

#_(defn move [transform move-to move-rate xy-keyword]
  "returns a transform with updated :x or :y, in the direction towards the move-to"
  (cond
    (> (xy-keyword transform) (utils/grid->world (xy-keyword move-to)))
    (update transform xy-keyword #(- % move-rate))

    (< (xy-keyword transform) (utils/grid->world (xy-keyword move-to)))
    (update transform xy-keyword #(+ % move-rate))

    :else
    transform))

;TODO maybe mark the tile you are moving to with :moving-to in the entity map and :ent-id
;otherwise shit will be obnoxious when you have multiple arms placing on belts.
;then when it arrives, and the switch over in tile happens, get rid of the moving-to from that tile
;the moving to will prevent an arm from placing something there.

#_(defn moving [ent-map ecs ent-id]
  (let [transform (ecs/component ecs :transform ent-id)
        pickupable-ent-id (->> (utils/ent-map-key transform) (get ent-map) :pickupable)
        pickupable-transform (if (nil? pickupable-ent-id) nil (ecs/component ecs :transform pickupable-ent-id))
        belt-dir (belt-direction transform)
        move-to (ecs/unsafe-component ecs :move-to pickupable-ent-id)
        belt-mover (ecs/component ecs :belt-mover ent-id)]
    (cond
      (or (nil? pickupable-ent-id)
          (and (nil? move-to) (not (can-move? transform belt-dir ent-map ecs))))
      {:ecs ecs
       :ent-map ent-map}

      (and (nil? move-to) (can-move? transform belt-dir ent-map ecs))
      {:ecs (let [move-to* (c/move-to (+ (:x belt-dir) (utils/world->grid (:x transform)))
                                      (+ (:y belt-dir) (utils/world->grid (:y transform))))]
              (-> (ecs/add-temp-component ecs
                                          :move-to
                                          move-to*
                                          pickupable-ent-id)
                  (ecs/replace-component :transform
                                         (-> pickupable-transform
                                             (move move-to* (:move-rate belt-mover) :x)
                                             (move move-to* (:move-rate belt-mover) :y))
                                         pickupable-ent-id)))
       :ent-map ent-map}

      ;when this condition trips, it should be done moving, because i'm not checking from the middle/origin of the thing.
      #_(and move-to
             (or (<= (dist-to-next-tile (:x pickupable-transform) (:x belt-dir)) (:move-rate belt-mover))
                 (<= (dist-to-next-tile (:y pickupable-transform) (:y belt-dir)) (:move-rate belt-mover))))
      (and move-to
           (or
             (== (Math/abs (- (utils/grid->world (:x move-to)) (:x pickupable-transform))) (:move-rate belt-mover))
             (== (Math/abs (- (utils/grid->world (:y move-to)) (:y pickupable-transform))) (:move-rate belt-mover))))
      (let [updtd-pick-trans (-> pickupable-transform
                                 (move move-to (:move-rate belt-mover) :x)
                                 (move move-to (:move-rate belt-mover) :y))]
        {:ecs     (-> (ecs/replace-component ecs
                                             :transform
                                             updtd-pick-trans
                                             pickupable-ent-id)
                      (ecs/remove-temp-component :move-to pickupable-ent-id))
         :ent-map (-> (assoc-in ent-map [(utils/ent-map-key transform) :pickupable] nil)
                      (assoc-in [(utils/ent-map-key updtd-pick-trans) :pickupable] pickupable-ent-id))})

      :else
      {:ecs (ecs/replace-component ecs
                                   :transform
                                   (-> pickupable-transform
                                       (move move-to (:move-rate belt-mover) :x)
                                       (move move-to (:move-rate belt-mover) :y))
                                   pickupable-ent-id)
       :ent-map ent-map}
      )))

;(defn run [ent-id game]
;  (let [ecs-ent-map (case (:state (ecs/component (:ecs game) :belt-mover ent-id))
;                      :idle (idle (:entity-map game) (:ecs game) ent-id)
;                      :moving (moving (:entity-map game) (:ecs game) ent-id))]
;    (assoc game :ecs (:ecs ecs-ent-map)
;                :entity-map (:ent-map ecs-ent-map))))

(defn move [transform belt-dir move-rate]
  (assoc transform :x (+ (:x transform) (* (:x belt-dir) move-rate))
                   :y (+ (:y transform) (* (:y belt-dir) move-rate))))

(defn game-updtd-ecs-em [game ecs ent-map]
  (assoc game :ecs ecs
              :entity-map ent-map))

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

(defn any-belt-moving-to-this? [this-belt ent-map ecs]
  (let [transform (ecs/component ecs :transform this-belt)
        north (get-in ent-map [(utils/ent-map-key transform 0 1) :building-id])
        south (get-in ent-map [(utils/ent-map-key transform 0 -1) :building-id])
        east (get-in ent-map [(utils/ent-map-key transform 1 0) :building-id])
        west (get-in ent-map [(utils/ent-map-key transform -1 0) :building-id])]
    (or (and (ecs/unsafe-component ecs :belt-mover north)
             (== 180 (:rotation (ecs/component ecs :transform north)))
             (:moving-item (ecs/component ecs :belt-mover north)))
        (and (ecs/unsafe-component ecs :belt-mover south)
             (== 0 (:rotation (ecs/component ecs :transform south)))
             (:moving-item (ecs/component ecs :belt-mover south)))
        (and (ecs/unsafe-component ecs :belt-mover east)
             (== 270 (:rotation (ecs/component ecs :transform east)))
             (:moving-item (ecs/component ecs :belt-mover east)))
        (and (ecs/unsafe-component ecs :belt-mover west)
             (== 90 (:rotation (ecs/component ecs :transform west)))
             (:moving-item (ecs/component ecs :belt-mover west)))
        )))

(defn can-move? [belt-transform ecs ent-map]
  (let [belt-dir (belt-direction belt-transform)
        neigh-building-id (get-in ent-map [(utils/ent-map-key belt-transform
                                                              (:x belt-dir)
                                                              (:y belt-dir))
                                           :building-id])]
    (if neigh-building-id
      (and (ecs/unsafe-component ecs :belt-mover neigh-building-id)
           (or (:moving-item (ecs/component ecs :belt-mover neigh-building-id))
               (nil? (get-in ent-map [(utils/ent-map-key (ecs/component ecs :transform neigh-building-id))
                                      :pickupable])))
           (not (any-belt-moving-to-this? neigh-building-id ent-map ecs)))
      false)))

(defn update-belt [belt-id game]
  (let [ecs (:ecs game)
        ent-map (:entity-map game)
        belt-mover (ecs/component ecs :belt-mover belt-id)
        belt-trans (ecs/component ecs :transform belt-id)
        energy (ecs/component ecs :energy belt-id)]
    (cond
      (and (nil? (:moving-item belt-mover))
           (get-in ent-map [(utils/ent-map-key belt-trans) :pickupable])
           (== 100 (:current-amount energy))
           (can-move? belt-trans ecs ent-map))
      (game-updtd-ecs-em
        game
        (-> ecs
            (ecs/replace-component
              :belt-mover
              (assoc belt-mover :moving-item (get-in ent-map [(utils/ent-map-key belt-trans) :pickupable])
                                :move-ticks (/ utils/tile-size (:move-rate belt-mover)))
              belt-id)
            (ecs/replace-component
              :energy
              (assoc energy :current-amount 0)
              belt-id))
        ;remove from ent-map so nothing else picks this up. we have started the move.
        (assoc-in ent-map [(utils/ent-map-key belt-trans) :pickupable] nil))

      (and (:moving-item belt-mover)
           (zero? (:move-ticks belt-mover)))
      (game-updtd-ecs-em
        game
        (ecs/replace-component ecs :belt-mover (assoc belt-mover :moving-item nil) belt-id)
        (let [belt-dir (belt-direction (ecs/component ecs :transform belt-id))]
          (assoc-in ent-map
                    [(utils/ent-map-key belt-trans (:x belt-dir) (:y belt-dir)) :pickupable]
                    (:moving-item belt-mover))))

      (and (:moving-item belt-mover)
           (> (:move-ticks belt-mover) 0))
      (game-updtd-ecs-em
        game
        (let [belt-dir (belt-direction (ecs/component ecs :transform belt-id))
              pickupable-trans (-> (ecs/component ecs :transform (:moving-item belt-mover))
                                   (move belt-dir (:move-rate belt-mover)))]
          (-> ecs
              (ecs/replace-component :transform pickupable-trans (:moving-item belt-mover))
              (ecs/replace-component :belt-mover
                                     (update belt-mover :move-ticks dec)
                                     belt-id)))
        ent-map)

      :else
      (game-updtd-ecs-em
        game
        (ecs/update-component ecs :energy belt-id #(assoc % :current-amount 0))
        ent-map)
      )))

(defn update-belt-loop [belt-id game]
  "Does not check can-move, used to avoid deadlock, since loops should always move."
  (let [ecs (:ecs game)
        ent-map (:entity-map game)
        belt-mover (ecs/component ecs :belt-mover belt-id)
        belt-trans (ecs/component ecs :transform belt-id)
        energy (ecs/component ecs :energy belt-id)]
    (cond
      (and (nil? (:moving-item belt-mover))
           (get-in ent-map [(utils/ent-map-key belt-trans) :pickupable])
           (== 100 (:current-amount energy)))
      (game-updtd-ecs-em
        game
        (-> ecs
            (ecs/replace-component
              :belt-mover
              (assoc belt-mover :moving-item (get-in ent-map [(utils/ent-map-key belt-trans) :pickupable])
                                :move-ticks (/ utils/tile-size (:move-rate belt-mover)))
              belt-id)
            (ecs/replace-component
              :energy
              (assoc energy :current-amount 0)
              belt-id))
        ;remove from ent-map so nothing else picks this up. we have started the move.
        (assoc-in ent-map [(utils/ent-map-key belt-trans) :pickupable] nil))

      (and (:moving-item belt-mover)
           (zero? (:move-ticks belt-mover)))
      (game-updtd-ecs-em
        game
        (ecs/replace-component ecs :belt-mover (assoc belt-mover :moving-item nil) belt-id)
        (let [belt-dir (belt-direction (ecs/component ecs :transform belt-id))]
          (assoc-in ent-map
                    [(utils/ent-map-key belt-trans (:x belt-dir) (:y belt-dir)) :pickupable]
                    (:moving-item belt-mover))))

      (and (:moving-item belt-mover)
           (> (:move-ticks belt-mover) 0))
      (game-updtd-ecs-em
        game
        (let [belt-dir (belt-direction (ecs/component ecs :transform belt-id))
              pickupable-trans (-> (ecs/component ecs :transform (:moving-item belt-mover))
                                   (move belt-dir (:move-rate belt-mover)))]
          (-> ecs
              (ecs/replace-component :transform pickupable-trans (:moving-item belt-mover))
              (ecs/replace-component :belt-mover
                                     (update belt-mover :move-ticks dec)
                                     belt-id)))
        ent-map)

      :else
      (game-updtd-ecs-em
        game
        (ecs/update-component ecs :energy belt-id #(assoc % :current-amount 0))
        ent-map)
      )))

(defn update-belts [belt-update-fn belt-update-orders game]
  (loop [belt-groups belt-update-orders
         gam game]
    (if (empty? belt-groups)
      gam
      (recur (rest belt-groups)
             (loop [belt-grp (first belt-groups)
                    g gam]
               (if (empty? belt-grp)
                 g
                 (recur (rest belt-grp)
                        (belt-update-fn (first belt-grp) g))))))))

(defn run-belts [game]
  (->> game
       (update-belts update-belt-loop (:loop-belt-update-orders game))
       (update-belts update-belt (:belt-update-orders game))))

(defn create []
  {:function   run-belts
   :is-belt true
   :predicates {:and #{:belt-mover}}})


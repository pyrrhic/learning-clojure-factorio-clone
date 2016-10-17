(ns proja.systems.belt-move
  (:require [proja.utils :as utils]
            [proja.ecs.core :as ecs]
            [proja.components.core :as c]
            [proja.belt-group :as belt-group]))

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

(defn move [transform belt-dir move-rate]
  (assoc transform :x (+ (:x transform) (* (:x belt-dir) move-rate))
                   :y (+ (:y transform) (* (:y belt-dir) move-rate))))

(defn game-updtd-ecs-em [game ecs ent-map]
  (assoc game :ecs ecs
              :entity-map ent-map))

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
        (ecs/replace-component ecs :energy (assoc energy :current-amount 0) belt-id)
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
        (ecs/replace-component ecs :energy (assoc energy :current-amount 0) belt-id)
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


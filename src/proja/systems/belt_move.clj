(ns proja.systems.belt-move
  (:require [proja.utils :as utils]
            [proja.ecs.core :as ecs]
            [proja.components.core :as c]))

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

(defn can-move? [transform belt-dir ent-map ecs]
  "True if the 'neighbor' tile has a belt and is not facing the current belt.
  And if neighbor has no pickupable, or the pickupable is moving to another tile, also true."
  (let [neighbor-key (utils/ent-map-key (-> transform :x (utils/world->grid) (#(+ % (:x belt-dir))))
                                        (-> transform :y (utils/world->grid) (#(+ % (:y belt-dir)))))
        building-id (-> ent-map (get neighbor-key) :building-id)
        neighbor-pickupable-id (-> ent-map (get neighbor-key) :pickupable)]
    (and
      building-id
      (ecs/unsafe-component ecs :belt-mover building-id)
      (not (facing-each-other? belt-dir (belt-direction (ecs/component ecs :transform building-id))))
      (or (nil? neighbor-pickupable-id)
          (let [move-to (ecs/unsafe-component ecs :move-to neighbor-pickupable-id)
                n-p-transform (ecs/component ecs :transform neighbor-pickupable-id)]
            (if move-to
              ;the question here is, am i moving to another tile, or still moving into place in existing tile?
              (or (not= (utils/world->grid (:x n-p-transform)) (:x move-to))
                  (not= (utils/world->grid (:y n-p-transform)) (:y move-to)))
              false))))))

(defn move [transform move-to move-rate xy-keyword]
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

(defn moving [ent-map ecs ent-id]
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

(defn run [ent-id game]
  (let [ecs-ent-map (case (:state (ecs/component (:ecs game) :belt-mover ent-id))
                      :idle (idle (:entity-map game) (:ecs game) ent-id)
                      :moving (moving (:entity-map game) (:ecs game) ent-id))]
    (assoc game :ecs (:ecs ecs-ent-map)
                :entity-map (:ent-map ecs-ent-map))))

(defn create []
  {:function   run
   :predicates {:and #{:belt-mover}}})

;(ns proja.screens.main-screen)
;(require '[proja.entities.core :as e])
;(update-game! #(assoc % :ecs (e/ore-patch (:ecs game) (:tex-cache game) 5 5)))
;(update-game! #(assoc % :ecs (e/ore-patch (:ecs game) (:tex-cache game) 5 6)))
;(update-game! #(assoc % :ecs (e/ore-patch (:ecs game) (:tex-cache game) 4 7)))
;(update-game! #(assoc % :ecs (e/ore-patch (:ecs game) (:tex-cache game) 4 8)))
;(update-game! #(assoc % :ecs (e/ore-miner (:ecs game) (:tex-cache game) 5 5)))
;(update-game! #(assoc % :ecs (e/ore-miner (:ecs game) (:tex-cache game) 8 5)))
;(update-game! #(assoc % :ecs (e/ore-patch (:ecs game) (:tex-cache game) 0 5)))
;(update-game! #(assoc % :ecs (e/ore-patch (:ecs game) (:tex-cache game) 7 5)))
;
;(loop [ent-ids (range 1 9)]
;  (if (empty? ent-ids)
;    nil
;    (let [id (-> ent-ids (first) (str) (keyword))]
;      (if (not (or (= id :5) (= id :6)))
;        (do (let [ent-id id
;                  k (ent-map-key (-> (ecs/component (:ecs game) :transform ent-id)
;                                     (get :x)
;                                     (proja.utils/world->grid))
;                                 (-> (ecs/component (:ecs game) :transform ent-id)
;                                     (get :y)
;                                     (proja.utils/world->grid)))]
;              (update-game! #(assoc-in % [:entity-map k :ore] #{ent-id})))
;            (recur (rest ent-ids)))
;        (recur (rest ent-ids))))))
;
;(update-game! #(assoc % :ecs (e/arm (:ecs game) (:tex-cache game) 6 9 0)))
;(update-game! #(assoc % :ecs (e/belt (:ecs game) (:tex-cache game) 6 10 90)))
;(update-game! #(assoc % :ecs (e/belt (:ecs game) (:tex-cache game) 7 10 90)))
;(update-game! #(assoc % :ecs (e/belt (:ecs game) (:tex-cache game) 8 10 90)))
;(update-game! #(assoc % :ecs (e/belt (:ecs game) (:tex-cache game) 9 10 90)))
;(update-game! #(assoc % :ecs (e/arm (:ecs game) (:tex-cache game) 6 9 180)))

;(ns proja.screens.main-screen)
;(require '[proja.entities.core :as e])
;(update-game! #(assoc % :ecs (e/ore-miner (:ecs game) (:tex-cache game) 1 1)))
;(update-game! #(assoc % :ecs (e/ore-patch (:ecs game) (:tex-cache game) 1 1)))
;(update-game! #(assoc % :ecs (e/arm (:ecs game) (:tex-cache game) 2 5 0)))
;(update-game! #(assoc % :ecs (e/belt (:ecs game) (:tex-cache game) 2 6 90)))
;(update-game! #(assoc % :ecs (e/belt (:ecs game) (:tex-cache game) 3 6 90)))

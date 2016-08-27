(ns proja.systems.belt-move
  (:require [proja.utils :as utils]
            [proja.ecs.core :as ecs]))

;TODO make belts work if you rotate them.
;TODO do the visual optimization that is worked out on your whiteboard.

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

(defn dist-to-next-tile [xy origin-xy dir]
  (case dir
    ;just return something big.
    0 utils/tile-size
    ;moving to the right/up. the left/top side of each tile is inclusive.
    1 (- utils/tile-size (mod (+ xy origin-xy)
                              utils/tile-size))
    ;moving to the left/down. the left/down side of each tile is exclusive, so dec the tile-size.
    ;otherwise we have off by 1 error.
    -1 (inc (mod (+ xy origin-xy)
                 utils/tile-size))))

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

;TODO you're not actually using move-rate. at all.

(defn moving [ent-map ecs ent-id]
  (let [transform (ecs/component ecs :transform ent-id)
        pickupable-ent-id (->> (utils/ent-map-key transform) (get ent-map) :pickupable)
        pickupable-transform (ecs/component ecs :transform pickupable-ent-id)
        belt-dir (belt-direction transform)]
    (cond
      (or (nil? pickupable-ent-id)
          (and pickupable-ent-id
               ;pickupable on this tile is about to cross over to the next tile
               (or (== 1 (dist-to-next-tile (:x pickupable-transform) (:origin-x pickupable-transform) (:x belt-dir)))
                   (== 1 (dist-to-next-tile (:y pickupable-transform) (:origin-y pickupable-transform) (:y belt-dir))))
               ;if the next tile has a pickupable on it
               (ent-map-pickupable-id transform belt-dir ent-map)))
      {:ecs     (-> (ecs/update-component ecs :belt-mover ent-id #(assoc % :state :idle))
                    (ecs/update-component :animation ent-id utils/stop-animation))
       :ent-map ent-map}

      (and pickupable-ent-id
           (> (dist-to-next-tile (:x pickupable-transform) (:origin-x pickupable-transform) (:x belt-dir)) 1)
           (> (dist-to-next-tile (:y pickupable-transform) (:origin-y pickupable-transform) (:y belt-dir)) 1))
      {:ecs     (-> (ecs/replace-component ecs :transform
                                           (move-transform pickupable-transform belt-dir)
                                           pickupable-ent-id)
                    (ecs/update-component :animation ent-id #(assoc % :current-animation :move)))
       :ent-map ent-map}

      (and pickupable-ent-id
           (or (== 1 (dist-to-next-tile (:x pickupable-transform) (:origin-x pickupable-transform) (:x belt-dir)))
               (== 1 (dist-to-next-tile (:y pickupable-transform) (:origin-y pickupable-transform) (:y belt-dir))))
           (-> (ent-map-pickupable-id transform belt-dir ent-map) (nil?)))
      (let [updated-p-transform (move-transform pickupable-transform belt-dir)]
        {:ecs     (ecs/replace-component ecs :transform updated-p-transform pickupable-ent-id)
         :ent-map (-> (assoc-in ent-map
                                [(utils/ent-map-key pickupable-transform)
                                 :pickupable]
                                nil)
                      (assoc-in [(utils/ent-map-key updated-p-transform)
                                 :pickupable]
                                pickupable-ent-id))}))))

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

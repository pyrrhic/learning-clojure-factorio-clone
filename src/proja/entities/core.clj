(ns proja.entities.core
  (:require [proja.components.core :as c]
            [proja.ecs.core :as ecs]
            [proja.utils :as u]))

;(defn potato-farm [ecs tex-cache x y]
;  (let [texture (:potato-farm tex-cache)]
;    (:ecs (ecs/add-entity ecs [(c/transform x y
;                                            0               ;rotation
;                                            (/ (.getRegionWidth texture) 2)
;                                            (/ (.getRegionHeight texture) 2))
;                               (c/renderable texture)
;                               (c/produces 1                ;max-workers
;                                           {:potato (c/product [] 1 10)})
;                               (c/haulable-storage)
;                               (c/loading-dock 5 1)]))))
;
;(defn warehouse [ecs tex-cache x y]
;  (let [texture (:warehouse tex-cache)
;        ecs-ent-id (ecs/add-entity ecs [(c/transform x y
;                                              0               ;rotation
;                                              (/ (.getRegionWidth texture) 2)
;                                              (/ (.getRegionHeight texture) 2))
;                                 (c/renderable texture)
;                                 (c/warehouse-request-queue)
;                                 (c/loading-dock 3 9)
;                                 ])
;        ecs-w-tags (ecs/add-tag (:ecs ecs-ent-id) :warehouse (:ent-id ecs-ent-id))]
;    ecs-w-tags))
;
;(defn road [ecs tex-cache x y]
;  (let [texture (:road tex-cache)]
;    (:ecs (ecs/add-entity ecs [(c/transform x y
;                                            0               ;rotation
;                                            (/ (.getRegionWidth texture) 2)
;                                            (/ (.getRegionHeight texture) 2))
;                               (c/renderable texture)]))))

(defn ore-piece [tex-cache x y]
  "Returns list of components"
  (let [texture (:ore-piece tex-cache)]
    [(c/transform (u/grid->world x) (u/grid->world y)
                  0               ;rotation
                  (/ (.getRegionWidth texture) 2)
                  (/ (.getRegionHeight texture) 2))
     (c/renderable texture)
     (c/pickupable)]))

(defn ore-patch [ecs tex-cache x y]
  (let [texture (:ore-patch tex-cache)]
    (:ecs (ecs/add-entity ecs [(c/transform (u/grid->world x) (u/grid->world y)
                                            0               ;rotation
                                            (/ (.getRegionWidth texture) 2)
                                            (/ (.getRegionHeight texture) 2))
                               (c/renderable texture)
                               (c/resource :iron-ore 100)]))))

(defn ore-miner [ecs tex-cache x y]
  (let [texture (:mining-building-1 tex-cache)]
    (:ecs
      (ecs/add-entity
        ecs
        [(c/transform (u/grid->world x) (u/grid->world y)
                      0               ;rotation
                      (/ (.getRegionWidth texture) 2)
                      (/ (.getRegionHeight texture) 2))
         (c/renderable texture)
         (c/animation :mining
                      (c/frames-h :mining
                                  [(c/frame-h (:mining-building-1 tex-cache) 0.1)
                                   (c/frame-h (:mining-building-2 tex-cache) 0.1)
                                   (c/frame-h (:mining-building-3 tex-cache) 0.1)]
                                  true))
         (c/miner x y (.getRegionWidth texture) (.getRegionHeight texture) ;texture
                  1                                         ;mining rate
                  1 3                                       ;output
                  )]))))

(defn arm [ecs tex-cache x y]
  (let [texture (:arm-1 tex-cache)]
    (:ecs (ecs/add-entity ecs [(c/transform (u/grid->world x) (u/grid->world y)
                                            0               ;rotation
                                            (+ 16 32)
                                            (+ 16 32))
                               (c/renderable texture)
                               (c/swingable)
                               (c/animation nil
                                            (c/frames-h :swing
                                                        [(c/frame-h (:arm-1 tex-cache) 0.05)
                                                         (c/frame-h (:arm-2 tex-cache) 0.05)
                                                         (c/frame-h (:arm-3 tex-cache) 0.05)
                                                         (c/frame-h (:arm-4 tex-cache) 0.05)
                                                         (c/frame-h (:arm-5 tex-cache) 0.05)]
                                                        false
                                                        :swing-back
                                                        [(c/frame-h (:arm-5 tex-cache) 0.05)
                                                         (c/frame-h (:arm-4 tex-cache) 0.05)
                                                         (c/frame-h (:arm-3 tex-cache) 0.05)
                                                         (c/frame-h (:arm-2 tex-cache) 0.05)
                                                         (c/frame-h (:arm-1 tex-cache) 0.05)]
                                                        false))]))))
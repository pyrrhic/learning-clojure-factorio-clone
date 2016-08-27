(ns proja.entities.core
  (:require [proja.components.core :as c]
            [proja.ecs.core :as ecs]
            [proja.utils :as u]))

(defn ore-piece [tex-cache x y]
  "Returns list of components"
  (let [texture (:ore-piece tex-cache)]
    [(c/transform (u/grid->world x) (u/grid->world y)
                  0               ;rotation
                  (/ (.getRegionWidth texture) 2)
                  (/ (.getRegionHeight texture) 2))
     (c/renderable texture)
     (c/storable :ore 1)]))

(defn ore-patch [ecs tex-cache x y]
  (let [texture (:ore-patch tex-cache)]
    (:ecs (ecs/add-entity ecs [(c/transform (u/grid->world x) (u/grid->world y)
                                            0               ;rotation
                                            (/ (.getRegionWidth texture) 2)
                                            (/ (.getRegionHeight texture) 2))
                               (c/renderable texture -2)
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
         (c/renderable texture 1)
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

;(defn- arm-output-em-key [x y rotation]
;  (let [offsets (case rotation
;                  0 {:x 0, :y 1}
;                  90 {:x 1, :y 0}
;                  180 {:x 0, :y -1}
;                  270 {:x -1, :y 0})]
;    (str (+ x (:x offsets)) (+ y (:y offsets)))))
;
;(defn- arm-input-em-key [x y rotation]
;  (let [offsets (case rotation
;                  0 {:x 0, :y -1}
;                  90 {:x -1, :y 0}
;                  180 {:x 0, :y 1}
;                  270 {:x 1, :y 0})]
;    (str (+ x (:x offsets)) (+ y (:y offsets)))))

(defn- output-loc [x y rotation]
  (let [offsets (case rotation
                  0 {:x 0, :y 1}
                  90 {:x 1, :y 0}
                  180 {:x 0, :y -1}
                  270 {:x -1, :y 0})]
    {:x (+ x (:x offsets)), :y (+ y (:y offsets))}))

(defn- input-loc [x y rotation]
  (let [offsets (case rotation
                  0 {:x 0, :y -1}
                  90 {:x -1, :y 0}
                  180 {:x 0, :y 1}
                  270 {:x 1, :y 0})]
    {:x (+ x (:x offsets)), :y (+ y (:y offsets))}))

(defn arm [ecs tex-cache x y rotation]
  (let [texture (:arm-1 tex-cache)]
    (:ecs (ecs/add-entity ecs [(c/transform (u/grid->world x) (u/grid->world (dec y))
                                            rotation
                                            16
                                            (+ 16 32))
                               (c/renderable texture 2)
                               (c/swingable (input-loc x y rotation)
                                            (output-loc x y rotation))
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

(defn belt [ecs tex-cache x y rotation]
  (let [texture (:belt-1 tex-cache)]
    (:ecs (ecs/add-entity ecs [(c/transform (u/grid->world x) (u/grid->world y)
                                            rotation
                                            (/ (.getRegionWidth texture) 2)
                                            (/ (.getRegionHeight texture) 2))
                               (c/renderable texture -1)
                               (c/belt-mover)
                               (c/animation :move
                                            (c/frames-h :move
                                                        [(c/frame-h (:belt-1 tex-cache) 0.05)
                                                         (c/frame-h (:belt-2 tex-cache) 0.05)
                                                         (c/frame-h (:belt-3 tex-cache) 0.05)]
                                                        true)
                                            )]))))

(defn storage [ecs tex-cache x y]
  (let [texture (:storage tex-cache)]
    (:ecs (ecs/add-entity ecs [(c/transform (u/grid->world x) (u/grid->world y)
                                            0
                                            (/ (.getRegionWidth texture) 2)
                                            (/ (.getRegionHeight texture) 2))
                               (c/renderable texture 1)
                               (c/container)]))))

(defn gun [x y]
  [(c/transform (u/grid->world x) (u/grid->world y)
                0
                0
                0)
   (c/storable :gun
               10)])

(defn bullet [x y]
  [(c/transform (u/grid->world x) (u/grid->world y)
                0
                0
                0)
   (c/storable :bullet
               1)])

(defn factory [ecs tex-cache x y]
  (let [texture (:factory-1 tex-cache)]
    (:ecs (ecs/add-entity ecs [(c/transform (u/grid->world x) (u/grid->world y)
                                            0
                                            (/ (.getRegionWidth texture) 2)
                                            (/ (.getRegionHeight texture) 2))
                               (c/renderable texture 1)
                               (c/input-container)
                               (c/output-container)
                               (c/producer
                                 (c/recipes-h [:bullet (c/recipe-h {:ore 1}
                                                                   {:bullet 1}
                                                                   1
                                                                   1)
                                               :gun (c/recipe-h {:ore 5}
                                                                {:gun 1}
                                                                60
                                                                1)]))
                               (c/animation nil
                                            (c/frames-h :produce
                                                        [(c/frame-h (:factory-1 tex-cache) 0.05)
                                                         (c/frame-h (:factory-2 tex-cache) 0.05)
                                                         (c/frame-h (:factory-3 tex-cache) 0.05)]
                                                        true))]))))
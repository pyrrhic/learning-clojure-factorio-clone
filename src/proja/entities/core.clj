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

(defn ore-patch [ecs tex-cache x y rotation]
  (let [texture (:ore-patch tex-cache)]
    (:ecs (ecs/add-entity ecs [(c/transform (u/grid->world x) (u/grid->world y)
                                            rotation
                                            (/ (.getRegionWidth texture) 2)
                                            (/ (.getRegionHeight texture) 2))
                               (c/renderable texture -2)
                               (c/resource :iron-ore 100)]))))

(defn ore-miner-x-output [rotation]
  (case rotation
    0 1
    90 3
    180 1
    270 -1))

(defn ore-miner-y-output [rotation]
  (case rotation
    0 3
    90 1
    180 -1
    270 1))

(defn ore-miner [ecs tex-cache x y rotation]
  (let [texture (:mining-building-1 tex-cache)]
    (:ecs
      (ecs/add-entity
        ecs
        [(c/transform (u/grid->world x) (u/grid->world y)
                      rotation
                      (/ (.getRegionWidth texture) 2)
                      (/ (.getRegionHeight texture) 2))
         (c/renderable texture 1)
         (c/energy 100)
         (c/animation :mining
                      (c/frames-h :mining
                                  [(c/frame-h (:mining-building-1 tex-cache) 0.01)
                                   (c/frame-h (:mining-building-2 tex-cache) 0.01)
                                   (c/frame-h (:mining-building-3 tex-cache) 0.01)]
                                  true))
         (c/miner x y (.getRegionWidth texture) (.getRegionHeight texture) ;texture
                  0.5                                         ;mining rate
                  (ore-miner-x-output rotation)
                  (ore-miner-y-output rotation)
                  )]))))

(defn- output-loc [x y rotation]
  (let [offsets (case rotation
                  0 {:x 0, :y -1}
                  90 {:x -1, :y 0}
                  180 {:x 0, :y 1}
                  270 {:x 1, :y 0})]
    {:x (+ x (:x offsets)), :y (+ y (:y offsets))}))

(defn- input-loc [x y rotation]
  (let [offsets (case rotation
                  0 {:x 0, :y 1}
                  90 {:x 1, :y 0}
                  180 {:x 0, :y -1}
                  270 {:x -1, :y 0})]
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
                               (c/energy 100)
                               (c/animation nil
                                            (c/frames-h :swing
                                                        [(c/frame-h (:arm-swing-1 tex-cache) 0.3)
                                                         (c/frame-h (:arm-2 tex-cache) 0.3)]
                                                        false
                                                        :swing-back
                                                        [(c/frame-h (:arm-swingback-1 tex-cache) 0.3)
                                                         (c/frame-h (:arm-1 tex-cache) 0.3)]
                                                        false))]))))

(defn belt [ecs tex-cache x y rotation]
  (let [texture (:belt-1 tex-cache)]
    (:ecs (ecs/add-entity ecs [(c/transform (u/grid->world x) (u/grid->world y)
                                            rotation
                                            (/ (.getRegionWidth texture) 2)
                                            (/ (.getRegionHeight texture) 2))
                               (c/renderable texture -1)
                               (c/belt-mover)
                               (c/energy 100)
                               (c/animation :move
                                            (c/frames-h :move
                                                        [(c/frame-h (:belt-1 tex-cache) 0.2)
                                                         (c/frame-h (:belt-2 tex-cache) 0.2)
                                                         (c/frame-h (:belt-3 tex-cache) 0.2)
                                                         (c/frame-h (:belt-4 tex-cache) 0.2)
                                                         (c/frame-h (:belt-5 tex-cache) 0.2)]
                                                        true)
                                            )]))))

(defn storage [ecs tex-cache x y rotation]
  (let [texture (:storage tex-cache)]
    (:ecs (ecs/add-entity ecs [(c/transform (u/grid->world x) (u/grid->world y)
                                            rotation
                                            (/ (.getRegionWidth texture) 2)
                                            (/ (.getRegionHeight texture) 2))
                               (c/renderable texture 1)
                               (c/container)]))))

;TODO thing is expecting a list of components back? not have it added to the ecs. not sure why.
(defn bullets [ecs tex-cache x y]
  (let [texture (:bullets tex-cache)]
    (:ecs (ecs/add-entity ecs [(c/transform (u/grid->world x) (u/grid->world y)
                                            0
                                            (/ (.getRegionWidth texture) 2)
                                            (/ (.getRegionHeight texture) 2))
                               (c/renderable texture)
                               (c/storable :bullets
                                           5)
                               ]))))

(defn factory [ecs tex-cache x y rotation]
  (let [texture (:factory-1 tex-cache)]
    (:ecs (ecs/add-entity ecs [(c/transform (u/grid->world x) (u/grid->world y)
                                            rotation
                                            (/ (.getRegionWidth texture) 2)
                                            (/ (.getRegionHeight texture) 2))
                               (c/renderable texture 1)
                               (c/input-container)
                               (c/output-container)
                               (c/energy 100)
                               (c/producer
                                 (c/recipes-h [:bullets (c/recipe-h {:ore 1}
                                                                    {:bullets 1}
                                                                    100
                                                                    1)]))
                               (c/animation nil
                                            (c/frames-h :produce
                                                        [(c/frame-h (:factory-1 tex-cache) 0.05)
                                                         (c/frame-h (:factory-2 tex-cache) 0.05)
                                                         (c/frame-h (:factory-3 tex-cache) 0.05)
                                                         (c/frame-h (:factory-4 tex-cache) 0.05)
                                                         (c/frame-h (:factory-3 tex-cache) 0.05)
                                                         (c/frame-h (:factory-2 tex-cache) 0.05)
                                                         (c/frame-h (:factory-1 tex-cache) 0.05)]
                                                        false))]))))
(ns proja.entities.core
  (:require [proja.components.core :as c]
            [proja.ecs.core :as ecs]))

(defn potato-farm [ecs tex-cache x y]
  (let [texture (:potato-farm tex-cache)]
    (:ecs (ecs/add-entity ecs [(c/transform x y
                                            0               ;rotation
                                            (/ (.getRegionWidth texture) 2)
                                            (/ (.getRegionHeight texture) 2))
                               (c/renderable texture)
                               (c/produces 1                ;max-workers
                                           {:potato (c/product [] 1 10)})
                               (c/haulable-storage)
                               (c/loading-dock 5 1)]))))

(defn warehouse [ecs tex-cache x y]
  (let [texture (:warehouse tex-cache)
        ecs-ent-id (ecs/add-entity ecs [(c/transform x y
                                              0               ;rotation
                                              (/ (.getRegionWidth texture) 2)
                                              (/ (.getRegionHeight texture) 2))
                                 (c/renderable texture)
                                 (c/warehouse-request-queue)
                                 (c/loading-dock 3 9)
                                 ])
        ecs-w-tags (ecs/add-tag (:ecs ecs-ent-id) :warehouse (:ent-id ecs-ent-id))]
    ecs-w-tags))

(defn road [ecs tex-cache x y]
  (let [texture (:road tex-cache)]
    (:ecs (ecs/add-entity ecs [(c/transform x y
                                            0               ;rotation
                                            (/ (.getRegionWidth texture) 2)
                                            (/ (.getRegionHeight texture) 2))
                               (c/renderable texture)]))))
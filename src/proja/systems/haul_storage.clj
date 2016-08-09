(ns proja.systems.haul-storage
  (:require [proja.ecs.core :as ecs]
            [proja.pathfinder :as pathfinder]
            [proja.tile-map.core :as t-map]
            [proja.utils :as utils]))

(defn- send-pickup-request? [h-storage]
  (boolean
    (and (some pos? (vals (:storage h-storage)))
         (not (:requested-haul h-storage)))))

(defn- all-warehouses [ecs requestor-ent-id]              ;distance should be to loading-dock
  "Vector of maps, {:ent-id ?, :transform ?}"
  (let [ent-ids (ecs/ent-ids-for-tag ecs :warehouse)
        rt (ecs/component ecs :transform requestor-ent-id)]
    (->> (map (fn [ent-id]
                (let [t (ecs/component ecs :transform ent-id)]
                  {:ent-id    ent-id
                   :transform t
                   :distance  (-> (proja.utils/euclidean-distance (:x rt) (:y rt)
                                                                  (:x t) (:y t))
                                  (Math/round))}))
              ent-ids)
         (sort-by :distance))))

(defn- loading-dock-tile [transform loading-dock road-map]
  (let [x (+ (-> (:x transform) (utils/world->grid))
             (:x loading-dock))
        y (+ (-> (:y transform) (utils/world->grid))
             (:y loading-dock))]
    (t-map/get-tile x y road-map)))

(defn- path-to-warehouse [ecs start-ent-id dest-ent-id road-map]
  (let [s (loading-dock-tile (ecs/component ecs :transform start-ent-id)
                             (ecs/component ecs :loading-dock start-ent-id)
                             road-map)
        d (loading-dock-tile (ecs/component ecs :transform dest-ent-id)
                             (ecs/component ecs :loading-dock dest-ent-id)
                             road-map)]
    {:warehouse-ent-id dest-ent-id
     :path             (pathfinder/calc-path s d road-map)}))


(defn- send-pickup-request [ecs requestor-ent-id road-map]
  "Returns nil if it failed to send a request. The only reason it would fail is if there are no warehouses,
  or none that are connected.
  Else it returns an updatd ecs that has the sent request (request is on the warehouse request queue)."
  (let [whs (all-warehouses ecs requestor-ent-id)
        paths (map #(path-to-warehouse ecs requestor-ent-id (:ent-id %) road-map) whs)]
    (loop [ps paths]
      (if (empty? ps)
        nil
        (let [p (first ps)]
          (if (not-empty (:path p))
            ;add to the warehouse request queue.
            (-> (ecs/update-component ecs
                                      :warehouse-request-queue
                                      (:warehouse-ent-id p)
                                      #(conj % {:requestor-ent-id requestor-ent-id
                                                :path             (:path p)})))
            (recur (rest ps))))))))

;if storage is empty, set requested-haul to false? or maybe let the hauler reset it when he takes the stuff.
;i think that makes more sense.

(defn run [ent-id game]
  (let [ecs (:ecs game)
        h-storage (ecs/component ecs :haulable-storage ent-id)]
    (if (send-pickup-request? h-storage)
      ;when we show error messages for no warehouse connected, then process the nil that can come
      ;back from this mamma jamma.
      (let [ecs-pickup-requested (send-pickup-request ecs ent-id (:road-map game))]
        (if (nil? ecs-pickup-requested)
          game
          (->> (ecs/add-component ecs-pickup-requested
                                  :haulable-storage (assoc h-storage :requested-haul true)
                                  ent-id)
               (assoc game :ecs))))
      game)))

(defn create []
  {:function   run
   :predicates {:and #{:haulable-storage}}})


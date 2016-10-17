(ns proja.systems.mine-ore
  (:require [proja.ecs.core :as ecs]
            [proja.entities.core :as e]
            [proja.utils :as utils])
  (:import (com.badlogic.gdx.graphics.g2d TextureRegion)))

(defn tile-locations [ecs x y width height ent-map]
  (for [x (range x (+ x width))
        y (range y (+ y height))
        :let [tile-loc (utils/ent-map-key x y)
              ore-ids (:ore (get ent-map tile-loc))]
        :when (and (not-empty ore-ids) (pos? (:quantity (ecs/component ecs :resource (first ore-ids)))))]
    tile-loc))

(defn remove-ore-patch-ecs [ecs resource resource-id]
  (if (zero? (:quantity resource))
    (ecs/remove-entity ecs resource-id)
    ecs))

(defn remove-ore-patch-ent-map [ent-map resource ore-tile-loc]
  (if (zero? (:quantity resource))
    (assoc-in ent-map [ore-tile-loc :ore] #{})
    ent-map))

(defn updtd-ecs-ent-map [ecs ent-id ore-tile-loc ent-map game]
  (let [miner (ecs/component ecs :miner ent-id)
        ore-patch-id (-> (get ent-map ore-tile-loc) :ore (first))
        output-ent-map-key (utils/ent-map-key (:output-x miner) (:output-y miner))
        energy (ecs/component ecs :energy ent-id)
        updated-ecs-em (cond
                         (nil? ore-tile-loc)
                         {:ecs     (ecs/update-component ecs :animation ent-id utils/stop-animation)
                          :ent-map ent-map}

                         (pos? (:mining-cooldown miner))
                         {:ecs     (-> (ecs/replace-component ecs :miner
                                                              (assoc miner :mining-cooldown
                                                                           (- (:mining-cooldown miner)
                                                                              (:delta game)))
                                                              ent-id)
                                       (ecs/update-component :animation
                                                             ent-id
                                                             #(assoc % :current-animation :mining)))
                          :ent-map ent-map}

                         (and (-> (get ent-map output-ent-map-key) :pickupable (nil?))
                              (== 100 (:current-amount energy)))
                         (let [new-ore-ent (ecs/add-entity ecs (e/ore-piece (:tex-cache game)
                                                                            (:output-x miner)
                                                                            (:output-y miner)))
                               resource (ecs/component ecs :resource ore-patch-id)
                               quant-updated-resource (update resource :quantity dec)]
                           {:ecs     (-> (:ecs new-ore-ent)
                                         (ecs/update-component :miner
                                                               ent-id
                                                               #(assoc % :mining-cooldown (:mining-rate %)))
                                         (ecs/replace-component :resource quant-updated-resource ore-patch-id)
                                         (remove-ore-patch-ecs quant-updated-resource ore-patch-id))
                            :ent-map (-> (assoc-in ent-map [output-ent-map-key :pickupable] (:ent-id new-ore-ent))
                                         (remove-ore-patch-ent-map quant-updated-resource ore-tile-loc))})

                         :else
                         {:ecs     (ecs/update-component ecs :animation ent-id utils/stop-animation)
                          :ent-map ent-map})]
    (update updated-ecs-em :ecs #(ecs/replace-component %
                                                        :energy
                                                        (assoc energy :current-amount 0)
                                                        ent-id))))

(defn run [ent-id game]
  (let [ecs (:ecs game)
        transform (ecs/component ecs :transform ent-id)
        texture (:texture (ecs/component ecs :renderable ent-id))
        ent-map (:entity-map game)
        tile-locs (tile-locations ecs
                                  (-> transform :x (utils/world->grid))
                                  (-> transform :y (utils/world->grid))
                                  (utils/world->grid (.getRegionWidth ^TextureRegion texture))
                                  (utils/world->grid (.getRegionHeight ^TextureRegion texture))
                                  ent-map)
        ecs-ent-map (updtd-ecs-ent-map ecs ent-id (first tile-locs) ent-map game)]
    (assoc game :ecs (:ecs ecs-ent-map)
                :entity-map (:ent-map ecs-ent-map))))

(defn create []
  {:function   run
   :predicates {:and #{:miner}}})
(ns proja.systems.mine-ore
  (:require [proja.ecs.core :as ecs]
            [proja.entities.core :as e]
            [proja.utils :as utils])
  (:import (com.badlogic.gdx.graphics.g2d TextureRegion)))

;logic to update the ent-map when an ore patch is depleted.
;logic to delete ore patch entity when it's depleted.
;logic to update entioty map when entity is created.

(defn tile-locations [ecs x y width height ent-map]
  (for [x (range x (+ x width))
        y (range y (+ y height))
        :let [tile-loc (str x y)
              ore-ids (:ore (get ent-map tile-loc))]
        :when (and (not-empty ore-ids) (pos? (:quantity (ecs/component ecs :resource (first ore-ids)))))]
    tile-loc))

(defn updated-comps-and-ents [ecs ent-id ore-tile-loc ent-map game]
  (let [miner (ecs/component ecs :miner ent-id)]
    (if (nil? ore-tile-loc)
      {:animation (utils/stop-animation (ecs/component ecs :animation ent-id))}
      (if (pos? (:mining-cooldown miner))
        {:miner (assoc miner :mining-cooldown (- (:mining-cooldown miner) (:delta game)))
         :animation (assoc (ecs/component ecs :animation ent-id) :current-animation :mining)}
        ;if output location empty
        ;reset cooldown
        ;take ore from ore patch.
        ; create ore entity.
        ;put ore in output location.
        (let [output-loc-empty? (-> (get ent-map (str (:output-x miner) (:output-y miner)))
                                    :pickupable
                                    (nil?))
              ore-patch-id (-> (get ent-map ore-tile-loc) :ore (first))]
          (if output-loc-empty?
            {:miner     (assoc miner :mining-cooldown (:mining-rate miner))
             :other-ent (ecs/update-component ecs :resource ore-patch-id #(update % :quantity dec))
             :add-ent   (e/ore-piece (:tex-cache game) (:output-x miner) (:output-y miner))}
            {:animation (utils/stop-animation (ecs/component ecs :animation ent-id))}))
        ))))

(defn updated-other-ent [ecs updated-c-e]
  (if-let [oe (:other-ent updated-c-e)]
    oe
    ecs))

(defn added-other-ent [ecs updated-c-e]
  (if-let [ad (:add-ent updated-c-e)]
    (ecs/add-entity ecs ad)
    ;to mimic what comes out of add-entity. this is kinda shitty, but just trying to get things done right now.
    {:ecs ecs}))

(defn clean-updated-c-e [updated-c-e]
  (dissoc updated-c-e :other-ent :add-ent))

(defn add-to-ent-map [ent-map ecs ent-id]
  (let [transform (ecs/component ecs :transform ent-id)
        k (str (-> transform :x (utils/world->grid)) (-> transform :y (utils/world->grid)))]
    (assoc-in ent-map [k :pickupable] ent-id)))

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
        u-comps-a-ents (updated-comps-and-ents ecs ent-id (first tile-locs) ent-map game)
        ecs-w-added-ent-and-ent-id (-> ecs
                                 (updated-other-ent u-comps-a-ents)
                                 (added-other-ent u-comps-a-ents))]
    (assoc game :ecs (ecs/update-components (:ecs ecs-w-added-ent-and-ent-id)
                                            ent-id
                                            (clean-updated-c-e u-comps-a-ents))
                :entity-map (if (:ent-id ecs-w-added-ent-and-ent-id)
                              (add-to-ent-map ent-map
                                              (:ecs ecs-w-added-ent-and-ent-id)
                                              (:ent-id ecs-w-added-ent-and-ent-id))
                              ent-map))))

(defn create []
  {:function   run
   :predicates {:and #{:miner}}})
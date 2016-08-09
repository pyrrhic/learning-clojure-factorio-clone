(ns proja.systems.render
  (:import (com.badlogic.gdx.graphics.g2d TextureRegion))
  (:require [proja.ecs.core :as ecs]))

(defn run-single [trans-comp render-comp batch]
  {:pre [(= (:type trans-comp) :transform),
         (= (:type render-comp) :renderable)]}
  (let [texture-region ^TextureRegion (:texture (:data render-comp))
        x (float (get-in trans-comp [:data :x]))
        y (float (get-in trans-comp [:data :y]))
        origin-x (float (get-in trans-comp [:data :origin-x]))
        origin-y (float (get-in trans-comp [:data :origin-y]))
        ;libgdx draws rotation counter clock wise, and um, i want to keep my code clock wise because it  makes more sense to me.
        rotation (* -1.0 (float (get-in trans-comp [:data :rotation])))
        width (float (.getRegionWidth texture-region))
        height (float (.getRegionHeight texture-region))
        scale-x (float (:scale-x (:data render-comp)))
        scale-y (float(:scale-y (:data render-comp)))]
    (.begin batch)
    (.draw batch texture-region x y origin-x origin-y width height scale-x scale-y rotation)
    (.end batch)))

(defn run [ent-id game]                           ;might need anything. camera, batch, map, later things.
  (let [entity-cs (:ecs game)
        renderable (ecs/component entity-cs :renderable ent-id)
        transform (ecs/component entity-cs :transform ent-id)
        batch (:batch game)
        tex-region (:texture renderable)]
    (.draw batch tex-region
           (:x transform) (:y transform)
           (:origin-x transform) (:origin-y transform)
           (float (.getRegionWidth tex-region)) (float (.getRegionHeight tex-region))
           (float (:scale-x renderable)) (float (:scale-y renderable))
           ;libgdx draws rotation counter clock wise, and um, i want to keep my code clock wise because it  makes more sense to me.
           (* -1.0 (float (:rotation transform)))))
  game)

(defn create []
  {:function   run
   :predicates {:and #{:renderable :transform}}
   :begin (fn begin-render [g]
            (.begin (:batch g))
            g)
   :end (fn end-render [g]
          (.end (:batch g))
          g)})


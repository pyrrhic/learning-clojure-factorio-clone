(ns proja.systems.render
  (:import (com.badlogic.gdx.graphics.g2d SpriteBatch TextureRegion))
  (:require [proja.ecs.core :as ecs]))

;(defn run-for-all [{{ents :entities} :ecs ^SpriteBatch batch :batch cam :camera}]
;  (let [qualifying-ents (filterv #(and (:renderable %) (:transform %)) ents)]
;    (.setProjectionMatrix batch (.combined cam))
;    (.begin batch)
;    ;this loop isn't really any faster than using map. the slow part is the actual drawing done by libgdx/opengl
;    (loop [q-ents qualifying-ents]
;      (if (empty? q-ents)
;        ents
;        (let [e (first q-ents)
;              texture-region ^TextureRegion (:texture (:renderable e))
;              x (float (get-in e [:transform :x]))
;              y (float (get-in e [:transform :y]))
;              origin-x (float (get-in e [:transform :origin-x]))
;              origin-y (float (get-in e [:transform :origin-y]))
;              ;libgdx draws rotation counter clock wise, and um, i want to keep my code clock wise because it  makes more sense to me.
;              rotation (* -1.0 (float (get-in e [:transform :rotation])))
;              width (float (.getRegionWidth texture-region))
;              height (float (.getRegionHeight texture-region))
;              scale-x (float (:scale-x (:renderable e)))
;              scale-y (float(:scale-y (:renderable e)))]
;          (.draw batch texture-region x y origin-x origin-y width height scale-x scale-y rotation)
;          (recur (rest q-ents)))))
;    (.end batch))
;  ents)

;(defn run* [ent batch]
;  {:pre [(:transform ent), (:renderable ent)]}
;  (let [texture-region ^TextureRegion (:texture (:renderable ent))
;        x (float (get-in ent [:transform :x]))
;        y (float (get-in ent [:transform :y]))
;        origin-x (float (get-in ent [:transform :origin-x]))
;        origin-y (float (get-in ent [:transform :origin-y]))
;        ;libgdx draws rotation counter clock wise, and um, i want to keep my code clock wise because it  makes more sense to me.
;        rotation (* -1.0 (float (get-in ent [:transform :rotation])))
;        width (float (.getRegionWidth texture-region))
;        height (float (.getRegionHeight texture-region))
;        scale-x (float (:scale-x (:renderable ent)))
;        scale-y (float(:scale-y (:renderable ent)))]
;    (.begin batch)
;    (.draw batch texture-region x y origin-x origin-y width height scale-x scale-y rotation)
;    (.end batch)))

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


(ns proja.systems.render
  (:import (com.badlogic.gdx.graphics.g2d TextureRegion SpriteBatch)))

(defn draw [transform renderable batch]                           ;might need anything. camera, batch, map, later things.
  (let [tex-region (:texture renderable)]
    (.draw ^SpriteBatch batch ^TextureRegion tex-region
           (:x transform) (:y transform)
           (:origin-x transform) (:origin-y transform)
           (float (.getRegionWidth ^TextureRegion tex-region)) (float (.getRegionHeight ^TextureRegion tex-region))
           (float (:scale-x renderable)) (float (:scale-y renderable))
           ;libgdx draws rotation counter clock wise, and um, i want to keep my code clock wise because it  makes more sense to me.
           (* -1.0 (float (:rotation transform))))))

(defn draw-all [ecs batch]
  (.begin ^SpriteBatch batch)
  (loop [qualified-ents (->> (filter #(and (:renderable %) (not (:disabled %))) (-> ecs :ent-comps (vals)))
                             (sort-by #(get-in % [:renderable :z])))]
    (if (empty? qualified-ents)
      nil
      (let [e (first qualified-ents)]
        (draw (:transform e) (:renderable e) batch)
        (recur (rest qualified-ents)))))
  (.end ^SpriteBatch batch))

;(defn run [ent-id game]                           ;might need anything. camera, batch, map, later things.
;  (let [entity-cs (:ecs game)
;        renderable (ecs/component entity-cs :renderable ent-id)
;        transform (ecs/component entity-cs :transform ent-id)
;        batch (:batch game)
;        tex-region (:texture renderable)]
;    (.draw ^SpriteBatch batch ^TextureRegion tex-region
;           (:x transform) (:y transform)
;           (:origin-x transform) (:origin-y transform)
;           (float (.getRegionWidth tex-region)) (float (.getRegionHeight tex-region))
;           (float (:scale-x renderable)) (float (:scale-y renderable))
;           ;libgdx draws rotation counter clock wise, and um, i want to keep my code clock wise because it  makes more sense to me.
;           (* -1.0 (float (:rotation transform)))))
;  game)
;
;(defn create []
;  {:is-render-system true                                   ;this system will run differently than all other systems.
;   :function   run
;   :predicates {:and #{:renderable :transform}}
;   :begin (fn begin-render [g]
;            (.begin ^SpriteBatch (:batch g))
;            g)
;   :end (fn end-render [g]
;          (.end ^SpriteBatch (:batch g))
;          g)})


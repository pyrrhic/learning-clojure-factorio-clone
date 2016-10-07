(ns proja.screens.game)

(def g {})

(defn update-game! [func]
  "Expects a function with 1 parameter which will be the game map. The function must return the updated game map."
  (alter-var-root (var g) #(func %))
  nil)

(defn pause []
  (update-game! #(assoc % :paused true)))

(defn resume []
  (update-game! #(assoc % :paused false)))

;(do
;  (let [body-def (doto (BodyDef.))
;        world (:box2d-world g)
;        ;body (.createBody ^World world body-def)
;        shape (doto (CircleShape.)
;                (.setRadius (float 6)))
;        fixture-def (FixtureDef.)
;        ;fixture (.createFixture ^Body body fixture-def)
;        ]
;    ;body-def
;    (set! (.type body-def) (BodyDef$BodyType/DynamicBody))
;    (.set (.position body-def) 0 0)
;    ;fixture-def
;    (set! (.shape fixture-def) shape)
;    (set! (.density fixture-def) (float 0.5))
;    ;create
;    (let [body (.createBody world body-def)
;          fixture (.createFixture body fixture-def)]
;      (.dispose shape))
;    ))

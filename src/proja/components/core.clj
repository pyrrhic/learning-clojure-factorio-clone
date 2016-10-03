(ns proja.components.core
  (:require [proja.ecs.core :as ecs]
            [proja.utils :as utils]))

;Functions with a -h suffix means they are not components, just functions to help build components
;;with heavily nested data.

(defn transform [x y rot orig-x orig-y]
  (ecs/create-component :transform
                        {:x x
                         :y y
                         :rotation rot
                         :origin-x orig-x
                         :origin-y orig-y}))

(defn renderable
  ([texture]
    (renderable texture 1 1 0))
  ([texture z]
   (renderable texture 1 1 z))
  ([texture scale-x scale-y]
   (renderable texture scale-x scale-y 0))
  ([texture scale-x scale-y z]
   (ecs/create-component :renderable
                         {:texture texture
                          :scale-x scale-x
                          :scale-y scale-y
                          :z z})))

(defn frame-h [tex dur]
  "A single frame."
  {:texture tex
   :duration dur})

(defn frames-h [& args]
  "Expecting groupings of 3: name of frames, frame(s), loop?
   ex.
   :name1 [f f f] true
   :name2 [f f] false
   :name3 f true
   Returns: A map of the same data from the args
   {:name1 {:frame-durations :loop}}"
  (let [mapped (map (fn [name-frame]
                      {(first name-frame)
                       {:frame-durations (let [second-item (second name-frame)]
                                          (if (vector? second-item)
                                            second-item
                                            (vector second-item)))
                        :loop? (nth name-frame 2)}})
                    (partition 3 args))]
    (reduce conj mapped)))

(defn animation [current-animation frames]
  (ecs/create-component :animation
                        {:current-frame     -1
                         :current-duration  0.0
                         ;should map to a key in the :frames map
                         :current-animation current-animation
                         :frames            frames}))

(defn resource [type quantity]
  (ecs/create-component :resource
                        {:type     type
                         :quantity quantity}))

(defn miner [x y width height mining-rate output-x output-y]
  (ecs/create-component :miner
                        {:x               x
                         :y               y
                         :width           width
                         :height          height
                         :mining-rate     mining-rate
                         :mining-cooldown mining-rate
                         :output-x        (+ x output-x)
                         :output-y        (+ y output-y)}))

(defn swingable [input-loc output-loc]
  (ecs/create-component :swingable
                        {:state :idle                       ;:idle, :swing, :swing-back
                         :held-item nil
                         :input-loc input-loc               ;{:x x :y y}
                         :output-loc output-loc             ;{:x x :y y}
                         :input-em-key (utils/ent-map-key (:x input-loc)
                                                          (:y input-loc))
                         :output-em-key (utils/ent-map-key (:x output-loc)
                                                           (:y output-loc))}))

(defn belt-mover []
  (ecs/create-component :belt-mover
                        {:state     :idle                   ;:idle, :moving
                         :move-rate 2                       ;pixels per frame
                         :moving-item nil                   ;id of entity being moved
                         :move-ticks 0                      ;used to figure out how many moves are left
                         }))

(defn container []
  (ecs/create-component :container
                        {:max-size 100
                         :current-size 0
                         ;keys are types
                         ;values are sets of ent-ids
                         :items {}}))

(defn input-container []
  (ecs/create-component :input-container
                        {:max-size 100
                         :current-size 0
                         ;keys are types
                         ;values are sets of ent-ids
                         :items {}}))

(defn output-container []
  (ecs/create-component :output-container
                        {:max-size 100
                         :current-size 0
                         ;keys are types
                         ;values are sets of ent-ids
                         :items {}}))

(defn recipe-h [inputs output duration size]
  {:inputs inputs
   :output output
   :duration duration
   :size size})

(defn recipes-h [recipe-data]
  "Input should be vector of :recipe-type recipe-data, repeating.
  Must have at least 2 items (type and data).
  Must be pairs of 2."
  {:pre [(even? (count recipe-data))]}
  (->> (partition 2 recipe-data)
       (map (fn [kd] {(first kd) (second kd)}))
       (reduce conj)))

(defn producer [recipes]
  (ecs/create-component :producer
                        {:current-recipe nil
                         :remaining-duration -1.0
                         ;recipes should look like
                         ;{:recipe-type {:inputs #{types}
                         ;               :output type
                         ;               :duration 0.0}}
                         :recipes recipes}))

(defn storable [type size]
  (ecs/create-component :storable
                        {:type type
                         :size size}))

(defn move-to [x-grid y-grid]
  "temp component"
  {:x x-grid
   :y y-grid})

(defn energy [max-amount]
  (ecs/create-component :energy
                        {:current-amount 0
                         :max-amount max-amount}))

;(defn warehouse-request-queue []
;  (ecs/create-component :warehouse-request-queue
;                        (clojure.lang.PersistentQueue/EMPTY)))
;
;(defn loading-dock [x y]
;  (ecs/create-component :loading-dock
;                        {:x x, :y y}))
;
;(defn haulable-storage []
;  (ecs/create-component :haulable-storage
;                        {:storage {}
;                         :requested-haul false}))
;
;(defn product [inputs quantity build-time]
;  {:inputs inputs
;   :quantity quantity
;   :build-time build-time})
;
;(defn produces [max-workers products]
;  "Products is a map. Keys are generally the name of the produce, then the value is a
;  proja.components.core/product"
;  (ecs/create-component :produces
;                        {:max-workers max-workers           ;1 to max
;                         :current nil                       ;current produce, map.
;                         :remaining-build-time nil          ;seconds
;                         :amount nil                        ;amount to produce, -1 is infinity
;                         :products products}))
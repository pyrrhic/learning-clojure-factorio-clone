(ns proja.components.core
  (:require [proja.ecs.core :as ecs]))

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
    (renderable texture 1 1))
  ([texture scale-x scale-y]
   (ecs/create-component :renderable
                         {:texture texture
                          :scale-x scale-x
                          :scale-y scale-y})))

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

(defn miner [x y width height mining-rate output-x output-y ]
  (ecs/create-component :miner
                        {:x               x
                         :y               y
                         :width           width
                         :height          height
                         :mining-rate     mining-rate
                         :mining-cooldown mining-rate
                         :output-x        (+ x output-x)
                         :output-y        (+ y output-y)}))

(defn pickupable []
  (ecs/create-component :pickupable
                        {}))

(defn swingable []
  (ecs/create-component :swingable
                        {:state :idle                       ;:idle, :swing, :swing-back
                         :held-item nil}))

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
(ns proja.components.core
  (:require [proja.ecs.core :as ecs]))

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

(defn warehouse-request-queue []
  (ecs/create-component :warehouse-request-queue
                        (clojure.lang.PersistentQueue/EMPTY)))

(defn loading-dock [x y]
  (ecs/create-component :loading-dock
                        {:x x, :y y}))

(defn haulable-storage []
  (ecs/create-component :haulable-storage
                        {:storage {}
                         :requested-haul false}))

(defn product [inputs quantity build-time]
  {:inputs inputs
   :quantity quantity
   :build-time build-time})

(defn produces [max-workers products]
  "Products is a map. Keys are generally the name of the produce, then the value is a
  proja.components.core/product"
  (ecs/create-component :produces
                        {:max-workers max-workers           ;1 to max
                         :current nil                       ;current produce, map.
                         :remaining-build-time nil          ;seconds
                         :amount nil                        ;amount to produce, -1 is infinity
                         :products products}))
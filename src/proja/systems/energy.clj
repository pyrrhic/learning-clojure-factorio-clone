(ns proja.systems.energy
  (:require [proja.ecs.core :as ecs]
            [proja.screens.game :as ga]))

(defn run [ent-id game]
  (let [ecs (:ecs game)
        energy-tick (:energy-tick game)
        energy (let [nrg (ecs/component ecs :energy ent-id)
                     nrg-inc 100]
                 (if (zero? energy-tick)
                   (if (>= (+ nrg-inc (:current-amount nrg))
                           (:max-amount nrg))
                     (assoc nrg :current-amount (:max-amount nrg))
                     (update nrg :current-amount #(+ % nrg-inc)))
                   nrg))]
    (assoc game :ecs (ecs/replace-component ecs :energy energy ent-id)
                :energy-tick energy-tick)))

(defn create []
  {:function   run
   :predicates {:and #{:energy}}
   :begin (fn begin-energy [g]
            (assoc g :energy-tick (if (== 29 (:energy-tick g))
                                    0
                                    (inc (:energy-tick g)))))})

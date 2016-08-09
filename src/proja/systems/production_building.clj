(ns proja.systems.production-building
  (:require [proja.ecs.core :as ecs]))

(defn- reset-remaining-build-time [produces curr-prod]
  (case (:amount produces)
    -1 (assoc produces :remaining-build-time (get-in produces [:current curr-prod :build-time]))
    0 (assoc produces :current nil
                      :remaining-build-time nil
                      :amount nil)))

(defn run [ent-id game]
  (let [produces (ecs/component (:ecs game) :produces ent-id)
        haulable-storage (ecs/component (:ecs game) :haulable-storage ent-id)
        updated-comps (cond
                        (nil? (:current produces))
                        {} ;do nothing because no product is set to be built.

                        (<= (:remaining-build-time produces) 0)
                        (let [current-prod (-> (:current produces) (keys) (first))]
                          {:produces (reset-remaining-build-time produces current-prod)
                           :haulable-storage (update-in haulable-storage [:storage current-prod]
                                                        #((fnil + 0) % (get-in produces [:current current-prod :quantity])))})

                        (pos? (:remaining-build-time produces))
                        {:produces (update produces :remaining-build-time #(- % (:delta game)))})]
    (let [updated-ecs (loop [ks (keys updated-comps)
                             ecs (:ecs game)]
                        (if (empty? ks)
                          ecs
                          (recur (rest ks)
                                 (ecs/add-component ecs (first ks) ((first ks) updated-comps) ent-id)
                                 )))]
        (assoc game :ecs updated-ecs))))

(defn create []
  {:function   run
   :predicates {:and #{:produces}}})
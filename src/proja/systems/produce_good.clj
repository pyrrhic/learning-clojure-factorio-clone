(ns proja.systems.produce-good
  (:require [proja.entities.core :as e]
            [proja.ecs.core :as ecs]
            [proja.utils :as utils]))

;TODO consume input from input container when you start building something.

(defn- has-all-inputs? [recipe-inputs input-container-items]
  (let [])
  (loop [ks (keys recipe-inputs)]
    (if (empty? ks)
      true
      (let [k (first ks)]
        (if (and (k input-container-items)
                 (>= (count (k input-container-items)) (k recipe-inputs)))
          (recur (rest ks))
          false)))))

(defn- output-has-space? [recipe-size output-container]
  (<= (+ (:current-size output-container)
         recipe-size)
      (:max-size output-container)))

(defn- ready? [producer input-container-items output-container animation energy]
  (let [current-recipe (:current-recipe producer)
        curr-recipe-data (if (nil? current-recipe) nil (-> producer :recipes current-recipe))]
    (and current-recipe
         (nil? (:current-animation animation))
         (has-all-inputs? (:inputs curr-recipe-data) input-container-items)
         (output-has-space? (:size curr-recipe-data) output-container)
         (== 100 (:current-amount energy)))))

(defn- waiting? [producer input-container-items output-container animation energy]
  (let [current-recipe (:current-recipe producer)
        curr-recipe-data (if (nil? current-recipe) nil (-> producer :recipes current-recipe))]
    (and current-recipe
         (< (:current-amount energy) 100)
         (nil? (:current-animation animation))
         (has-all-inputs? (:inputs curr-recipe-data) input-container-items)
         (output-has-space? (:size curr-recipe-data) output-container))))

(defn- start-animation [ecs animation-name ent-id]
  (let [u-animation-fn (fn [a] (assoc a :current-animation animation-name))]
    (ecs/update-component ecs :animation ent-id u-animation-fn)))

(defn- producing? [producer animation]
  (and (:current-recipe producer) (>= (:remaining-duration producer) 0) (:current-animation animation)))

(defn- keep-producing [ecs producer delta ent-id]
  (let [u-producer (update producer :remaining-duration #(- % delta))]
    (ecs/replace-component ecs :producer u-producer ent-id)))

(defn done-producing? [producer]
  (and (:current-recipe producer) (<= (:remaining-duration producer) 0)))

(defn- create-entity [ecs tex-cache recipe-type ent-id]
  "Everything will be created at 0,0 because they're all disabled so who cares.
  Expectation is that they will be moved when re-enabled."
  (let [new-ent-ecs (case recipe-type
                      :bullets (e/bullets ecs tex-cache 0 0))
        new-ent-id (utils/my-keyword (ecs/latest-ent-id new-ent-ecs))
        storable (ecs/component new-ent-ecs :storable new-ent-id)]
    (-> (ecs/disable-entity new-ent-ecs new-ent-id)
        (ecs/update-component :output-container ent-id #(update % :current-size
                                                                (fn [cs] (+ cs (:size storable)))))
        (ecs/update-component :output-container ent-id #(update-in % [:items recipe-type]
                                                                   (fn [ids] (if ids
                                                                               (conj ids new-ent-id)
                                                                               #{new-ent-id}
                                                                               ))))
        )))

(defn remove-recipe-inputs [ecs curr-recipe ent-id]
  ;inputs is a map like {:ore #{:1 :2 :3}}
  (loop [inputs (-> (ecs/component ecs :producer ent-id) :recipes curr-recipe :inputs)
         input-container (ecs/component ecs :input-container ent-id)
         ent-cs ecs]
    (if (empty? inputs)
      (ecs/replace-component ent-cs :input-container input-container ent-id)
      (let [item-type (first (first inputs))
            amount (-> inputs first second)]
        (recur (dissoc inputs item-type)
               (update-in input-container [:items item-type] #(loop [a amount
                                                                     item-ids %]
                                                               (if (zero? a)
                                                                 item-ids
                                                                 (recur (dec a)
                                                                        (rest item-ids)))))
               (ecs/remove-entity ent-cs amount))))))

(defn- reset-curr-recipe [ecs producer ent-id]
  ecs
  #_(ecs/replace-component ecs
                         :producer
                         (-> (assoc producer :current-recipe nil)
                             (assoc :remaining-duration 0))
                         ent-id))

(defn- stop-animation [ecs ent-id]
  (ecs/update-component ecs :animation ent-id #(utils/stop-animation %)))

(defn- zero-energy [ecs ent-id]
  (ecs/update-component ecs :energy ent-id #(assoc % :current-amount 0)))

(defn run [ent-id game]
  "recipe and duration should be set by something else (presumably from the UI)"
  (let [ecs (:ecs game)
        ent-map (:entity-map game)
        producer (ecs/component ecs :producer ent-id)
        ecs-ent-map (cond
                      (ready? producer
                              (:items (ecs/component ecs :input-container ent-id))
                              (ecs/component ecs :output-container ent-id)
                              (ecs/component ecs :animation ent-id)
                              (ecs/component ecs :energy ent-id))
                      {:ecs     (-> (create-entity ecs (:tex-cache game) (:current-recipe producer) ent-id)
                                    (remove-recipe-inputs (:current-recipe producer) ent-id)
                                    (reset-curr-recipe producer ent-id)
                                    (start-animation :produce ent-id)
                                    (zero-energy ent-id))
                       :ent-map ent-map}

                      ;if ready to start but not enough energy, wait for enough energy
                      (waiting? producer
                                (:items (ecs/component ecs :input-container ent-id))
                                (ecs/component ecs :output-container ent-id)
                                (ecs/component ecs :animation ent-id)
                                (ecs/component ecs :energy ent-id))
                      {:ecs ecs
                       :ent-map ent-map}

                      ;it's doing nothing, deplete energy so it doesn't instantly make something
                      :else
                      {:ecs (zero-energy ecs ent-id)
                       :ent-map ent-map}
                      )]
    (-> (assoc game :ecs (:ecs ecs-ent-map))
        (assoc :entity-map (:ent-map ecs-ent-map)))))

(defn create []
  {:function   run
   :predicates {:and #{:producer}}})

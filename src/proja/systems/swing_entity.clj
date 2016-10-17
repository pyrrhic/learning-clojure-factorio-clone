(ns proja.systems.swing-entity
  (:require [proja.utils :as utils]
            [proja.ecs.core :as ecs]
            [proja.entities.core :as e]
            [clojure.test :as test]))

;TODO pull stuff from output containers.
;TODO can arms pull stuff from containers? I want them to.

(defn- container-type-drop [entity-map loc]
  (let [tile (get entity-map loc)]
    (cond
      (:pickupable tile) :pickupable
      (:input-container tile) :input-container
      (:container tile) :container
      :default :pickupable)))

(defn- container-type-pickup [entity-map loc]
  (let [tile (get entity-map loc)]
    (cond
      (:pickupable tile) :pickupable
      (:output-container tile) :output-container
      (:container tile) :container
      :default :pickupable)))

(defn- get-item-id [ecs entity-map loc]
  "pickupables are just picked up.
  containers and output-containers have their first item picked up."
  (let [container-t (container-type-pickup entity-map loc)]
    (cond
      (= :pickupable container-t)
      (get-in entity-map [loc container-t])

      (= :output-container container-t)
      (-> (ecs/component ecs :output-container (get-in entity-map [loc :building-id]))
          :items
          first
          second
          first)

      (= :container container-t)
      (-> (ecs/component ecs :container (get-in entity-map [loc :building-id]))
          :items
          first
          second
          first)
      )))

(defn remove-item [ecs container-type container-id item-id]
  {:post [(test/is #(not (nil? %))
                   (str container-type item-id))]}
  (if (not= :pickupable container-type)
    (let [container (ecs/component ecs container-type container-id)
          item-type (-> (filter #((second %) item-id)
                                (:items container))
                        first
                        first)]
      (ecs/update-component ecs container-type container-id
                            (fn [c] (update-in c [:items item-type] #(disj % item-id)))))
    ecs))

(defn pickup [ecs ent-id item-id ent-map swingable]
  (let [container-type (container-type-pickup ent-map (:input-em-key swingable))
        container-id (get-in ent-map [(:input-em-key swingable) :building-id])]
    (-> (ecs/update-component ecs :swingable
                              ent-id
                              #(assoc % :held-item item-id))
        (remove-item container-type container-id item-id)
        (ecs/disable-entity item-id))))

(defn start-animation [ecs ent-id animation-name]
  (ecs/update-component ecs :animation ent-id
                        #(assoc % :current-animation animation-name)))

(defn remove-item-id [entity-map loc]
  "Removes the item id from the entity map."
  (let [container-t (container-type-drop entity-map loc)]
    (if (= :pickupable container-t)
      (assoc-in entity-map [loc container-t] nil)
      entity-map)))

(defn can-drop? [ecs ent-map loc held-item-id container-ent-id]
  (let [container-t (container-type-drop ent-map loc)]
    (if (not= :pickupable container-t)
      (let [storable (ecs/component ecs :storable held-item-id)
            container (ecs/component ecs container-t container-ent-id)]
        (<= (+ (:current-size container) (:size storable))
            (:max-size container)))
      (not (get-in ent-map [loc container-t])))))

(defn- drop-item-container [ecs ent-map loc held-item-id ent-id]
  "returns ecs"
  (let [container-id (get-in ent-map [loc :container])
        storable-held-item (ecs/component ecs :storable held-item-id)
        container-type (container-type-drop ent-map loc)
        updated-container (-> (update (ecs/component ecs container-type container-id) :current-size
                                      #(+ % (:size storable-held-item)))
                              (update-in [:items (:type storable-held-item)]
                                         #(if % (conj % held-item-id) #{held-item-id})))]
    (-> (ecs/replace-component ecs container-type updated-container container-id)
        (ecs/update-component :swingable ent-id #(assoc % :held-item nil))
        (ecs/update-component :swingable ent-id #(assoc % :state :swing-back))
        (start-animation ent-id :swing-back))))

(defn- drop-item-ground [ent-map loc held-item-id]
  "returns entity map"
  (assoc-in ent-map [loc :pickupable] held-item-id))

(defn- drop-item [ecs ent-id ent-map loc held-item-id]
  (if (= :pickupable (container-type-drop ent-map loc))
    {:ecs     (let [swingable (-> (ecs/component ecs :swingable ent-id)
                                  (assoc :held-item nil)
                                  (assoc :state :swing-back))
                    held-item-transform (-> (ecs/component ecs :transform held-item-id)
                                            (assoc :x (-> swingable :output-loc :x (utils/grid->world))
                                                   :y (-> swingable :output-loc :y (utils/grid->world))))]
                (-> (ecs/replace-component ecs :swingable swingable ent-id)
                    (ecs/replace-component :transform held-item-transform held-item-id)
                    (start-animation ent-id :swing-back)
                    (ecs/enable-entity held-item-id)))
     :ent-map (drop-item-ground ent-map loc held-item-id)}
    {:ecs (drop-item-container ecs ent-map loc held-item-id ent-id)
     :ent-map ent-map}))

(defn return-data [game success?]
  {:game game
   :success? success?})

(defn try-pickup [ent-id game]
  (let [ecs (:ecs game)
        energy (ecs/component ecs :energy ent-id)]
    (if (== (:current-amount energy) 100)
      (let [ent-map (:entity-map game)
            swingable (ecs/component ecs :swingable ent-id)
            item-id (get-item-id ecs ent-map (:input-em-key swingable))]
        (if item-id
          (return-data
            (assoc game :ecs (-> (pickup ecs ent-id item-id ent-map swingable)
                                 (start-animation ent-id :swing)
                                 (ecs/replace-component :energy (assoc energy :current-amount 0) ent-id))
                        :entity-map (remove-item-id ent-map (:input-em-key swingable)))
            true)
          (return-data
            (assoc game :ecs (ecs/replace-component ecs
                                                    :energy
                                                    (assoc energy :current-amount 0)
                                                    ent-id))
            false)))
      (return-data game false))))

(defn try-drop [ent-id game]
  (let [ecs (:ecs game)
        energy (ecs/component ecs :energy ent-id)]
    (if (== (:current-amount energy) 100)
      (let [ecs (:ecs game)
            ent-map (:entity-map game)
            swingable (ecs/component ecs :swingable ent-id)]
        (if (can-drop? ecs ent-map (:output-em-key swingable)
                       (:held-item swingable)
                       (get-in ent-map [(:output-em-key swingable) :container]))
          (let [dropped (drop-item ecs ent-id ent-map (:output-em-key swingable) (:held-item swingable))]
            (return-data
              (assoc game :ecs (ecs/replace-component (:ecs dropped)
                                                      :energy
                                                      (assoc energy :current-amount 0)
                                                      ent-id)
                          :entity-map (:ent-map dropped))
              true))
          (return-data
            (assoc game :ecs (ecs/replace-component ecs :energy (assoc energy :current-amount 0) ent-id))
            false)))
      (return-data game false))))

(defn get-action-fn [action-key]
  (case action-key
    :try-pickup try-pickup
    :try-drop try-drop))

(defn inc-action-idx [swingable]
  (let [actions (:actions swingable)
        action-idx (inc (:action-idx swingable))]
    (if (== action-idx (count actions))
      (assoc swingable :action-idx 0)
      (assoc swingable :action-idx action-idx)
      )))

(defn run [ent-id game]
  game
  (do
    (let [ecs (:ecs game)
          swingable (ecs/component ecs :swingable ent-id)
          action-ran ((get-action-fn (nth (:actions swingable) (:action-idx swingable))) ent-id game)]
      (if (:success? action-ran)
        (assoc (:game action-ran)
          :ecs (ecs/update-component (:ecs (:game action-ran)) :swingable ent-id
                                     #(inc-action-idx %)))
        (:game action-ran)))))

(defn create []
  {:function   run
   :predicates {:and #{:swingable}}})



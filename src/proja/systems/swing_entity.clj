(ns proja.systems.swing-entity
  (:require [proja.utils :as utils]
            [proja.ecs.core :as ecs]
            [proja.entities.core :as e]))

;TODO pull stuff from output containers.
;TODO can arms pull stuff from containers? I want them to.

(defn- container-type [entity-map loc]
  (let [tile (get entity-map loc)]
    (cond
      (and (:pickupable tile) (:pickupable tile)) :pickupable
      (:container tile) :container
      (:input-container tile) :input-container
      :default :pickupable)))

(defn- get-item-id [entity-map loc]
  "pickupables are just picked up.
  containers and input-containers have their first item picked up."
  (let [container-t (container-type entity-map loc)]
    (case container-t
      :pickupable (get-in entity-map [loc container-t])
      (first (get-in entity-map [loc container-t])))))

(defn pickup [ecs ent-id item-id]
  (-> (ecs/update-component ecs :swingable
                            ent-id
                            #(assoc % :held-item item-id))
      (ecs/disable-entity item-id)))

(defn start-animation [ecs ent-id animation-name]
  (ecs/update-component ecs :animation ent-id
                        #(assoc % :current-animation animation-name)))

;(defn set-state [ecs ent-id state-name]
;  (ecs/update-component ecs :swingable
;                        ent-id
;                        #(assoc % :state state-name)))

(defn remove-item-id [entity-map loc item-id]
  "Removes the item id from the entity map."
  (let [container-t (container-type entity-map loc)]
    (case container-t
      :pickupable (assoc-in entity-map [loc container-t] nil)
      (update-in entity-map [loc container-t]
                 #(disj % item-id)))))

;(defn- idle [ent-map ecs ent-id]
;  (let [swingable (ecs/component ecs :swingable ent-id)
;        item-id (get-item-id ent-map (:input-em-key swingable))]
;    (if item-id
;      {:ecs     (-> (pickup ecs ent-id item-id)
;                    (start-animation ent-id :swing)
;                    (set-state ent-id :swing))
;       :ent-map (remove-item-id ent-map (:input-em-key swingable) item-id)}
;      {:ecs     ecs
;       :ent-map ent-map})))

(defn can-drop? [ecs ent-map loc held-item-id container-ent-id]
  (let [container-t (container-type ent-map loc)]
    ;if container
    (if (not= :pickupable container-t)
      (let [storable (ecs/component ecs :storable held-item-id)
            container (ecs/component ecs container-t container-ent-id)]
        (<= (+ (:current-size container) (:size storable))
            (:max-size container)))
      ;if pickupable nil
      (not (get-in ent-map [loc container-t])))))

(defn- drop-item-container [ecs ent-map loc held-item-id ent-id]
  "returns ecs"
  (let [container-id (get-in ent-map [loc :container-id])
        storable-held-item (ecs/component ecs :storable held-item-id)
        container-type (container-type ent-map loc)
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
  (if (= :pickupable (container-type ent-map loc))
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

;(defn- swing [ent-map ecs ent-id]
;  (let [animation (ecs/component ecs :animation ent-id)]
;    (if (:current-animation animation)
;      {:ecs ecs
;       :ent-map ent-map}
;      (let [swingable (ecs/component ecs :swingable ent-id)]
;        (if (and (not (:current-animation animation))
;                 (can-drop? ecs ent-map (:output-em-key swingable)
;                            (:held-item swingable)
;                            (get-in ent-map [(:output-em-key swingable) :container-id])))
;          (drop-item ecs ent-id ent-map (:output-em-key swingable) (:held-item swingable))
;          {:ecs ecs
;           :ent-map ent-map})))))
;
;(defn- swing-back [ent-map ecs ent-id]
;  (if (:current-animation (ecs/component ecs :animation ent-id))
;    {:ecs ecs
;     :ent-map ent-map}
;    {:ecs (ecs/update-component ecs :swingable ent-id #(assoc % :state :idle))
;     :ent-map ent-map}))

;(defn run [ent-id game]
;  (let [ecs (:ecs game)
;        updtd-ecs-em (case (:state (ecs/component ecs :swingable ent-id))
;                       :idle (idle (:entity-map game) ecs ent-id)
;                       :swing (swing (:entity-map game) ecs ent-id)
;                       :swing-back (swing-back (:entity-map game) ecs ent-id))]
;    (assoc game :ecs (:ecs updtd-ecs-em)
;                :entity-map (:ent-map updtd-ecs-em))))

(defn return-data [game success?]
  {:game game
   :success? success?})

(defn try-pickup [ent-id game]
  (let [ecs (:ecs game)
        energy (ecs/component ecs :energy ent-id)]
    (if (== (:current-amount energy) 100)
      (let [ent-map (:entity-map game)
            swingable (ecs/component ecs :swingable ent-id)
            item-id (get-item-id ent-map (:input-em-key swingable))]
        (if item-id
          (return-data
            (assoc game :ecs (-> (pickup ecs ent-id item-id)
                                 (start-animation ent-id :swing)
                                 (ecs/replace-component :energy (assoc energy :current-amount 0) ent-id))
                        :entity-map (remove-item-id ent-map (:input-em-key swingable) item-id))
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
                       (get-in ent-map [(:output-em-key swingable) :container-id]))
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

;(ns proja.screens.main-screen)
;(require '[proja.entities.core :as e])
;(do
;  (update-game! #(assoc % :ecs (e/ore-miner (:ecs game/g) (:tex-cache game/g) 1 1)))
;  (update-game! #(assoc % :ecs (e/ore-patch (:ecs game/g) (:tex-cache game/g) 1 1)))
;  (update-game! #(assoc % :ecs (e/ore-patch (:ecs game/g) (:tex-cache game/g) 2 1)))
;  (update-game! #(assoc-in % [:entity-map "11" :ore] #{:2}))
;  (update-game! #(assoc-in % [:entity-map "21" :ore] #{:3}))
;  (update-game! #(assoc % :ecs (e/arm (:ecs game/g) (:tex-cache game/g) 2 5 0)))
;
;
;  (update-game! #(assoc % :ecs (e/factory (:ecs game/g) (:tex-cache game/g) 2 6)))
;  (update-game! #(assoc % :entity-map
;                          (utils/add-producer (:entity-map game/g) :5
;                                              (ecs/component (:ecs game/g) :transform :5)
;                                              (ecs/component (:ecs game/g) :renderable :5)))))


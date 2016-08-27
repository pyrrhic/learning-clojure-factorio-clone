(ns proja.systems.swing-entity
  (:require [proja.utils :as utils]
            [proja.ecs.core :as ecs]
            [proja.entities.core :as e]))

;TODO pull stuff from output containers.
;TODO can arms pull stuff from containers? I want them to.

(defn- container-type [entity-map loc]
  (let [tile (get entity-map loc)]
    (cond
      (and (contains? tile :pickupable) (:pickupable tile)) :pickupable
      (contains? tile :container) :container
      (contains? tile :input-container) :input-container
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

(defn set-state [ecs ent-id state-name]
  (ecs/update-component ecs :swingable
                        ent-id
                        #(assoc % :state state-name)))

(defn remove-item-id [entity-map loc item-id]
  "Removes the item id from the entity map."
  (let [container-t (container-type entity-map loc)]
    (case container-t
      :pickupable (assoc-in entity-map [loc container-t] nil)
      (update-in entity-map [loc container-t]
                 #(disj % item-id)))))

(defn- idle [ent-map ecs ent-id]
  (let [swingable (ecs/component ecs :swingable ent-id)
        item-id (get-item-id ent-map (:input-em-key swingable))]
    (if item-id
      {:ecs     (-> (pickup ecs ent-id item-id)
                    (start-animation ent-id :swing)
                    (set-state ent-id :swing))
       :ent-map (remove-item-id ent-map (:input-em-key swingable) item-id)}
      {:ecs     ecs
       :ent-map ent-map})))

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

(defn- swing [ent-map ecs ent-id]
  (let [animation (ecs/component ecs :animation ent-id)]
    (if (:current-animation animation)
      {:ecs ecs
       :ent-map ent-map}
      (let [swingable (ecs/component ecs :swingable ent-id)]
        (if (and (not (:current-animation animation))
                 (can-drop? ecs ent-map (:output-em-key swingable)
                            (:held-item swingable)
                            (get-in ent-map [(:output-em-key swingable) :container-id])))
          (drop-item ecs ent-id ent-map (:output-em-key swingable) (:held-item swingable))
          {:ecs ecs
           :ent-map ent-map})))))

(defn- swing-back [ent-map ecs ent-id]
  (if (:current-animation (ecs/component ecs :animation ent-id))
    {:ecs ecs
     :ent-map ent-map}
    {:ecs (ecs/update-component ecs :swingable ent-id #(assoc % :state :idle))
     :ent-map ent-map}))

(defn run [ent-id game]
  (let [ecs (:ecs game)
        updtd-ecs-em (case (:state (ecs/component ecs :swingable ent-id))
                       :idle (idle (:entity-map game) ecs ent-id)
                       :swing (swing (:entity-map game) ecs ent-id)
                       :swing-back (swing-back (:entity-map game) ecs ent-id))]
    (assoc game :ecs (:ecs updtd-ecs-em)
                :entity-map (:ent-map updtd-ecs-em))))

(defn create []
  {:function   run
   :predicates {:and #{:swingable}}})

;(ns proja.screens.main-screen)
;(require '[proja.entities.core :as e])
;(update-game! #(assoc % :ecs (e/ore-miner (:ecs game) (:tex-cache game) 1 1)))
;(update-game! #(assoc % :ecs (e/ore-patch (:ecs game) (:tex-cache game) 1 1)))
;(update-game! #(assoc-in % [:entity-map "11" :ore] #{:2}))
;(update-game! #(assoc % :ecs (e/arm (:ecs game) (:tex-cache game) 2 5 0)))
;
;(update-game! #(assoc % :ecs (e/factory (:ecs game) (:tex-cache game) 2 6)))
;(update-game! #(assoc % :entity-map
;                        (utils/add-producer (:entity-map game) :7
;                                            (ecs/component (:ecs game) :transform :7)
;                                            (ecs/component (:ecs game) :renderable :7))))

;(update-game! #(assoc % :ecs (e/storage (:ecs game) (:tex-cache game) 2 6)))
;(update-game! #(assoc % :entity-map
;                        (utils/add-storage (:entity-map game) :4
;                                           (ecs/component (:ecs game) :transform :4)
;                                           (ecs/component (:ecs game) :renderable :4))))

;(update-game! #(assoc % :ecs (e/belt (:ecs game) (:tex-cache game) 2 6 90)))
;(update-game! #(assoc % :ecs (e/belt (:ecs game) (:tex-cache game) 3 6 90)))
;(update-game! #(assoc % :ecs (e/arm (:ecs game) (:tex-cache game) 5 6 90)))
;(update-game! #(assoc % :ecs (e/arm (:ecs game) (:tex-cache game) 2 5 0)))


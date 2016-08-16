(ns proja.systems.swing-entity
  (:require [proja.utils :as utils]
            [proja.ecs.core :as ecs]
            [proja.entities.core :as e]))

;pickup item
;remove it from the entity map
;move it to somewhere off the map so it's 'hidden'.
;set it in the swingable :held-item

(defn ent-map-key
  ([transform]
   (ent-map-key transform 0 0))
  ([transform x-offset y-offset]
   (let [add (fn [n offset] (+ n (* utils/tile-size offset)))
         x (-> transform :x (add x-offset) (utils/world->grid))
         y (-> transform :y (add y-offset) (utils/world->grid))]
     (str x y))))

;idle
;--if there is a pickupable in pickup spot, change state to swing
;--else do nothing
(defn idle [ent-map ecs ent-id]
  (let [transform (ecs/component ecs :transform ent-id)
        have-pickupable? (-> (:pickupable (get ent-map (ent-map-key transform))) (nil?) (not))]
    (if have-pickupable?
      {:ecs (ecs/update-component ecs :swingable ent-id #(assoc % :state :swing))
       :ent-map ent-map}
      {:ecs ecs
       :ent-map ent-map})))

;swing
;--if not 'holding' item and current animation is nil, set the current animation to swing and pickup item
;--else if 'holding' item and current animation is set to swing, do nothing
;--else if 'holding' item and current animation is nil, set state to swing-back
(defn swing [ent-map ecs ent-id]
  (let [swingable (ecs/component ecs :swingable ent-id)
        held-item (:held-item swingable)
        animation (ecs/component ecs :animation ent-id)
        current-animation (:current-animation animation)
        transform (ecs/component ecs :transform ent-id)]
    (cond
      (and (not held-item) (not current-animation))
      {:ecs
       (-> ecs
           (ecs/replace-component :animation
                                  (assoc animation :current-animation :swing)
                                  ent-id)
           (ecs/replace-component :swingable
                                  (assoc swingable :held-item
                                                   (->> (ent-map-key transform) (get ent-map) :pickupable))
                                  ent-id)
           (ecs/remove-entity (:pickupable (get ent-map (ent-map-key transform)))))
       :ent-map
       (dissoc ent-map (ent-map-key transform) :pickupable)}

      (and held-item current-animation)
      {:ecs ecs
       :ent-map ent-map}

      (and held-item (nil? current-animation))
      {:ecs (ecs/replace-component ecs
                                   :swingable
                                   (assoc swingable :state :swing-back)
                                   ent-id)
       :ent-map ent-map})))

;swing-back
;--if holding item and current animation is nil, set animation to swing back and drop item
;--if not holding item and current animation is set to swing back, do nothing
;--if not holding item and current animation is nil, set state to idle
;TODO need to check if 'output' spot already has a :pickupable there or not.
(defn swing-back [ent-map ecs ent-id tex-cache]
  (let [swingable (ecs/component ecs :swingable ent-id)
        held-item (:held-item swingable)
        animation (ecs/component ecs :animation ent-id)
        current-animation (:current-animation animation)
        transform (ecs/component ecs :transform ent-id)]
    (cond
      (and held-item (nil? current-animation))
      (let [ecs-added-ent-id (ecs/add-entity ecs
                                             (e/ore-piece tex-cache
                                                          (-> transform :x (utils/world->grid))
                                                          (+ 2 (-> transform :y (utils/world->grid)))))]
        {:ecs     (-> (:ecs ecs-added-ent-id)
                      (ecs/replace-component :animation
                                             (assoc animation :current-animation :swing-back)
                                             ent-id)
                      (ecs/replace-component :swingable
                                             (assoc swingable :held-item nil)
                                             ent-id))
         :ent-map (assoc-in ent-map [(ent-map-key transform 0 2) :pickupable] (:ent-id ecs-added-ent-id))})

      (and (not held-item) (= current-animation :swing-back))
      {:ecs ecs
       :ent-map ent-map}

      (and (not held-item) (nil? current-animation))
      {:ecs (ecs/replace-component ecs
                                   :swingable
                                   (assoc swingable :state :idle)
                                   ent-id)
       :ent-map ent-map}
      )))

(defn run [ent-id game]
  (let [ecs-ent-map (case (:state (ecs/component (:ecs game) :swingable ent-id))
                      :idle (idle (:entity-map game) (:ecs game) ent-id)
                      :swing (swing (:entity-map game) (:ecs game) ent-id)
                      :swing-back (swing-back (:entity-map game) (:ecs game) ent-id (:tex-cache game)))]
    (assoc game :ecs (:ecs ecs-ent-map)
                :entity-map (:ent-map ecs-ent-map))))

(defn create []
  {:function   run
   :predicates {:and #{:swingable}}})

;(ns proja.screens.main-screen)
;(require '[proja.entities.core :as e])
;(update-game! #(assoc % :ecs (e/ore-patch (:ecs game) (:tex-cache game) 5 5)))
;(update-game! #(assoc % :ecs (e/ore-patch (:ecs game) (:tex-cache game) 5 6)))
;(update-game! #(assoc % :ecs (e/ore-patch (:ecs game) (:tex-cache game) 4 7)))
;(update-game! #(assoc % :ecs (e/ore-patch (:ecs game) (:tex-cache game) 4 8)))
;(update-game! #(assoc % :ecs (e/ore-miner (:ecs game) (:tex-cache game) 5 5)))
;(update-game! #(assoc % :ecs (e/ore-miner (:ecs game) (:tex-cache game) 8 5)))
;(update-game! #(assoc % :ecs (e/ore-patch (:ecs game) (:tex-cache game) 0 5)))
;(update-game! #(assoc % :ecs (e/ore-patch (:ecs game) (:tex-cache game) 7 5)))
;
;(loop [ent-ids (range 1 9)]
;  (if (empty? ent-ids)
;    nil
;    (let [id (-> ent-ids (first) (str) (keyword))]
;      (if (not (or (= id :5) (= id :6)))
;        (do (let [ent-id id
;                  k (ent-map-key (-> (ecs/component (:ecs game) :transform ent-id)
;                                     (get :x)
;                                     (proja.utils/world->grid))
;                                 (-> (ecs/component (:ecs game) :transform ent-id)
;                                     (get :y)
;                                     (proja.utils/world->grid)))]
;              (update-game! #(assoc-in % [:entity-map k :ore] #{ent-id})))
;            (recur (rest ent-ids)))
;        (recur (rest ent-ids))))))
;
;(update-game! #(assoc % :ecs (e/arm (:ecs game) (:tex-cache game) 6 8)))

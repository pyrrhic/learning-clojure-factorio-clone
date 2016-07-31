(ns proja.ecs.core
  (:require [proja.utils :as utils]))

(defn init []
  "Returns ecs map"
  {:entity-id-counter (atom 0N)
   ; {:ent-id {:c-types c-data}}
   :ent-comps {},
   ; vector of these maps
   ; {:function, the logic
   ;  :begin, logic to be run before.
   ;  :end, logic to be run after.
   ;  :predicates, {:and [c-types]
   ;               :or [c-types]
   ;               :not [c-types]}
   ;  :qualifying-ents, #{entity id's}}}
   ;
   :systems []})

(defn- new-id! [ecs]
  (let [counter (:entity-id-counter ecs)]
    (swap! counter inc)
    @counter))

(defn- num->keyword [n]
  (-> n str keyword))

(defn- qualify? [ecs ent-id sys-preds]
  "sys-preds should be a map containing {:and [c-types], :or [c-types], :not [c-types]}"
  (let [comps (get-in ecs [:ent-comps ent-id])
        and? (every? #(% comps) (:and sys-preds))
        not? (if (:not sys-preds)
               (not-every? #(% comps) (:not sys-preds))
               true)
        or? (if (:or sys-preds)
              (some #(% comps) (:or sys-preds))
              true)]
    (and and? not? or?)))

(defn- update-system-ents [ecs ent-id]
  (assoc ecs :systems (mapv (fn update-sys-ents [s]
                              (if (qualify? ecs ent-id (:predicates s))
                                (update s :qualifying-ents #(conj % ent-id))
                                (update s :qualifying-ents #(disj % ent-id))))
                            (get ecs :systems))))

(defn add-component [ecs c-type c-data ent-id]
  "Returns an updated ecs"
  (-> ecs
      (assoc-in [:ent-comps ent-id c-type] c-data)
      (update-system-ents ent-id)))

;update-component that only modifys ent-comps, don't need to update the system entities

(defn remove-component [ecs c-type ent-id]
  "Returns an updated ecs"
  (-> ecs
      (utils/dissoc-in [:ent-comps ent-id c-type])
      (update-system-ents ent-id)))

(defn component [ecs c-type ent-id]
  (get-in ecs [:ent-comps ent-id c-type]))

(defn add-entity
  "@param components should be a vector of {:type component-type, :data component-data}
  @returns a map with an updated ecs, :ecs and a new entity id, :new-id.
  If no components were provided, the returned ecs will be unmodified."
  ([ecs]
    (add-entity ecs []))
  ([ecs components]
   (let [ent-id (num->keyword (new-id! ecs))]
     (loop [a-ecs ecs
            comps components]
       (if (empty? comps)
         {:ecs a-ecs, :new-id ent-id}
         (let [c (first comps)]
           (recur (add-component a-ecs (:type c) (:data c) ent-id)
                  (rest comps))))))))

(defn remove-entity [ecs ent-id]
  (let [cleaned-ecs-ent-comp (utils/dissoc-in ecs [:ent-comps ent-id])
        cleaned-systems (mapv (fn [s]
                                (update s :qualifying-ents #(disj % ent-id)))
                              (-> ecs :systems))]
    (assoc cleaned-ecs-ent-comp :systems cleaned-systems)))

(defn add-system [ecs system]
  {:pre [(:function system), (:predicates system), (not (:qualifying-ents system))]}
  "system should be {:function f :predicates p} with optional keys :begin and :end.
   :begin should be a function that takes and returns a game map. it will be called before the :function
   :end should be a function that takes and returns a game map. it will be called after the :function
  All systems must be added before any entities or components are added to the ecs.
  Otherwise those entities will never be picked up by any systems."
  (update ecs :systems #(conj % (assoc system :qualifying-ents #{}))))

(defn- run-system [sys game]
  (let [f (:function sys)
        begin-fn #(if (:begin sys) ((:begin sys) %) %)
        end-fn #(if (:end sys) ((:end sys) %) %)
        run-sys-fn #(loop [es (:qualifying-ents sys)
                           g %]
                     (if (empty? es)
                       g
                       (recur (rest es)
                              (f (first es) g))))]
    (-> game
        (begin-fn)
        (run-sys-fn)
        (end-fn))))

(defn run [game]
  (let [entity-cs (:ecs game)
        systems (:systems entity-cs)]
    (loop [sys systems
           g game]
      (if (empty? sys)
        g
        (recur (rest sys)
               (run-system (get sys 0) g))))))






(ns proja.ecs.core
  (:require [proja.utils :as utils]
            [clojure.test :as test]))

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
   :systems []
   ;map, keys are the tags, values are Sets of ent-id's
   :tags {}})

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

(defn component [ecs c-type ent-id]
  #_{:post [(test/is (cond
                     (= :disabled c-type) true
                     (nil? %) false
                     :else true)
                   (str "c-type = " c-type " ent-id = " ent-id))]}
  (get-in ecs [:ent-comps ent-id c-type]))

(defn unsafe-component [ecs c-type ent-id]
  (get-in ecs [:ent-comps ent-id c-type]))

(defn add-component [ecs c-type c-data ent-id]
  "Returns an updated ecs."
  (-> ecs
      (assoc-in [:ent-comps ent-id c-type] c-data)
      (update-system-ents ent-id)))

(defn add-temp-component [ecs c-type c-data ent-id]
  (assoc-in ecs [:ent-comps ent-id c-type] c-data))

;update-component that only modifys ent-comps, don't need to update the system entities
(defn update-component [ecs c-type ent-id func]
  {:pre [(fn? func)]}
  (let [c-data (func (component ecs c-type ent-id))]
    (assoc-in ecs [:ent-comps ent-id c-type] c-data)))

(defn remove-component [ecs c-type ent-id]
  "Returns an updated ecs"
  (-> ecs
      (utils/dissoc-in [:ent-comps ent-id c-type])
      (update-system-ents ent-id)))

(defn remove-temp-component [ecs c-type ent-id]
  (utils/dissoc-in ecs [:ent-comps ent-id c-type]))

(defn replace-component [ecs c-type c-data ent-id]
  (assoc-in ecs [:ent-comps ent-id c-type] c-data))

(defn update-components [ecs ent-id updated-comps]
  "updated-comps should be a map, where the key is the component type and the value is the component data.
  Returns an updated ecs."
  (loop [ks (keys updated-comps)
         ent-cs ecs]
    (if (empty? ks)
      ent-cs
      (recur (rest ks)
             (replace-component ent-cs (first ks) ((first ks) updated-comps) ent-id)))))

(defn add-tag [ecs tag ent-id]
  (update-in ecs [:tags tag] #(if (nil? %)
                    (conj #{} ent-id)
                    (conj % ent-id))))

(defn ent-ids-for-tag [ecs tag]
  "Set of ent-id's for the tag"
  (get-in ecs [:tags tag]))

(defn create-component [type data]
  "This is only useful for adding entities with a vector of components in add-entity. No other use."
  {:type type, :data data})

(defn add-entity
  "@param components should be a vector of {:type component-type, :data component-data}
  @returns a map with an updated ecs, :ecs and a new entity id, :ent-id.
  If no components were provided, the returned ecs will be unmodified."
  ([ecs]
    (add-entity ecs []))
  ([ecs components]
   (let [ent-id (num->keyword (new-id! ecs))]
     (loop [a-ecs ecs
            comps components]
       (if (empty? comps)
         {:ecs a-ecs, :ent-id ent-id}
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

(defn run-system [sys game]
  (let [ecs (:ecs game)
        f (:function sys)
        begin-fn #(if (:begin sys) ((:begin sys) %) %)
        end-fn #(if (:end sys) ((:end sys) %) %)
        run-sys-fn (if (:is-belt sys)
                     #(f %)
                     #(loop [es (:qualifying-ents sys)
                             g %]
                       (if (empty? es)
                         g
                         (if (component ecs :disabled (first es))
                           (recur (rest es)
                                  g)
                           (recur (rest es)
                                  (f (first es) g))
                           ))))]
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
               (run-system (first sys) g))))))

(defn disable-entity [ecs ent-id]
  "Returns an updated ecs."
  (add-component ecs :disabled {} ent-id))

(defn enable-entity [ecs ent-id]
  "Returns an updated ecs."
  (remove-component ecs :disabled ent-id))

(defn latest-ent-id [ecs]
  @(:entity-id-counter ecs))




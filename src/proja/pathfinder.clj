(ns proja.pathfinder
  (:require [proja.tile-map.core :as grid]))

;g(n) represents the exact cost of the path from the starting point to any vertex n
;h(n) represents the heuristic estimated cost from vertex n to the goal.
;f(n) = g(n) + h(n).

(defn get-cheapest-node-indx [open]
  "Returns the index of the node with the cheapest f value.
   Returns nil if open is empty."
  (loop [indx 0
         cheapest-f-indx 0]
    (cond
      (empty? open)
      nil
      (= (count open) indx)
      cheapest-f-indx
      :else (recur (inc indx)
                   (if (try (< (:f (nth open indx))
                               (:f (nth open cheapest-f-indx)))
                            (catch Exception e
                              (println (:f (nth open indx)))
                              (println (:f (nth open cheapest-f-indx)))))
                     indx
                     cheapest-f-indx)))))

(defn is-goal-node? [node goal]
  "Returns true if node == goal"
  (let [x (:grid-x node)
        y (:grid-y node)
        goal-x (:grid-x goal)
        goal-y (:grid-y goal)]
    (and
      (= x goal-x)
      (= y goal-y))))

(defn remove-indx [vect indx]
  "Returns a new vector that does not contain the item at the specified index."
  (let [vect (vec vect)]
    (if (= (inc indx) (count vect))
      (pop vect)
      (concat (subvec vect 0 indx) (subvec vect (inc indx))))))

(defn contains-node? [vect-of-maps node]
  "Returns true if the vector contains a map with an :grid-x and :grid-y matching that in the node map"
  (let [x (:grid-x node) y (:grid-y node)]
    (not (empty? (filter (fn [m] (and (= (:grid-x m) x)
                                      (= (:grid-y m) y)))
                         vect-of-maps)))))

(defn calc-g-for-node [node from-node]
  "Calculate the cost aka g value for param node. from-node should be the parent, or where we are coming from."
  (+ (:g from-node) (:move-cost node)))

(defn calc-h-for-node [node goal-node]
  "Calculate the manhattan distance from node to goal-node."
  (+ (Math/abs (- (:grid-x node) (:grid-x goal-node)))
     (Math/abs (- (:grid-y node) (:grid-y goal-node)))))

(defn calc-f-for-node [node goal-node]
  "Calculate total cost"
  (+ (:g node)
     (calc-h-for-node node goal-node)))

(defn get-index-of-node [vect node]
  (loop [indx 0]
    (cond
      (= (count vect) indx)
      nil
      (and (= (:grid-x node) (:grid-x (nth vect indx)))
           (= (:grid-y node) (:grid-y (nth vect indx))))
      indx
      :else
      (recur (inc indx)))))

(defn remove-node [vect node]
  (remove-indx vect (get-index-of-node vect node)))

(defn get-node [vect node]
  (nth vect (get-index-of-node vect node)))

(defn calculate-costs [neighbor-node origin-node goal-node]
  (let [neighbor-updated-parent (assoc neighbor-node :parent origin-node)
        new-g (calc-g-for-node neighbor-updated-parent origin-node)
        neighbor-partial-updated (assoc neighbor-updated-parent :g new-g)
        new-f (calc-f-for-node neighbor-partial-updated goal-node)
        neighbor-fully-updated (assoc neighbor-partial-updated :f new-f)]
    neighbor-fully-updated))

(defn inspect-neighbors [origin-node tile-grid open-vec closed-vec goal-node]
  (loop [neighbors (grid/get-neighbors (:grid-x origin-node) (:grid-y origin-node) tile-grid)
         open open-vec
         closed closed-vec]
    (if (empty? neighbors)
      {:open open
       :closed closed}
      (let [unvetted-neighbor-node (first neighbors)
            cost-from-closed (if (contains-node? closed unvetted-neighbor-node) (get-node closed unvetted-neighbor-node) nil)
            cost-from-open (if (contains-node? open unvetted-neighbor-node) (get-node open unvetted-neighbor-node) nil)
            neighbor-node (if (nil? cost-from-closed)
                            (if (nil? cost-from-open)
                              unvetted-neighbor-node
                              cost-from-open)
                            cost-from-closed)]
        (cond
          ;ignore if start node? so start doesn't have parent - wtf is this comment?
          (false?(:passable neighbor-node))
          (recur (rest neighbors)
                 open
                 closed) ;skip
          (and (contains-node? closed neighbor-node) (>= (+ (:g origin-node) (:move-cost neighbor-node)) (:g neighbor-node)))
          (recur (rest neighbors)
                 open
                 closed) ;skip
          (and (contains-node? closed neighbor-node) (< (+ (:g origin-node) (:move-cost neighbor-node)) (:g neighbor-node)))
          (recur (rest neighbors)
                 open
                 (conj (remove-node closed neighbor-node) (calculate-costs neighbor-node origin-node goal-node)))
          (and (contains-node? open neighbor-node) (>= (+ (:g origin-node) (:move-cost neighbor-node)) (:g neighbor-node)))
          (recur (rest neighbors)
                 open
                 closed) ;skip
          (and (contains-node? open neighbor-node) (< (+ (:g origin-node) (:move-cost neighbor-node)) (:g neighbor-node)))
          (recur (rest neighbors)
                 (conj (remove-node open neighbor-node) (calculate-costs neighbor-node origin-node goal-node))
                 closed)
          :else
          (recur (rest neighbors)
                 (conj open (calculate-costs neighbor-node origin-node goal-node))
                 closed))))))

;TODO: the start node is being included in the path. do I really want that? I don't think there is a scenario where this is ever useful.
;      but, maybe there will be, so I'll just leave it in for now.
(defn extract-path [goal]
  (loop [path (list goal)
         current (:parent goal)]
    (if (nil? current)
      (map (fn [tile] (dissoc tile :parent :texture :f :g)) path)
      (recur (conj path current) (:parent current)))))

;sometimes the start will have it's parent set to another node, creating an infinite loop
;when we're trying to build a path from the goal
(defn calc-path [start goal tile-grid]
  "returns a string if no path is found, else returns the goal node with parent pointing all the way back to start."
  (loop [open [(assoc start :g 0 :f 0)]
         closed []]
    (if (empty? open)
      '() ;failed to find a path
      (let [current-index (get-cheapest-node-indx open)
            current-node (nth open current-index)
            open-without-current (remove-indx open current-index)
            closed-with-current (conj closed current-node)]
        (if (is-goal-node? current-node goal)
          (extract-path current-node)
          (let [updated-lists (inspect-neighbors current-node tile-grid open-without-current closed-with-current goal)]
            (recur (:open updated-lists)
                   (:closed updated-lists))))))))

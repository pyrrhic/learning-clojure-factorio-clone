(ns proja.utils)

(def energy-tick 30)

(def tile-size 32)

(defn world->grid [n]
  (int (quot n tile-size)))

(defn grid->world [n]
  (float (* n tile-size)))

(defn stop-animation [animation]
  (-> animation
      (assoc :current-animation nil)
      (assoc :current-frame -1)))

(defn ent-map-key
  "Returns the entity map key based on the transform.
  Offsets are in terms of tiles.
  The x and y for the transform are in terms of world coordinates."
  ([x y]
    (str x \_ y))
  ([transform]
   (ent-map-key transform 0 0))
  ([transform x-offset y-offset]
   (let [add-offset (fn [n offset] (+ n (* tile-size offset)))
         add-origin (fn [n origin] (+ n origin))
         x (-> transform :x (add-origin (:origin-x transform)) (add-offset x-offset) (world->grid))
         y (-> transform :y (add-origin (:origin-y transform)) (add-offset y-offset) (world->grid))]
     (ent-map-key x y))))

;Keys are strings of x grid + y grid, so (str (+ 1 1)).
;Values are Maps, keys are ent 'types' and values are Sets of ent id's
(defn add-producer [ent-map ent-id transform renderable]
  (let [x (-> transform :x (world->grid))
        y (-> transform :y (world->grid))
        width (-> renderable :texture (.getRegionWidth) (world->grid))
        height (-> renderable :texture (.getRegionHeight) (world->grid))
        fns (for [x (range x (+ x width))
                  y (range y (+ y height))
                  :let [k (ent-map-key (int x) (int y))]]
              (fn [ent-map] (update ent-map k #(if %
                                                (assoc % :input-container ent-id
                                                         :output-container ent-id
                                                         :container ent-id)
                                                {:input-container  ent-id,
                                                 :output-container ent-id
                                                 :container ent-id}))))]
    (loop [funcs fns
           e-map ent-map]
      (if (empty? funcs)
        e-map
        (recur (rest funcs)
               ((first funcs) e-map))))))

(defn add-storage [ent-map ent-id transform renderable]
  (let [x (-> transform :x (world->grid))
        y (-> transform :y (world->grid))
        width (-> renderable :texture (.getRegionWidth) (world->grid))
        height (-> renderable :texture (.getRegionHeight) (world->grid))
        fns (for [x (range x (+ x width))
                  y (range y (+ y height))
                  :let [k (ent-map-key (int x) (int y))]]
              (fn [ent-map] (update ent-map k #(if %
                                                (assoc % :container ent-id)
                                                {:container ent-id}))))]
    (loop [funcs fns
           e-map ent-map]
      (if (empty? funcs)
        e-map
        (recur (rest funcs)
               ((first funcs) e-map))))))

(defn add-building
  ([ent-map ent-id transform renderable]
   (add-building ent-map ent-id
                 transform
                 0
                 0
                 (-> renderable :texture (.getRegionWidth) (world->grid))
                 (-> renderable :texture (.getRegionHeight) (world->grid))))
  ([ent-map ent-id transform x-offset y-offset width height]
   (let [x (+ (-> transform :x (world->grid)) x-offset)
         y (+ (-> transform :y (world->grid)) y-offset)
         fns (for [x (range x (+ x width))
                   y (range y (+ y height))
                   :let [k (ent-map-key (int x) (int y))]]
               (fn [ent-map] (assoc-in ent-map [k :building-id] ent-id)))]
     (loop [funcs fns
            e-map ent-map]
       (if (empty? funcs)
         e-map
         (recur (rest funcs)
                ((first funcs) e-map)))))))

(defn collides-with-a-building?
  ([ent-map transform renderable]
   (let [x (-> transform :x (world->grid))
         y (-> transform :y (world->grid))
         width (-> renderable :texture (.getRegionWidth) (world->grid))
         height (-> renderable :texture (.getRegionHeight) (world->grid))
         building-ids (for [x (range x (+ x width))
                            y (range y (+ y height))
                            :let [k (ent-map-key (int x) (int y))]]
                        (-> ent-map (get k) :building-id))]
     (pos? (count (filter some? building-ids)))))
  ([ent-map transform width height]
   (let [x (-> transform :x (world->grid))
         y (-> transform :y (world->grid))
         building-ids (for [x (range x (+ x width))
                            y (range y (+ y height))
                            :let [k (ent-map-key (int x) (int y))]]
                        (-> ent-map (get k) :building-id))]
     (pos? (count (filter some? building-ids))))))

(defn my-keyword [n]
  (-> n (str) (keyword)))

(defn dissoc-in
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (assoc m k newmap))
      m)
    (dissoc m k)))

(defn man-distance [x0 y0 x1 y1]
  "manhattan distance"
  (+ (Math/abs (- x1 x0)) (Math/abs (- y1 y0))))

(defn euclidean-distance [x0 y0 x1 y1]
  (Math/sqrt
    (+ (Math/pow (- x1 x0) 2)
       (Math/pow (- y1 y0) 2))))

(defn round-to-decimal [number decimal]
  (if (instance? Long number)
    number
    (let [formatted (format (str "%." decimal "f") number)]
      (if (instance? String formatted)
        (Double/parseDouble formatted)
        formatted))))
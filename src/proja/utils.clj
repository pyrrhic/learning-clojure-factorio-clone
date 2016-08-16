(ns proja.utils)

(def tile-size 32)

(defn world->grid [n]
  (int (/ n tile-size)))

(defn grid->world [n]
  (float (* n tile-size)))

(defn stop-animation [animation]
  (-> animation
      (assoc :current-animation nil)
      (assoc :current-frame -1)))

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
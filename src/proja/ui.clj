(ns proja.ui
  (:require [proja.utils :as utils]
            [proja.ecs.core :as ecs]
            [proja.components.core :as c]
            [proja.entities.core :as e]
            [proja.screens.game :as game]
            [proja.systems.render :as render])
  (:import (com.badlogic.gdx.scenes.scene2d.utils ChangeListener)
           (com.badlogic.gdx.scenes.scene2d.ui TextButton Table)
           (com.badlogic.gdx.scenes.scene2d Stage)
           (com.badlogic.gdx.math Vector3)
           (com.badlogic.gdx.graphics.g2d SpriteBatch)
           (com.badlogic.gdx Gdx)))

(defn set-storage-ent-map [ent-map ecs ent-id]
  (let [transform (ecs/component ecs :transform ent-id)
        x (-> transform :x (utils/world->grid))
        y (-> transform :y (utils/world->grid))
        texture (:texture (ecs/component ecs :renderable ent-id))
        width (-> (.getRegionWidth texture) (utils/world->grid))
        height (-> (.getRegionHeight texture) (utils/world->grid))
        e-map-functions (for [x (range x (+ x width))
                              y (range y (+ y height))
                              :let [tile-loc (utils/ent-map-key x y)]]
                          (fn [ent-map] (assoc-in ent-map [tile-loc :storage] ent-id)))]
    (loop [fs e-map-functions
           e-map ent-map]
      (if (empty? fs)
        e-map
        (recur (rest fs)
               ((first fs) e-map))))))

;(defn tiles-under [transform-d renderable-d tile-map]
;  "Expects the entity to be grid aligned."
;  (let [w (-> renderable-d :texture (.getRegionWidth) (/ utils/tile-size))
;        h (-> renderable-d :texture (.getRegionHeight) (/ utils/tile-size))
;        x (-> transform-d :x (utils/world->grid))
;        y (-> transform-d :y (utils/world->grid))]
;    (for [row (range x (+ x w))
;          col (range y (+ y h))]
;      (tmap/get-tile row col tile-map))))

;(defn all-passable? [tiles]
;  (every? #(:passable %) tiles))
;
;(defn set-passable [tile-map tiles passable?]
;  (loop [tilez tiles
;         tmap tile-map]
;    (if (empty? tilez)
;      tmap
;      (recur (rest tilez)
;             (let [t (first tilez)]
;               (assoc-in tmap [(:grid-x t)
;                               (:grid-y t)
;                               :passable]
;                         passable?))))))

;(defn roadless? [tiles]
;  (not-any? #(:passable %) tiles))

;(defn placeable? [tiles-under road-tiles-under]
;  (and (all-passable? tiles-under)
;       (roadless? road-tiles-under)))

;(defn build-warehouse [game]
;  (let [pf-tex (-> game :tex-cache :warehouse)
;        mx (-> game :inputs :mouse-x)
;        my (-> game :inputs :mouse-y)
;        world-v3 (-> (:camera game) (.unproject (Vector3. mx my 0)))
;        tile-align-x (-> (quot (.-x world-v3) utils/tile-size) (* utils/tile-size))
;        tile-align-y (-> (quot (.-y world-v3) utils/tile-size) (* utils/tile-size))
;        transform-c (c/transform tile-align-x tile-align-y 0 0 0)
;        renderable-c (c/renderable pf-tex)
;        tiles-under-wh (tiles-under (:data transform-c) (:data renderable-c) (:tile-map game))
;        road-tiles-under (tiles-under (:data transform-c) (:data renderable-c) (:road-map game))
;        placement? (placeable? tiles-under-wh road-tiles-under)]
;    (if placement?
;      (render/run-single transform-c
;                         renderable-c
;                         (:batch game))
;      (render/run-single transform-c
;                         (assoc-in renderable-c [:data :texture] (-> game :tex-cache :warehouse-red))
;                         (:batch game)))
;
;    (let [click-x (-> game :inputs :mouse-click-x)
;          click-y (-> game :inputs :mouse-click-y)]
;      (if (and click-x click-y placement?)
;        (let [wh-added-g (assoc game :ecs (e/warehouse (:ecs game)
;                                                         (:tex-cache game)
;                                                         tile-align-x
;                                                         tile-align-y))]
;          (assoc wh-added-g :tile-map (set-passable (:tile-map wh-added-g) tiles-under-wh false)))
;        game))))

;(defn build-potato-farm [game]
;  (let [pf-tex (-> game :tex-cache :potato-farm)
;        mx (-> game :inputs :mouse-x)
;        my (-> game :inputs :mouse-y)
;        world-v3 (-> (:camera game) (.unproject (Vector3. mx my 0)))
;        tile-align-x (-> (quot (.-x world-v3) utils/tile-size) (* utils/tile-size))
;        tile-align-y (-> (quot (.-y world-v3) utils/tile-size) (* utils/tile-size))
;        transform-c (c/transform tile-align-x tile-align-y 0 0 0)
;        renderable-c (c/renderable pf-tex)
;        tiles-under-pf (tiles-under (:data transform-c) (:data renderable-c) (:tile-map game))
;        road-tiles-under (tiles-under (:data transform-c) (:data renderable-c) (:road-map game))
;        placement? (placeable? tiles-under-pf road-tiles-under)]
;    (if placement?
;      (render/run-single transform-c
;                         renderable-c
;                         (:batch game))
;      (render/run-single transform-c
;                         (assoc-in renderable-c [:data :texture] (-> game :tex-cache :potato-farm-red))
;                         (:batch game)))
;
;    (let [click-x (-> game :inputs :mouse-click-x)
;          click-y (-> game :inputs :mouse-click-y)]
;      (if (and click-x click-y placement?)
;        (let [pf-added-g (assoc game :ecs (e/potato-farm (:ecs game)
;                                                         (:tex-cache game)
;                                                         tile-align-x
;                                                         tile-align-y))]
;            (assoc pf-added-g :tile-map (set-passable (:tile-map pf-added-g) tiles-under-pf false)))
;        game))))

;(defn build-road [game]
;  (let [texture (-> game :tex-cache :road)
;        mx (-> game :inputs :mouse-x)
;        my (-> game :inputs :mouse-y)
;        world-v3 (-> (:camera game) (.unproject (Vector3. mx my 0)))
;        tile-align-x (-> (quot (.-x world-v3) utils/tile-size) (* utils/tile-size))
;        tile-align-y (-> (quot (.-y world-v3) utils/tile-size) (* utils/tile-size))
;        transform-c (c/transform tile-align-x tile-align-y 0 0 0)
;        renderable-c (c/renderable texture)
;        tiles-under-road (tiles-under (:data transform-c) (:data renderable-c) (:tile-map game))
;        road-tiles-under (tiles-under (:data transform-c) (:data renderable-c) (:road-map game))
;        placement? (placeable? tiles-under-road road-tiles-under)]
;    (if placement?
;      (render/run-single transform-c
;                         renderable-c
;                         (:batch game))
;      (render/run-single transform-c
;                         (assoc-in renderable-c [:data :texture] (-> game :tex-cache :road-red))
;                         (:batch game)))
;
;    (let [click-x (-> game :inputs :mouse-click-x)
;          click-y (-> game :inputs :mouse-click-y)]
;      (if (and click-x click-y placement?)
;        (let [thing-added-g (assoc game :ecs (e/road (:ecs game)
;                                                     (:tex-cache game)
;                                                     tile-align-x
;                                                     tile-align-y))
;              road-map-updt-g (assoc-in thing-added-g [:road-map
;                                                       (utils/world->grid tile-align-x)
;                                                       (utils/world->grid tile-align-y)
;                                                       :passable]
;                                        true)]
;          road-map-updt-g)
;        game)))
;  )

;if ore is not already there in entity map
;--
;tile x and y, entity map, check if there is (-> :ore ent-id) there.

;get mouse location in world coordinates
;get tile coords of the mouse location
;if ore is not already there in entity map
;  render
;  if there is a mouse click
;    place the thing
;    clear out mouse click
;else
;  render red
;  clear mouse click

(defn single-draw [transform renderable batch]
  (.begin ^SpriteBatch batch)
  (render/draw transform renderable batch)
  (.end ^SpriteBatch batch)
  )

(defn build-mode-rotation [game]
  (let [rotation (get-in game [:ui :rotation])]
    (if (nil? rotation) 0 rotation)))

(defn clear-clicks [game]
  (-> (assoc-in game [:inputs :mouse-click-x] nil)
      (assoc-in [:inputs :mouse-click-y] nil)))

(defn tile-aligned-mouse [inputs camera]
  (let [mx (-> inputs :mouse-x)
        my (->> inputs :mouse-y (- (.getHeight Gdx/graphics)))
        world-v3 (-> camera (.unproject (Vector3. mx my 0)))]
    {:x (-> (quot (.-x world-v3) utils/tile-size) (* utils/tile-size))
     :y (-> (quot (.-y world-v3) utils/tile-size) (* utils/tile-size))}))

(defn add-building [game transform renderable]
  (assoc game :entity-map (utils/add-building (:entity-map game)
                                              (utils/my-keyword (ecs/latest-ent-id (:ecs game)))
                                              transform
                                              renderable)))

(defn placeable? [tile-x tile-y entity-map type]
  (let [k (utils/ent-map-key tile-x tile-y)
        v (get-in entity-map [k type])]
    (if v (zero? (count v)) true)))

(defn make-ore-miner [game]
  (let [{tile-align-x :x
         tile-align-y :y} (tile-aligned-mouse (:inputs game) (:camera game))
        tex-cache (:tex-cache game)
        transform (:data (c/transform tile-align-x
                                      tile-align-y
                                      (build-mode-rotation game)
                                      (/ (.getRegionWidth (:mining-building-1 tex-cache)) 2)
                                      (/ (.getRegionWidth (:mining-building-1 tex-cache)) 2)))
        renderable (:data (c/renderable (:mining-building-1 tex-cache)))]
    (if (not (utils/collides-with-a-building? (:entity-map game)
                                              transform
                                              renderable))
      (do (single-draw transform renderable (:batch game))
          (let [click-x (-> game :inputs :mouse-click-x)
                click-y (-> game :inputs :mouse-click-y)]
            (if (and click-x click-y)
              (let [new-ecs (e/ore-miner (:ecs game)
                                         tex-cache
                                         (utils/world->grid tile-align-x)
                                         (utils/world->grid tile-align-y)
                                         (build-mode-rotation game))]
                (-> (assoc game :ecs new-ecs)
                    (add-building transform renderable)
                    (clear-clicks)))
              game)))
      game)))

(defn make-ore-patch [game]
  (let [{tile-align-x :x
         tile-align-y :y} (tile-aligned-mouse (:inputs game) (:camera game))
        tex-cache (:tex-cache game)]
    (if (placeable? tile-align-x tile-align-y (:entity-map game) :ore)
      (let [transform (:data (c/transform tile-align-x
                                          tile-align-y
                                          0 0 0))
            renderable (:data (c/renderable (:ore-patch tex-cache)))]
        (single-draw transform renderable (:batch game))
        (let [click-x (-> game :inputs :mouse-click-x)
              click-y (-> game :inputs :mouse-click-y)]
          (if (and click-x click-y)
            (let [new-ecs (e/ore-patch (:ecs game)
                                       tex-cache
                                       (utils/world->grid tile-align-x)
                                       (utils/world->grid tile-align-y))]
              (-> (assoc game :ecs new-ecs)
                  (assoc-in [:entity-map
                             (utils/ent-map-key (utils/world->grid tile-align-x)
                                                (utils/world->grid tile-align-y))
                             :ore]
                            #{(utils/my-keyword (ecs/latest-ent-id new-ecs))})
                  (clear-clicks)))
            game)))
      game)))

(defn sync-animation [game ent-id]
  (let [ecs (:ecs game)
        belt-id (->> (-> ecs :ent-comps)
                     (filter #(not (nil? (:belt-mover (second %)))))
                     ;TODO animation for new belts is set to move. going to need a different way to filter out new belt.
                     (filter #(not (nil? (-> (second %) :animation :current-animation))))
                     (first)
                     (first))]
    (if belt-id
      (assoc game :ecs (ecs/replace-component ecs
                                              :animation
                                              (ecs/component ecs :animation belt-id)
                                              ent-id))
      game)))

(defn make-belt [game]
  (let [{tile-align-x :x
         tile-align-y :y} (tile-aligned-mouse (:inputs game) (:camera game))
        tex-cache (:tex-cache game)
        texture (:belt-1 tex-cache)
        transform (:data (c/transform tile-align-x
                                      tile-align-y
                                      (build-mode-rotation game)
                                      (/ (.getRegionWidth texture) 2)
                                      (/ (.getRegionWidth texture) 2)))
        renderable (:data (c/renderable texture))]
    (if (not (utils/collides-with-a-building? (:entity-map game)
                                              transform
                                              renderable))
      (do (single-draw transform renderable (:batch game))
          (let [click-x (-> game :inputs :mouse-click-x)
                click-y (-> game :inputs :mouse-click-y)]
            (if (and click-x click-y)
              (let [new-ecs (e/belt (:ecs game)
                                    tex-cache
                                    (utils/world->grid tile-align-x)
                                    (utils/world->grid tile-align-y)
                                    (build-mode-rotation game))]
                (-> (assoc game :ecs new-ecs)
                    (sync-animation (utils/my-keyword (ecs/latest-ent-id new-ecs)))
                    (add-building transform renderable)
                    (clear-clicks)))
              game)))
      game)))

(defn set-build-mode [game bm]
  (assoc-in game [:ui :build-mode] bm))

(defn build-mode [game]
  (get-in game [:ui :build-mode]))

(defn set-untyped [game key]
  {:pre [(if (true? (-> game :inputs :key-typed key)) true (println key))]}
  (assoc-in game [:inputs :key-typed key] false))

(defn rotate-build-mode-rotation [game]
  (-> (update-in game [:ui :rotation] #(case %
                                        0 90
                                        90 180
                                        180 270
                                        270 0
                                        nil 90))
      (set-untyped :r)))

(defn input-logic [game]
  (let [inputs (:inputs game)]
    (cond
      (:escape inputs) (set-build-mode game nil)
      (-> inputs :key-typed :r) (rotate-build-mode-rotation game)
      (and (nil? (build-mode game)) (:mouse-click-x inputs)) (clear-clicks game)
      :else game)))

(defn run [game]
  (let [g (input-logic game)]
    (if (empty? (:inputs g))
      g
      (case (build-mode g)
        :ore-patch (make-ore-patch g)
        :ore-miner (make-ore-miner g)
        :belt (make-belt g)
        nil g
        ))))

(defn btn [text skin build-mode]
  "text = String
  build-mode = a keyword that is used to set the build mode."
  (let [btn (TextButton. text skin)
        listener (proxy [ChangeListener] []
                   (changed [event actor]
                     (alter-var-root (var game/g) #(set-build-mode % build-mode))))]
    (.addListener btn listener)
    btn))

(defn init [game]
  (let [stage (:stage game)
        root-table (doto (Table.)
                     (.setFillParent true))]
    (.setDebugAll stage true)
    (.addActor stage root-table)
    (let [buildings-table (doto (Table.)
                            (.setHeight (float 100))
                            (.setWidth (.getWidth stage)))
          skin (-> game :tex-cache :skin)]
      (.bottom root-table)
      (.add root-table buildings-table)
      (doto buildings-table
        (.add (btn "Ore Patch" skin :ore-patch))
        (.add (btn "Ore Miner" skin :ore-miner))
        (.add (btn "Belt" skin :belt))
        ))))

;(ns proja.ui)
;(require '[proja.screens.main-screen :as ms])
;(require '[proja.screens.game :as g])
(ns proja.screens.main-screen
  (:import [com.badlogic.gdx Screen Gdx InputProcessor Input$Keys InputMultiplexer]
           [com.badlogic.gdx.graphics GL20 OrthographicCamera]
           [com.badlogic.gdx.graphics.g2d SpriteBatch TextureAtlas]
           (com.badlogic.gdx.scenes.scene2d.ui Skin TextButton Table)
           (com.badlogic.gdx.scenes.scene2d Stage)
           (com.badlogic.gdx.utils.viewport ScreenViewport)
           (com.badlogic.gdx.scenes.scene2d.utils ChangeListener)
           (com.badlogic.gdx.math Vector3))
  (:require [proja.tile-map.core :as tmap]
            [proja.components.core :as c]
            [proja.systems.render :as render]
            [proja.systems.production-building :as prod-building]
            [proja.ecs.core :as ecs]
            [proja.entities.core :as e]
            [proja.utils :as utils]))

;http://www.gamefromscratch.com/post/2015/02/03/LibGDX-Video-Tutorial-Scene2D-UI-Widgets-Layout-and-Skins.aspx
;http://www.badlogicgames.com/forum/viewtopic.php?f=11&t=8327

(def game {})

(defn update-game! [func]
  "Expects a function with 1 parameter which will be the game map. The function must return the updated game map."
  (alter-var-root (var game) #(func %)))

(defn clear-screen []
  (doto (Gdx/gl)
    (.glClearColor 1 1 1 1)
    (.glClear GL20/GL_COLOR_BUFFER_BIT)))

(defn input-processor []
  (reify InputProcessor
    (touchDown [this x y pointer button] false)
    (keyDown [this keycode]
      (alter-var-root (var game) #(assoc-in % [:inputs (-> keycode
                                                           (Input$Keys/toString)
                                                           (clojure.string/lower-case)
                                                           (keyword))]
                                            true))
      true)
    (keyUp [this keycode]
      (alter-var-root (var game) #(assoc-in % [:inputs (-> keycode
                                                           (Input$Keys/toString)
                                                           (clojure.string/lower-case)
                                                           (keyword))]
                                            false))
      true)
    (keyTyped [this character] false)
    (touchUp [this x y pointer button]
      (alter-var-root (var game) #(assoc-in % [:inputs :mouse-click-x] x))
      (alter-var-root (var game) #(assoc-in % [:inputs :mouse-click-y] y))
      true)
    (touchDragged [this x y pointer] false)
    (mouseMoved [this x y]
      (alter-var-root (var game) #(assoc-in % [:inputs :mouse-x] x))
      (alter-var-root (var game) #(assoc-in % [:inputs :mouse-y] y))
      true)
    (scrolled [this amount] false)))

(defn texture-atlas []
  (let [atlas (TextureAtlas. "s.pack")
        regions (.getRegions atlas)]
    (loop [idx (dec (.-size regions))
           tex-cache {}]
      (if (neg? idx)
        tex-cache
        (recur (dec idx)
               (let [v (.get regions idx)
                     k (keyword (.-name v))]
                 (assoc tex-cache k v)))))))

(defn ui-skin [tex-cache]
  (assoc tex-cache :skin (Skin. (.internal Gdx/files "uiskin.json"))))

(defn init-game []
  (let [texture-cache (-> (texture-atlas) (ui-skin))]
    (-> (assoc {}
          :camera (OrthographicCamera. (.getWidth Gdx/graphics) (.getHeight Gdx/graphics))
          :batch (SpriteBatch.)
          :stage (Stage. (ScreenViewport.))
          :tex-cache texture-cache
          :inputs {}
          :tile-map (tmap/create-grid 25 19 texture-cache)
          ;used for road pathfinding and road placement. dissoc grass for now because we don't need textures.
          ;passable = true means there is a road there.
          :road-map (mapv (fn [column]
                            (mapv (fn [tile] (assoc tile :passable false))
                                  column))
                          (tmap/create-grid 25 19 (dissoc (:tex-cache game) :grass-1)))
          :ecs (-> (ecs/init)
                   (ecs/add-system (render/create))
                   (ecs/add-system (prod-building/create))
                   )))))

(defn tiles-under [transform-d renderable-d tile-map]
  "Expects the entity to be grid aligned."
  (let [w (-> renderable-d :texture (.getRegionWidth) (/ utils/tile-size))
        h (-> renderable-d :texture (.getRegionHeight) (/ utils/tile-size))
        x (-> transform-d :x (utils/world->grid))
        y (-> transform-d :y (utils/world->grid))]
    (for [row (range x (+ x w))
          col (range y (+ y h))]
      (tmap/get-tile row col tile-map))))

(defn all-passable? [tiles]
  (every? #(:passable %) tiles))

(defn set-passable [tile-map tiles passable?]
  (loop [tilez tiles
         tmap tile-map]
    (if (empty? tilez)
      tmap
      (recur (rest tilez)
             (let [t (first tilez)]
               (assoc-in tmap [(:grid-x t)
                               (:grid-y t)
                               :passable]
                         passable?))))))

(defn roadless? [tiles]
  (not-any? #(:passable %) tiles))

(defn placeable? [tiles-under road-tiles-under]
  (and (all-passable? tiles-under)
       (roadless? road-tiles-under)))

(defn build-warehouse [game]
  (let [pf-tex (-> game :tex-cache :warehouse)
        mx (-> game :inputs :mouse-x)
        my (-> game :inputs :mouse-y)
        world-v3 (-> (:camera game) (.unproject (Vector3. mx my 0)))
        tile-align-x (-> (quot (.-x world-v3) utils/tile-size) (* utils/tile-size))
        tile-align-y (-> (quot (.-y world-v3) utils/tile-size) (* utils/tile-size))
        transform-c (c/transform tile-align-x tile-align-y 0 0 0)
        renderable-c (c/renderable pf-tex)
        tiles-under-wh (tiles-under (:data transform-c) (:data renderable-c) (:tile-map game))
        road-tiles-under (tiles-under (:data transform-c) (:data renderable-c) (:road-map game))
        placement? (placeable? tiles-under-wh road-tiles-under)]
    (if placement?
      (render/run-single transform-c
                         renderable-c
                         (:batch game))
      (render/run-single transform-c
                         (assoc-in renderable-c [:data :texture] (-> game :tex-cache :warehouse-red))
                         (:batch game)))

    (let [click-x (-> game :inputs :mouse-click-x)
          click-y (-> game :inputs :mouse-click-y)]
      (if (and click-x click-y placement?)
        (let [wh-added-g (assoc game :ecs (e/warehouse (:ecs game)
                                                         (:tex-cache game)
                                                         tile-align-x
                                                         tile-align-y))]
          (assoc wh-added-g :tile-map (set-passable (:tile-map wh-added-g) tiles-under-wh false)))
        game))))

(defn build-potato-farm [game]
  (let [pf-tex (-> game :tex-cache :potato-farm)
        mx (-> game :inputs :mouse-x)
        my (-> game :inputs :mouse-y)
        world-v3 (-> (:camera game) (.unproject (Vector3. mx my 0)))
        tile-align-x (-> (quot (.-x world-v3) utils/tile-size) (* utils/tile-size))
        tile-align-y (-> (quot (.-y world-v3) utils/tile-size) (* utils/tile-size))
        transform-c (c/transform tile-align-x tile-align-y 0 0 0)
        renderable-c (c/renderable pf-tex)
        tiles-under-pf (tiles-under (:data transform-c) (:data renderable-c) (:tile-map game))
        road-tiles-under (tiles-under (:data transform-c) (:data renderable-c) (:road-map game))
        placement? (placeable? tiles-under-pf road-tiles-under)]
    (if placement?
      (render/run-single transform-c
                         renderable-c
                         (:batch game))
      (render/run-single transform-c
                         (assoc-in renderable-c [:data :texture] (-> game :tex-cache :potato-farm-red))
                         (:batch game)))

    (let [click-x (-> game :inputs :mouse-click-x)
          click-y (-> game :inputs :mouse-click-y)]
      (if (and click-x click-y placement?)
        (let [pf-added-g (assoc game :ecs (e/potato-farm (:ecs game)
                                                         (:tex-cache game)
                                                         tile-align-x
                                                         tile-align-y))]
            (assoc pf-added-g :tile-map (set-passable (:tile-map pf-added-g) tiles-under-pf false)))
        game))))

(defn build-road [game]
  (let [texture (-> game :tex-cache :road)
        mx (-> game :inputs :mouse-x)
        my (-> game :inputs :mouse-y)
        world-v3 (-> (:camera game) (.unproject (Vector3. mx my 0)))
        tile-align-x (-> (quot (.-x world-v3) utils/tile-size) (* utils/tile-size))
        tile-align-y (-> (quot (.-y world-v3) utils/tile-size) (* utils/tile-size))
        transform-c (c/transform tile-align-x tile-align-y 0 0 0)
        renderable-c (c/renderable texture)
        tiles-under-road (tiles-under (:data transform-c) (:data renderable-c) (:tile-map game))
        road-tiles-under (tiles-under (:data transform-c) (:data renderable-c) (:road-map game))
        placement? (placeable? tiles-under-road road-tiles-under)]
    (if placement?
      (render/run-single transform-c
                         renderable-c
                         (:batch game))
      (render/run-single transform-c
                         (assoc-in renderable-c [:data :texture] (-> game :tex-cache :road-red))
                         (:batch game)))

    (let [click-x (-> game :inputs :mouse-click-x)
          click-y (-> game :inputs :mouse-click-y)]
      (if (and click-x click-y placement?)
        (let [thing-added-g (assoc game :ecs (e/road (:ecs game)
                                                     (:tex-cache game)
                                                     tile-align-x
                                                     tile-align-y))
              road-map-updt-g (assoc-in thing-added-g [:road-map
                                                       (utils/world->grid tile-align-x)
                                                       (utils/world->grid tile-align-y)
                                                       :passable]
                                        true)]
          road-map-updt-g)
        game)))
  )

(defn set-build-mode [game bm]
  (assoc-in game [:ui :build-mode] bm))

(defn build-mode [game]
  (get-in game [:ui :build-mode]))

(defn build-mode-logic [game]
  (let [g (if (-> game :inputs :escape) (set-build-mode game nil) game)]
    (if (empty? (:inputs g))
      g
      (case (build-mode g)
        :potato-farm (build-potato-farm g)
        :road (build-road g)
        :warehouse (build-warehouse g)
        nil g
        ))))

(defn btn [text skin build-mode]
  "text = String
  build-mode = a keyword that is used to set the build mode."
  (let [btn (TextButton. text skin)
        listener (proxy [ChangeListener] []
                   (changed [event actor]
                     (alter-var-root (var game) #(set-build-mode % build-mode))))]
    (.addListener btn listener)
    btn))

(defn ui []
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
        (.add (btn "Potato Farm" skin :potato-farm))
        (.add (btn "Road" skin :road))
        (.add (btn "Warehouse" skin :warehouse))))))

;generic shit for game loop
(defn pause []
  (update-game! #(assoc % :paused true)))

(defn resume []
  (update-game! #(assoc % :paused false)))

(def last-fps 0)
(def fps 0)
(def second-counter 0.0)

(defn fps-logic []
  (def second-counter (+ second-counter (:delta game)))
  (def fps (inc fps))
  (when (>= second-counter 1.0)
    (do
      (def second-counter 0.0)
      (def last-fps fps)
      (def fps 0)
      (when (< last-fps 60)
        (println "frame rate is dropping below 60 : " last-fps " @ " (new java.util.Date))))))

(defn move-camera [{inputs :inputs, cam :camera}]
  (do
    (when (:right inputs)
      (.translate cam 1 0))
    (when (:left inputs)
      (.translate cam -1 0))
    (when (:up inputs)
      (.translate cam 0 1))
    (when (:down inputs)
      (.translate cam 0 -1))
    ))

(defn update-cam! [game]
  (move-camera game)
  (.update (:camera game))
  game)

(defn update-stage! [game]
  (.act (:stage game) (:delta game))
  (.draw (:stage game))
  game)

(defn game-loop [game]
  (fps-logic)
  (if (:paused game)
    game
    (do (clear-screen)

        (.setProjectionMatrix (:batch game) (.combined (:camera game)))
        (tmap/draw-grid (:tile-map game) (:batch game))

        (-> game
            (ecs/run)
            (build-mode-logic)
            (assoc-in [:inputs :mouse-click-x] nil)
            (assoc-in [:inputs :mouse-click-y] nil)
            (update-cam!)
            (update-stage!)))))

(defn screen []
  (reify Screen
    (show [this]
      ;(.setInputProcessor Gdx/input (input-processor))
      (def game (init-game))
      (ui)
      (.setInputProcessor Gdx/input (InputMultiplexer. (into-array InputProcessor (seq [(:stage game) (input-processor)]))))
      )

    (render [this delta]
      (if (empty? game) ;if this file is reloaded in the repl, setScreen does not get called and bad things happen. So this avoids doing anything.
        ""
        (do
          (update-game! #(assoc % :delta delta))
          (update-game! #(game-loop %)))))

    (dispose [this])
    (hide [this])
    (pause [this])
    (resize [this w h]
      (-> (:stage game) (.getViewport) (.update w h))       ;tell the ui viewport to change size.
      (-> (:stage game) (.getViewport) (.apply true))       ;re-centers the ui camera.
      (.setToOrtho (:camera game) false (.getWidth Gdx/graphics) (.getHeight Gdx/graphics)))
    (resume [this])))
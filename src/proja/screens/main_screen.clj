(ns proja.screens.main-screen
  (:import [com.badlogic.gdx Screen Gdx InputProcessor Input$Keys InputMultiplexer]
           [com.badlogic.gdx.graphics GL20 OrthographicCamera]
           [com.badlogic.gdx.graphics.g2d SpriteBatch TextureAtlas]
           (com.badlogic.gdx.scenes.scene2d.ui Skin)
           (com.badlogic.gdx.scenes.scene2d Stage)
           (com.badlogic.gdx.utils.viewport ScreenViewport)
           (com.badlogic.gdx.physics.box2d World Box2DDebugRenderer)
           (com.badlogic.gdx.math Vector2 Matrix4))
  (:require [proja.screens.game :as game]
            [proja.tile-map.core :as tmap]
            [proja.systems.render :as render]
            [proja.systems.animate :as animate]
            [proja.systems.mine-ore :as mine-ore]
            [proja.systems.swing-entity :as swing-entity]
            [proja.systems.belt-move :as belt-move]
            [proja.systems.produce-good :as produce-good]
            [proja.ecs.core :as ecs]
            [proja.ui :as ui]
            [proja.utils :as utils]))

;http://www.gamefromscratch.com/post/2015/02/03/LibGDX-Video-Tutorial-Scene2D-UI-Widgets-Layout-and-Skins.aspx
;http://www.badlogicgames.com/forum/viewtopic.php?f=11&t=8327

;(def game {})

(defn update-game! [func]
  "Expects a function with 1 parameter which will be the game map. The function must return the updated game map."
  (alter-var-root (var game/g) #(func %))
  nil)

(defn clear-screen []
  (doto (Gdx/gl)
    (.glClearColor 1 1 1 1)
    (.glClear GL20/GL_COLOR_BUFFER_BIT)))

(defn input-processor []
  (reify InputProcessor
    (touchDown [this x y pointer button] false)
    (keyDown [this keycode]
      (alter-var-root (var game/g) #(assoc-in % [:inputs (-> keycode
                                                           (Input$Keys/toString)
                                                           (clojure.string/lower-case)
                                                           (keyword))]
                                            true))
      true)
    (keyUp [this keycode]
      (alter-var-root (var game/g) #(assoc-in % [:inputs (-> keycode
                                                           (Input$Keys/toString)
                                                           (clojure.string/lower-case)
                                                           (keyword))]
                                            false))
      true)
    (keyTyped [this character]
      (alter-var-root (var game/g) #(assoc-in % [:inputs :key-typed (-> character
                                                                        (clojure.string/lower-case)
                                                                        (keyword))]
                                              true))
      true)
    (touchUp [this x y pointer button]
      (alter-var-root (var game/g) #(assoc-in % [:inputs :mouse-click-x] x))
      (alter-var-root (var game/g) #(assoc-in % [:inputs :mouse-click-y] (- (.getHeight Gdx/graphics) y)))
      true)
    (touchDragged [this x y pointer] false)
    (mouseMoved [this x y]
      (alter-var-root (var game/g) #(assoc-in % [:inputs :mouse-x] x))
      (alter-var-root (var game/g) #(assoc-in % [:inputs :mouse-y] (- (.getHeight Gdx/graphics) y)))
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

(defn init-game [_]
  (let [texture-cache (-> (texture-atlas) (ui-skin))]
    (-> (assoc {}
          :box2d-world (World. (Vector2. 0 0) true)
          :box2d-debug-renderer (Box2DDebugRenderer.)
          :box2d-debug-matrix (Matrix4.)
          :box2d-accumulator 0.0
          :camera (OrthographicCamera. (.getWidth Gdx/graphics) (.getHeight Gdx/graphics))
          :batch (SpriteBatch.)
          :stage (Stage. (ScreenViewport.))
          :tex-cache texture-cache
          :inputs {}
          :tile-map (tmap/create-grid 25 19 texture-cache)
          ;Keys are strings of x grid + y grid, so (str (+ 1 1)).
          ;Values are Maps, keys are ent 'types' and values are Sets of ent id's
          :entity-map {}
          ;used for road pathfinding and road placement. dissoc grass for now because we don't need textures.
          ;passable = true means there is a road there.
          ;:road-map (mapv (fn [column]
          ;                  (mapv (fn [tile] (assoc tile :passable false))
          ;                        column))
          ;                (tmap/create-grid 25 19 (dissoc (:tex-cache game) :grass-1)))
          :ecs (-> (ecs/init)
                   (ecs/add-system (animate/create))
                   (ecs/add-system (mine-ore/create))
                   (ecs/add-system (swing-entity/create))
                   (ecs/add-system (belt-move/create))
                   (ecs/add-system (produce-good/create))
                   )))))

(defn reset-ecs-em []
  (do (update-game! #(assoc % :ecs (-> (ecs/init)
                                       (ecs/add-system (animate/create))
                                       (ecs/add-system (mine-ore/create))
                                       (ecs/add-system (swing-entity/create))
                                       (ecs/add-system (belt-move/create))
                                       (ecs/add-system (produce-good/create))
                                       )))
      (update-game! #(assoc % :entity-map {}))))

(defn pause []
  (update-game! #(assoc % :paused true)))

(defn resume []
  (update-game! #(assoc % :paused false)))

(def last-fps 0)
(def fps 0)
(def second-counter 0.0)

(defn fps-logic []
  (def second-counter (+ second-counter (:delta game/g)))
  (def fps (inc fps))
  (when (>= second-counter 1.0)
    (do
      (def second-counter 0.0)
      (def last-fps fps)
      (def fps 0)
      (when (< last-fps 30)
        (println "frame rate is dropping below 30 : " last-fps " @ " (new java.util.Date))))))

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

(defn update-physics [game]
  ;0.022 aprox = 1/45
  ;max frame time to avoid spiral of death (on slow devices)
  (let [frame-time (min (:delta game) 0.25)]
    (loop [acc (+ (:box2d-accumulator game) frame-time)]
      (if (< acc 0.022)
        (assoc game :box2d-accumulator acc)
        (recur (do (.step ^World (:box2d-world game) 0.022 6 2)
                   (- acc 0.022)))))))

(defn game-loop [game]
  (fps-logic)
  (do (clear-screen)
      (.setProjectionMatrix ^SpriteBatch (:batch game) (.combined ^OrthographicCamera (:camera game)))
      (tmap/draw-grid (:tile-map game) (:batch game))
      (render/draw-all (:ecs game) (:batch game))
      (doto (:box2d-debug-matrix game)
        (.set (.combined ^OrthographicCamera (:camera game)))
        (.scale (float 32) (float 32) (float 1)))
      (.render ^Box2DDebugRenderer (:box2d-debug-renderer game)
               ^World (:box2d-world game)
               (:box2d-debug-matrix game))

      (if (:paused game)
        (-> game
            ;(ecs/run)
            (ui/run)
            (update-cam!)
            (update-stage!))
        (-> game
            (ui/run)
            (ecs/run)
            (update-cam!)
            (update-stage!)
            (update-physics)
            ))))

(defn screen []
  (reify Screen
    (show [this]
      ;(.setInputProcessor Gdx/input (input-processor))
      (update-game! #(init-game %))
      (ui/init game/g)
      (.setInputProcessor Gdx/input (InputMultiplexer. (into-array InputProcessor (seq [(:stage game/g) (input-processor)]))))
      )

    (render [this delta]
      (if (empty? game/g) ;if this file is reloaded in the repl, setScreen does not get called and bad things happen. So this avoids doing anything.
        ""
        (do
          (update-game! #(assoc % :delta delta))
          (update-game! #(game-loop %)))))

    (dispose [this])
    (hide [this])
    (pause [this])
    (resize [this w h]
      (-> (:stage game/g) (.getViewport) (.update w h))       ;tell the ui viewport to change size.
      (-> (:stage game/g) (.getViewport) (.apply true))       ;re-centers the ui camera.
      (.setToOrtho (:camera game/g) false (.getWidth Gdx/graphics) (.getHeight Gdx/graphics)))
    (resume [this])))
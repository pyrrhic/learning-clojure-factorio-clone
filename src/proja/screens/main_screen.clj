(ns proja.screens.main-screen
  (:import [com.badlogic.gdx Screen Gdx InputProcessor Input$Keys InputMultiplexer]
           [com.badlogic.gdx.graphics GL20 OrthographicCamera]
           [com.badlogic.gdx.graphics.g2d SpriteBatch TextureAtlas BitmapFont]
           (com.badlogic.gdx.scenes.scene2d.ui Skin TextButton Dialog Table)
           (com.badlogic.gdx.scenes.scene2d Stage)
           (com.badlogic.gdx.utils.viewport ScreenViewport)
           (com.badlogic.gdx.scenes.scene2d.utils ClickListener ChangeListener)
           (com.badlogic.gdx.utils Timer Timer$Task)
           (com.badlogic.gdx.math Vector3))
  (:require [proja.tile-map.core :as tmap]
            [proja.components.core :as c]
            [proja.systems.render :as render]
            [proja.ecs.core :as ecs]
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
      (alter-var-root (var game) #(assoc-in % [:inputs (keyword (Input$Keys/toString keycode))] true))
      true)
    (keyUp [this keycode]
      (alter-var-root (var game) #(assoc-in % [:inputs (keyword (Input$Keys/toString keycode))] false))
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
          :ecs (-> (ecs/init)
                   (ecs/add-system (render/create))
                   )))))

(defn move-camera [{inputs :inputs, cam :camera}]
  (do
    (when (:Right inputs)
      (.translate cam 1 0))
    (when (:Left inputs)
      (.translate cam -1 0))
    (when (:Up inputs)
      (.translate cam 0 1))
    (when (:Down inputs)
      (.translate cam 0 -1))
    ))

;UI code
(defn tiles-under-entity [ent tile-map]
  "Expects the entity to be grid aligned."
  (let [w (-> ent :renderable :texture (.getRegionWidth) (/ utils/tile-size))
        h (-> ent :renderable :texture (.getRegionHeight) (/ utils/tile-size))
        x (-> ent :transform :x (utils/world->grid))
        y (-> ent :transform :y (utils/world->grid))]
    (for [row (range x (+ x w))
          col (range y (+ y h))]
      (tmap/get-tile row col tile-map))))

(defn all-passable? [tiles]
  (every? #(:passable %) tiles))

(defn placement-valid? [ent tile-map]
  (-> ent
      (tiles-under-entity tile-map)
      (all-passable?)))

(defn potato-farm [tile-x tile-y texture]
  (-> (c/renderable {} texture)
      (c/transform (utils/grid->world tile-x)
                   (utils/grid->world tile-y)
                   0
                   (/ (.getRegionWidth texture) 2)
                   (/ (.getRegionHeight texture) 2))))

(defn build-potato-farm [game]
  (let [pf-tex (-> game :tex-cache :potato-farm-green)
        mx (-> game :inputs :mouse-x)
        my (-> game :inputs :mouse-y)
        world-v3 (-> (:camera game) (.unproject (Vector3. mx my 0)))
        tile-align-x (-> (quot (.-x world-v3) utils/tile-size) (* utils/tile-size))
        tile-align-y (-> (quot (.-y world-v3) utils/tile-size) (* utils/tile-size))
        pf-ent (-> {}
                   (c/transform tile-align-x tile-align-y 0 0 0)
                   (c/renderable pf-tex))
        placement? (placement-valid? pf-ent (:tile-map game))]
    (if placement?
      (render/run pf-ent (:batch game))
      (-> pf-ent
          (assoc-in [:renderable :texture] (-> game :tex-cache :potato-farm-red))
          ;(render/run (:batch game))
          ))
    (let [click-x (-> game :inputs :mouse-click-x)
          click-y (-> game :inputs :mouse-click-y)]
      (if (and click-x click-y placement?)
        true
        false)))
  game
  )

(defn set-build-mode [game bm]
  (assoc-in game [:ui :build-mode] bm))

(defn build-mode [game]
  (get-in game [:ui :build-mode]))

(defn build-mode-logic [game]
  (if (empty? (:inputs game))
    game
    (case (build-mode game)
      :potato-farm (build-potato-farm game)
      nil game
      )))

(defn ui []
  (let [stage (:stage game)
        root-table (doto (Table.)
                     (.setFillParent true))]
    (.setDebugAll stage true)
    (.addActor stage root-table)
    (let [farms-table (doto (Table.)
                        (.setHeight (float 100))
                        (.setWidth (.getWidth stage)))
          skin (-> game :tex-cache :skin)]
      (.bottom root-table)
      (.add root-table farms-table)
      (let [potato-btn (TextButton. "Potato Farm" skin)
            potato-l (proxy [ChangeListener] []
                       (changed [event actor]
                         (alter-var-root (var game) #(set-build-mode % :potato-farm))))]
        (.addListener potato-btn potato-l)
        (.add farms-table potato-btn)))))

;generic shit for game loop
(defn pause []
  (update-game! #(assoc % :paused true)))

(defn resume []
  (update-game! #(assoc % :paused false)))

(def last-fps 0)
(def fps 0)
(def second-counter 0.0)

(defn game-loop [game]
  (def second-counter (+ second-counter (:delta game)))
  (def fps (inc fps))
  (when (>= second-counter 1.0)
    (do
      (def second-counter 0.0)
      (def last-fps fps)
      (def fps 0)
      (when (< last-fps 60)
        (println "frame rate is dropping below 60 : " last-fps " @ " (new java.util.Date)))))

  (if (:paused game)
    game
    ;(assoc-in game [:ecs :entities] (sys/render game))
    (do (clear-screen)
        (.setProjectionMatrix (:batch game) (.combined (:camera game)))
        (tmap/draw-grid (:tile-map game) (:batch game))
        (move-camera game)
        (.update (:camera game))
        ;(.act (:stage game) (.getDeltaTime Gdx/graphics))
        ;(.draw (:stage game))
        ;(-> game
        ;    (build-mode-logic)
        ;    (assoc-in [:inputs :mouse-click-x] nil)
        ;    (assoc-in [:inputs :mouse-click-y] nil))
        (-> game
            (ecs/run)
            )
        ))
  )

(defn screen []
  (reify Screen
    (show [this]
      ;(.setInputProcessor Gdx/input (input-processor))
      (def game (init-game))
      ;(ui)
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
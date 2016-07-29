(ns proja.screens.main-screen
  (:import [com.badlogic.gdx Screen Gdx InputProcessor Input$Keys InputMultiplexer]
           [com.badlogic.gdx.graphics GL20 OrthographicCamera]
           [com.badlogic.gdx.graphics.g2d SpriteBatch TextureAtlas BitmapFont]
           (com.badlogic.gdx.scenes.scene2d.ui Skin TextButton Dialog Table)
           (com.badlogic.gdx.scenes.scene2d Stage)
           (com.badlogic.gdx.utils.viewport ScreenViewport)
           (com.badlogic.gdx.scenes.scene2d.utils ClickListener)
           (com.badlogic.gdx.utils Timer Timer$Task))
  (:require [proja.tile-map.core :as tmap]))

;http://www.gamefromscratch.com/post/2015/02/03/LibGDX-Video-Tutorial-Scene2D-UI-Widgets-Layout-and-Skins.aspx

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
      (alter-var-root (var game) #(assoc-in % [:inputs :mouse-x] x))
      (alter-var-root (var game) #(assoc-in % [:inputs :mouse-y] (- 600 y)))
      false)
    (touchDragged [this x y pointer] false)
    (mouseMoved [this x y] false)
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
          :tile-map (tmap/create-grid 25 19 texture-cache)))))

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
        (tmap/draw-grid (:tile-map game) (:batch game))
        (move-camera game)
        (.setProjectionMatrix (:batch game) (.combined (:camera game)))
        (.update (:camera game))
        (.act (:stage game) (.getDeltaTime Gdx/graphics))
        (.draw (:stage game))))
  game
  )

(defn ui []
  (let [stage (:stage game)
        table (doto (Table.)
                (.setWidth (.getWidth stage))
                (.align 1)                            ;use Align class. 1 is center.
                (.debug)
                (.setPosition 0 0))]
    (.addActor stage table))
  (let [table (-> (:stage game) (.getActors) (.get 0))
        skin (-> game :tex-cache :skin)
        potato-btn (TextButton. "Potato Farm" skin)
        potato-l (proxy [ClickListener] []
                   (clicked [event x y]
                     (println "potato farm!!")
                     ;(.stop event)
                     ))]
    (.bottom table)
    (.addListener potato-btn potato-l)
    (.add table potato-btn)
    (.setDisabled potato-btn true)
    )
  (let [table (-> (:stage game) (.getActors) (.get 0))
        btn (-> table (.getCells) (.get 0) (.getActor))]

    ;(.isDisabled btn)
    )
  )

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
      (.setToOrtho (:camera game) false (.getWidth Gdx/graphics) (.getHeight Gdx/graphics)))
    (resume [this])))


;(let [stage (Stage. (ScreenViewport.))
;      skin (Skin. (.internal Gdx/files "uiskin.json"))
;      dialog (Dialog. "Click Message" skin)
;      button (doto (TextButton. "Click me" skin "default")
;               (.setWidth 200)
;               (.setHeight 50)
;               (.addListener (proxy [ClickListener] []
;                               (clicked [e x y]
;                                 (.show dialog stage)
;                                 (Timer/schedule (proxy [Timer$Task] []
;                                                   (run [] (.hide dialog)))
;                                                 2)))))]
;  (.addActor stage button)
;  (.setInputProcessor Gdx/input stage)
;  stage)
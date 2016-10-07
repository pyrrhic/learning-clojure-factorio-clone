(ns proja.desktop-launcher
  (:import [com.badlogic.gdx Gdx Application]
           [com.badlogic.gdx.backends.lwjgl LwjglApplication LwjglApplicationConfiguration])
  (:require [proja.screens.main-screen :as main-screen]))

(def game (proja.MyGame.))

(defn set-main-screen []
  (.setScreen game (main-screen/screen)))

(defn run-on-main-thread [f]
  (.postRunnable Gdx/app f))

(defn reset []
  (run-on-main-thread set-main-screen))

;(require 'proja.main-screen :reload-all)

(defn app []
  (let [config (LwjglApplicationConfiguration.)]
    (set! (.height config) 768)
    (set! (.width config) 1366)
    (set! (.title config) "hey you")
    (set! (.-foregroundFPS config) 30)
    (set! (.-backgroundFPS config) 30)
    (LwjglApplication. game config)))

(app)

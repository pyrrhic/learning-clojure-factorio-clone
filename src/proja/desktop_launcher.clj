(ns proja.desktop-launcher
  (:import [com.badlogic.gdx Gdx Application]
           [com.badlogic.gdx.backends.lwjgl LwjglApplication])
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
  (LwjglApplication. game "hello" 800 600))

(app)
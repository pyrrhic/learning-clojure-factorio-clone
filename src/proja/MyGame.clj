(ns proja.MyGame
  (:require [proja.screens.main-screen])
  (:import (com.badlogic.gdx Game)))

(gen-class
  :name proja.MyGame
  :extends com.badlogic.gdx.Game)

(defn -create [^Game this]
  (.setScreen this (proja.screens.main-screen/screen)))

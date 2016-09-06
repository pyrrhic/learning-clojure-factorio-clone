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
    (set! (.height config) 600)
    (set! (.width config) 800)
    (set! (.title config) "hey you")
    (set! (.-foregroundFPS config) 30)
    (set! (.-backgroundFPS config) 30)
    (LwjglApplication. game config)))

(app)

;nrg system
;every entity stores energy/action points, 0 is none.
;every 30 frames = a tick, give X energy to every entity.
;don't give them more than max. if they're at max, no change.
;
;time management system
;set flag on game?
; every second is a new turn, unless animations are playing?
; aka every 30 frames is a new turn, unless animations are playing.
;
; things that care about this
; energy every turn
; run systems once every turn
; run animation system once every turn
;
; a new turn will not start if animation system is running.
; but this should not happen, need to avoid as much as possible so we don't get slow downs.
; keep track of this timing and print to log or whatever when it occurs and how much it ran over.
;
; need an indicator that this is beginning of a turn
;
;animation manager system
;queue of animations
;2 queues, synch and asynch
;synch only runs one animation at a time, the first one. when it's done, pop it off.
;asynch runs all of the animations at the same time.
;both queues will be run each frame.
;hmm, but if i have a sync queue, those animations need to be really fast. do i even need sync?
;need ability to add a sequence of asynch animations. like, a arm pickup then arm swing animation that need to run
;in that order, but it has no relationship to any other animations / entities.
;
;async queue data:
; ent-id, animation key(s), started? - starts as false
;
; if not started, pull out animation and set the key and set started to true
; if animation nil (finished), remove animation key from queue data.
;    if there is another animation, start it.
;    when it finishes, remove key from queue data, check for another animation, etc.
;
;ore miner
;starts with full energy.
;if have enough energy
; subtract from energy
; do the existing ore extraction / delete if necessary logic.
; add animation to async queue.
;
;arm
;if have energy
; subtract energy
; either pickup and swing, or dropping and swing, or waiting to drop, or waiting to pickup.
; if doing 2 actions in one turn, like drop and turn, i want ability to animate both. the animations only need
; to be synchronized in relation to each other. asynch in relaiton to everything else.
; add animation to async queue
;
;belt
; don't really need energy system..
; just run once a turn, each action is 1 turn's worth
; this is one that will be passed all qualified entities.
; group-by :group-id
; for each group:
;  if group has an end, if end has something to move, stall. don't move, don't run the group.
;  else call move for each belt in the group.
;



;
;change to ecs to allow systems that are given all qualified entities, instead of one at a time
;set a flag or something to dictate which one the run function is expecting.

(ns proja.systems.animate
  (:require [proja.ecs.core :as ecs]))

(defn run [ent-id game]
  (let [ecs (:ecs game)
        animation (ecs/component ecs :animation ent-id)
        renderable (ecs/component ecs :renderable ent-id)
        curr-frame-durations (get-in animation [:frames (:current-animation animation) :frame-durations])
        updated-comps (cond
                        ;if animation has not been set.
                        (nil? (:current-animation animation))
                        {:animation animation
                         :renderable renderable}

                        ;if animation has been set, but not started.
                        (neg? (:current-frame animation))
                        {:animation  (-> (assoc animation :current-frame 0)
                                         (assoc :current-duration (-> curr-frame-durations (first) :duration)))
                         :renderable (assoc renderable :texture (-> curr-frame-durations (first) :texture))}

                        ;if frame for current animation is currently running
                        (>= (:current-duration animation) 0)
                        {:animation (update animation :current-duration #(- % (:delta game)))
                         :renderable renderable}

                        ;if current frame finished and there are more frames to play
                        (and (neg? (:current-duration animation))
                             (< (:current-frame animation) (dec (count curr-frame-durations))))
                        (let [incd-frame-num (inc (:current-frame animation))]
                          {:animation (-> (assoc animation :current-frame incd-frame-num)
                                          (assoc :current-duration (-> curr-frame-durations (nth incd-frame-num) :duration)))
                           :renderable (assoc renderable :texture (-> curr-frame-durations (nth incd-frame-num) :texture))})

                        ;if all of the frames have been run, then check if we're supposed to loop
                        (= (:current-frame animation) (dec (count curr-frame-durations)))
                        (if (get-in animation [:frames (:current-animation animation) :loop?])
                          {:animation (-> (assoc animation :current-frame 0)
                                          (assoc :current-duration (-> curr-frame-durations (first) :duration)))
                           :renderable (assoc renderable :texture (-> curr-frame-durations (first) :texture))}
                          {:animation (-> (assoc animation :current-animation nil)
                                          (assoc :current-frame -1))
                           :renderable renderable}))]
    (assoc game :ecs (ecs/update-components ecs ent-id updated-comps))))

(defn create []
  {:function   run
   :predicates {:and #{:renderable :animation}}})

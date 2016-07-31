(ns proja.components.core)

(defn transform [ent x y rot orig-x orig-y]
  (assoc ent
    :transform {:x        x
                :y        y
                :rotation rot
                :origin-x orig-x
                :origin-y orig-y}))

(defn renderable
  ([ent tex]
   (renderable ent tex 1 1))
  ([ent tex scale-x scale-y]
   (assoc ent
     :renderable {:texture tex
                  :scale-x scale-x
                  :scale-y scale-y})))
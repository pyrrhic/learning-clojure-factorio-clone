(defproject proja "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.badlogicgames.gdx/gdx "1.8.0"]
                 [com.badlogicgames.gdx/gdx-backend-lwjgl "1.8.0"]
                 [com.badlogicgames.gdx/gdx-box2d "1.8.0"]
                 [com.badlogicgames.gdx/gdx-box2d-platform "1.8.0"
                  :classifier "natives-desktop"]
                 [com.badlogicgames.gdx/gdx-bullet "1.8.0"]
                 [com.badlogicgames.gdx/gdx-bullet-platform "1.8.0"
                  :classifier "natives-desktop"]
                 [com.badlogicgames.gdx/gdx-platform "1.8.0"
                  :classifier "natives-desktop"]
                 [com.rpl/specter "0.12.0"]
                 ]
  ;:source-paths ["src"]
  :aot [proja.MyGame]
  :main proja.desktop-launcher
  ;:main ^:skip-aot trying-things.core
  ;:target-path "target/%s"
  ;:profiles {:uberjar {:aot :all}})
  :jvm-opts ["-Dcom.sun.management.jmxremote"
             "-Dcom.sun.management.jmxremote.ssl=false"
             "-Dcom.sun.management.jmxremote.authenticate=false"
             "-Dcom.sun.management.jmxremote.port=43210"]   ;port number is arbitrary, pick whatever.
  )
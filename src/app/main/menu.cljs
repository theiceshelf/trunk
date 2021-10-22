(ns app.main.menu
  (:require
   ["electron" :refer [app Menu]]))

(def is-mac (= (.-platform js/process) "darwin"))

(def template
  [
   (when is-mac
     {:label   (.-name app)
      :submenu [{:role "hide"}
                {:role "hideOthers"}
                {:role "unhide"}
                {:type "separator"}
                {:role "quit"}]})
   {:label   "View"
    :submenu [{ :role "reload" },
              { :type "separator" },
              { :role "togglefullscreen" }]}
   {:label   "Window"
    :submenu [{:role "minimize"}
              (when is-mac
                {:type "separator"}
                {:role "front"})]}])

(defn init
  []
  (let [menu (.buildFromTemplate Menu (clj->js template))]
    (.setApplicationMenu Menu menu)))
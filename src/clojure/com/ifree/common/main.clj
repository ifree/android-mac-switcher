(ns com.ifree.common.main
  (:use [neko.activity :only [defactivity set-content-view! *activity*]]
        [neko.threading :only [on-ui]]
        [neko.data :only [get-shared-preferences]]
        [neko.find-view]
        [neko.listeners.view :only [on-click]]
        [neko.log :as log]
        [neko.context :only [context get-service]]
        [clojure.java.shell :only [sh]]
        [neko.ui :only [make-ui]])
  (:import (android.widget TextView ArrayAdapter EditText ListView Button)
           (android.app Activity AlertDialog AlertDialog$Builder)
           (android.content
            SharedPreferences
            Context
            DialogInterface
            DialogInterface$OnClickListener)
           (android.view View View$OnClickListener)
           (com.ifree.common R$layout R$id)
           (java.io OutputStreamWriter)
           (java.util HashSet)
           )
  )
(comment
 +-------------------------------------------------------|
 |   xxxx:xxxx:xxx:xxx                                   |
 |  ---------------------------------------------------  |
 |   xxx:xxx:xxx:xxx                                     |
 |  ---------------------------------------------------  |
 |   xxx:xxx:xxx:xxx                                     |
 |  ---------------------------------------------------  |
 |                                                       |
 |  o---------+                       --o-------|        |
 |  | add mac |                       | apply   |        |
 |  ----------+                       |----------        |
 |                                                       |
 |--------------------------------------------------------
 )

(def storage-key "android-mac-switch::macs")
(def storage "android-mac-switch::settings")


(defn fill-list
  "fill list view with stored mac address"
  [^ArrayAdapter list-provider macs]
  (.clear list-provider)
  (.addAll list-provider macs)
  (log/i "list filled" list-provider)
  )

(defn ^HashSet fetch-macs
  "get mac address in shared prefs"
  []
  (let
      [^SharedPreferences prefs (get-shared-preferences storage :private)
       macs (.getStringSet prefs storage-key (HashSet.))
       ]
    
    (log/i "macs" {:mac macs})
    macs
    )
  )

(defn store-mac
  "store mac address in shared prefs"
  [mac]
  (let
      [
       ^SharedPreferences prefs (get-shared-preferences storage :private)
       ^HashSet macs (fetch-macs)
       ]
    (.add macs mac)
    (-> (.edit prefs)
       (.putStringSet storage-key macs) 
        .commit
        )
    (log/i "mac stored" {:mac mac})
      )
  )

(defn prompt-add
  "prompt user for add mac address"
  []
  (let [
        ^EditText txt-input (EditText. *activity*)
        ^AlertDialog$Builder dlg (AlertDialog$Builder. *activity*)
        ]    
    (doto dlg
      (.setTitle "Add Mac Address")
      (.setMessage "please input mac address")
      (.setView txt-input)
      (.setPositiveButton "OK"
                          (proxy [DialogInterface$OnClickListener] []
                            (onClick [dlg btn]
                             (store-mac (.getText txt-input))
                             (fill-list (.getAdapter (find-view *activity* R$id/lstAddrs)) (fetch-macs))
                             )
                           )
                          )
      (.setNegativeButton "Cancel"
                          (proxy [DialogInterface$OnClickListener] []
                            (onClick [^DialogInterface dlg btn]
                              (.cancel dlg)
                              )
                            )
                          )
      (.show)
      )
    )
  )


(defn current-mac
  "get current mac adress"
  []
  (last (re-find #"(?:HWaddr)\s([^\s]+)" (:out (sh "busybox" "ifconfig" "wlan0"))))
  )

(defn change-mac
  "change mac address.  It's may little tricky, when I set mac address to wlan0,
It will take no effect. So I shut download wlan0, when Android(networkmanager)
detects that wlan0 down, It will restart it with preset mac address. thus mac
address successfully changed. "
  [mac]
  (sh "su" "-c"  (str "busybox ifconfig wlan0 hw ether " mac " \n"))
  (sh "su" "-c" "busybox ifconfig wlan0 down")
  )

(defn init-mac []
  (let
      [^TextView txt-mac (find-view *activity* R$id/txtMac)]
      (.setText txt-mac (current-mac))
    )
  )

(defn setup []
  (let [
        ^Button btn-add (find-view *activity* R$id/btnAdd)
        ^Button btn-apply (find-view *activity* R$id/btnApply)
        ^Button btn-reset (find-view *activity* R$id/btnReset)
        ^ListView lst-macs (find-view *activity* R$id/lstAddrs)
        ^ArrayAdapter mac-provider (ArrayAdapter. *activity* android.R$layout/simple_list_item_single_choice)
        ]
    (.setAdapter lst-macs mac-provider)
    (.setOnClickListener btn-add
                         (on-click
                          (prompt-add)
                          )
                         )
    (.setOnClickListener btn-apply
                         (on-click
                          (when (> (.getCount lst-macs) 0)
                            (change-mac (.getItemAtPosition lst-macs 0))
                            (init-mac)
                            )
                          )
                         )
    (.setOnClickListener btn-reset
                         (on-click
                          (let
                              [wm (get-service :wifi)]
                            (future
                              (.setWifiEnabled wm false)
                              (Thread/sleep 2000)
                              (.setWifiEnabled wm true)
                              )
                            )
                          )
                         )
    (.setChoiceMode lst-macs 1)
    (fill-list mac-provider (fetch-macs))
    (init-mac)
    )
  )

(defactivity com.ifree.common.MainActivity
  :def view
  :on-create
  (fn [this bundle]
    (on-ui
     (set-content-view! view R$layout/main))
    (alter-var-root #'neko.activity/*activity* (constantly view) 
     )
    (setup)
    ))

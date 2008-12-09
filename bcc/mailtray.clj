;; A Qt4 system tray app to check mail servers for new mail,
;; displaying an icon in the system tray.
;;
;; When there are new messages, a number is drawn over the icon
;; and the icon's context menu is updated to display info about
;; the new messages.
;;
;; This app can check multiple servers and multiple folders on the
;; same server.
;;
;; Requires Qt Jambi, Javamail, and clojure-contrib.
;;
;; NOTE: SUPPLY YOUR OWN MAIL ICON.  Transparent icons work.

(ns bcc.mailtray
  (:import (com.trolltech.qt.gui QAction QFont QFont$Weight)
           (com.trolltech.qt.core QCoreApplication))
  (:use clojure.contrib.str-utils
        (bcc qt4 mail util)))

(def *messages* (ref {}))
(def *mailspecs* (ref #{}))
(def *message-fetch-interval-ms* (* 60 1000))
(def *message-updater* (agent nil))
(def *icon-filename* "mail.png") ;; ***** CUSTOMIZE ME *****

;; Mail-fetching fns
(defn check-mail
  "Fetches a fresh list of UNSEEN messages from a server and updates the messages ref for this server.  Updates the systray to reflect new info."
  [mailspec callback]
  (when @*running*
    (dosync
     (try
      (commute *messages* conj [(mailspec-name mailspec) (get-new-messages mailspec)])
      (catch Exception e (println e))))
    (callback)
    (Thread/sleep *message-fetch-interval-ms*)
    (send-off *agent* check-mail callback))
  mailspec)

(defn count-messages
  "Sum and return the number of messages in the messages ref for all servers."
  [messages]
  (reduce + (map count (vals messages))))

;; Menu-making fns
(defn make-title-action
  "Make a menu title."
  [menu text]
   (doto (new QAction text menu)
     (.setFont (new QFont "Deja Vu Sans" 10 (.. QFont$Weight Bold value)))))

(defn make-message-action
  "Make a menu item for a single email message."
  [menu message]
  (new QAction (str (:from message) " - " (:subject message)) menu))

(defn make-mail-menu
  "Make a new menu with titles and actions for all the messages in the global message ref."
  [messages]
  (make-quit-menu
   (fn [menu]
     (dorun
      (map (fn [[folder-name ms]]
             (.addAction menu (make-title-action menu folder-name))
             (dorun (map (fn [m]
                           (.addAction menu (make-message-action menu m)))
                         ms))
             (.addSeparator menu))
           messages)))))

;; Systray-updating fns
(defn update-systray-icon
  "Update the systray icon - generate a new icon and assign it to the systray."
  ([systray] (update-systray-icon systray nil))
  ([systray text]
     (.dispose (.icon systray))
     (.setIcon systray (make-icon *icon-filename* text))
     systray))

(defn update-systray-menu
  "Update the systray menu - generate a new menu and assign it to the systray, disposing the old menu."
  [systray menu]
  (.dispose (.contextMenu systray))
  (.setContextMenu systray menu))

(defn update-systray
  "Update the systray - refresh the icon and the context menu."
  [systray]
  (QCoreApplication/invokeLater
   (fn []
     (update-systray-icon systray (count-messages @*messages*))
     (update-systray-menu systray (make-mail-menu @*messages*)))))

(defn run
  "Start the UI and start the background threads for fetching messages from the server.  Runs forever until QCoreApplication/quit is called."
  []
  (qt4
   (let [systray (make-systray *icon-filename*)
         callback (fn [] (update-systray systray))]
     (dorun (map #(send-off % check-mail callback) @*mailspecs*)))))

(defn load-mailspecs [specs]
  "Given a set of maps, initializes *mailspecs* (agents)."
  (dosync
   (ref-set *mailspecs*
            (into #{}
                  (map #(agent (reduce conj (struct mailspec) %)) specs)))))

;; Example usage
(comment
  ;; Pass in a set of maps
  (load-mailspecs #{
                    {:protocol "imaps"
                     :host "imap.gmail.com"
                     :user "username@gmail.com"
                     :password "password"
                     :folder-name "INBOX"}
                    
                    {:protocol "imap"
                     :host "imap.somedomain.xyz"
                     :user "someuser"
                     :password "somepassword"
                     :folder-name "INBOX.somefolder.subfolder"}

                    {:protocol "pop3"
                     :host "pop.otherdomain.xyz"
                     :user "someuser"
                     :password "somepassword"
                     :folder-name "INBOX"}})
  ;; And off we go.
  (run))


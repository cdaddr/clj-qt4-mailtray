;; Clojure wrapper for Qt Jambi (Qt for Java: http://trolltech.com/downloads/opensource/appdev )
;; Plus a few icon and menu manipulation fns.
(ns qt4-mailtray.qt4
  (:import (com.trolltech.qt.gui
            QApplication
            QMenu QAction QIcon
            QPainter QColor QBrush
            QFont QFont QFont$Weight QFontMetrics
            QSystemTrayIcon)
           (com.trolltech.qt.core
            QCoreApplication
            Qt$AlignmentFlag)))

(def *running* (ref false))

(defn init
  "Initialize Qt; this will throw all kinds of interesting exceptions if you run it twice without running exec and quit in between."
  []
  (QApplication/initialize (make-array String 0)))

(defn exec
  "Begin Qt execution (event loop)"
  []
  (QApplication/exec))

(defmacro qt4 
  "Execute some code in the context of a running Qt event loop, being sure to set *running* to false when it ends.  (RuntimeException during (init) can occur for trivial reasons; it's usually safe to swallow the error.)"
  [& rest]
  `(do
     (try
      (dosync (ref-set *running* true))
      (try (init) (catch RuntimeException e# (println e#)))
      ~@rest
      (exec)
      (finally (dosync (ref-set *running* false))))))

(defn make-icon
  "Given an image filename, make and return an icon, possibly with some text overlayed in a colored box."
  ([file] (make-icon file nil))
  ([file text] (if (or (nil? text)
                       (= (str text) "0"))
                 (new QIcon file)
                 (let [text (str text)
                       icon (new QIcon file)
                       size 32
                       pixmap (.pixmap icon size size)
                       painter (new QPainter pixmap)
                       font (new QFont "Deja Vu Sans" 12)
                       qfm (new QFontMetrics font)
                       rect (.tightBoundingRect qfm text)]
                   (doto rect
                     (.adjust -4 -4 4 4)
                     (.moveTo (/ (- size (.width rect)) 2)
                              (dec (/ (- size (.height rect)) 2))))
                   (doto painter
                     (.setOpacity 1.0)
                     (.setBrush QColor/black)
                     (.setPen QColor/black)
                     (.drawRoundedRect rect 2.0 2.0)

                     (.setFont font)
                     (.setPen QColor/white)
                     (.setOpacity 1.0)
                     (.drawText (.rect pixmap) (.. Qt$AlignmentFlag AlignCenter value) text)
                     (.end))
                   (new QIcon pixmap)))))

(defn make-quit-menu
  "Make a menu where the last item is a 'quit' item.  Accepts an fn to which the menu is passed, which allows the caller to add more items before the 'quit' item."
  ([] (make-quit-menu nil))
  ([menu-builder-fn]
     (let [app (QCoreApplication/instance)
           menu (new QMenu)
           quit-action (new QAction "Quit" menu)]
       (.. quit-action triggered (connect app "quit()"))
       (when menu-builder-fn
         (menu-builder-fn menu))
       (.addAction menu quit-action)
       menu)))

(defn make-systray
  "Given an icon filename, make and return a system tray icon with a default quit menu."
  [icon-file]
  (let [systray (new QSystemTrayIcon)]
    (doto systray
      (.setIcon (make-icon icon-file))
      (.setContextMenu (make-quit-menu))
      (.show))))

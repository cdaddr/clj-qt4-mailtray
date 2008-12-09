;; Clojure wrapper for Javamail. (http://java.sun.com/products/javamail/)
(ns bcc.mail
  (:import (java.util Properties)
           (javax.mail Session Store Folder Message Flags Flags$Flag FetchProfile FetchProfile$Item)
           (javax.mail.internet InternetAddress))
  (:use clojure.contrib.str-utils))

;; Mailspec
(defstruct mailspec :protocol :host :port :user :password :folder-name)

(defn mailspec-name
  "Nicely formatted mailspec name, including host and folder name."
  [mailspec]
  (str (:host mailspec) "::" (:folder-name mailspec)))

;; Message
(defstruct message :from :subject)

(defn jmm-from
  "Nicely formatted 'from' string for an email, including name and email addr."
  [m]
  (str-join \, (map (fn [from] (format "%s <%s>" (.getPersonal from) (.getAddress from))) (.getFrom m))))

(defn jmm-subject
  "Subject line for an email."
  [m]
  (.getSubject m))

(defn jmm-to-message
  "Constructor for a message struct, fetching info from a javax.mail.Message object."
  [m]
  (struct-map message :from (jmm-from m) :subject (jmm-subject m)))

;; Javamail stuff
(defn unseen?
  "Return boolean indicating whether an email's UNSEEN flag is set."
  [message]
  (not (.isSet message Flags$Flag/SEEN)))


(defmacro with-mail-store
  "Execute some code in the context of an open mail store."
  [store [mailspec] & rest]
  `(let [mailspec# ~mailspec
         props# (new Properties)
         session# (Session/getDefaultInstance props# nil)
         store# (.getStore session# (:protocol mailspec#))]
     (try
      (.connect store# (:host mailspec#) (:user mailspec#) (:password mailspec#))
      (let [~store store#]
        ~@rest)
      (finally (.close store#)))))

(defmacro with-folder
  "Execute some code in the context of an open mail folder."
  [folder [mailspec] & rest]
  `(with-mail-store store# [~mailspec]
                    (let [folder# (.getFolder store# (:folder-name ~mailspec))]
                      (try
                       (.open folder# Folder/READ_ONLY)
                       (let [~folder folder#]
                         ~@rest)))))

(defmacro with-messages
  "Execute some code in the context of a list of open mail messages.  Pre-fetches FLAGS for all messages."
  [messages [mailspec] & rest]
  `(with-folder folder# [~mailspec]
                (let [messages# (.getMessages folder#)
                      fp# (new FetchProfile)]
                  (doto fp#
                    (.add FetchProfile$Item/FLAGS))
                  (.fetch folder# messages# fp#)
                  (let [~messages messages#]
                    ~@rest))))


(defn get-new-messages
  "Connects to server, and returns a list of message objects representing the UNSEEN messages on the server."
  [mailspec]
  (with-messages messages [mailspec]
                 ;; doall is needed to override laziness
                 ;; (otherwise the folder will be closed by the time we try to fetch)
                 (doall (map jmm-to-message
                             (filter unseen? messages)))))


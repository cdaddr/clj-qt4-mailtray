;; Misc. utils
(ns qt4-mailtray.util)

(defn find-first
  "Return the first item in coll for which pred is true."
  [pred coll]
  (when coll
    (if (pred (first coll))
      (first coll)
      (recur pred (rest coll)))))

(defn to-class [o]
  (if (= (class o) java.lang.Class)
    (identity o)
    (class o)))

(defn java-methods [obj]
  (let [klass (to-class obj)]
    (into [] (.getMethods klass))))

(defn java-method-names [obj]
  (sort (map #(.getName %) (java-methods obj))))

(ns editors.depsedn
  (:require [rewrite-clj.zip :as z]
            [babashka.fs :as fs]
            [slim]))

(defn update-mvn-version [f library version-string]
  (let [deps-file (fs/file slim/*pwd* f)]
    (-> (z/of-string (slurp deps-file))
        (z/find-value z/next library)
        z/next
        z/next
        z/next
        (z/edit (constantly version-string))
        (z/root-string)
        (as-> s (spit deps-file s)))))


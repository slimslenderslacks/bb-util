(ns editors.depsedn
  (:require [rewrite-clj.zip :as z]
            [babashka.fs :as fs]
            [slim]))

(defn ^{:atomist/command "update-mvn-version"
        :atomist/parameter-specs
        [[nil "--version VERSION" "mvn version"]
         [nil "--library LIBRARY" "library"
          :parse-fn symbol]
         [nil "--file FILE" "FILE"
          :default "deps.edn"]]} update-mvn-version
  [{{:keys [file library version]} :options}]
  (let [deps-file (fs/file slim/*pwd* file)]
    (-> (z/of-string (slurp deps-file))
        (z/find-value z/next library)
        z/next
        z/next
        z/next
        (z/edit (constantly version))
        (z/root-string)
        (as-> s (spit deps-file s)))))


(ns editors.leiningen
  (:require [rewrite-clj.zip :as z]
            [babashka.fs :as fs]
            [pwd :refer [*pwd*]]))

(defn ^{:atomist/command "insert-leiningen-dependency"
        :atomist/parameter-specs
        [[nil "--version VERSION" "mvn version"]
         [nil "--library LIBRARY" "library"
          :parse-fn symbol]
         [nil "--file FILE" "FILE"
          :default "project.clj"]]} insert-leiningen-dependency
  [{{:keys [file library version]} :options}]
  (let [deps-file (fs/file *pwd* file)]
    (-> (z/of-string (slurp deps-file))
        (z/find-value z/next :dependencies)
        z/next
        z/next
        z/rightmost
        (z/insert-right '[log4j/log4j "1.2.14"])
        (z/root-string)
        (as-> s (spit deps-file s)))))

(defn ^{:atomist/command "remove-last-leiningen-dependency"
        :atomist/parameter-specs
        [[nil "--version VERSION" "mvn version"]
         [nil "--library LIBRARY" "library"
          :parse-fn symbol]
         [nil "--file FILE" "FILE"
          :default "project.clj"]]} remove-last-leiningen-dependency
  [{{:keys [file library version]} :options}]
  (let [deps-file (fs/file *pwd* file)]
    (-> (z/of-string (slurp deps-file))
        (z/find-value z/next :dependencies)
        z/next
        z/next
        z/rightmost
        z/remove
        (z/root-string)
        (as-> s (spit deps-file s)))))

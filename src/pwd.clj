(ns pwd
  (:require [babashka.fs :as fs]))

(def ^:dynamic *pwd* nil)

(defn init-pwd
  ([pwd]
   (alter-var-root #'*pwd* (constantly pwd)))
  ([]
   (if-let [pwd (System/getenv "BB_CWD")]
     (init-pwd (fs/file pwd))
     (init-pwd (fs/file ".")))))

(defn ^{:atomist/command "pwd"} pwd 
  [_]
  (println *pwd*))

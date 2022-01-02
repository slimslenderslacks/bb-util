(ns slim
  (:require [clojure.tools.cli :refer [parse-opts]]
            [babashka.fs :as fs]))

(def ^:dynamic *pwd* nil)

(defn init-pwd
  ([pwd]
   (alter-var-root #'*pwd* (constantly pwd)))
  ([]
   (if-let [pwd (System/getenv "BB_CWD")]
     (init-pwd (fs/file pwd))
     (init-pwd (fs/file ".")))))

(defn cli [f ks specs]
  (try
    (let [{:keys [options]} (parse-opts *command-line-args* specs)
          args (reduce (fn [agg k] (conj agg (k options))) [] ks)]
      (apply f args))
    (catch Throwable t
      (println t))))

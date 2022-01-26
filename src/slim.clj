(ns slim
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as str]
            [doric.core :as doric]))

(def parameter-specs [])

(declare command-vars)

(defn ^{:atomist/command "help"} help
  [_]
  (->> command-vars
       (sort-by key)
       (map (fn [[k v]] {:name k :required-parameters (-> v meta :atomist/required) :doc (-> v meta :doc)}))
       (doric/table [:name :required-parameters :doc])
       (println)))

(def namespaces-to-scan ["tasks" 
                         "atomist.github.interface" 
                         "editors.depsedn"
                         "bb.reset-slenderslack"
                         "pwd"
                         "slim"])
(doseq [x namespaces-to-scan] (require (symbol x)))
(def command-vars
  (->> namespaces-to-scan
       (mapcat (comp seq ns-publics read-string))
       (map second)
       (filter #(contains? (meta %) :atomist/command))
       (map #(-> [(-> % meta :atomist/command)] (conj %)))
       (into {})))
(def added-parameter-specs
  (->> command-vars
       (vals)
       (mapcat (comp :atomist/parameter-specs meta))))

(defn completions [_]
  (println
   (->> (concat
         (->> command-vars
              (vals)
              (map meta)
              (map :atomist/command))
         (->> (concat parameter-specs added-parameter-specs)
              (map second)
              (map (fn [s] (first (str/split s #" "))))))
        (interpose " ")
        (apply str))))

(defn cli [& args]
  (if (some #(= "slim/completions" %) args)
    (completions {})
    (let [cli-options (parse-opts args (concat parameter-specs added-parameter-specs))]
      (if (:errors cli-options)
        (printf "%s\n" (:errors cli-options))
        (if (-> cli-options :options :help)
          (printf "Usage:  cli {fqfn or command} [opts]\n")
          (if-let [command (some command-vars (-> cli-options :arguments))]
            (try
              (command (update cli-options :arguments rest))
              (catch Throwable t
                (binding [*out* *err*]
                  (println (str t)))
                (System/exit 1)))
            (do
              (binding [*out* *err*]
                (println (format "no command found\n\t")))
              (System/exit 1))))))))

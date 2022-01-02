(ns tasks
  (:require [slim] 
            [version-clj.core :as v]
            [babashka.fs :as fs]
            [clojure.string :as s]
            [babashka.process :refer [$ process]]))

(defn pwd []
  (println slim/*pwd*))

(defn increment-tag [s]
  (if (nil? s)
    "v0.0.1"
    (if-let [[_ last-tag] (re-find #"v(.*)-\d+-g[a-z,0-9]+" s)]
      (if-let [_ last-tag] (re-find #"v(.*)" s)
        last-tag
        (throw (ex-info (format "%s tag is not a valid semver tag" s) {}))))))

(defn increment-version [f]
  (spit f
        (s/replace
         (slurp f)
         #":version (\d+)"
         (fn [[_ v]] (format ":version %s" (inc (Integer/parseInt v)))))))

(defn describe 
  "describe current HEAD - throw if no ancestor commits have tags"
  []
  (let [{tag :out exit-code :exit}
        (->
         ^{:dir slim/*pwd* :out :string} ($ git describe --tags)
         deref)]
    (if (= 0 exit-code)
      tag
      (throw (ex-info "no initial tag" {})))))

(defn tagged? 
  "HEAD commit is tagged"
  []
  (let [{tag :out exit-code :exit} (-> ^{:dir slim/*pwd* :out :string} ($ git describe --tags --exact-match)
                                       deref)]
    (not (or (= "" tag) (nil? tag)))))

(defn clean? 
  "check working copy"
  []
  (let [{:keys [out]}(-> ^{:dir slim/*pwd* :out :string} ($ git status --porcelain)
      deref)]
    (or (= "" out) (nil? out))))

(defn recommit-any-updates [s]
  (if (not s)
    (throw (ex-info "need a message" {}))
    (->
     (process (-> ["git" "commit" "-a" "-m" s]
                  (concat (if (tagged?) [] ["--amend"])))
              {:dir slim/*pwd* :out :string})
     deref)))

(ns tasks
  (:require [pwd] 
            [version-clj.core :as v]
            [babashka.fs :as fs]
            [clojure.string :as s]
            [babashka.process :refer [$ process]]
            [remorse.core :as rm]))

(defn ^{:atomist/command "to-morse"} to-morse
  [{[s] :arguments :as opts}]
  (println (rm/string->morse s)))

(defn ^{:atomist/command "from-morse"} from-morse
  [{[s] :arguments}]
  (println (rm/morse->string s)))

(defn increment-tag [s]
  (if (nil? s)
    "v0.0.1"
    (when-let [[_ last-tag] (re-find #"v(.*)-\d+-g[a-z,0-9]+" s)]
      (if-let [[_ last-tag] (re-find #"v(.*)" s)]
        last-tag
        (throw (ex-info (format "%s tag is not a valid semver tag" s) {}))))))

(defn increment-simple-version-tag [s]
  (if (nil? s)
    "v1"
    (when-let [[_ previous-version] (re-find #"v(\d+)-\d+-g[a-z,0-9]+" s)]
      (str "v" (inc (Integer/parseInt previous-version))))))

(comment
  (increment-simple-version-tag "v1-4-gabcde")
  )

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
         ^{:dir pwd/*pwd* :out :string} ($ git describe --tags)
         deref)]
    (if (= 0 exit-code)
      tag
      (throw (ex-info "no initial tag" {})))))

(def next-tag (comp increment-simple-version-tag describe))

(defn tagged? 
  "HEAD commit is tagged"
  []
  (let [{tag :out exit-code :exit} (-> ^{:dir pwd/*pwd* :out :string} ($ git describe --tags --exact-match)
                                       deref)]
    (not (or (= "" tag) (nil? tag)))))

(defn clean? 
  "check working copy"
  []
  (let [{:keys [out]}(-> ^{:dir pwd/*pwd* :out :string} ($ git status --porcelain)
      deref)]
    (or (= "" out) (nil? out))))

(defn recommit-any-updates [& args]
  (cond
    (not (tagged?))
    (->
     (process  ["git" "commit" "-a" "--no-edit" "--amend"]
               {:dir pwd/*pwd* :out :string})
     deref)
    (first args)
    (->
     (process (-> ["git" "commit" "-a" "-m" (first args)]
                  (concat (if (tagged?) [] ["--amend"])))
              {:dir pwd/*pwd* :out :string})
     deref)
    :else
    (throw (ex-info "need a message" {}))))

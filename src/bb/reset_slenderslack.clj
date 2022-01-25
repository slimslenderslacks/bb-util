(ns bb.reset-slenderslack
  (:require [atomist.github.interface :as gh]
            [babashka.process :refer [$] :as p]
            [babashka.fs :as fs]
            [clojure.string :as s]))

(def location "/Users/slim/atomist/slenderslack")
(def dirs #{["pinning-test" "Dockerfile" ["Dockerfile"]]
            ["pinning-test-gcr" "src/atomist/main.clj" ["docker/Dockerfile"]]
            ["ecr-service" "service/Dockerfile" ["service/Dockerfile"]]
            ["pinning-test-dockerhub" "index.js" ["Dockerfile"]]
            ["pinning-test-actions-dockerhub" "Dockerfile" ["Dockerfile"]]
            ["pin-test-repo1" "Dockerfile" ["Dockerfile"]]
            ["pin-test-repo2" "Dockerfile" ["Dockerfile"]]
            ["pin-test-repo3" "version.edn" ["Dockerfile"]]
            ["pin-test-repo4" "version.edn" ["Dockerfile"]]
            ["pin-test-repo5" "version.edn" ["Dockerfile"]]
            ["distroless-pinning-test" "version.edn" ["Dockerfile"]]
            ["elated-shirley" "version.edn" []]
            ["horrors" "version.edn" []]
            ["hadolint-fail" "Dockerfile" ["Dockerfile" "docker/Dockerfile"]]})

(defn git-commit-and-push [basedir]
  (-> (p/process ["git" "commit" "-am" "bump"] {:dir basedir}) deref :out slurp println)
  (-> (p/process ["git" "push" "origin" "main"] {:dir basedir}) deref :out slurp println)
  (-> (p/process ["git" "rev-parse" "HEAD"] {:dir basedir}) deref :out slurp println))

(defn bump [basedir versioned-file]
  (let [filename (fs/file basedir versioned-file)]
    (println "update " filename)
    (if (fs/exists? filename)
      (do
        (spit filename
              (s/replace
               (slurp filename)
               #":version (\d+)"
               (fn [[_ v]] (format ":version %s" (inc (Integer/parseInt v))))))
        (git-commit-and-push basedir))
      (throw (ex-info (format "%s does not exist" (fs/path filename)) {})))))

(defn replace-in-file
  "replace all regex occurrences using f replacement function"
  [filename regex f]
  (spit filename 
      (s/replace 
        (slurp filename) 
        regex 
        f)))

(defn reset-repo [slug dir unpinnables versioned-file]
  (println "------------------------- " slug)
  (-> ^{:dir dir} ($ git fetch origin) deref :out slurp println)
  (-> ^{:dir dir} ($ git checkout main) deref :out slurp println)
  (-> ^{:dir dir} ($ git pull origin) deref :out slurp println)
  (gh/close-all-repo-prs {:slug slug :token (get (System/getenv) "GITHUB_TOKEN_SLENDERSLACK")})
  (gh/clean-branches {:slug slug :token (get (System/getenv) "GITHUB_TOKEN_SLENDERSLACK")})
  (doseq [unpin unpinnables]
    (replace-in-file
     (format "%s/%s" dir unpin)
     #"(\S+:\S+)(@sha256:\S+)"
     (fn [[_ pre]] pre)))
  (bump dir versioned-file))

(defn ^{:atomist/command "reset-slenderslack"} -main 
  [_]
  (doseq [[d f unpinnables] dirs :let [dir (format "%s/%s" location d)
                                       slug (format "%s/%s" "slenderslack" d)]]
    (reset-repo slug dir unpinnables f)))


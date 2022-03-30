(ns user
  (:require [atomist.github.core :as github-core]
            [babashka.curl :as curl]
            [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :refer [pprint]]))

(defn repos-query [startCursor org]
  (-> (github-core/query-github
       {:token (System/getenv "GITHUB_TOKEN_SLIMSLENDERSLACKS")}
       (slurp (fs/file "/Users/slim/atmhq/bb_scripts/graphql/search_repos.graphql"))
       {:repoQuery (format "org:%s archived:false" org) :before startCursor})
      :data
      :search))

(defn all-repos-in-org [org-name]
  (loop [startCursor nil repos []]
    (let [{{:keys [endCursor hasNextPage]} :pageInfo edges :edges :as result} (repos-query startCursor org-name)
          repos (concat repos (->> edges (map (comp :name :node))))]
      (println :repositoryCount result)
      (if hasNextPage
        (recur endCursor repos)
        repos))))

(defn extract-code-sum [s]
  (let [[_ _ _ _ d] (re-find #"SUM:\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)" s)] d)
  )

(defn cloc-org [s]
  (->>
   (for [d (->> (fs/list-dir (fs/file s)))]
     [(fs/file-name d) (-> (p/process (format "cloc %s" (fs/file-name d)) {:dir s :out :string})
                           deref
                           :out
                           (extract-code-sum)
                           (Integer/parseInt))])
   (into {})))

(defn clone-all [org-name repos]
  (doseq [repo repos
          :let [remote (format "git@github.com:%s/%s" org-name repo)]]
    (println (format "clone %s -> %s" remote
                     (-> (p/process (format "git clone %s" remote) {:dir (format "/Users/slim/atomist-repos/%s" org-name)})
                         deref
                         :exit)))))

(comment

  ;;
  (spit "dev/atomisthq-repos.txt" (str/split "\n" (all-repos-in-org "atomisthq")))
  (spit "dev/atomist-repos.txt" (str/split "\n" (all-repos-in-org "atomist")))
  (spit "dev/atomist-skills-repos.txt" (str/split "\n" (all-repos-in-org "atomist-skills")))

  ;;
  (def atomisthq-repos (line-seq (io/reader (io/file "dev/atomisthq-repos.txt"))))
  (def atomist-skills-repos (line-seq (io/reader (io/file "dev/atomist-skills-repos.txt"))))
  (def atomist-repos (line-seq (io/reader (io/file "dev/atomist-repos.txt"))))

  ;;
  (clone-all "atomisthq" atomisthq-repos)
  (clone-all "atomist-skills" atomist-skills-repos)
  (clone-all "atomist-repos" atomist-repos)

  ;; 
  (def atomisthq-clocs (cloc-org "/Users/slim/atomist-repos/atomisthq"))
  (def atomist-skills-clocs (cloc-org "/Users/slim/atomist-repos/atomist-skills"))
  (def atomist-clocs (cloc-org "/Users/slim/atomist-repos/atomist"))

  ;;
  (+
   (->> atomisthq-clocs vals (reduce + 0))
   (->> atomist-skills-clocs vals (reduce + 0))))




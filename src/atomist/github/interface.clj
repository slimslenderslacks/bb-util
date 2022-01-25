(ns atomist.github.interface
  (:require [atomist.github.core :as core]))

(defn ^{:atomist/required [:token :slug]} close-all-repo-prs 
  "close every open pr for one repo"
  [cli-options]
  (core/close-all-repo-prs cli-options))

(defn ^{:atomist/command "clean-branches"
        :atomist/parameter-specs [[nil "--slug SLUG" "SLUG"]
                                  [nil "--token TOKEN" "TOKEN"
                                   :default-fn (fn [x] (System/getenv "GITHUB_TOKEN_SLENDERSLACK"))]]} clean-branches
  "clean up non-default branch refs"
  [cli-options]
  (core/clean-branches cli-options))


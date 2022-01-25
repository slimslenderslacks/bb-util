(ns atomist.json.core
  (:require [cheshire.core :as cheshire]
            [clojure.pprint :refer [pprint]]))

(defn ->obj 
  [s & {:keys [keywordize-keys]}]
  (cheshire/parse-string s keywordize-keys))

(defn ->str 
  [obj & {:keys [keyword-fn] :or {keyword-fn name}}]
  (cheshire/generate-string obj {:key-fn keyword-fn}))

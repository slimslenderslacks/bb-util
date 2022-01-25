(ns atomist.json.interface
  (:require [atomist.json.core :as core]))

(defn ->obj [s & {:keys [keywordize-keys] :or {keywordize-keys true}}]
  (core/->obj s :keywordize-keys keywordize-keys))

(defn ->str 
  ([obj & {:keys [keyword-fn]
           :or {keyword-fn name}}]
   (core/->str obj :keyword-fn keyword-fn)))


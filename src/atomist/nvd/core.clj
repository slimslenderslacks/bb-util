(ns atomist.nvd.core
  (:require [babashka.curl :as curl]
            [atomist.json.interface :as json]
            [clojure.pprint :refer [pprint]]
            [babashka.process :as process]
            [babashka.fs :as fs]))

(comment
  (def x (curl/get "https://nvd.nist.gov/vuln/data-feeds#JSON_FEED"))

  (pprint (:headers x))
  (println (:body x))
  (def zips (re-seq #"nvdcve-1.1-[0-9]*\.json\.zip" (:body x)))
  (doseq [f zips :let [url (format "https://nvd.nist.gov/feeds/json/cve/1.1/%s" f)]]
    (process/$ "curl" "-O" url)
    (process/$ "unzip" f))

  (def json-files (->> (fs/list-dir (fs/file "."))
                       (filter #(re-find #"nvdcve.*\.json$" (fs/file-name %)))))

  (def nvdcve-2021
    (-> json-files
        (first)
        (slurp)
        (json/->obj)))

  (->> nvdcve-2021
       :CVE_Items
       (take 2)
       (map (fn [{:keys [cve configurations publishedDate lastModifiedDate]}]
              (pprint cve)))))

(defn get-cve [cve]
  (println "fetch " cve)
  (-> (curl/get 
        (format "https://services.nvd.nist.gov/rest/json/cve/1.0/%s" cve)
        {:throw false})
      :body
      ((fn [body] (println "body " body) body))
      (json/->obj)
      :result
      :CVE_Items
      first))

;; https://csrc.nist.gov/CSRC/media/Projects/National-Vulnerability-Database/documents/web%20service%20documentation/Automation%20Support%20for%20CVE%20Retrieval.pdf
(comment
  (:publishedDate (get-cve "CVE-2019-19882"))
  (pprint (get-cve "CVE-2019-19882"))
  (pprint (get-cve "CVE-2021-35942"))
  )

(ns atomist.github.core 
  (:require [babashka.curl :as curl]
            [atomist.json.interface :as json]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.pprint :refer [pprint]]))

(defn- slug->org-repo [slug]
  (let [[_ org repo] (re-find #"(.*)/(.*)" slug)]
    [org repo]))

(defn- v4-headers
  [{:keys [token] :as opts}]
  (if token
    {"Accept" "application/vnd.github.bane-preview+json"
     "Authorization" (format "bearer %s" token)}
    (throw (ex-info "no github api token in environment" opts))))

(defn- headers
  [{:keys [token] :as opts}]
  (if token
    {"Accept" "application/vnd.github.v3+json"
     "Authorization" (format "bearer %s" token)}
    (throw (ex-info "no github api token in environment" opts))))

(defn- delete-repo [{:keys [slug] :as opts}]
  (println (apply format "https://api.github.com/repos/%s/%s" (slug->org-repo slug)))
  (curl/delete (apply format "https://api.github.com/repos/%s/%s" (slug->org-repo slug))
               {:headers (headers opts)}))

(defn- transfer-repo [{:keys [new_owner slug] :as opts}]
  (curl/post (apply format "https://api.github.com/repos/%s/%s/transfer" (slug->org-repo slug))
             {:headers (headers opts)
              :body (json/->str {:new_owner new_owner})}))

(defn- delete-ref [opts url]
  (curl/delete url {:headers (headers opts)}))

(defn- close-pr [opts org-name repo-name number]
  (println "close-pr " org-name repo-name number)
  (curl/patch (format "https://api.github.com/repos/%s/%s/pulls/%s" org-name repo-name number)
              {:headers (headers opts)
               :body (json/->str {:state "closed"})}))

(defn- merge-pr [opts org-name repo-name number]
  (curl/put (format "https://api.github.com/repos/%s/%s/pulls/%s/merge" org-name repo-name number)
              {:headers (headers opts)
               :throw false
               :body (json/->str {})}))

(defn- list-prs 
  "list prs on one repo"
  [opts org-name repo-name]
  (println "list-prs" opts)
  (curl/get (format "https://api.github.com/repos/%s/%s/pulls" org-name repo-name)
            {:headers (headers opts)
             :query-params {"state" "open"}}))

(defn- list-refs [opts org-name repo-name]
  (curl/get (format "https://api.github.com/repos/%s/%s/git/refs" org-name repo-name)
            {:headers (headers opts)
             :query-params {"per_page" "100"}}))

(defn list-packages [opts org-name]
  (let [response
        (curl/get (format "https://api.github.com/orgs/%s/packages" org-name)
                  {:headers (headers opts)
                   :throw false
                   :query-params {"package_type" "container"}})]
    (if (= 200 (:status response))
      (->> response 
           :body
           (json/->obj)
           (map :name)
           (into [])) [])))

(defn create-issue [opts org-name repo-name title body]
  (let [response (curl/post (format "https://api.github.com/repos/%s/%s/issues" org-name repo-name)
                            {:headers (headers opts)
                             :body (json/->str {:title title
                                                          :body body
                                                          :labels ["phase/in-progress"]
                                                          :assignees ["slimslenderslacks"]})})]
    (if (= 201 (:status response))
      (-> response :body (json/->obj) :number)
      response)))

(defn query-github [cli-options query variables]
  (let [response (curl/post "https://api.github.com/graphql"
                            {:headers (v4-headers cli-options)
                             :body (json/->str 
                                     {:query query
                                      :variables variables})})]
    (if (= 200 (:status response))
      (json/->obj (:body response) keyword)
      (printf "status %s \n" (:status response)))))

(defn issue-query [{:keys [org title]}]
  (->>
    (cond-> 
      ["is:open is:pr archived:false"]
      org (conj (format "org:%s" org))
      title (conj (format "%s in:title" title)))
    (interpose " ")
    (apply str)))

(def script-dir (io/file "/Users/slim/atmhq/bb_scripts"))

(defn- list-open-prs 
  "use github search api to query for all open PRs across an entire Org
    :repo-filter and :title-filter can be used to filter the results"
  [cli-options]
  (let [repo-filter (some-> cli-options
                            :options
                            :repo-filter
                            (slurp)
                            (read-string))]
    (-> (query-github cli-options 
                      (slurp (io/file script-dir "graphql/search_open_prs.graphql")) 
                      {:issueQuery (issue-query (:options cli-options))})
        :data
        :search
        :edges
        (->> (map :node)
             #_(filter #(if repo-filter (repo-filter (-> % :repository :name)) true))
             (filter #(if (-> cli-options :options :title-filter) 
                        (string/includes? (:title %) (-> cli-options :options :title-filter))
                        true))
             (map #(format "%-90s%s" 
                           (format "https://github.com/%s/pull/%s" (-> % :repository :nameWithOwner) (:number %)) 
                           (:title %)))
             (map println)
             (doall))))
  nil)

(defn- bulk-merge-prs [cli-options]
  (let [repo-filter (some-> cli-options
                            :options
                            :repo-filter
                            (slurp)
                            (read-string))]
    (-> (query-github cli-options 
                      (slurp (io/file script-dir "graphql/search_open_prs.graphql")) 
                      {:issueQuery (issue-query (:options cli-options))})
        :data
        :search
        :edges
        (->> (map :node)
             (filter #(if repo-filter (repo-filter (-> % :repository :name)) true))
             (filter #(and (-> cli-options :options :title-filter) 
                           (string/includes? (:title %) (-> cli-options :options :title-filter))))
             (map #(let [[_ org repo] (re-find #"(.*)/(.*)" (-> % :repository :nameWithOwner))] 
                     (println (format "%s/%s/%s - %s" 
                                      org repo (-> % :number) 
                                      (:status (merge-pr cli-options org repo (:number %)))))))
             (doall))))
  nil)

(defn- bulk-close-prs [cli-options]
  (let [repo-filter (some-> cli-options
                            :options
                            :repo-filter
                            (slurp)
                            (read-string))]
    (-> (query-github cli-options 
                      (slurp (io/file script-dir "graphql/search_open_prs.graphql")) 
                      {:issueQuery (issue-query (:options cli-options))})
        :data
        :search
        :edges
        (->> (map :node)
             #_(filter #(if repo-filter (repo-filter (-> % :repository :name)) true))
             (filter #(and (-> cli-options :options :title-filter) 
                           (string/includes? (:title %) (-> cli-options :options :title-filter))))
             (map #(let [[_ org repo] (re-find #"(.*)/(.*)" (-> % :repository :nameWithOwner))] 
                     (println (format "%s/%s/%s - %s" 
                                      org repo (-> % :number) 
                                      #_(:title %)
                                      (:status (close-pr cli-options org repo (:number %)))))))
             (doall))))
  nil)

(defn- get-gists [cli-options]
  (let [url (format "https://api.github.com/gists")
        response (curl/get url {:headers (headers cli-options)})]
    (-> response
        :body
        (json/->obj))))

(defn- put-issue-labels [cli-options org-name repo-name number {:keys [plus minus]}]
  (let [url (format "https://api.github.com/repos/%s/%s/issues/%s/labels" org-name repo-name number)
        response (curl/get url {:headers headers})]
    (if (= 200 (:status response))
      (curl/post url
                 {:headers (headers cli-options)
                  :body (json/->str {:labels (-> response
                                                 :body
                                                 (json/->obj)
                                                 (->> (map :name)
                                                      (concat plus)
                                                      (remove minus)
                                                      (into [])))})}))))

(defn close-all-repo-prs [{:keys [slug] :as opts}]
  (let [[org repo] (slug->org-repo slug)]
    (->> (list-prs opts org repo)
         :body
         ((fn [s] (let [coll (json/->obj s)] (seq coll))))
         (map (fn [m] (:number m)))
         ((fn [coll] (println "numbers " coll " " org " " repo) coll))
         (map (partial close-pr opts org repo))
         (doall))))

(defn clean-branches [{:keys [slug] :as opts}]
  (let [[org repo] (slug->org-repo slug)]
    (->> (list-refs opts org repo)
         :body
         ((fn [s] (let [coll (json/->obj s)] (seq coll))))
         (filter (complement (fn [{:keys [ref]}]
                               (or (string/includes? ref "refs/pull")
                                   (string/includes? ref "refs/heads/main")
                                   (string/includes? ref "refs/heads/master")))))
         (map :url)
         (map (partial delete-ref opts))
         (doall))))


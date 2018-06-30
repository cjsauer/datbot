(ns datbot.core
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [datomic.client.api :as d]
            [datomic.ion.lambda.api-gateway :as apigw]
            [cognitect.anomalies :as anom]))

(def config
  (-> (io/resource "config.edn")
      slurp
      read-string))

(def datomic-system (:datomic-system config))
(def datomic-region (:datomic-region config))
(def datomic-cfg
  {:server-type :ion
   :region datomic-region
   :system datomic-system
   :query-group datomic-system
   :endpoint (format "http://entry.%s.%s.datomic.net:8182/" datomic-system datomic-region)
   :proxy-port 8182})

(def datomic-client (d/client datomic-cfg))
(d/create-database datomic-client {:db-name (:db-name config)})
(def conn (d/connect datomic-client {:db-name (:db-name config)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Slack
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def usage
  (str "Here's a list of example commands that I can handle: \n"
       "-transact: `{:tx-data [{:seesaw/project-name \"Diet ID\"}]}`\n"
       "-query: `{:query [:find ?e :where [?e :seesaw/project-name \"Diet ID\"] :in $]}`\n"
       "-pull: `{:pull {:entity 123456789 :selector [*]}}`"))

(def slack-api-base-url "https://slack.com/api/%s")
(def slack-api-post-message-url (format slack-api-base-url "chat.postMessage"))

(defn send-message
  [channel message]
  (http/post slack-api-post-message-url
             {:body (json/write-str {:channel channel :text message})
              :content-type :json
              :accept :json
              :oauth-token (:datbot-oauth-token config)}))

(def content-type-json {"Content-Type" "application/json"})
(defn read-json
  [input-stream]
  (-> input-stream io/reader (json/read :key-fn keyword)))

(s/def ::tx-data coll?)
(s/def ::query coll?)
(s/def ::selector coll?)
(s/def ::entity #(or (coll? %) (int? %)))
(s/def ::pull (s/keys :req-un [::selector ::entity]))
(s/def ::tx-mention (s/keys :req-un [::tx-data]))
(s/def ::query-mention (s/keys :req-un [::query]))
(s/def ::pull-mention (s/keys :req-un [::pull]))
(s/def ::mention (s/or :tx-mention ::tx-mention
                       :query-mention ::query-mention
                       :pull-mention ::pull-mention))

(defn- pp-str
  [d]
  (with-out-str (pprint d)))

(defn- conform!
  [spec x]
  (let [result (s/conform spec x)]
    (if (= ::s/invalid result)
      (throw (ex-info (s/explain spec x)
                      (s/explain-data spec x)))
      result)))

(defn- handle-tx-mention
  [tx-data]
  (-> (d/transact conn tx-data)
      pp-str))

(defn- handle-query-mention
  [query-data]
  (-> query-data
      (merge {:args [(d/db conn)]})
      d/q
      pp-str))

(defn- handle-pull-mention
  [{:keys [selector entity] :as pull-data}]
  (let [db (d/db conn)
        pull-result (d/pull db selector entity)]
    (pp-str pull-result)))

(defn- sanitize-message-text
  [text]
  (-> text
      (string/replace #"<@[^>]*>" "")  ;; remove the mention (i.e. @datbot)
      (string/replace #"[“”]" "\"")    ;; normalize quotations
      string/trim))

(defn handle-bot-mention
  [{:keys [text channel] :as message}]
  (if (empty? text)
    (send-message channel usage)
    (try
      (let [sanitized-text (sanitize-message-text text)
            parsed (edn/read-string sanitized-text)
            conformed (conform! ::mention parsed)]
        (case (first conformed)
          :tx-mention (send-message channel (-> conformed second handle-tx-mention))
          :query-mention (send-message channel (-> conformed second handle-query-mention))
          :pull-mention (send-message channel (-> conformed second :pull handle-pull-mention)))
        {:conformed (pp-str conformed)})
      (catch Exception e
        (let [{:keys [val] :as data} (ex-data e)]
          (if val
            (send-message channel (-> data pp-str))
            (send-message channel (.getMessage e)))
          (println e))))))

(defn slack-event-handler*
  [{:keys [headers body] :as req}]
  (let [json (read-json body)]
    (if-let [challenge (:challenge json)]
      {:status 200
       :headers content-type-json
       :body (json/write-str {:challenge challenge})}
      {:status 200
       :headers content-type-json
       :body (-> (:event json) handle-bot-mention json/write-str)})))

(def slack-event-handler
  (apigw/ionize slack-event-handler*))

(comment

  (send-message "datbot-testing" "Hello!")

  ;; challenge test
  (slack-event-handler* {:headers {:content-type "application/json"}
                         :body (-> (io/resource "fixtures/challenge-body.json")
                                   (io/input-stream))})

  ;; bot mention tests
  (slack-event-handler* {:headers {:content-type "application/json"}
                         :body (-> (io/resource "fixtures/tx-mention.json")
                                   (io/input-stream))})

  (slack-event-handler* {:headers {:content-type "application/json"}
                         :body (-> (io/resource "fixtures/query-mention.json")
                                   (io/input-stream))})

  (slack-event-handler* {:headers {:content-type "application/json"}
                         :body (-> (io/resource "fixtures/pull-mention.json")
                                   (io/input-stream))})

  (slack-event-handler* {:headers {:content-type "application/json"}
                         :body (-> (io/resource "fixtures/empty-mention.json")
                                   (io/input-stream))})

  (remove-mentioned-user "<@calvin> Testing")

  ;; some sample schemas
  (d/transact conn {:tx-data [{:db/ident :seesaw/project-name
                               :db/valueType :db.type/string
                               :db/cardinality :db.cardinality/one
                               :db/unique :db.unique/identity
                               :db/doc "Names of projects at SeeSaw Labs"}]})

  )

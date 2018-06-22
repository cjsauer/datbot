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

(defn- remove-mentioned-user
  [text]
  (string/trim (string/replace text #"<@[^>]*>" "")))

(s/def ::tx-data coll?)
(s/def ::query coll?)
(s/def ::tx-mention (s/keys :req-un [::tx-data]))
(s/def ::query-mention (s/keys :req-un [::query]))
(s/def ::mention (s/or :tx-mention ::tx-mention
                       :query-mention ::query-mention))

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

(defn handle-bot-mention
  [{:keys [text channel] :as message}]
  (try
    (let [sanitized-text (remove-mentioned-user text)
          parsed (edn/read-string sanitized-text)
          conformed (conform! ::mention parsed)]
      (case (first conformed)
        :tx-mention (send-message channel (-> conformed second handle-tx-mention))
        :query-mention (send-message channel (-> conformed second handle-query-mention)))
      {:response (pp-str parsed)})
    (catch Exception e
      (send-message channel (-> e ex-data pp-str))
      {::anomoly ::anom/incorrect
       :exception (ex-data e)})))

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

  ;; bot mention test
  (slack-event-handler* {:headers {:content-type "application/json"}
                         :body (-> (io/resource "fixtures/mention-body.json")
                                   (io/input-stream))})

  (remove-mentioned-user "<@calvin> Testing")

  )

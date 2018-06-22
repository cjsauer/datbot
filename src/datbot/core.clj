(ns datbot.core
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]
            [datomic.ion.lambda.api-gateway :as apigw]
            [clojure.data.json :as json]))

(def config
  (-> (io/resource "config.edn")
      slurp
      read-string))

(def slack-api-base-url "https://slack.com/api/%s")
(def slack-api-post-message-url (format slack-api-base-url "chat.postMessage"))

(defn send-message
  [channel message]
  (http/post slack-api-post-message-url
             {:body (json/write-str {:channel channel :text message})
              :content-type :json
              :accept :json
              :oauth-token "xoxb-4412666713-386381976739-85dPA73sGtkZF2eegytkuSKw"}))

(def content-type-json {"Content-Type" "application/json"})
(defn read-json
  [input-stream]
  (-> input-stream io/reader (json/read :key-fn keyword)))

(defn bot-receive-message*
  [{:keys [headers body] :as req}]
  (let [json (read-json body)]
    (if-let [challenge (:challenge json)]
      {:status 200
       :headers content-type-json
       :body (json/write-str {:challenge challenge})}
      {:status 200
       :headers content-type-json})))

(def bot-receive-message
  (apigw/ionize bot-receive-message*))

(comment

  (send-message "datbot-testing" "Hello!")

  ;; challenge test
  (bot-receive-message* {:headers {:content-type "application/json"}
                         :body (-> (io/resource "fixtures/challenge-body.json")
                                   (io/input-stream))})

  )

(ns datbot.core
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]
            [datomic.ion.lambda.api-gateway :as apigw]
            [cheshire.core :as json]))

(def config
  (-> (io/resource "config.edn")
      slurp
      read-string))

(def slack-api-base-url "https://slack.com/api/%s")
(def slack-api-post-message-url (format slack-api-base-url "chat.postMessage"))

(defn send-message
  [channel message]
  (http/post slack-api-post-message-url
             {:body (json/generate-string {:channel channel :text message})
              :content-type :json
              :accept :json
              :oauth-token "xoxb-4412666713-386381976739-85dPA73sGtkZF2eegytkuSKw"}))

(def content-type-json {"Content-Type" "application/json"})

(defn bot-receive-message*
  [{:keys [headers body] :as req}]
  (let [json (json/parse-stream body keyword)]
    (if-let [challenge (:challenge json)]
      {:status 200
       :headers content-type-json
       :body (json/generate-string {:challenge challenge})}
      {:status 200
       :headers content-type-json})))

(def bot-receive-message
  (apigw/ionize bot-receive-message*))

(comment

  (send-message "datbot-testing" "Hello!")

  ;; challenge test
  (bot-receive-message* {:headers {:content-type "application/json"}
                         :body (-> (io/resource "fixtures/challenge-body.json")
                                   (io/reader))})

  )

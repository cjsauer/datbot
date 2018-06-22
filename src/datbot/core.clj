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

(defn bot-receive-message*
  [{:keys [headers body] :as req}]
  (if-let [challenge (some-> body (json/read-str :key-fn keyword) :challenge)]
    {:status 200
     :headers content-type-json
     :body (json/write-str {:challenge challenge})}
    {:status 200
     :headers content-type-json}))

(def bot-receive-message
  (apigw/ionize bot-receive-message*))

(comment

  (send-message "datbot-testing" "Hello!")

  (bot-receive-message* {:headers {:content-type "application/json"}
                         :body (json/write-str {:challenge "TEST-CHALLENGE"})})

  )

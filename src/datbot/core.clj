(ns datbot.core
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]
            [datomic.ion.lambda.api-gateway :as apigw]))

(def config
  (-> (io/resource "config.edn")
      slurp
      read-string))

(def slack-api-base-url "https://slack.com/api/%s")
(def slack-api-post-message-url (format slack-api-base-url "chat.postMessage"))

(defn send-message
  [channel message]
  (http/post slack-api-post-message-url
             {:body (format "{\"channel\": \"%s\", \"text\": \"%s\"}" channel message)
              :content-type :json
              :accept :json
              :oauth-token "xoxb-4412666713-386381976739-85dPA73sGtkZF2eegytkuSKw"}))

(defn bot-receive-message*
  [{:keys [headers body] :as req}]
  (prn req)
  {:status 200
   :headers {"Content-Type" (:content-type headers)}
   :body body})

(def bot-receive-message
  (apigw/ionize bot-receive-message*))

(comment

  (send-message "datbot-testing" "Hello!")

  (bot-receive-message* {:headers {:content-type "application/json"}
                         :body "{\"test\": \"Whoop!\"}"})


  )

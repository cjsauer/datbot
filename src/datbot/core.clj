(ns datbot.core
  (:require [clojure.java.io :as io]
            [clj-http.client :as http]))

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

(comment

  (send-message "datbot-testing" "Hello!")

  )

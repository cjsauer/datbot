(ns datbot.core
  (:require [clojure.java.io :as io]
            [clj-http.client :as http]))

(def config
  (-> (io/resource "config.edn")
      slurp
      read-string))

(defn send-message
  [message]
  (http/post (:slack-webhook-url config)
             {:body (format "{\"text\": \"%s\"}" message)
              :headers {"Content-Type" "application/json"}
              :accept :json}))

(comment

  (send-message "Hello!")

  )

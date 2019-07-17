(ns gcp-utils.pubsub
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]])
  (:import (com.google.cloud ServiceOptions)
           (java.util.concurrent TimeUnit)
           (com.google.pubsub.v1 ProjectSubscriptionName PushConfig ProjectTopicName)
           (com.google.cloud.pubsub.v1 Subscriber MessageReceiver AckReplyConsumer SubscriptionAdminSettings SubscriptionAdminClient)
           (com.google.api.core ApiService$Listener)
           (java.util.concurrent Executors)))

(defn create-api-service-listener [topic]
  (proxy [ApiService$Listener] []
    (failed [from failure]
      (println "Subscriber failed" (.getMessage failure)))
    (terminated [from]
      (println "Subscriber terminated"))
    (starting []
      (println "Subscriber starting"))
    (running []
      (println "Tailing topic" topic))
    (stopping [from]
      (println "Subscriber stopping"))))

(defn tail [{:keys [topic print-metadata]}]
  (let [subscription-admin (-> (SubscriptionAdminSettings/newBuilder)
                               (.build)
                               (SubscriptionAdminClient/create))
        project (ServiceOptions/getDefaultProjectId)
        subscription-id (str/join "-" (remove nil? ["gcp-util" (System/getenv "USER") (System/currentTimeMillis)]))
        project-subscription (ProjectSubscriptionName/of project subscription-id)
        _ (.createSubscription subscription-admin
                                          (ProjectSubscriptionName/of project subscription-id)
                                          (ProjectTopicName/of project topic)
                                          (.build (PushConfig/newBuilder))
                                          (Integer/valueOf 30))
        _ (println "Created subscription" (.toString project-subscription))
        message-receiver (reify MessageReceiver
                           (receiveMessage [this message ack-reply-consumer]
                             (-> message
                                 (.getData)
                                 (.toStringUtf8)
                                 (println))
                             (when print-metadata
                               (let [attributes (.getAttributesMap message)]
                                 (when (seq attributes)
                                   (println (str "  metadata: " (pr-str (into {} attributes)))))))
                             (.ack ack-reply-consumer)))
        subscriber (-> (Subscriber/newBuilder project-subscription message-receiver)
                       (.build))]
    (.addShutdownHook (Runtime/getRuntime) (Thread. (fn []
                                                      (-> subscriber
                                                          (.stopAsync)
                                                          (.awaitTerminated 10 TimeUnit/SECONDS))
                                                      (.deleteSubscription subscription-admin (.toString project-subscription))
                                                      (println "Deleted subscription" (.toString project-subscription)))))
    (.addListener subscriber
                  (create-api-service-listener topic)
                  (Executors/newSingleThreadExecutor))
    (-> subscriber
        (.startAsync)
        (.awaitRunning 10 TimeUnit/SECONDS))))

(def cli-options
  [["-t" "--topic TOPIC" "Topic name"]
   ["-m" "--print-metadata" "Print metadata"]])

(defn main [args]
  (let [{:keys [options arguments errors] :as opts} (parse-opts args cli-options)]
    (cond
      errors (do
               (println (str "The following errors occurred while parsing your command:\n\n"
                             (str/join \newline errors)))
               (System/exit 1))
      (empty? arguments) (do
                           (println "Missing pubsub command")
                           (System/exit 1))
      :else (case (first arguments)
              "tail" (tail options)
              (println "Unknown pubsub command:" (first arguments))))))

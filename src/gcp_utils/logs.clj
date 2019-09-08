(ns gcp-utils.logs
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as str])
  (:import (com.google.cloud.logging LoggingOptions Logging$EntryListOption Payload$JsonPayload Payload$ProtoPayload Payload$StringPayload)
           (java.time Instant ZoneId format.DateTimeFormatter temporal.ChronoUnit Duration)
           (com.google.cloud.audit AuditLog)
           (com.google.protobuf.util JsonFormat)))

(def timestamp-formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:MM:ss"))
(def system-default-zone (ZoneId/systemDefault))

(def cli-options
  [["-f" "--filter FILTER" "Log filter to apply, e.g. resource.type=gce_instance"]
   ["-u" "--user-labels" "Print user defined labels"]
   ["-r" "--resource-labels" "Print resource labels"]
   ["-s" "--start FROM" "Start from the given amount in past, e.g. -s 2h => 2 hours ago. Supports: m (minutes), h (hours), d (days)"
    :parse-fn #(let [[_ amount unit] (re-find #"(\d+)(m|h|d)" %)]
                 (Duration/of (Long/parseLong amount)
                              (case unit
                                "m" ChronoUnit/MINUTES
                                "h" ChronoUnit/HOURS
                                "d" ChronoUnit/DAYS)))]])

(defn entry-list-options [{:keys [page-size timestamp filter]}]
  (let [filter-string (cond-> (format "timestamp > \"%s\""
                                      (.format DateTimeFormatter/ISO_INSTANT timestamp))
                        filter (str " AND (" filter ")"))
        opts [(Logging$EntryListOption/pageSize page-size)
              (Logging$EntryListOption/filter filter-string)]]
    (into-array Logging$EntryListOption opts)))

(def logging (.getService (LoggingOptions/getDefaultInstance)))

(defn poll [opts]
  (.listLogEntries logging (entry-list-options opts)))

(defn format-timestamp [millis]
  (.format timestamp-formatter (-> millis
                                   (Instant/ofEpochMilli)
                                   (.atZone system-default-zone))))

(defn format-proto-payload [payload]
  (condp = (-> payload (.getData) (.getTypeUrl))
    "type.googleapis.com/google.cloud.audit.AuditLog"
    (let [audit-log (AuditLog/parseFrom (-> payload (.getData) (.getValue)))]
      (str (.getServiceName audit-log)
           " "
           (.getMethodName audit-log)
           " "
           (.toString (.getServiceData audit-log))))
    (.toString payload)))

(def ptofobuf-json-printer (JsonFormat/printer))

(defn format-json-payload [payload]
  (let [fields-map (-> payload (.getData) (.getFieldsMap))]
    (if-let [message (or (get fields-map "message")
                         (get fields-map "MESSAGE"))]
      (.getStringValue message)
      (.print ptofobuf-json-printer (.getData payload)))))

(defprotocol FormatPayload
  (format-payload [this]))

(extend-protocol FormatPayload
  Payload$ProtoPayload
  (format-payload [this]
    (format-proto-payload this))
  Payload$JsonPayload
  (format-payload [this]
    (format-json-payload this))
  Payload$StringPayload
  (format-payload [this]
    (.getData this))
  nil
  (format-payload [this]
    ""))

(defn format-labels-map [m]
  (str/join ","
            (for [e m]
              (str (key e) "=" (val e)))))

(defn print-log-entry [entry {:keys [user-labels resource-labels]}]
  (let [line (str (format-timestamp (.getTimestamp entry))
                  " "
                  (when user-labels
                    (str (format-labels-map (.getLabels entry))
                         " "))
                  (when resource-labels
                    (str (format-labels-map (-> entry (.getResource) (.getLabels)))
                         " "))
                  (format-payload (.getPayload entry)))
        last-char (.charAt line (dec (.length line)))]
    (if (= \newline last-char)
      (print line)
      (println line))
    (flush)))

(defn by-timestamp-insert-id [a b]
  (compare [(first a) (second a)]
           [(first b) (second b)]))

(defn make-sort-key [entry]
  [(.getTimestamp entry) (.getInsertId entry)])

(defn make-last-seen-insert-ids-by-timestamp [last-seen-insert-ids-by-timestamp page]
  (into (sorted-map-by by-timestamp-insert-id) (take-last 1000 (into last-seen-insert-ids-by-timestamp
                                                                     (map (juxt make-sort-key #(.getInsertId %)))
                                                                     (.getValues page)))))

(defn tail [{:keys [filter start] :as opts}]
  (let [page-size 100
        start-timestamp (cond-> (Instant/now)
                          start (.minus start))]
    (loop [page (poll {:page-size page-size
                       :timestamp start-timestamp
                       :filter filter})
           last-seen-insert-ids-by-timestamp (sorted-map-by by-timestamp-insert-id)]
      (let [last-seen-insert-ids (into #{} (vals last-seen-insert-ids-by-timestamp))]
        (doseq [entry (.getValues page)
                :when (not (contains? last-seen-insert-ids (.getInsertId entry)))]
          (print-log-entry entry opts)))
      (when-not (.isInterrupted (Thread/currentThread))
        (if-let [next-page (.getNextPage page)]
          (recur next-page (make-last-seen-insert-ids-by-timestamp last-seen-insert-ids-by-timestamp page))
          (do
            (Thread/sleep 1000)
            (recur (poll {:page-size page-size
                          :timestamp (if-let [last-entry (last (.getValues page))]
                                       (Instant/ofEpochMilli (.getTimestamp last-entry))
                                       start-timestamp)
                          :filter filter})
                   (make-last-seen-insert-ids-by-timestamp last-seen-insert-ids-by-timestamp page))))))))

(defn main [args]
  (let [{:keys [options _arguments errors] :as _opts} (parse-opts args cli-options)]
    (cond
      errors (do
               (println (str "The following errors occurred while parsing your command:\n\n"
                             (str/join \newline errors)))
               (System/exit 1))
      :else (tail options))))

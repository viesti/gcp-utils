(ns gcp-utils.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [gcp-utils.pubsub :as pubsub]
            [clojure.string :as str]))

(def cli-options
  [["-h" "--help"]])

(defn usage [options-summary commands-summary]
  (->> ["Google Cloud utilities"
        ""
        "Usage: clj -m gcp-utils.core [options] <command> [command-options]"
        ""
        "Options"
        options-summary
        ""
        "Available commands:"
        commands-summary]
       (str/join \newline)
       println))

(defn -main [& args]
  (let [{:keys [options arguments summary errors]} (parse-opts args cli-options :in-order true)]
    (cond
      (or (:help options)
          (empty? arguments))
      (do
        (usage summary (->> [""
                             "pubsub"
                             (:summary (parse-opts args pubsub/cli-options))
                             ""]
                            (str/join \newline)))
        (System/exit (if (and (empty? arguments) (not (:help options))) 1 0)))

      errors
      (do
        (println (str "The following errors occurred while parsing your command:\n\n"
                      (str/join \newline errors)))
        (System/exit 1)))
    (case (first arguments)
      "pubsub" (pubsub/main (rest args))
      (println "Unknown command:" (first arguments)))))

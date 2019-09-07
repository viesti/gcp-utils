# Google Cloud utilities

```shell
Usage: clj -m gcp-utils.core [options] <command> [command-options]

Options
  -h, --help

Available commands:

pubsub
  -t, --topic TOPIC     Topic name
  -m, --print-metadata  Print metadata

logs
  -f, --filter FILTER    Log filter to apply, e.g. resource.type=gce_instance
  -u, --user-labels      Print user defined labels
  -r, --resource-labels  Print resource labels
  -s, --start FROM       Start from the given amount in past, e.g. -s 2h => 2 hours ago. Supports: m (minutes), h (hours), d (days)
```

## Examples:

### Use via inline deps

```clojure
$> export GOOGLE_APPLICATION_CREDENTIALS=path/to/account.json
$> clj -Sdeps '{:deps {gcp-utils {:git/url "https://github.com/viesti/gcp-utils" :sha "b13a92c5bdac124e224e835586166e1d6c40733c"}}}' -m gcp-utils.core -h
Google Cloud utilities

Usage: clj -m gcp-utils.core [options] <command> [command-options]
...
```

### Tailing Stackdriver logs

```shell
$> export GOOGLE_APPLICATION_CREDENTIALS=path/to/account.json
$> clj -m gcp-utils.core logs \
       --filter "resource.type=cloud_function OR resource.type=cloud_run_revision" \
       -r
2019-09-07 19:09:09 location=us-central1,project_id=tiuhti,service_name=run-app,revision_name=run-app-eb822eba-f771-41eb-810f-c10b30988770,configuration_name=run-app
2019-09-07 19:09:18 region=europe-west1,project_id=tiuhti,function_name=my-fun Function execution started
2019-09-07 19:09:18 region=europe-west1,project_id=tiuhti,function_name=my-fun Hello from Cloud Function!
2019-09-07 19:09:19 region=europe-west1,project_id=tiuhti,function_name=my-fun Function execution took 9 ms, finished with status code: 200
```

### Tailing Pubsub topic

```shell
$> export GOOGLE_APPLICATION_CREDENTIALS=path/to/account.json
$> clj -m gcp-utils.core pubsub tail -t input -m
Created subscription projects/tiuhti/subscriptions/gcp-util-kimmoko-1563392747139
Subscriber starting
Tailing topic input
hello
  metadata: {"ts" "2019-07-09T14:43:01Z"}
again
  metadata: {"ts" "2019-07-09T14:43:01Z"}
Subscriber stopping
Subscriber terminated
Deleted subscription projects/tiuhti/subscriptions/gcp-util-kimmoko-1563392747139
```

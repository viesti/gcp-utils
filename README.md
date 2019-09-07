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
  -f, --filter FILTER  Log filter to apply, e.g. resource.type=gce_instance
  -l, --labels         Print log entry labels
```

## Example:

```shell
$> GOOGLE_APPLICATION_CREDENTIALS=path/to/account.json clj -m gcp-utils.core pubsub tail -t input -m
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

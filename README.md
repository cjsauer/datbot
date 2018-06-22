# datbot

Slack bot for interfacing with a Datomic Cloud instances.

# Development

Run the Datomic SOCKS proxy:

_(Requires having `autossh` installed.)_

```
# Install autossh
sudo apt-get install autossh

./bin/socks
```

If you're using Cider, start the REPL:

```
./bin/cider-repl
```

Then you can use cider connect.

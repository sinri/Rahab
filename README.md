# Rahab

> By faith the harlot Rahab perished not with them that believed not, when she had received the spies with peace. (Hebrews 11:31)

## Design

```
In the days of Joshua,
King of Jericho did not want to accept his fate of extinction,
He relied on the wall of the city.

However, the army of the Lord, sent two spies into the city,
They were protected by a woman there called Rahab,
Eventually completed their mission.

The wall seemed high and steady,
But the will of the Lord is higher than all humans' thought,
He prepared two sides, while the wall in between.
```

Commonly, many platform enterprises learnt the method of monopoly to gather more profits.
They build walls and only permit users to buy services inside, and limit their want.
Amongst them are the Heavenly Cats, Capital Easterners and Join More and More. 
They called their area inside walls tower, wok or cloud.
Browsers or servers on Internet would not be able to access the service provided by the Giant Heads.

You need to...

```
Browser / Server --|INTERNET/INTRANET|--> Rahab Local              --|WALL|--> Rahab Remote      --|INTRANET|--> Service
↑                                         ↑                                    ↑                                 ↑
Call service.com                          Call encoded to remote               Decode and call to service        Monopoly Job
```

## Usage

```
Usage: Rahab [-c <value>] [-h] [-p <value>] [-m <value>] [-s <value>] [-r
       <value>] [-e <value>] [-v]

Tear the wall of Jericho down!

Options and Arguments:
 -c,--config-file <value>      Config YAML File ← /app/config/rahab-remote.yml by default, if not exists, check other arguments
 -h,--help                     Help and Usages
 -p,--listen-port <value>      Listen Port
 -m,--mode <value>             Running Mode: LOCAL / REMOTE
 -s,--pool-size <value>        Pool Size
 -r,--remote-address <value>   Needed in LOCAL mode, IP or domain
 -e,--remote-port <value>      Needed in LOCAL mode, port
 -v,--use-vertx-log-only       Only use vertx standard logging ← by default, use RahabLogger for special log format

```

### Request Side

Add ip of local side deployment to `/etc/hosts` for the domain of target service api.

### Local Side

Listen for domain `local.rahab.de` (or other)

Config file as `/app/config/rahab-local.yml` (or other path)

```yaml
# Rahab - Local
pool-size: 10
listen-port: 8001
mode: LOCAL
# For LOCAL
remote-address: remote.rahab.de
remote-port: 8002
```

Command: `java -jar Rahab.jar -c /app/config/rahab-local.yml`

Equals to: `java -jar Rahab.jar -p 8001 -m LOCAL -s 10 -r remote.rahab.de -e 8002`

### Remote Side

Listen for domain `remote.rahab.de` (or other)

Config file as `/app/config/rahab-remote.yml` (or other path)

```yaml
# Rahab - Remote
pool-size: 10
listen-port: 8002
mode: REMOTE
```

Command: `java -jar Rahab.jar -c /app/config/rahab-remote.yml`

Equals to: `java -jar Rahab.jar -p 8002 -m REMOTE -s 10`

The target service api would be called here to real service provider.
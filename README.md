# Rahab

> By faith the harlot Rahab perished not with them that believed not, when she had received the spies with peace. (Hebrews 11:31)

## Usage of Rahab Version 2.1

```
Usage: java -jar Rahab-2.1.1.jar [-h] [--LiaisonBrokerHost <value>]
       [--LiaisonBrokerPort <value>] [--LiaisonSourceWorker {wormhole}]
       [--LiaisonSourceWorkerWormholeHost <value>]
       [--LiaisonSourceWorkerWormholePort <value>] --mode {Wormhole, HttpProxy,
       LiaisonBroker, LiaisonSource} --port <value> [--WormholeDestinationHost
       <value>] [--WormholeDestinationPort <value>] [--WormholeName <value>]
       [--WormholeTransformer {HttpClient, HttpServer}]
       [--WormholeTransformerHttpFakeHost <value>]

Rahab 2.1.1 启动命令

Options and Arguments:
 -h,--help                                                       获取命令帮助
    --LiaisonBrokerHost <value>                                  情报源使用的掮客服务的远程地址
    --LiaisonBrokerPort <value>                                  情报源使用的掮客服务的远程端口
    --LiaisonSourceWorker {wormhole}                             情报源的工作方式
    --LiaisonSourceWorkerWormholeHost <value>                    情报源使用的掮客服务的远程地址
    --LiaisonSourceWorkerWormholePort <value>                    情报源使用的掮客服务的远程端口
    --mode {Wormhole, HttpProxy, LiaisonBroker, LiaisonSource}   运行模式
    --port <value>                                               使用的端口
    --WormholeDestinationHost <value>                            作为虫洞运行时使用的远程地址
    --WormholeDestinationPort <value>                            作为虫洞运行时使用的远程端口
    --WormholeName <value>                                       虫洞的名称
    --WormholeTransformer {HttpClient, HttpServer}               作为虫洞运行时使用的数据混淆器
    --WormholeTransformerHttpFakeHost <value>
                                                                 作为虫洞运行时,使用数据混淆器
                                                                 【HttpClient】时指定
                                                                 一个伪装的域名

===== SAMPLE =====
# Run as HTTP Proxy, listen on port 1080
java -jar Rahab-2.1.1.jar --mode HttpProxy --port 1080
# Run as Wormhole, listen on port 1081, destination is 127.0.0.1:1080, data without transform
java -jar Rahab-2.1.1.jar --mode Wormhole --port [1081] --WormholeName [SAMPLE] --WormholeDestinationHost [127.0.0.1] --WormholeDestinationPort [1080]
# Run as Wormhole, listen on port 1081, destination is 127.0.0.1:1080, use transformer HttpServer
java -jar Rahab-2.1.1.jar --mode Wormhole --port [1081] --WormholeName [SAMPLE] --WormholeDestinationHost [127.0.0.1] --WormholeDestinationPort [1080] --WormholeTransformer HttpServer
# Run as Wormhole, listen on port 1081, destination is 127.0.0.1:1080, use transformer HttpClient with fake host api.com
java -jar Rahab-2.1.1.jar --mode Wormhole --port [1081] --WormholeName [SAMPLE] --WormholeDestinationHost [127.0.0.1] --WormholeDestinationPort [1080] --WormholeTransformer HttpClient --WormholeTransformerHttpFakeHost api.com
# Run as LiaisonBroker, listen on port 1082
java -jar Rahab-2.1.1.jar --mode LiaisonBroker --port 1082
# Run as LiaisonSource, target broker is 127.0.0.1:1082, with worker as Wormhole (destination is 192.168.0.1:1080)
java -jar Rahab-2.1.1.jar --mode LiaisonSource --LiaisonBrokerHost 127.0.0.1 --LiaisonBrokerPort 1082 --LiaisonSourceWorker wormhole --LiaisonSourceWorkerWormholeHost 192.168.0.1 --LiaisonSourceWorkerWormholePort 1080
```
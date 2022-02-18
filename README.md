# Rahab

> By faith the harlot Rahab perished not with them that believed not, when she had received the spies with peace. (Hebrews 11:31)

## Usage of Rahab Version 2.1

```
Usage: RahabV2 [-h] [--LiaisonBrokerHost <value>] [--LiaisonBrokerPort <value>]
       [--LiaisonSourceWorker {wormhole}] 
       [--LiaisonSourceWorkerWormholeHost <value>] 
       [--LiaisonSourceWorkerWormholeport <value>] 
       --mode {Wormhole, RahabLiaisonBroker, HttpProxy, RahabLiaisonSource} --port <value>
       [--WormholeDestinationHost <value>] [--WormholeDestinationPort <value>]
       [--WormholeName <value>] [--WormholeTransformer {HttpClient, HttpServer}]
       [--WormholeTransformerHttpFakeHost <value>]

Rahab 2.1 启动命令

Options and Arguments:
    -h,--help
        获取命令帮助
    --LiaisonBrokerHost <value>
        情报源使用的掮客服务的远程地址
    --LiaisonBrokerPort <value>
        情报源使用的掮客服务的远程端口
    --LiaisonSourceWorker {wormhole}
        情报源的工作方式
    --LiaisonSourceWorkerWormholeHost <value>
        情报源使用的掮客服务的远程地址
    --LiaisonSourceWorkerWormholeport <value>
        情报源使用的掮客服务的远程端口
    --mode {Wormhole, RahabLiaisonBroker, HttpProxy, RahabLiaisonSource}   
        运行模式
    --port <value>                                                         
        使用的端口
    --WormholeDestinationHost <value>
        作为虫洞运行时使用的远程地址
    --WormholeDestinationPort <value>
        作为虫洞运行时使用的远程端口
    --WormholeName <value>                                                 
        虫洞的名称
    --WormholeTransformer {HttpClient, HttpServer}
        作为虫洞运行时使用的数据混淆器
    --WormholeTransformerHttpFakeHost <value>
        作为虫洞运行时,使用数据混淆器【HttpClient】时指定一个伪装的域名

```
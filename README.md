# Rahab

> By faith the harlot Rahab perished not with them that believed not, when she had received the spies with peace. (Hebrews 11:31)

Run as `java -jar Rahab-2.2.0.jar -h`

## Design

### Wormhole

| CLIENT            | socket | WORMHOLE                  | socket | SERVER             |
|-------------------|--------|---------------------------|--------|--------------------|
| raw request data  | →      | Transform with method A ↓ | &nbsp; | &nbsp;             |
| &nbsp;            | &nbsp; | packaged request data     | →      | Handle by SERVER ↓ |
| &nbsp;            | &nbsp; | Transform with method B ↓ | ←      | raw response data  |
| raw response data | ←      | packaged response data    | &nbsp; | &nbsp;             |

The above is the basic of a wormhole as a simple TCP proxy.

Wormhole might be concatenated.

#### Liaison (when Worker as Wormhole)

| CLIENT           | socket | BROKER                                       | socket | SOURCE                                       | socket | SERVER             |
|------------------|--------|----------------------------------------------|--------|----------------------------------------------|--------|--------------------|
| &nbsp;           | &nbsp; | Register Source ○                            | ←      | Source Register Data                         | &nbsp; | &nbsp;             |
| Raw Request Data | →      | Package Data and seek Source Socket ↓        | &nbsp; | &nbsp;                                       | &nbsp; | &nbsp;             |
| &nbsp;           | &nbsp; | Packaged Data                                | →      | Parsed Data to Client and Raw Request Data ↓ | &nbsp; | &nbsp;             |
| &nbsp;           | &nbsp; | &nbsp;                                       | &nbsp; | Raw Request Data                             | →      | Handle by Server ↓ |
| &nbsp;           | &nbsp; | &nbsp;                                       | &nbsp; | Package Data with Client ↓                   | ←      | Raw Response Data  |
| &nbsp;           | &nbsp; | Parse Data to Client and Raw Response Data ↓ | ←      | Packaged Data                                | &nbsp; | &nbsp;             |
| Handle by Client | ←      | Raw Response Data                            | &nbsp; | &nbsp;                                       | &nbsp; | &nbsp;             | 

If the SOURCE is not reachable by outsiders (in a private network), let the CLIENT communicate with it through a BROKER.

### HTTP Proxy

A proxy implementation
of [HTTP Tunneling](https://developer.mozilla.org/zh-CN/docs/Web/HTTP/Proxy_servers_and_tunneling) using CONNECT defined
in [HTTP/1.1](https://www.ietf.org/rfc/rfc2068.txt).

### Socks5 Proxy

An incomplete implementation of [SOCKS Protocol Version 5](https://datatracker.ietf.org/doc/html/rfc1928), for TCP and
CONNECT only.
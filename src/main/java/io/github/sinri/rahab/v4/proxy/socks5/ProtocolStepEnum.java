package io.github.sinri.rahab.v4.proxy.socks5;

public enum ProtocolStepEnum {
    STEP_1_CONFIRM_METHOD,// client -[VER | NM | Ms]-> server -[VER | M]-> client
    STEP_2_AUTH_METHOD,
    STEP_3_CONFIRM_DEST,// client -[VER | CMD |  RSV  | ATYP | DST.ADDR | DST.PORT ]-> server -[VER | REP |  RSV  | ATYP | BND.ADDR | BND.PORT]-> client
    STEP_4_TRANSFER // transfer data
}

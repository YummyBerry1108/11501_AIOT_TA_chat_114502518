package com.example.chatclient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Server 與 Client 之間每一行文字都是一個 JSON 物件（一行一則，靠 println / readLine 切開）。
 *
 * Client -> Server
 *   {"type":"join",  "name":"Client1"}      連線後第一則：報上名字
 *   {"type":"msg",   "text":"Hi"}           聊天訊息
 *   {"type":"leave"}                         正常離開
 *
 * Server -> Client
 *   {"type":"chat",   "name":"Client1", "text":"Hi"}   某人說話
 *   {"type":"system", "text":"Welcome Client1 join us!"} 系統通知（加入 / 離開）
 *   {"type":"users",  "list":["Server","Client1"]}      目前在線名單
 *   {"type":"close",  "text":"..."}                      Server 關閉
 */
final class Protocol {
    private Protocol() {}

    static final int PORT = 6100;

    static final String JOIN = "join";
    static final String MSG = "msg";
    static final String LEAVE = "leave";
    static final String CHAT = "chat";
    static final String SYSTEM = "system";
    static final String USERS = "users";
    static final String CLOSE = "close";

    static String chat(String name, String text) {
        return obj(CHAT).put2("name", name).put2("text", text).toString();
    }

    static String system(String text) {
        return obj(SYSTEM).put2("text", text).toString();
    }

    static String close(String text) {
        return obj(CLOSE).put2("text", text).toString();
    }

    static String users(List<String> names) {
        return obj(USERS).put2("list", new JSONArray(names)).toString();
    }

    private static Json obj(String type) {
        return new Json().put2("type", type);
    }

    /** JSONObject 的 put 會丟 checked exception，包一層方便串接 */
    static final class Json extends JSONObject {
        Json() {}

        Json(String s) throws JSONException {
            super(s);
        }

        Json put2(String k, Object v) {
            try {
                put(k, v);
            } catch (JSONException e) {
                throw new IllegalArgumentException(e);
            }
            return this;
        }
    }
}

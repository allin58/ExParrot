package com.messager.allin.exparrot.messagerAPI;

import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

public interface Layer {
    boolean handShakeWithServer() throws Exception;
    void handShakeWithUser(String username)throws Exception;
    void sendMSG(String msg, String receiver)throws Exception;
    ArrayList getAllUsers()throws Exception;
    Map check()throws Exception ;




}

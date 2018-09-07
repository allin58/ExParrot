package com.example.a1.myapplication.messagerAPI;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class HSWS {


    public String tlabel;      // метка для сервера

    String username;
    String server;
    String url;
    URL obj;
    HttpURLConnection connection;

    BufferedReader in;

    public void setUrl(String url) throws Exception{
        this.url = url;
        obj = new URL(url);
        connection = (HttpURLConnection) obj.openConnection();
        connection.setRequestMethod("GET");


        }



    public void setServer(String server) throws Exception{
       this.server=server;


    }
    public void setUserName(String username) throws Exception{
        this.username=username;


    }




    public JSONObject doGet()throws Exception{


            in = new BufferedReader(new InputStreamReader(connection.getInputStream()));       // тут происходит запрос



        String inputLine;
            StringBuffer response = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            JSONParser jsonParser = new JSONParser();
            JSONObject jsonObject = (JSONObject) jsonParser.parse(response.toString());
            return jsonObject;
        }

    public byte[] handShake()throws Exception{
            byte[] key;
            ECDH user_server_dh =new ECDH();
            String myPubKey = user_server_dh.bytesToHex(user_server_dh.dataPubA);
            String hsws_request = server+"/hsws"+"?username="+username+"&pubk="+myPubKey;
            setUrl(hsws_request);
           // String server_pub = (String)doGet().get("pubk");
            JSONObject jobj =doGet();
            String server_pub =(String)jobj.get("pubk");
            tlabel =(String)jobj.get("label");



                key= user_server_dh.doECDH(user_server_dh.dataPrvA,user_server_dh.parseHexBinary(server_pub));
                return key;


        }

    public JSONObject check(String c_label)throws Exception{

        String request = server+"/check"+"?c_label="+c_label;
         setUrl(request);
         JSONObject jobj =doGet();
         tlabel =(String)jobj.get("label");
        return jobj;
     }



    public JSONObject users(String c_label)throws Exception{

        String request = server+"/users"+"?c_label="+c_label;
        setUrl(request);
        JSONObject jobj =doGet();
        tlabel =(String)jobj.get("label");
        return jobj;
    }

    public JSONObject handShakeWithUser(String c_label,String counterparty_c,String upk_c)throws Exception{
        String request = server+"/hswu"+"?c_label="+c_label+"&counterparty_c="+counterparty_c+"&upk_c="+upk_c;
        setUrl(request);
        JSONObject jobj =doGet();
        tlabel =(String)jobj.get("label");
        return jobj;

    }



}

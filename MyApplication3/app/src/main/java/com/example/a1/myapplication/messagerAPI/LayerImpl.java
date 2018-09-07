package com.example.a1.myapplication.messagerAPI;

import org.json.simple.JSONObject;

import java.util.*;

public class LayerImpl implements Layer {


    static private  LayerImpl inst;

    String username;
    String server;

    HashMap<String,ECDH> AesMap = new HashMap<String,ECDH>();  // для каждого usera ECDH

    HashMap<String,byte[]> AcceptedUsers = new HashMap<String,byte[]>();


    String label = "";
    HSWS hsws;
    ECDH ecdh;
    CBCAESBouncyCastle AES;

    byte[] DHkey;


    public void setUsername(String username)throws Exception {
        this.username = username;
        hsws.setUserName(username);

    }

    public void setServer(String server) throws Exception{
        this.server = server;
        hsws.setServer(server);

    }


   static public LayerImpl getInst()throws Exception {

        if(inst==null){
            inst=new LayerImpl();
        }

        return inst;
    }




    public LayerImpl()throws Exception{


        hsws = new HSWS();

        ecdh = new ECDH();


        AES = new CBCAESBouncyCastle();


    }

    public boolean  handShakeWithServer()throws Exception {


        DHkey= hsws.handShake();
        AES.setKey(DHkey);
        label=new String( AES.decrypt(ecdh.parseHexBinary(hsws.tlabel)), "UTF-8") ;
        label=label.split(username)[1];
        return true;
    }

    public Map check() throws Exception {



        AES.setKey(DHkey);
        JSONObject jobj=hsws.check(ecdh.bytesToHex (AES.encrypt(label.getBytes())));      // шифруется метка

        label=new String( AES.decrypt(ecdh.parseHexBinary(hsws.tlabel)), "UTF-8") ;
        label=label.split(username)[1];
        Map<String,String> map_c = new HashMap<String, String>();
        Map<String,String> map_d = new HashMap<String, String>();

        Set set = jobj.keySet();

        for (Object o : set) {
           String l=(String)o;
               if (!l.equals("label")){
                map_c.put((String) o,(String)jobj.get(o));
            }
        }

        for(Map.Entry<String, String> entry : map_c.entrySet()) {

            map_d.put( entry.getKey(),new String( AES.decrypt(ecdh.parseHexBinary(entry.getValue())), "UTF-8"));

        }



        if(map_d.size()>0){
          return protokol_cherepaha(map_d);

        }



        return map_d;

    }

    public ArrayList getAllUsers()throws Exception {

        AES.setKey(DHkey);
        JSONObject jobj=hsws.users(ecdh.bytesToHex (AES.encrypt(label.getBytes())));

        label=new String( AES.decrypt(ecdh.parseHexBinary(hsws.tlabel)), "UTF-8") ;
        label=label.split(username)[1];
        Map<String,String> map = new HashMap<String, String>();
        Set set = new HashSet();
        set = jobj.keySet();

        for (Object o : set) {
            String l=(String)o;
            if (!l.equals("label")){
                map.put((String) o,(String)jobj.get(o));
            }
        }

          Set usersSet_c =  map.keySet();
          Set usersSet =  new HashSet<String>();

        String temp;
        for (Object o : usersSet_c) {

            temp=new String( AES.decrypt(ecdh.parseHexBinary((String)o)), "UTF-8");

            usersSet.add(temp);
        }

        usersSet.remove(username);

        ArrayList<String> usersArray = new ArrayList<>();

        for (Object o : usersSet) {
            usersArray.add((String)o);
        }



        return usersArray;

    }

    public void handShakeWithUser2(String username2) throws Exception{




        String DHkeyUSER= AesMap.get(username2).bytesToHex(AesMap.get(username2).dataPubA);
        /* System.out.println(DHkeyUSER);                                                    /////////////////////////*/
        AES.setKey(DHkey);
        JSONObject jobj=hsws.handShakeWithUser(ecdh.bytesToHex (AES.encrypt(label.getBytes())),
                ecdh.bytesToHex (AES.encrypt(username2.getBytes())),
                ecdh.bytesToHex (AES.encrypt(DHkeyUSER.getBytes())));      // передаётся public key

        label=new String( AES.decrypt(ecdh.parseHexBinary(hsws.tlabel)), "UTF-8") ;



        label=label.split(username)[1];


        /*Map<String,String> map = new HashMap<String, String>();
        Set set = new HashSet();
        set = jobj.keySet();*/



    }

    public void handShakeWithUser(String username2) throws Exception{



        ECDH newUser= new ECDH();
        AesMap.put(username2,newUser);


        String DHkeyUSER= AesMap.get(username2).bytesToHex(AesMap.get(username2).dataPubA);
       /* System.out.println(DHkeyUSER);                                                    /////////////////////////*/
        AES.setKey(DHkey);
        JSONObject jobj=hsws.handShakeWithUser(ecdh.bytesToHex (AES.encrypt(label.getBytes())),
                ecdh.bytesToHex (AES.encrypt(username2.getBytes())),
                ecdh.bytesToHex (AES.encrypt(DHkeyUSER.getBytes())));      // передаётся public key



        label=new String( AES.decrypt(ecdh.parseHexBinary(hsws.tlabel)), "UTF-8") ;



        label=label.split(username)[1];


        /*Map<String,String> map = new HashMap<String, String>();
        Set set = new HashSet();
        set = jobj.keySet();*/



    }

    public void sendMSG( String receiver,String msg) throws Exception{
        boolean flag = false;


        for (String s : AcceptedUsers.keySet()) {
           if(s.equals(receiver))
            {flag=true;


            }
        }
           if (!flag){
            handShakeWithUser(receiver);

            Thread.sleep(5000);
            check();
           // System.out.println(AcceptedUsers);
        }




        AES.setKey(AcceptedUsers.get(receiver));

        msg=AesMap.get(receiver).bytesToHex (AES.encrypt(msg.getBytes()));
        AES.setKey(DHkey);
        JSONObject jobj=hsws.handShakeWithUser(ecdh.bytesToHex (AES.encrypt(label.getBytes())),
                ecdh.bytesToHex (AES.encrypt(receiver.getBytes())),
                ecdh.bytesToHex (AES.encrypt(msg.getBytes())));

        label=new String( AES.decrypt(ecdh.parseHexBinary(hsws.tlabel)), "UTF-8") ;



        label=label.split(username)[1];

    }

    public Map protokol_cherepaha(Map<String,String> map_d)throws Exception{
         int flaf = 1;



         String sender = map_d.get("sender");
         String msg = map_d.get("msg");
        map_d.clear();


        if (sender.equals("qweasdfgsergsfgbbgf")){
            flaf=4;
        }



        for (String acceptedUser :  AesMap.keySet()) {
            if (sender.equals(acceptedUser)){
                flaf=2;
            }
           }

        for (String acceptedUser :  AcceptedUsers.keySet()) {
            if (sender.equals(acceptedUser)){
                flaf=3;
            }
        }




     if(flaf==2){   //второй этап обмена ключами (запрос на handshake уже сделан)

         AcceptedUsers.put(sender, AesMap.get(sender).doECDH    (AesMap.get(sender).dataPrvA,    AesMap.get(sender).parseHexBinary(msg)))   ;





     }


        if(flaf==1){   //первый этап обмена ключами (только принял check с pk)

            ECDH newUser= new ECDH();
            AesMap.put(sender,newUser);


            AcceptedUsers.put(sender, AesMap.get(sender).doECDH    (AesMap.get(sender).dataPrvA,    AesMap.get(sender).parseHexBinary(msg)))   ;

            handShakeWithUser2(sender);


        }


        if(flaf==3){

            map_d.put("sender",sender);
            AES.setKey(AcceptedUsers.get(sender));
            map_d.put("msg", new String( AES.decrypt(AesMap.get(sender).parseHexBinary(msg)), "UTF-8"));

        }

        if(flaf==4){

           AesMap .remove(msg);

           AcceptedUsers.remove(msg);

        }



     return map_d;


    }



}

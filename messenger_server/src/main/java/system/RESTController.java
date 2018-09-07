package system;



import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import system.auxiliary.ClientData;
import system.temp.CBCAESBouncyCastle;
import system.temp.ECDH;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.Date;


@RestController
public class RESTController  {

    @Autowired
    ClientData CD;

    ECDH ecdh = new ECDH();
    CBCAESBouncyCastle AES = new CBCAESBouncyCastle();




    @RequestMapping(value = "/hsws", method = RequestMethod.GET)
    public HashMap handShakeWithServer(@RequestParam(value="username", required=true) String username, @RequestParam(value="pubk", required=true) String pubk)throws Exception{

        if (CD.users.keySet().contains(username)) {                     // защита от одинаковых username
            return new HashMap<String, String>();
        }

        HashMap<String,String> map = new HashMap<String, String>();

        synchronized (CD) {
            CD.users.put(username, ecdh.doECDH(ecdh.dataPrvA, ecdh.parseHexBinary(pubk)));  // добовляю нового пользователя
            String uuid = UUID.randomUUID().toString();
            uuid = uuid.replace("-", "");
            CD.labels.put(username, uuid);
            map.put("pubk", ecdh.bytesToHex(ecdh.dataPubA));
            String label = username + CD.labels.get(username);
            AES.setKey(CD.users.get(username));
            map.put("label", ecdh.bytesToHex(AES.encrypt(label.getBytes())));
        }
        return map;
    }

    @RequestMapping(value = "/hswu", method = RequestMethod.GET)
    public HashMap handShakeWithUser(@RequestParam(value="c_label", required=true) String c_label,@RequestParam(value="counterparty_c", required=true) String counterparty_c,@RequestParam(value="upk_c", required=true) String upk_c)throws Exception{
        String username;
        HashMap<String,String> map = takeNewLable(c_label);
        username=map.get("username");
        HashMap<String,String> mapToSend = new HashMap<String, String>();
        mapToSend.put("label",map.get("label"));

        synchronized (CD) {
            AES.setKey(CD.users.get(username));
        }

        String upk=new String( AES.decrypt(ecdh.parseHexBinary(upk_c)), "UTF-8") ;
        String counterparty=new String( AES.decrypt(ecdh.parseHexBinary(counterparty_c)), "UTF-8") ;


        // System.out.println(username+" ostavila dla "+counterparty);///////////////////////////////////////////////////////////

        ArrayList<String> als = new ArrayList<String>();
        als.add(counterparty);
        als.add(username);
        als.add(upk);
        synchronized (CD) {
            CD.hswsAL.add(als);
        }
        /*System.out.println(username);
        System.out.println(counterparty);
        System.out.println(upk);*/

        return mapToSend;
    }

    @RequestMapping(value = "/users", method = RequestMethod.GET)
    public HashMap users(@RequestParam(value="c_label", required=true) String c_label)throws Exception{
        HashMap<String,String> map = takeNewLable(c_label);
        HashMap<String,String> mapToSend = new HashMap<String, String>();
        mapToSend.put("label",map.get("label"));
        Set<String> setUsers;
        synchronized (CD) {
            setUsers = CD.users.keySet();
            String username = map.get("username");
            AES.setKey(CD.users.get(username));
        }
        for (String setUser : setUsers) {

            mapToSend.put(ecdh.bytesToHex (AES.encrypt(setUser.getBytes())),"");
        }


        return mapToSend;
    }



    @RequestMapping(value = "/check", method = RequestMethod.GET)
    public HashMap check(@RequestParam(value="c_label", required=true) String c_label)throws Exception{ ////// определить пользователя и дать ему новый label


        HashMap<String,String> map = null;

            map = takeNewLable(c_label);



        HashMap<String,String> mapToSend = new HashMap<String, String>();
        mapToSend.put("label",map.get("label"));
        String username = map.get("username");
        // System.out.println(map.get("username"));

        synchronized (CD) {

            Long temp_date=new Date().getTime()/1000;
            ArrayList<String> s_users = new ArrayList<String>();
            for (String s : CD.DeadLockMap.keySet()) {
               if (temp_date- CD.DeadLockMap.get(s)>11){
                   s_users.add(s);
               }
            }

            for (String s_user : s_users) {
                CD.DeadLockMap.remove(s_user);
                CD.users.remove(s_user);
                CD.labels.remove(s_user);


            }

            for (String s : CD.users.keySet()) {
                for (String s_user : s_users) {
                    ArrayList<String> als = new ArrayList<String>();
                    als.add(s);
                    als.add("qweasdfgsergsfgbbgf");
                    als.add(s_user);
                    CD.hswsAL.add(als);
                }
            }
            
            
            
            
            
            AES.setKey(CD.users.get(username));


            for (int i = 0; i < CD.hswsAL.size(); i++) {

                if (CD.hswsAL.get(i).get(0).equals(username)) {
                    //  System.out.println(username+  " zabral ot "   +CD.hswsAL.get(i).get(1));///////////////////////////////////////////////////
                    mapToSend.put("sender", ecdh.bytesToHex(AES.encrypt(CD.hswsAL.get(i).get(1).getBytes())));
                    mapToSend.put("msg", ecdh.bytesToHex(AES.encrypt(CD.hswsAL.get(i).get(2).getBytes())));
                    CD.hswsAL.remove(i);
                    break;/////////////!
                }
            }



                CD.DeadLockMap.put(username,new Date().getTime()/1000);

              

         






        }

        //System.out.println(mapToSend);

        return mapToSend;
    }



 /*   @RequestMapping(value = "/admin", method = RequestMethod.GET)             //////////////// http://localhost:8080/Controller/page?user=alex
    @ResponseBody
    public ArrayList<Map> admin() {

        ArrayList<Map> arrayList = new ArrayList<Map>();
        arrayList.add(CD.users);
        arrayList.add(CD.labels);


        return arrayList;
    }
*/







    HashMap<String, String> takeNewLable(String c_label)throws Exception{

        String label="";
        String username="";
        HashMap<String,String> map = new HashMap<String, String>();



            synchronized (CD) {
                for (Map.Entry<String, byte[]> entry : CD.users.entrySet()) {     //перебираем все aes ключи
                    AES.setKey(entry.getValue());
                    try {
                        label = new String(AES.decrypt(ecdh.parseHexBinary(c_label)), "UTF-8");   // каждым ключём открываем зашифрованную метку
                    } catch (Exception e) {
                        label = "label";
                    }





                    for (Map.Entry<String, String> entryv : CD.labels.entrySet()) {
                        if (label.equals(entryv.getValue())) {
                            username = entryv.getKey();



                        }
                    }
                }

                String uuid = UUID.randomUUID().toString();
                uuid = uuid.replace("-", "");





                CD.labels.put(username, uuid);
                label = username + CD.labels.get(username);
                AES.setKey(CD.users.get(username));
                map.put("label", ecdh.bytesToHex(AES.encrypt(label.getBytes())));




                map.put("username", username);
            }


        return map;
    }











}




   /* @RequestMapping(value = "/page", method = RequestMethod.GET)             //////////////// http://localhost:8080/Controller/page?user=alex
    @ResponseBody
    public Map<String,String> jkhg(@RequestParam(value="user", required=true) String user) {
        Map<String,String> map = new HashMap<String, String>();
        map.put("1","odin");
        map.put("2","dva");
        map.put("3","tri");
        map.put("4",user);
        return map;
    }


    @RequestMapping(value="/method7/{id}") //////////////// http://localhost:8080/Controller/method7/2
    @ResponseBody
    public String method7(@PathVariable("id") int id){
        return "method7 with id="+id;
    }*/
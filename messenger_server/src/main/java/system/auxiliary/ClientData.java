package system.auxiliary;

import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Repository
public class ClientData {

    public ClientData() {
    }

    public  Map<String,byte[]> users = new HashMap<String, byte[]>();

    public Map<String,String> labels = new HashMap<String, String>();

    public Map<String,Long> DeadLockMap = new HashMap<String, Long>();



    public ArrayList<ArrayList<String>> hswsAL = new  ArrayList<ArrayList<String>>();









}

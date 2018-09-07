package system.temp;

import org.bouncycastle.util.encoders.Hex;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;

public class cli {

    public static void main(String[] args) throws Exception{

        int serverPort = 5885;
        String address = "localhost";

        InetAddress ipAddress = InetAddress.getByName(address);
        Socket socket = new Socket(ipAddress, serverPort);


        InputStream sin = socket.getInputStream();
        OutputStream sout = socket.getOutputStream();

        // Конвертируем потоки в другой тип, чтоб легче обрабатывать текстовые сообщения.
        DataInputStream in = new DataInputStream(sin);
        DataOutputStream out = new DataOutputStream(sout);

        ECDH Bob =new ECDH();

        out.write(Bob.dataPubA);
        byte[] alisapub = new byte[33];
        in.read(alisapub);

        byte[] res= Bob.doECDH( Bob.dataPrvA, alisapub);




        byte[] key = new byte[32];

        for (int i = 0; i < key.length; i++) {
            key[i]=res[i];
              }



        CBCAESBouncyCastle cabc = new CBCAESBouncyCastle();
        cabc.setKey(key);

        int sb = in.read();


        byte[] encr=new byte[sb];
        in.read(encr);




        byte[] decr = cabc.decrypt(encr);
        System.out.println("Decrypted[" + decr.length + "]: " + new String(Hex.encode(decr)));


        String output = new String(decr, "UTF-8");
        System.out.println("Output[" + output.length() + "]: " + output);


        socket.close();
    }


}

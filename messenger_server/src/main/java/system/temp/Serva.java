package system.temp;

import org.bouncycastle.util.encoders.Hex;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

import static java.lang.Math.abs;

public class Serva {


    public static void main(String[] args)throws Exception {

        int port = 5885;
        ServerSocket ss = new ServerSocket(port);

        System.out.println("Waiting for a client...");
        Socket socket = ss.accept();
        System.out.println("Got a client :");


        InputStream sin = socket.getInputStream();
        OutputStream sout = socket.getOutputStream();

        DataInputStream in = new DataInputStream(sin);
        DataOutputStream out = new DataOutputStream(sout);




        ECDH Alisa =new ECDH();
        byte[] bobpub = new byte[33];
        in.read(bobpub);
        out.write(Alisa.dataPubA);


        byte[] res= Alisa.doECDH( Alisa.dataPrvA, bobpub);




        byte[] key = new byte[32];






        for (int i = 0; i < key.length; i++) {
            key[i]=res[i];
        }

        CBCAESBouncyCastle cabc = new CBCAESBouncyCastle();
        cabc.setKey(key);

        String input = "This";
        System.out.println("Input[" + input.length() + "]: " + input);

        byte[] plain = input.getBytes("UTF-8");




        int l = plain.length;
        int sb = 32 + 16*(abs(l/16));
        System.out.println(sb);
        out.write(sb);


        System.out.println("Plaintext[" + plain.length + "]: " + new String(Hex.encode(plain)));


        byte[] encr = cabc.encrypt(plain);
        System.out.println("Encrypted[" + encr.length + "]: " + new String(Hex.encode(encr)));

        out.write(encr);

        byte[] decr = cabc.decrypt(encr);
        System.out.println("Decrypted[" + decr.length + "]: " + new String(Hex.encode(decr)));


        String output = new String(decr, "UTF-8");
        System.out.println("Output[" + output.length() + "]: " + output);







socket.close();
    }


}

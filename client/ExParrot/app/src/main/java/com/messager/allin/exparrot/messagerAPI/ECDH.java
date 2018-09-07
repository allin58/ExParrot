package com.messager.allin.exparrot.messagerAPI;

import org.spongycastle.jce.ECNamedCurveTable;
import org.spongycastle.jce.interfaces.ECPrivateKey;
import org.spongycastle.jce.interfaces.ECPublicKey;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.jce.spec.ECParameterSpec;


import org.spongycastle.jce.spec.ECPublicKeySpec;
import org.spongycastle.jce.spec.ECPrivateKeySpec;
/*

import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
*/

import javax.crypto.KeyAgreement;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.ECGenParameterSpec;

public class ECDH {

    final protected static char[] hexArray = "0123456789abcdef".toCharArray();

    String curve="prime256v1";
    KeyPairGenerator kpgen;
    KeyPair pairA;
    public byte [] dataPrvA;
    byte [] dataPubA;




    public ECDH() throws Exception {

        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
        Security.addProvider(new BouncyCastleProvider());

         //kpgen = KeyPairGenerator.getInstance("ECDH", "BC");
         kpgen = KeyPairGenerator.getInstance("ECDH", "SC");
         kpgen.initialize(new ECGenParameterSpec(curve), new SecureRandom());
         KeyPair pairA = kpgen.generateKeyPair();
         dataPrvA = savePrivateKey(pairA.getPrivate());
         dataPubA = savePublicKey(pairA.getPublic());
        }

    public ECDH(String curve)throws Exception  {
        this.curve = curve;
        Security.addProvider(new BouncyCastleProvider());
        //kpgen = KeyPairGenerator.getInstance("ECDH", "BC");
        kpgen = KeyPairGenerator.getInstance("ECDH", "SC");
        kpgen.initialize(new ECGenParameterSpec(curve), new SecureRandom());
        KeyPair pairA = kpgen.generateKeyPair();

        dataPrvA = savePrivateKey(pairA.getPrivate());
        dataPubA = savePublicKey(pairA.getPublic());



    }


    public String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }


    public byte [] savePublicKey (PublicKey key) throws Exception
    {
        //return key.getEncoded();

        ECPublicKey eckey = (ECPublicKey)key;
        return eckey.getQ().getEncoded(true);
    }


    public  PublicKey loadPublicKey (byte [] data) throws Exception
    {
		/*KeyFactory kf = KeyFactory.getInstance("ECDH", "BC");
		return kf.generatePublic(new X509EncodedKeySpec(data));*/

        ECParameterSpec params = ECNamedCurveTable.getParameterSpec(curve);
       // ECPublicKeySpec pubKey = new ECPublicKeySpec(params.getCurve().decodePoint(data), params);
        ECPublicKeySpec pubKey = new ECPublicKeySpec(params.getCurve().decodePoint(data), params);

        KeyFactory kf = KeyFactory.getInstance("ECDH", "SC");
      //  KeyFactory kf = KeyFactory.getInstance("ECDH", "BC");
        return kf.generatePublic(pubKey);
    }


    public  byte [] savePrivateKey (PrivateKey key) throws Exception
    {
        //return key.getEncoded();

        ECPrivateKey eckey = (ECPrivateKey)key;
        return eckey.getD().toByteArray();
    }


    public  PrivateKey loadPrivateKey (byte [] data) throws Exception
    {
        //KeyFactory kf = KeyFactory.getInstance("ECDH", "BC");
        //return kf.generatePrivate(new PKCS8EncodedKeySpec(data));

        ECParameterSpec params = ECNamedCurveTable.getParameterSpec(curve);
        ECPrivateKeySpec prvkey = new ECPrivateKeySpec(new BigInteger(data), params);
      //  KeyFactory kf = KeyFactory.getInstance("ECDH", "BC");
        KeyFactory kf = KeyFactory.getInstance("ECDH", "SC");
        return kf.generatePrivate(prvkey);
    }


    public  byte[] doECDH (byte[] dataPrv, byte[] dataPub) throws Exception
    {
       // KeyAgreement ka = KeyAgreement.getInstance("ECDH", "BC");
        KeyAgreement ka = KeyAgreement.getInstance("ECDH", "SC");
        ka.init(loadPrivateKey(dataPrv));
        ka.doPhase(loadPublicKey(dataPub), true);
        byte [] secret = ka.generateSecret();
        return secret;

    }





    public static byte[] parseHexBinary(String s) {
        final int len = s.length();

        // "111" is not a valid hex encoding.
        if( len%2 != 0 )
            throw new IllegalArgumentException("hexBinary needs to be even-length: "+s);

        byte[] out = new byte[len/2];

        for( int i=0; i<len; i+=2 ) {
            int h = hexToBin(s.charAt(i  ));
            int l = hexToBin(s.charAt(i+1));
            if( h==-1 || l==-1 )
                throw new IllegalArgumentException("contains illegal character for hexBinary: "+s);

            out[i/2] = (byte)(h*16+l);
        }

        return out;
    }

    private static int hexToBin( char ch ) {
        if( '0'<=ch && ch<='9' )    return ch-'0';
        if( 'A'<=ch && ch<='F' )    return ch-'A'+10;
        if( 'a'<=ch && ch<='f' )    return ch-'a'+10;
        return -1;
    }












}


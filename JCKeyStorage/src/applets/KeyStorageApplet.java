/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package applets;

import javacard.framework.*;
import javacard.security.*;
import javacardx.crypto.Cipher;

/**
 * The applet for secure key storage on a smart card.
 * @author Manoja Kumar Das
 * @author Ondrej Mosnacek &lt;omosnacek@gmail.com&gt;
 */
public class KeyStorageApplet extends Applet {
    
    public static final byte[] AID = new byte[] {
        (byte)0x4a, (byte)0x43, (byte)0x4b, (byte)0x65, (byte)0x79, (byte)0x53,
        (byte)0x74, (byte)0x6f, (byte)0x72, (byte)0x61, (byte)0x67, (byte)0x65
    };
    
    public static final byte CLA_KEYSTORAGEAPPLET = (byte)0xB0;
    
    public static final byte INS_GETPUBKEY = (byte)0x50;
    public static final byte INS_HANDSHAKE = (byte)0x51;
    public static final byte INS_COMMAND   = (byte)0x52;
    
    public static final byte CMD_AUTH       = (byte)0x00;
    public static final byte CMD_CHANGEPW   = (byte)0x01;
    public static final byte CMD_GENKEY     = (byte)0x02;
    public static final byte CMD_STOREKEY   = (byte)0x03;
    public static final byte CMD_LOADKEY    = (byte)0x04;
    public static final byte CMD_DELKEY     = (byte)0x05;
    public static final byte CMD_CLOSE      = (byte)0x06;
    
    public static final short RSA_BITS = KeyBuilder.LENGTH_RSA_2048;
    public static final short EC_BITS = KeyBuilder.LENGTH_EC_FP_192;
    
    public static final byte[] KEY_LABEL_ENC = new byte[] { (byte)0xEE };
    public static final byte[] KEY_LABEL_AUTH = new byte[] { (byte)0xAA };
    
    public static final short MAC_LENGTH = 32;
    public static final short SEQNUM_LENGTH = 2;
    public static final short BLOCK_LENGTH = 16;
    public static final short IV_LENGTH = 16;
    
    public static final byte MAX_PW_TRIES = 5;
    public static final byte MAX_PW_LEN = 64;
    
    /* secp192r1, as per http://www.secg.org/sec2-v2.pdf */
    public static final byte[] EC_FP_P = new byte[] {
        (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF,
        (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF,
        (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF,
        (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFE,
        (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF,
        (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF,
    };
    public static final byte[] EC_FP_A = new byte[] {
        (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF,
        (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF,
        (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF,
        (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFE,
        (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF,
        (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFC,
    };
    public static final byte[] EC_FP_B = new byte[] {
        (byte)0x64, (byte)0x21, (byte)0x05, (byte)0x19,
        (byte)0xE5, (byte)0x9C, (byte)0x80, (byte)0xE7,
        (byte)0x0F, (byte)0xA7, (byte)0xE9, (byte)0xAB,
        (byte)0x72, (byte)0x24, (byte)0x30, (byte)0x49,
        (byte)0xFE, (byte)0xB8, (byte)0xDE, (byte)0xEC,
        (byte)0xC1, (byte)0x46, (byte)0xB9, (byte)0xB1,
    };
    public static final byte[] EC_FP_G_x = new byte[] {
        (byte)0x18, (byte)0x8D, (byte)0xA8, (byte)0x0E,
        (byte)0xB0, (byte)0x30, (byte)0x90, (byte)0xF6,
        (byte)0x7C, (byte)0xBF, (byte)0x20, (byte)0xEB,
        (byte)0x43, (byte)0xA1, (byte)0x88, (byte)0x00,
        (byte)0xF4, (byte)0xFF, (byte)0x0A, (byte)0xFD,
        (byte)0x82, (byte)0xFF, (byte)0x10, (byte)0x12,
    };
    public static final byte[] EC_FP_G_y = new byte[] {
        (byte)0x07, (byte)0x19, (byte)0x2B, (byte)0x95,
        (byte)0xFF, (byte)0xC8, (byte)0xDA, (byte)0x78,
        (byte)0x63, (byte)0x10, (byte)0x11, (byte)0xED,
        (byte)0x6B, (byte)0x24, (byte)0xCD, (byte)0xD5,
        (byte)0x73, (byte)0xF9, (byte)0x77, (byte)0xA1,
        (byte)0x1E, (byte)0x79, (byte)0x48, (byte)0x11,
    };
    public static final byte[] EC_FP_R = new byte[] {
        (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF,
        (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF,
        (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF,
        (byte)0x99, (byte)0xDE, (byte)0xF8, (byte)0x36,
        (byte)0x14, (byte)0x6B, (byte)0xC9, (byte)0xB1,
        (byte)0xB4, (byte)0xD2, (byte)0x28, (byte)0x31,
    };
    public static final short EC_FP_K = 1;
    
    private enum State {
        IDLE,
        KEY_ESTABILISHED,
        AUTHENTICATED,
    };
    
    private State state;

    private final OwnerPIN masterPassword;
    
    private final KeyPair signingKeyPair;
    private final Signature signature;
    
    private final ECPublicKey dhPubKey;
    private final ECPrivateKey dhPrivKey;
    private final KeyPair dhKeyPair;
    private final KeyAgreement sessKeyAgreement;
    
    private final AESKey cipherKey;
    private final Cipher cipher;
    
    private final HMACKey macKey;
    private final Signature mac;
    
    private short seqNum;
    
    /* TODO ... */
    
    private KeyStorageApplet(byte[] bArray, short bOffset, byte bLength) throws ISOException {
        state = State.IDLE;
        
        masterPassword = new OwnerPIN(MAX_PW_TRIES, MAX_PW_LEN);
        if (bLength == 0) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        } else {
            /* set master password from install data: */
            masterPassword.update(bArray, bOffset, bLength);
        }
        
        signingKeyPair = new KeyPair(KeyPair.ALG_RSA_CRT, RSA_BITS);
        signingKeyPair.genKeyPair();
        
        signature = Signature.getInstance(Signature.ALG_RSA_SHA_PKCS1, false);
        
        /* prepare an ANSI X9.62 uncompressed EC point representation for G: */
        byte[] gBuf = new byte[(short)1 + (short)EC_FP_G_x.length + (short)EC_FP_G_y.length];
        gBuf[0] = 0x04;
        short off = 1;
        off = Util.arrayCopy(EC_FP_G_x, (short)0, gBuf, off, (short)EC_FP_G_x.length);
        off = Util.arrayCopy(EC_FP_G_y, (short)0, gBuf, off, (short)EC_FP_G_y.length);
        
        /* pre-set basic EC parameters: */
        dhPubKey = (ECPublicKey) KeyBuilder.buildKey(KeyBuilder.TYPE_EC_FP_PUBLIC,  EC_BITS, false);
        dhPubKey.setFieldFP(EC_FP_P, (short)0, (short)EC_FP_P.length);
        dhPubKey.setA(EC_FP_A, (short)0, (short)EC_FP_A.length);
        dhPubKey.setB(EC_FP_B, (short)0, (short)EC_FP_B.length);
        dhPubKey.setG(gBuf,    (short)0, (short)gBuf.length);
        dhPubKey.setR(EC_FP_R, (short)0, (short)EC_FP_R.length);
        dhPubKey.setK(EC_FP_K);

        dhPrivKey = (ECPrivateKey)KeyBuilder.buildKey(KeyBuilder.TYPE_EC_FP_PRIVATE, EC_BITS, false);
        dhPrivKey.setFieldFP(EC_FP_P, (short)0, (short)EC_FP_P.length);
        dhPrivKey.setA(EC_FP_A, (short)0, (short)EC_FP_A.length);
        dhPrivKey.setB(EC_FP_B, (short)0, (short)EC_FP_B.length);
        dhPrivKey.setG(gBuf,    (short)0, (short)gBuf.length);
        dhPrivKey.setR(EC_FP_R, (short)0, (short)EC_FP_R.length);
        dhPrivKey.setK(EC_FP_K);
        
        dhKeyPair = new KeyPair(dhPubKey, dhPrivKey);
        sessKeyAgreement = KeyAgreement.getInstance(KeyAgreement.ALG_EC_SVDP_DHC, false);
        
        cipherKey = (AESKey)KeyBuilder.buildKey(KeyBuilder.TYPE_AES_TRANSIENT_DESELECT,
                KeyBuilder.LENGTH_AES_256, false);
        cipher = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_CBC_NOPAD, false);
        
        macKey = (HMACKey)KeyBuilder.buildKey(KeyBuilder.TYPE_HMAC_TRANSIENT_DESELECT,
                KeyBuilder.LENGTH_HMAC_SHA_256_BLOCK_64, false);
        mac = Signature.getInstance(Signature.ALG_HMAC_SHA_256, false);
        /* TODO: ... */
    }
    
    public final boolean select() {
        state = State.IDLE;
        seqNum = 0;
        /* TODO: ... */
        return true;
    }
     
    /**
     * Method installing the applet.
     * @param bArray the array constaining installation parameters
     * @param bOffset the starting offset in bArray
     * @param bLength the length in bytes of the data parameter in bArray
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) throws ISOException {
        KeyStorageApplet applet = new KeyStorageApplet(bArray, bOffset, bLength);
        applet.register();
    }

    /**
     * Utility method to write the RSA public key into a buffer.
     * (Copied from HW02 solution)
     * @param buffer the output buffer
     * @param offset the output buffer offset
     * @return the size of the data written
     * @throws ISOException
     */
    private short fillPubKey(byte[] buffer, short offset) throws ISOException {
        short totalSize = 0;
        RSAPublicKey pubKey = (RSAPublicKey)signingKeyPair.getPublic();
        
        short modSizeOffset = offset;
        short modOffset = (short)(offset + 2);
        
        short modSize = pubKey.getModulus(buffer, modOffset);
        buffer[modSizeOffset] = (byte)(modSize & 0xFF);
        buffer[modSizeOffset + 1] = (byte)((modSize >> 8) & 0xFF);
        totalSize += 2;
        totalSize += modSize;
        
        short expSizeOffset = (short)(modOffset + modSize);
        short expOffset = (short)(expSizeOffset + 2);
        
        short expSize = pubKey.getExponent(buffer, expOffset);
        buffer[expSizeOffset] = (byte)(expSize & 0xFF);
        buffer[expSizeOffset + 1] = (byte)((expSize >> 8) & 0xFF);
        totalSize += 2;
        totalSize += expSize;
        
        return totalSize;
    }
    
    public final void process(APDU apdu) throws ISOException {
        // ignore the applet select command dispached to the process
        if (selectingApplet())
            return;

        short dataLen = apdu.setIncomingAndReceive();
        byte[] apduBuffer = apdu.getBuffer();
        
        if (apduBuffer[ISO7816.OFFSET_CLA] != CLA_KEYSTORAGEAPPLET) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }
        
        short size;
        switch(apduBuffer[ISO7816.OFFSET_INS]) {
            /* TODO: ... */
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
                break;
        }
    }
    
    private short processCommand(byte[] buffer, short offset, short length) {
        /* TODO */
        /* first byte is the command code; response data should be written
         * to the same buffer and its size returned */
        return -1;
    }
}

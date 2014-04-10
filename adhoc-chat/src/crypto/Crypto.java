package crypto;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import util.Util;

/**
 * 
 * @author Bob Rubbens, One Million Inc.
 * Instantiation provides a public and private key. Call getMyKey() to obtain the public key. Use addClient() and removeClient() to manage clients. Use encrypt() and decrypt() to encrypt and decrypt messages respectively.
 * 
 */
public class Crypto {
	public static final Crypto INSTANCE = new Crypto();
	
	
	public static final int KEY_LENGTH = 1024;
	public static final String ALGORITHM = "RSA";
	
	private Key publicKey;
	private Key privateKey;
	private Cipher myCipher;
	
	private HashMap<Byte, Cipher> ciphers = new HashMap<Byte, Cipher>();
	
	/**
	 * Initializes the crypto class. Provides you with two (secure) randomly generated keys: a private and a public key. To retrieve the public key, use the {@link #getMyKey()} method.
	 */
	private Crypto() {
		try {
			KeyPairGenerator kpg = KeyPairGenerator.getInstance(ALGORITHM);
			kpg.initialize(KEY_LENGTH);
			
			KeyPair kp = kpg.generateKeyPair();
			
			publicKey = kp.getPublic();
			privateKey = kp.getPrivate();
			
			myCipher = Cipher.getInstance(ALGORITHM);
			myCipher.init(Cipher.DECRYPT_MODE, privateKey);
		} catch (NoSuchAlgorithmException e) {
			System.out.println("[ERROR] No such algorithm available");
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			System.out.println("[ERROR] No such padding, whatever that means");
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			System.out.println("[ERROR] Invalid key specified");
			e.printStackTrace();
		}
	}
	
	/**
	 * @see {@link #decrypt(byte, byte[])}
	 */
	public byte[] encrypt(byte client, String receivedMsg) {
		return encrypt(client, receivedMsg.getBytes());
	}
	
	/**
	 * Encrypts a message using the public key of the given client. If an error occurs it will print to the console and return null.
	 * @param client The client who the message is sent to
	 * @param receivedMsg The message to send
	 * @return The encrypted message. Null if an error occurred
	 */
	public byte[] encrypt(byte client, byte[] receivedMsg) {
		if (!ciphers.containsKey(client)) {
			throw new NullPointerException("[Crypto] Client " + client + " unknown");
		} else {
			Cipher clientCipher = ciphers.get(client);
			
			byte[] encryptedMsg = blockCipher(receivedMsg, clientCipher, Cipher.ENCRYPT_MODE);
			
			return encryptedMsg;
		}
	}
	
	/**
	 * Decrypts a message with this instance's private key, assuming it is encrypted with this instances public key.
	 * @param msg The message to decrypt
	 * @return The decrypted message
	 */
	public byte[] decrypt(byte[] msg) {
		byte[] decryptedMsg = blockCipher(msg, myCipher, Cipher.DECRYPT_MODE);
		return decryptedMsg;
	}
	
	// TODO: Random initialisation vector
	/**
	 * Performs a block cipher using the given cipher.
	 * @param input The byte array to encrypt
	 * @param cipher
	 * @param mode
	 * @return
	 */
	public byte[] blockCipher(byte[] input, Cipher cipher, int mode) {
		int length = mode == Cipher.ENCRYPT_MODE ? 100 : 128;
		
		byte[] encryptedBlock = new byte[0];
		byte[] encryptedMsg = new byte[0];
		byte[] buffer = new byte[length];
		
		for (int i = 0; i < input.length; i++) {
			if (i > 0 && i % length == 0) {
				try {
					encryptedBlock = cipher.doFinal(buffer);
				} catch (IllegalBlockSizeException e) {
					System.out.println("[ERROR] Illegal block size");
					e.printStackTrace();
					System.exit(1);
				} catch (BadPaddingException e) {
					System.out.println("[ERROR] Bad padding");
					e.printStackTrace();
					System.exit(1);
				}
				
				encryptedMsg = Util.append(encryptedMsg, encryptedBlock);
				
				int newLength = length;
				if (i + length > input.length) {
					newLength = input.length - i;
				}
				
				buffer = new byte[newLength];
			}
			
			buffer[i % length] = input[i];
		}
		
		try {
			encryptedBlock = cipher.doFinal(buffer);
		} catch (IllegalBlockSizeException e) {
			System.out.println("[ERROR] Illegal block size");
			e.printStackTrace();
			System.exit(1);
		} catch (BadPaddingException e) {
			System.out.println("[ERROR] Bad padding");
			e.printStackTrace();
			System.exit(1);
		}
		
		encryptedMsg = Util.append(encryptedMsg, encryptedBlock);
		
		return encryptedMsg;
	}
	
	/**
	 * @return This instances public key.
	 */
	public byte[] getMyKey() {
		return publicKey.getEncoded();
	}
	
	/**
	 * Adds a given clients public key to the keyring
	 * @param client The client to add.
	 * @param key The public key of the client
	 */
	public void addClient(byte client, byte[] key) {
		if (key == null) {
			throw new NullPointerException("[ERROR] Specified key cannot be null");
		}
		
		if (ciphers.containsKey(client)) {
			System.out.println("[Crypto] Key of client " + client + " replaced");
		} else {
			System.out.println("[Crypto] Key of client " + client + " added");
		}
		
		try {
			Cipher clientCipher = Cipher.getInstance(ALGORITHM);
			
			KeyFactory kf = KeyFactory.getInstance(ALGORITHM);
			X509EncodedKeySpec keySpec = new X509EncodedKeySpec(key);
			PublicKey clientKey = kf.generatePublic(keySpec);
			
			clientCipher.init(Cipher.ENCRYPT_MODE, clientKey);
			
			ciphers.put(client, clientCipher);
			
		} catch (NoSuchAlgorithmException e) {
			System.out.println("[ERROR] Unknown algorithm");
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			System.out.println("[ERROR] No such padding");
			e.printStackTrace();
		} catch (InvalidKeySpecException e) {
			System.out.println("[ERROR] Invalid key specification");
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			System.out.println("[ERROR] Generated key invalid");
			e.printStackTrace();
		}
	}
	
	/**
	 * Removes the client from the keyring. Throws a NullPointerException if the client is unknown 
	 * @param client The client to remove
	 */
	public void removeClient(byte client) {
		if (!ciphers.containsKey(client)) {
			throw new NullPointerException("[ERROR] Client does not exist in keyring");
		} else {
			ciphers.remove(client);
			
			System.out.println("[Crypto] Key of client " + client + " removed");
		}
	}
	
	/**
	 * Test program for the crypto class.
	 * @param args Command line parameters. Unused.
	 */
	public static void main(String[] args) {
		Crypto[] cryptos = new Crypto[]{new Crypto(), new Crypto(), new Crypto()};
		
		cryptos[0].addClient((byte) 1, cryptos[1].getMyKey());
		cryptos[0].addClient((byte) 2, cryptos[2].getMyKey());
		
		cryptos[1].addClient((byte) 0, cryptos[0].getMyKey());
		cryptos[1].addClient((byte) 2, cryptos[2].getMyKey());
		
		cryptos[2].addClient((byte) 0, cryptos[0].getMyKey());
		cryptos[2].addClient((byte) 1, cryptos[1].getMyKey());
		
		String msg = "Foo";
		System.out.println("Short test [Foo]\n");
		
		Crypto.testMsg(0, msg, cryptos);
		Crypto.testMsg(1, msg, cryptos);
		Crypto.testMsg(2, msg, cryptos);
		
		msg = "BarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBar";
		System.out.println("Long test [Bar*lots]\n");
		
		Crypto.testMsg(0, msg, cryptos);
		Crypto.testMsg(1, msg, cryptos);
		Crypto.testMsg(2, msg, cryptos);
	}
	
	/**
	 * Tests if a given client can send an encrypted message to all other clients in the array
	 * @param sender The sending client
	 * @param msg The message to send
	 * @param receivers All receivers in the poll
	 */
	public static void testMsg(int sender, String msg, Crypto[] receivers) {
		System.out.println("Sender's message: " + msg);
		
		byte[] encryptedMsg;
		
		for (int i = 0; i < receivers.length; i++) {
			if (i != sender) {
				encryptedMsg = receivers[sender].encrypt((byte) i, msg);
				
				System.out.println("Encrypted with public key of client " + i + ": " + Util.toHex(encryptedMsg));
				
				System.out.println("Receiver " + i + " result: " + new String(receivers[i].decrypt(encryptedMsg)));
			}
		}
		
		System.out.println();
	}

}

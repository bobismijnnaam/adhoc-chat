package crypto;

import java.security.InvalidAlgorithmParameterException;
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
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import util.Util;

/**
 * 
 * @author Bob Rubbens, One Million Inc. Instantiation provides a public and
 *         private key. Call getMyKey() to obtain the public key. Use
 *         addClient() and removeClient() to manage clients. Use encrypt() and
 *         decrypt() to encrypt and decrypt messages respectively.
 * 
 */
public class Crypto {
	public static final Crypto INSTANCE = new Crypto();

	public static final int KEY_LENGTH = 1024;
	public static final String ALGORITHM = "RSA";
	public static final String CIPHER = "RSA/ECB/PKCS1Padding";

	public static final int SYMMETRIC_KEY_LENGTH = 128;

	// Public Key variables
	private Key publicKey;
	private Key privateKey;
	private Cipher myCipher;
	private HashMap<Byte, Cipher> ciphers = new HashMap<Byte, Cipher>();

	// Symmetric Key variables
	private HashMap<Byte, Cipher> symmetricCiphers = new HashMap<Byte, Cipher>();
	private HashMap<Byte, byte[]> symmetricKeys = new HashMap<Byte, byte[]>();
	private HashMap<Byte, byte[]> symmetricIVs = new HashMap<Byte, byte[]>();

	/**
	 * Initializes the crypto class. Provides you with two (secure) randomly
	 * generated keys: a private and a public key. To retrieve the public key,
	 * use the {@link #getMyKey()} method.
	 */
	private Crypto() {
		try {
			KeyPairGenerator kpg = KeyPairGenerator.getInstance(ALGORITHM);
			kpg.initialize(KEY_LENGTH);

			KeyPair kp = kpg.generateKeyPair();

			publicKey = kp.getPublic();
			privateKey = kp.getPrivate();

			myCipher = Cipher.getInstance(CIPHER);
			myCipher.init(Cipher.DECRYPT_MODE, privateKey);
		} catch (NoSuchAlgorithmException e) {
			System.out.println("[ERROR] No such algorithm available. "
					+ "Crypto could not be initialized: shutting down.");
			e.printStackTrace();
			System.exit(9999);
		} catch (NoSuchPaddingException e) {
			System.out.println("[ERROR] No such padding, whatever that means. "
					+ "Crypto could not be initialized: shutting down.");
			e.printStackTrace();
			System.exit(9999);
		} catch (InvalidKeyException e) {
			System.out.println("[ERROR] Invalid key specified. Crypto could not be initialized: shutting down.");
			e.printStackTrace();
			System.exit(9999);
		}
	}

	/**
	 * @see {@link #decrypt(byte, byte[])}
	 */
	public byte[] encrypt(byte client, String receivedMsg) {
		return encrypt(client, receivedMsg.getBytes());
	}

	/**
	 * Encrypts a message using the public key of the given client. If an error
	 * occurs it will print the error type and possibly the stack trace to the
	 * console and return null.
	 * 
	 * @param client
	 *            The client who the message is sent to
	 * @param receivedMsg
	 *            The message to send
	 * @return The encrypted message. Null if an error occurred
	 */
	public byte[] encrypt(byte client, byte[] receivedMsg) {
		if (!ciphers.containsKey(client)) {
			System.out.println("[ERROR] Client " + client + " unknown");
			return null;
		} else if (receivedMsg == null) {
			System.out.println("[ERROR] Message can't be null");
			return null;
		} else {
			Cipher clientCipher = ciphers.get(client);

			byte[] encryptedMsg = null;

			try {
				encryptedMsg = blockCipher(receivedMsg, clientCipher, Cipher.ENCRYPT_MODE);
			} catch (IllegalBlockSizeException e) {
				System.out.println("[ERROR] Illegal block size");
				e.printStackTrace();
			} catch (BadPaddingException e) {
				System.out.println("[ERROR] Bad padding");
				e.printStackTrace();
			}

			return encryptedMsg;
		}
	}

	/**
	 * Decrypts a message with this instance's private key, assuming it is
	 * encrypted with this instances public key. Returns null & prints error
	 * type and stack trace on error.
	 * 
	 * @param msg
	 *            The message to decrypt
	 * @return The decrypted message
	 */
	public byte[] decrypt(byte[] msg) {
		if (msg == null) {
			System.out.println("[ERROR] Message can't be null");
		}

		byte[] decryptedMsg = null;

		try {
			decryptedMsg = blockCipher(msg, myCipher, Cipher.DECRYPT_MODE);
		} catch (IllegalBlockSizeException e) {
			System.out.println("[ERROR] Illegal block size");
			e.printStackTrace();
		} catch (BadPaddingException e) {
			System.out.println("[ERROR] Bad padding");
			e.printStackTrace();
		}

		return decryptedMsg;
	}

	/**
	 * Performs a block cipher using the given cipher. Possible improvements:
	 * Random initialisation vector
	 * 
	 * @param input
	 *            The byte array to encrypt
	 * @param cipher
	 * @param mode
	 * @return
	 * @throws BadPaddingException
	 *             If this happens you're probably trying to decrypt or encrypt
	 *             with the wrong key
	 * @throws IllegalBlockSizeException
	 *             Same goes for this bad boy here
	 */
	public byte[] blockCipher(byte[] input, Cipher cipher, int mode) throws IllegalBlockSizeException,
			BadPaddingException {
		int length = mode == Cipher.ENCRYPT_MODE ? 100 : 128;

		byte[] encryptedBlock = new byte[0];
		byte[] encryptedMsg = new byte[0];
		byte[] buffer = new byte[Math.min(length, input.length)];

		for (int i = 0; i < input.length; i++) {
			if (i > 0 && i % length == 0) {
				encryptedBlock = cipher.doFinal(buffer);

				encryptedMsg = Util.append(encryptedMsg, encryptedBlock);

				int newLength = length;
				if (i + length > input.length) {
					newLength = input.length - i;
				}

				buffer = new byte[newLength];
			}

			buffer[i % length] = input[i];
		}

		encryptedBlock = cipher.doFinal(buffer);

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
	 * 
	 * @param client
	 *            The client to add.
	 * @param key
	 *            The public key of the client
	 */
	public void addClient(byte client, byte[] key) {
		if (key == null) {
			System.out.println("[ERROR] Specified key cannot be null");
			return;
		}

		if (ciphers.containsKey(client)) {
			System.out.println("[Crypto] Key of client " + client + " replaced");
		} else {
			System.out.println("[Crypto] Key of client " + client + " added");
		}

		try {
			Cipher clientCipher = Cipher.getInstance(CIPHER);

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
	 * Removes the client from the keyring. Throws a NullPointerException if the
	 * client is unknown
	 * 
	 * @param client
	 *            The client to remove
	 */
	public void removeClient(byte client) {
		if (!ciphers.containsKey(client)) {
			System.out.println("[ERROR] Client unknown to crypto (have you forgot adding the client on connect?)");
			return;
		} else {
			ciphers.remove(client);

			System.out.println("[Crypto] Key of client " + client + " removed");
		}
	}

	/**
	 * Start a session with given parameters (which you receive from another
	 * client). If you use this initializer Crypto expects you to want to
	 * decrypt data.
	 * 
	 * @param client
	 *            The client who started the session
	 * @param key
	 *            The key used for this session
	 * @param iv
	 *            The initialization vector for this session
	 */
	public void startSession(byte client, byte[] key, byte[] iv) {
		startSession(client, key, iv, Cipher.DECRYPT_MODE);
	}

	/**
	 * Start a session and generate IV & session key. Retrieve these with
	 * getSessionKey() and getSessionIV(). If you use this initializer Crypto
	 * expects you to want to encrypt data.
	 * 
	 * @param client
	 *            The client with whom you want to initialize a session
	 */
	public void startSession(byte client) {
		try {
			KeyGenerator keyGen = KeyGenerator.getInstance("AES");
			keyGen.init(SYMMETRIC_KEY_LENGTH);
			SecretKey k = keyGen.generateKey();

			startSession(client, k.getEncoded(), null, Cipher.ENCRYPT_MODE);

		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Initialize the variables needed for a session. Is used internally.
	 * 
	 * @param client
	 *            The client with whom the session is shared
	 * @param key
	 *            The key for this session
	 * @param iv
	 *            The IV for this session
	 * @param cipherMode
	 *            The cipher mode (receiving = decrypt, sending = encrypt)
	 */
	private void startSession(byte client, byte[] key, byte[] iv, int cipherMode) {
		if (symmetricCiphers.containsKey(client)) {
			System.out.println("[Crypto] Session with client " + client
					+ " detected! Overwriting old session information");
		}

		try {
			Cipher c = Cipher.getInstance("AES/CBC/PKCS5PADDING");

			SecretKeySpec k = new SecretKeySpec(key, "AES");
			if (iv == null) {
				c.init(cipherMode, k);
			} else {
				c.init(cipherMode, k, new IvParameterSpec(iv));
			}

			symmetricCiphers.put(client, c);
			symmetricKeys.put(client, key);
			symmetricIVs.put(client, c.getIV());
		} catch (NoSuchAlgorithmException e) {
			System.out.println("[ERROR] No such algorithm");
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			System.out.println("[ERROR] No such padding");
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			System.out.println("[ERROR] Invalid key");
			e.printStackTrace();
		} catch (InvalidAlgorithmParameterException e) {
			System.out.println("[ERROR] Invalid algorithm parameters");
			e.printStackTrace();
		}

		System.out.println("[Crypto] Session started with client " + client);
	}

	/**
	 * Returns the session key with a given client. Prints a warning & returns
	 * null on error
	 * 
	 * @param client
	 *            The client with whom the session is shared
	 * @return The session key
	 */
	public byte[] getSessionKey(byte client) {
		if (symmetricKeys.containsKey(client)) {
			return symmetricKeys.get(client);
		} else {
			System.out.println("[Crypto] No known session with client " + client);
			return null;
		}
	}

	/**
	 * Returns the IV for a given session. Prints a (clear) warning & returns
	 * null on error
	 * 
	 * @param client
	 *            The client with whom the session is shared
	 * @return The session IV
	 */
	public byte[] getSessionIV(byte client) {
		if (symmetricIVs.containsKey(client)) {
			return symmetricIVs.get(client);
		} else {
			System.out.println("[Crypto] No known session with client " + client);
			return null;
		}
	}

	/**
	 * Encrypts or decrypts given byte[]. Depends on which startSession was used
	 * (see their respective descriptions). Will print a warning & return null
	 * on error. This function can be used until endSession() is called
	 * 
	 * @param client
	 *            The client with whom the session is shared -
	 * @param msg
	 *            The message to en-/decrypt
	 * @return The en-/decoded message
	 */
	public byte[] executeSession(byte client, byte[] msg) {
		try {
			Cipher c = symmetricCiphers.get(client);
			return c.doFinal(msg);
		} catch (IllegalBlockSizeException e) {
			System.out.println("[ERROR] Illegal block size");
			e.printStackTrace();
			return null;
		} catch (BadPaddingException e) {
			System.out.println("[ERROR] Bad padding");
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Removes all entries related to the session shared with the given client.
	 * 
	 * @param client
	 *            The client with whom the session was shared.
	 */
	public void endSession(byte client) {
		if (symmetricCiphers.containsKey(client)) {
			symmetricCiphers.remove(client);
			symmetricKeys.remove(client);
			symmetricKeys.remove(client);

			System.out.println("[Crypto] Session with client " + client + " ended");
		} else {
			System.out.println("[Crypto] No known session with client " + client);
		}
	}

	/**
	 * Test program for the crypto class.
	 * 
	 * @param args
	 *            Command line parameters. Unused.
	 */
	public static void main(String[] args) {
		// PK TEST
		System.out.println("[PK TEST]\n");
		Crypto[] cryptos = new Crypto[] { new Crypto(), new Crypto(), new Crypto() };

		cryptos[0].addClient((byte) 1, cryptos[1].getMyKey());
		cryptos[0].addClient((byte) 2, cryptos[2].getMyKey());

		cryptos[1].addClient((byte) 0, cryptos[0].getMyKey());
		cryptos[1].addClient((byte) 2, cryptos[2].getMyKey());

		cryptos[2].addClient((byte) 0, cryptos[0].getMyKey());
		cryptos[2].addClient((byte) 1, cryptos[1].getMyKey());

		String msg = "Foo";
		System.out.println("\nShort test [Foo]\n");

		Crypto.testMsg(0, msg, cryptos);
		Crypto.testMsg(1, msg, cryptos);
		Crypto.testMsg(2, msg, cryptos);

		msg = "BarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBar"
				+ "BarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBar"
				+ "BarBarBarBarBarBarBarBarBarBasrBarBarBarBarBarBarBarBarBarBarBarBarBarBar"
				+ "BarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBarBar"
				+ "BarBarBarBarBarBarBarBarBar";
		System.out.println("Long test [Bar*lots]\n");

		Crypto.testMsg(0, msg, cryptos);
		Crypto.testMsg(1, msg, cryptos);
		Crypto.testMsg(2, msg, cryptos);

		// SYMMETRIC TEST
		System.out.println("[SYMMETRIC TEST]");

		System.out.println("\nShort test [Foo]\n");
		testSymmetricMsg("Foo");
		System.out.println("Long test [Bar*lots]\n");
		testSymmetricMsg(msg);
	}

	/**
	 * Tests if a given client can send an encrypted message to all other
	 * clients in the array
	 * 
	 * @param sender
	 *            The sending client
	 * @param msg
	 *            The message to send
	 * @param receivers
	 *            All receivers in the poll
	 */
	public static void testMsg(int sender, String msg, Crypto[] receivers) {
		System.out.println("Sender's message: " + msg);

		byte[] encryptedMsg;

		for (int i = 0; i < receivers.length; i++) {
			if (i != sender) {
				encryptedMsg = receivers[sender].encrypt((byte) i, msg);
				String result = new String(receivers[i].decrypt(encryptedMsg));

				System.out.println("Receiver " + i + "'s " + (result.equals(msg) ? "correct" : "incorrect")
						+ " result: \"" + result + "\"");
			}
		}

		System.out.println();
	}

	public static void testSymmetricMsg(String msg) {
		System.out.println("Sender's message: " + msg);
		Crypto[] cryptos = new Crypto[] { new Crypto(), new Crypto() };

		cryptos[0].startSession((byte) 1);
		cryptos[1].startSession((byte) 0, cryptos[0].getSessionKey((byte) 1), cryptos[0].getSessionIV((byte) 1));

		byte[] encryptedMsg = cryptos[0].executeSession((byte) 1, msg.getBytes());

		byte[] decryptedMsg = cryptos[1].executeSession((byte) 0, encryptedMsg);
		String decryptedString = new String(decryptedMsg);

		System.out.println("Receiver's " + (decryptedString.equals(msg) ? "correct" : "incorrect") + " Result: \""
				+ decryptedString + "\"");

		cryptos[0].endSession((byte) 1);
		cryptos[1].endSession((byte) 0);

		System.out.println();
	}
}

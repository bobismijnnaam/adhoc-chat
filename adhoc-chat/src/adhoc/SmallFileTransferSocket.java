package adhoc;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import adhoc.AdhocSocket.AdhocListener;

public class SmallFileTransferSocket implements AdhocListener {

	private static final String RECEIVED_PREFIX = "received_";
	private ReliableSocket socket;

	public static void main(String[] args) {
		try {
			SmallFileTransferSocket sock = new SmallFileTransferSocket(new ReliableSocket("willem1"));

//			sock.sendFile("cheatcodes.txt");
//			sock.sendFile("toobig.txt");

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public SmallFileTransferSocket(ReliableSocket socket) {
		this.socket = socket;
		socket.addListener(this);
	}

	// filename in root (relative)
	public void sendFile(String filename) {
		BufferedInputStream bufStream = null;
		try {
			File file = new File(filename);
			FileInputStream fis;
			fis = new FileInputStream(file);
			bufStream = new BufferedInputStream(fis);

			byte[] toSend = new byte[bufStream.available()];
			bufStream.read(toSend);

			ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
			DataOutputStream dataStream = new DataOutputStream(byteStream);
			dataStream.writeUTF(file.getName());
			dataStream.writeUTF(new String(toSend, "UTF-8"));

			socket.send((byte) 1, Packet.TYPE_FILE, byteStream.toByteArray());

			bufStream.close();
		} catch (FileNotFoundException e) {
			System.out.println("File " + filename + " not found in project root");
		} catch (IOException c) {
			c.printStackTrace();
		}
	}

	@Override
	public void onReceive(Packet packet) {

		try {
			ByteArrayInputStream byteStream = new ByteArrayInputStream(packet.getData());
			DataInputStream dataStream = new DataInputStream(byteStream);

			String filename = dataStream.readUTF();
			String filecontents = dataStream.readUTF();

			File file = new File(RECEIVED_PREFIX + filename);
			FileOutputStream fis;
			fis = new FileOutputStream(file);
			BufferedOutputStream bufStream = new BufferedOutputStream(fis);

			bufStream.write(filecontents.getBytes("UTF-8"));
			bufStream.flush();

		}catch(EOFException e){
			System.out.println("File too large :(");
			e.printStackTrace();
		}
			catch (IOException e) {
			System.out.println("Couldnt write file ");
			e.printStackTrace();
		}
	}

	@Override
	public void newConnection(Connection connection) {

	}

	@Override
	public void removedConnection(Connection connection) {

	}

}

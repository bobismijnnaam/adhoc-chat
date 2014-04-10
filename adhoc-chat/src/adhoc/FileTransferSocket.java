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

public class FileTransferSocket implements AdhocListener {

	private static final String RECEIVED_PREFIX = "received_";
	private static final int MAX_DATA_SIZE = 256; // bytes
	private ReliableSocket socket;

	public static void main(String[] args) {
		try {
			FileTransferSocket sock = new FileTransferSocket(new ReliableSocket("willem", new byte[] { 0, 0, 0, 0 }));

			// sock.sendFile("cheatcodes.txt", (byte) 1);
			// sock.sendFile("toobig.txt", (byte) 1);
			sock.sendFile("space.jpg", (byte) 1);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public FileTransferSocket(ReliableSocket socket) {
		this.socket = socket;
		socket.addListener(this);
	}

	// filename in root (relative)
	public void sendFile(String filename, byte destination) {
		BufferedInputStream bufStream = null;
		try {
			File file = new File(filename);
			FileInputStream fis;
			fis = new FileInputStream(file);
			bufStream = new BufferedInputStream(fis);

			int orderNr = 0;
			byte[] toSend = new byte[MAX_DATA_SIZE];
			while (bufStream.available() > 0) {
				bufStream.read(toSend);

				ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
				DataOutputStream dataStream = new DataOutputStream(byteStream);
				dataStream.writeUTF(file.getName());
				dataStream.writeInt(orderNr++);
				dataStream.writeUTF(new String(toSend, "UTF-8"));

				socket.send(destination, Packet.TYPE_FILE, byteStream.toByteArray());
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
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
			int orderNr = dataStream.readInt();
			String filecontents = dataStream.readUTF();

			File file = new File(RECEIVED_PREFIX + filename);
			FileOutputStream fos;

			if (orderNr == 0) {
				fos = new FileOutputStream(file, false);
			} else {
				fos = new FileOutputStream(file, true);
			}
			BufferedOutputStream bufStream = new BufferedOutputStream(fos);

			bufStream.write(filecontents.getBytes("UTF-8"));
			bufStream.flush();

		} catch (EOFException e) {
			System.out.println("File too large :(");
			e.printStackTrace();
		} catch (IOException e) {
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

package adhoc;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Random;

import adhoc.AdhocSocket.AdhocListener;
import crypto.Crypto;

public class DuplicatesTest {
	public static void main(String[] args) throws IOException, InterruptedException {
		boolean sender = true;
		final byte dest = 1;

		if (sender) {
			ReliableSocket socket = new ReliableSocket("sender", Crypto.INSTANCE.getMyKey());
			Random random = new Random();

			while (true) {
				ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
				DataOutputStream dataStream = new DataOutputStream(byteStream);

				dataStream.writeInt(random.nextInt());

				socket.send(dest, (byte) 5, byteStream.toByteArray());

				Thread.sleep(5);
			}
		} else {
			ReliableSocket socket = new ReliableSocket("receiver", Crypto.INSTANCE.getMyKey());

			socket.addListener(new AdhocListener() {
				private int prevData;

				@Override
				public void removedConnection(Connection connection) {
				}

				@Override
				public void onReceive(Packet packet) {
					if (packet.getType() == 5 && packet.getSourceAddress() == dest) {
						try {
							int data = packet.getDataInputStream().readInt();

							if (data == prevData) {
								System.out.println("Duplicate Packet!");
							}

							prevData = data;
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}

				@Override
				public void newConnection(Connection connection) {
				}
			});
		}
	}
}

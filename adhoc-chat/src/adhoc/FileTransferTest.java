package adhoc;

import java.io.IOException;
import java.util.Random;

import adhoc.FileTransferSocket.FileTransferListener;
import crypto.Crypto;

public class FileTransferTest implements FileTransferListener {

	public static void main(String[] args) {
		// new FileTransferTest().sender();
		new FileTransferTest().receiver();
	}

	FileTransferSocket socket;

	public FileTransferTest() {
		try {
			socket = new FileTransferSocket(
					new AdhocSocket("wiii" + new Random().nextInt(), Crypto.INSTANCE.getMyKey()));
			socket.addListener(this);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void sender() {
		try {
			socket.makeOffer((byte) 4, "Firefox_wallpaper.png");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void receiver() {

	}

	@Override
	public void onReceiveDownloadOffer(Download downloadOffer) {
		System.out.println("RECEIVED OFFER");
		socket.respondOffer(downloadOffer, true);
	}

	@Override
	public void onFileTransferComplete(Download download) {
		System.out.println("complete");
	}

	@Override
	public void onOfferRejected(Upload upload) {
		System.out.println(" :((( ");

	}

}

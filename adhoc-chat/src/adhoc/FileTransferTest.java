package adhoc;

import java.io.IOException;

import adhoc.FileTransferSocket.Download;
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
			socket = new FileTransferSocket(new AdhocSocket("sdfadameaeea", Crypto.INSTANCE.getMyKey()));
			socket.addListener(this);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void sender() {
		try {
			socket.makeOffer((byte) 2, "SPACE.jpg");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void receiver() {

	}

	@Override
	public void onReceiveDownloadOffer(Download downloadOffer) {
		socket.respondOffer(downloadOffer, true);
	}

	@Override
	public void onFileTransferComplete(Download download) {
		System.out.println("complete");
	}

	@Override
	public void onOfferRejected(Download download) {
		System.out.println("offer rejected/timed-out");
	}

}

package adhoc;

import java.awt.Toolkit;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

public class Main {

	public Main() {
		Toolkit.getDefaultToolkit().beep();
	}
	
	public static void main(String[] args) {
		try {
			byte[] address = InetAddress.getLocalHost().getAddress();
			System.out.println(Arrays.toString(address));
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
